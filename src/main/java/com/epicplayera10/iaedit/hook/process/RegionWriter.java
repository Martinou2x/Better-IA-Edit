package com.epicplayera10.iaedit.hook.process;

import dev.lone.itemsadder.api.CustomBlock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public final class RegionWriter {

    private record Operation(Location location, CustomBlock place) {}

    private static final Queue<Operation> QUEUE = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger FAILURES = new AtomicInteger();

    private static Plugin plugin;
    private static BukkitTask task;
    private static int perTick = 20000;
    private static boolean restoreLight = true;

    private RegionWriter() {}

    public static void start(Plugin owner, int operationsPerTick, boolean light) {
        stop();
        plugin = owner;
        perTick = Math.max(100, operationsPerTick);
        restoreLight = light;
        task = Bukkit.getScheduler().runTaskTimer(owner, RegionWriter::drain, 1L, 1L);
    }

    public static void stop() {
        if (task != null) task.cancel();
        task = null;
        while (!QUEUE.isEmpty()) drain();
    }

    public static void remove(Location location) {
        QUEUE.add(new Operation(location, null));
    }

    public static void place(CustomBlock customBlock, Location location) {
        QUEUE.add(new Operation(location, customBlock));
    }

    public static int pending() {
        return QUEUE.size();
    }

    private static void drain() {
        for (int done = 0; done < perTick; done++) {
            Operation operation = QUEUE.poll();
            if (operation == null) return;
            try {
                apply(operation);
            } catch (RuntimeException failure) {
                if (FAILURES.getAndIncrement() < 5 && plugin != null) {
                    plugin.getLogger().log(Level.WARNING, "Could not update ItemsAdder custom region at " + operation.location(), failure);
                }
            }
        }
    }

    private static void apply(Operation operation) {
        Location location = operation.location();
        if (location.getWorld() == null) return;
        if (operation.place() == null) {
            CustomBlock.Advanced.removeFromCustomRegion(location);
            return;
        }
        CustomBlock.Advanced.placeInCustomRegion(operation.place(), location);
        if (!restoreLight) return;
        int light = operation.place().getOriginalLightLevel();
        if (light <= 0) return;
        CustomBlock placed = CustomBlock.byAlreadyPlaced(location.getBlock());
        if (placed != null) placed.setCurrentLightLevel(light);
    }
}
