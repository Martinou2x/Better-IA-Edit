package com.epicplayera10.iaedit;

import com.epicplayera10.iaedit.command.IAEditCommand;
import com.epicplayera10.iaedit.hook.CustomBlocksFactory;
import com.epicplayera10.iaedit.hook.listener.WorldEditListener;
import com.epicplayera10.iaedit.hook.process.PasteTracker;
import com.epicplayera10.iaedit.hook.process.RegionWriter;
import com.epicplayera10.iaedit.hook.process.RotationFix;
import com.epicplayera10.iaedit.listeners.ItemsAdderListener;
import com.epicplayera10.iaedit.utils.IACache;
import com.sk89q.worldedit.WorldEdit;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;

final class ItemsAdderMode implements IAEdit.Mode {

    private final IAEdit plugin;
    private WorldEditListener worldEditListener;
    private IAEditCommand command;

    ItemsAdderMode(IAEdit plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        applyConfig();
        IACache.initIfLoaded(plugin.getLogger());
        Bukkit.getPluginManager().registerEvents(new ItemsAdderListener(), plugin);
        worldEditListener = new WorldEditListener();
        WorldEdit.getInstance().getEventBus().register(worldEditListener);
        WorldEdit.getInstance().getBlockFactory().register(new CustomBlocksFactory());
        PluginCommand pluginCommand = plugin.getCommand("iaedit");
        if (pluginCommand != null) {
            command = new IAEditCommand(plugin);
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }
    }

    @Override
    public void disable() {
        if (command != null) command.cancelAll();
        if (worldEditListener != null) {
            try {
                WorldEdit.getInstance().getEventBus().unregister(worldEditListener);
            } catch (RuntimeException ignored) {
                worldEditListener = null;
            }
        }
        RegionWriter.stop();
        PasteTracker.clear();
    }

    @Override
    public void applyConfig() {
        RotationFix.enabled(plugin.getConfig().getBoolean("rotation-fix.enabled", true));
        PasteTracker.commands(plugin.getConfig().getStringList("rotation-fix.commands"));
        RegionWriter.start(plugin, plugin.getConfig().getInt("region-writes-per-tick", 20000), plugin.getConfig().getBoolean("restore-light", true));
    }

    @Override
    public void reloadData() {
        IACache.initIfLoaded(plugin.getLogger());
    }
}
