package com.epicplayera10.iaedit.command;

import com.epicplayera10.iaedit.IAEdit;
import com.epicplayera10.iaedit.hook.process.RegionWriter;
import com.epicplayera10.iaedit.utils.IACache;
import com.epicplayera10.iaedit.utils.SpawnerNbt;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import dev.lone.itemsadder.api.CustomBlock;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

final class FixTask extends BukkitRunnable {

    private static final Set<Material> CUSTOM_TYPES = EnumSet.of(Material.NOTE_BLOCK, Material.MUSHROOM_STEM,
            Material.BROWN_MUSHROOM_BLOCK, Material.RED_MUSHROOM_BLOCK, Material.CHORUS_PLANT, Material.TRIPWIRE, Material.SPAWNER);
    private static final double TICK_MILLIS = 50.0;
    private static final double HEADROOM_MILLIS = 5.0;

    private final IAEdit plugin;
    private final Player player;
    private final World world;
    private final com.sk89q.worldedit.world.World weWorld;
    private final Region region;
    private final boolean cuboid;
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private final long[] chunks;
    private final double maxMillis;
    private final int maxInFlight;
    private final Set<Material> customTypes = EnumSet.copyOf(CUSTOM_TYPES);
    private final Queue<Chunk> loaded = new ConcurrentLinkedQueue<>();
    private final Queue<BlockVector3> spawners = new ConcurrentLinkedQueue<>();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger scanned = new AtomicInteger();
    private final AtomicInteger fixed = new AtomicInteger();
    private final AtomicInteger cleaned = new AtomicInteger();
    private final Runnable onFinish;
    private int next;
    private int ticks;

    FixTask(IAEdit plugin, Player player, Region region, Runnable onFinish) {
        this.plugin = plugin;
        this.player = player;
        this.world = player.getWorld();
        this.weWorld = BukkitAdapter.adapt(world);
        this.region = region;
        this.cuboid = region instanceof CuboidRegion;
        this.onFinish = onFinish;
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        this.minX = min.x();
        this.minZ = min.z();
        this.maxX = max.x();
        this.maxZ = max.z();
        this.minY = Math.max(min.y(), world.getMinHeight());
        this.maxY = Math.min(max.y(), world.getMaxHeight() - 1);
        int chunkMinX = minX >> 4, chunkMaxX = maxX >> 4, chunkMinZ = minZ >> 4, chunkMaxZ = maxZ >> 4;
        this.chunks = new long[(chunkMaxX - chunkMinX + 1) * (chunkMaxZ - chunkMinZ + 1)];
        int index = 0;
        for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
            for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) chunks[index++] = ((long) cx << 32) | (cz & 0xffffffffL);
        }
        this.maxMillis = Math.max(1.0, plugin.getConfig().getDouble("fix-max-ms-per-tick", 8.0));
        this.maxInFlight = Math.max(1, plugin.getConfig().getInt("fix-chunks-in-flight", 8));
        for (Material material : Material.values()) {
            if (material.isBlock() && !material.isLegacy() && IACache.isBaseMaterial(material)) customTypes.add(material);
        }
    }

    int chunkCount() {
        return chunks.length;
    }

    void stop() {
        if (!isCancelled()) cancel();
    }

    @Override
    public void run() {
        long start = System.nanoTime();
        long budget = (long) (budgetMillis() * 1_000_000L);
        while (next < chunks.length && inFlight.get() < maxInFlight) requestChunk(chunks[next++]);
        Chunk chunk;
        while (System.nanoTime() - start < budget && (chunk = loaded.poll()) != null) prepare(chunk);
        BlockVector3 spawner;
        while (System.nanoTime() - start < budget && (spawner = spawners.poll()) != null) checkSpawner(spawner);
        if (++ticks % 20 == 0) progress();
        if (next >= chunks.length && inFlight.get() == 0 && loaded.isEmpty() && spawners.isEmpty()) finish();
    }

    private double budgetMillis() {
        double mspt;
        try {
            mspt = Bukkit.getServer().getAverageTickTime();
        } catch (NoSuchMethodError ignored) {
            mspt = 0;
        }
        return Math.max(1.0, Math.min(maxMillis, TICK_MILLIS - HEADROOM_MILLIS - mspt));
    }

    private void requestChunk(long packed) {
        int cx = (int) (packed >> 32);
        int cz = (int) packed;
        inFlight.incrementAndGet();
        world.getChunkAtAsync(cx, cz).whenComplete((chunk, failure) -> {
            if (chunk == null || failure != null) {
                inFlight.decrementAndGet();
                scanned.incrementAndGet();
                return;
            }
            chunk.addPluginChunkTicket(plugin);
            loaded.add(chunk);
        });
    }

    private void prepare(Chunk chunk) {
        ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
        chunk.removePluginChunkTicket(plugin);
        Map<Long, String> registered = new HashMap<>();
        try {
            for (Map.Entry<Location, String> entry : CustomBlock.Advanced.getAllBlocksLocations(chunk).entrySet()) {
                Location location = entry.getKey();
                int x = location.getBlockX(), y = location.getBlockY(), z = location.getBlockZ();
                if (inside(x, y, z)) registered.put(pack(x, y, z), entry.getValue());
            }
        } catch (RuntimeException ignored) {
            registered.clear();
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                scan(snapshot, registered);
            } finally {
                scanned.incrementAndGet();
                inFlight.decrementAndGet();
            }
        });
    }

    private void scan(ChunkSnapshot snapshot, Map<Long, String> registered) {
        int baseX = snapshot.getX() << 4;
        int baseZ = snapshot.getZ() << 4;
        int fromX = Math.max(minX, baseX) - baseX, toX = Math.min(maxX, baseX + 15) - baseX;
        int fromZ = Math.max(minZ, baseZ) - baseZ, toZ = Math.min(maxZ, baseZ + 15) - baseZ;
        for (int y = minY; y <= maxY; y++) {
            for (int x = fromX; x <= toX; x++) {
                for (int z = fromZ; z <= toZ; z++) {
                    Material type = snapshot.getBlockType(x, y, z);
                    if (!customTypes.contains(type)) continue;
                    int worldX = baseX + x, worldZ = baseZ + z;
                    if (!cuboid && !region.contains(BlockVector3.at(worldX, y, worldZ))) continue;
                    String current = registered.remove(pack(worldX, y, worldZ));
                    if (type == Material.SPAWNER) {
                        spawners.add(BlockVector3.at(worldX, y, worldZ));
                        continue;
                    }
                    CustomBlock expected = IACache.getCustomBlockByBlockData(snapshot.getBlockData(x, y, z));
                    if (expected == null || expected.getNamespacedID().equals(current)) continue;
                    RegionWriter.place(expected, new Location(world, worldX, y, worldZ));
                    fixed.incrementAndGet();
                }
            }
        }
        for (Long key : registered.keySet()) {
            RegionWriter.remove(unpack(key));
            cleaned.incrementAndGet();
        }
    }

    private void checkSpawner(BlockVector3 position) {
        try {
            CustomBlock expected = SpawnerNbt.read(weWorld.getFullBlock(position).getNbtData());
            if (expected == null) return;
            Location location = new Location(world, position.x(), position.y(), position.z());
            if (expected.getNamespacedID().equals(CustomBlock.Advanced.getInCustomRegion(location))) return;
            RegionWriter.place(expected, location);
            fixed.incrementAndGet();
        } catch (RuntimeException ignored) {
            return;
        }
    }

    private boolean inside(int x, int y, int z) {
        if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) return false;
        return cuboid || region.contains(BlockVector3.at(x, y, z));
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private Location unpack(long key) {
        int x = (int) (key >> 38);
        int z = (int) (key << 26 >> 38);
        int y = (int) (key << 52 >> 52);
        return new Location(world, x, y, z);
    }

    private void progress() {
        if (!player.isOnline()) return;
        player.sendActionBar(plugin.message("fix-progress",
                Placeholder.unparsed("done", String.valueOf(scanned.get())),
                Placeholder.unparsed("total", String.valueOf(chunks.length)),
                Placeholder.unparsed("fixed", String.valueOf(fixed.get())),
                Placeholder.unparsed("cleaned", String.valueOf(cleaned.get()))));
    }

    private void finish() {
        stop();
        onFinish.run();
        if (!player.isOnline()) return;
        player.sendMessage(plugin.message("fix-done",
                Placeholder.unparsed("fixed", String.valueOf(fixed.get())),
                Placeholder.unparsed("cleaned", String.valueOf(cleaned.get()))));
    }
}
