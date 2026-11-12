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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class IAEdit extends JavaPlugin {

    private static IAEdit instance;

    private boolean faweEnabled;
    private WorldEditListener worldEditListener;
    private IAEditCommand command;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        faweEnabled = Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit");
        if (faweEnabled) getLogger().info("FastAsyncWorldEdit detected, using chunk processors.");
        applyConfig();
        IACache.initIfLoaded(getLogger());

        Bukkit.getPluginManager().registerEvents(new ItemsAdderListener(), this);
        worldEditListener = new WorldEditListener();
        WorldEdit.getInstance().getEventBus().register(worldEditListener);
        WorldEdit.getInstance().getBlockFactory().register(new CustomBlocksFactory());

        PluginCommand pluginCommand = getCommand("iaedit");
        if (pluginCommand != null) {
            command = new IAEditCommand(this);
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }
    }

    @Override
    public void onDisable() {
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

    public void applyConfig() {
        reloadConfig();
        RotationFix.enabled(getConfig().getBoolean("rotation-fix.enabled", true));
        PasteTracker.commands(getConfig().getStringList("rotation-fix.commands"));
        RegionWriter.start(this, getConfig().getInt("region-writes-per-tick", 20000), getConfig().getBoolean("restore-light", true));
    }

    public Component message(String key, TagResolver... resolvers) {
        String prefix = getConfig().getString("messages.prefix", "");
        String raw = getConfig().getString("messages." + key, key);
        return MiniMessage.miniMessage().deserialize(raw, TagResolver.resolver(TagResolver.resolver(resolvers), Placeholder.parsed("prefix", prefix)));
    }

    public static IAEdit instance() {
        return instance;
    }

    public boolean isFaweEnabled() {
        return faweEnabled;
    }
}
