package com.epicplayera10.iaedit.command;

import com.epicplayera10.iaedit.IAEdit;
import com.epicplayera10.iaedit.hook.CustomBlocksFactory;
import com.epicplayera10.iaedit.hook.process.RegionWriter;
import com.epicplayera10.iaedit.utils.IACache;
import com.epicplayera10.iaedit.utils.SpawnerNbt;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BaseBlock;
import dev.lone.itemsadder.api.CustomBlock;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class IAEditCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("fix", "info", "reload");

    private final IAEdit plugin;
    private final Map<UUID, FixTask> running = new ConcurrentHashMap<>();

    public IAEditCommand(IAEdit plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info" -> info(sender);
            case "reload" -> reload(sender);
            case "fix" -> fix(sender);
            default -> sender.sendMessage(plugin.message("usage", Placeholder.unparsed("label", label)));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) return List.of();
        String start = args[0].toLowerCase(Locale.ROOT);
        return SUBCOMMANDS.stream().filter(name -> name.startsWith(start)).toList();
    }

    public void cancelAll() {
        running.values().forEach(FixTask::stop);
        running.clear();
    }

    private void info(CommandSender sender) {
        sender.sendMessage(plugin.message("info",
                Placeholder.unparsed("version", plugin.getDescription().getVersion()),
                Placeholder.unparsed("blocks", String.valueOf(IACache.size())),
                Placeholder.unparsed("pending", String.valueOf(RegionWriter.pending())),
                Placeholder.unparsed("engine", plugin.isFaweEnabled() ? "FastAsyncWorldEdit" : "WorldEdit")));
    }

    private void reload(CommandSender sender) {
        plugin.applyConfig();
        IACache.initIfLoaded(plugin.getLogger());
        sender.sendMessage(plugin.message("reloaded", Placeholder.unparsed("blocks", String.valueOf(IACache.size()))));
    }

    private void fix(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("players-only"));
            return;
        }
        if (running.containsKey(player.getUniqueId())) {
            sender.sendMessage(plugin.message("fix-running"));
            return;
        }
        Region region;
        try {
            region = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player))
                    .getSelection(BukkitAdapter.adapt(player.getWorld()));
        } catch (IncompleteRegionException missing) {
            sender.sendMessage(plugin.message("no-selection"));
            return;
        }
        long volume = region.getVolume();
        long max = plugin.getConfig().getLong("fix-max-blocks", 5_000_000L);
        if (volume > max) {
            sender.sendMessage(plugin.message("selection-too-big", Placeholder.unparsed("max", String.valueOf(max))));
            return;
        }
        sender.sendMessage(plugin.message("fix-started", Placeholder.unparsed("blocks", String.valueOf(volume))));
        FixTask task = new FixTask(player, player.getWorld(), region.iterator(), plugin.getConfig().getInt("fix-blocks-per-tick", 50_000));
        running.put(player.getUniqueId(), task);
        task.runTaskTimer(plugin, 1L, 1L);
    }

    private final class FixTask extends BukkitRunnable {

        private final Player player;
        private final World world;
        private final com.sk89q.worldedit.world.World weWorld;
        private final Iterator<BlockVector3> positions;
        private final int perTick;
        private int fixed;
        private int cleaned;

        private FixTask(Player player, World world, Iterator<BlockVector3> positions, int perTick) {
            this.player = player;
            this.world = world;
            this.weWorld = BukkitAdapter.adapt(world);
            this.positions = positions;
            this.perTick = Math.max(1000, perTick);
        }

        @Override
        public void run() {
            for (int done = 0; done < perTick && positions.hasNext(); done++) {
                check(positions.next());
            }
            if (positions.hasNext()) return;
            stop();
            running.remove(player.getUniqueId());
            if (player.isOnline()) {
                player.sendMessage(plugin.message("fix-done",
                        Placeholder.unparsed("fixed", String.valueOf(fixed)),
                        Placeholder.unparsed("cleaned", String.valueOf(cleaned))));
            }
        }

        private void stop() {
            if (!isCancelled()) cancel();
        }

        private void check(BlockVector3 position) {
            if (position.y() < world.getMinHeight() || position.y() >= world.getMaxHeight()) return;
            Block block = world.getBlockAt(position.x(), position.y(), position.z());
            Material type = block.getType();
            Location location = block.getLocation();
            boolean customType = IACache.isBaseMaterial(type) || CustomBlocksFactory.isCustomBlockType(BukkitAdapter.asBlockType(type));
            if (!customType) {
                if (registeredAt(location) != null) {
                    RegionWriter.remove(location);
                    cleaned++;
                }
                return;
            }
            CustomBlock expected = expected(block, position, type);
            if (expected == null || expected.getNamespacedID().equals(registeredAt(location))) return;
            RegionWriter.place(expected, location);
            fixed++;
        }

        private CustomBlock expected(Block block, BlockVector3 position, Material type) {
            try {
                if (type == Material.SPAWNER) {
                    BaseBlock full = weWorld.getFullBlock(position);
                    return SpawnerNbt.read(full.getNbtData());
                }
                return IACache.getCustomBlockByBlockData(block.getBlockData());
            } catch (RuntimeException ignored) {
                return null;
            }
        }

        private String registeredAt(Location location) {
            try {
                return CustomBlock.Advanced.getInCustomRegion(location);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }
}
