package com.epicplayera10.iaedit.command;

import com.epicplayera10.iaedit.IAEdit;
import com.epicplayera10.iaedit.hook.process.RegionWriter;
import com.epicplayera10.iaedit.utils.IACache;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.Region;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

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
        long max = plugin.getConfig().getLong("fix-max-blocks", 0L);
        if (max > 0 && volume > max) {
            sender.sendMessage(plugin.message("selection-too-big", Placeholder.unparsed("max", String.valueOf(max))));
            return;
        }
        FixTask task = new FixTask(plugin, player, region, () -> running.remove(player.getUniqueId()));
        sender.sendMessage(plugin.message("fix-started", Placeholder.unparsed("blocks", String.valueOf(volume)),
                Placeholder.unparsed("chunks", String.valueOf(task.chunkCount()))));
        running.put(player.getUniqueId(), task);
        task.runTaskTimer(plugin, 1L, 1L);
    }
}
