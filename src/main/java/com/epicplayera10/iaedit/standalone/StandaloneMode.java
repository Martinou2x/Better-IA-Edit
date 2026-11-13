package com.epicplayera10.iaedit.standalone;

import com.epicplayera10.iaedit.IAEdit;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public final class StandaloneMode implements IAEdit.Mode, TabExecutor {

    private static final List<String> PAPER_FLAGS = List.of(
            "block-updates.disable-noteblock-updates",
            "block-updates.disable-mushroom-block-updates",
            "block-updates.disable-tripwire-updates",
            "block-updates.disable-chorus-plant-updates");

    private final IAEdit plugin;
    private volatile StandaloneData data = StandaloneData.EMPTY;

    public StandaloneMode(IAEdit plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        reloadData();
        Bukkit.getPluginManager().registerEvents(new StandaloneListener(() -> data), plugin);
        PluginCommand command = plugin.getCommand("iaedit");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        warnPaperFlags();
    }

    @Override
    public void disable() {
        data = StandaloneData.EMPTY;
    }

    @Override
    public void applyConfig() {
        reloadData();
    }

    @Override
    public void reloadData() {
        Path file = dataFile();
        if (!Files.isRegularFile(file)) {
            data = StandaloneData.EMPTY;
            plugin.getLogger().warning("No ItemsAdder data at " + file + ", run /packsyncer to fetch it.");
            return;
        }
        try {
            data = StandaloneData.load(file);
            plugin.getLogger().info("Loaded " + data.blocks() + " custom blocks and " + data.furnitureCount() + " furniture from " + file);
        } catch (Exception failure) {
            plugin.getLogger().log(Level.WARNING, "Could not read " + file, failure);
        }
    }

    private Path dataFile() {
        String configured = plugin.getConfig().getString("standalone.data-file", "plugins/NPackSyncer/itemsadder-data.json");
        return Bukkit.getWorldContainer().toPath().resolve(configured).normalize();
    }

    private void warnPaperFlags() {
        File paperGlobal = new File(Bukkit.getWorldContainer(), "config/paper-global.yml");
        if (!paperGlobal.isFile()) return;
        YamlConfiguration paper = YamlConfiguration.loadConfiguration(paperGlobal);
        List<String> missing = new ArrayList<>();
        for (String flag : PAPER_FLAGS) if (!paper.getBoolean(flag, false)) missing.add(flag);
        if (!missing.isEmpty()) {
            plugin.getLogger().warning("Set these to true in config/paper-global.yml so custom blocks never change state: " + String.join(", ", missing));
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.applyConfig();
                sender.sendMessage(plugin.message("reloaded", Placeholder.unparsed("blocks", String.valueOf(data.blocks()))));
            }
            case "info" -> sender.sendMessage(plugin.message("standalone-info",
                    Placeholder.unparsed("version", plugin.getDescription().getVersion()),
                    Placeholder.unparsed("blocks", String.valueOf(data.blocks())),
                    Placeholder.unparsed("furniture", String.valueOf(data.furnitureCount()))));
            default -> sender.sendMessage(plugin.message("standalone-usage", Placeholder.unparsed("label", label)));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) return List.of();
        return List.of("info", "reload").stream().filter(name -> name.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
}
