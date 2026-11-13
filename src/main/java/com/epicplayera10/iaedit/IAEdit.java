package com.epicplayera10.iaedit;

import com.epicplayera10.iaedit.standalone.StandaloneMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class IAEdit extends JavaPlugin {

    public interface Mode {
        void enable();

        void disable();

        void applyConfig();

        void reloadData();
    }

    private static IAEdit instance;

    private boolean faweEnabled;
    private Mode mode;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        faweEnabled = Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit");
        if (faweEnabled) getLogger().info("FastAsyncWorldEdit detected, using chunk processors.");
        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") != null) {
            mode = new ItemsAdderMode(this);
        } else {
            getLogger().info("ItemsAdder not found, running in standalone mode.");
            mode = new StandaloneMode(this);
        }
        mode.enable();
    }

    @Override
    public void onDisable() {
        if (mode != null) mode.disable();
    }

    public void applyConfig() {
        reloadConfig();
        if (mode != null) mode.applyConfig();
    }

    public void reloadData() {
        if (mode != null) mode.reloadData();
    }

    public boolean standalone() {
        return mode instanceof StandaloneMode;
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
