package com.epicplayera10.iaedit.standalone;

import com.epicplayera10.iaedit.IAEdit;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

final class ItemsMenu implements InventoryHolder {

    private static final int SIZE = 54;
    private static final int PER_PAGE = 45;
    private static final int SLOT_PREVIOUS = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 53;

    private final IAEdit plugin;
    private final List<StandaloneData.CustomItem> items;
    private final String filter;
    private final int page;
    private final Inventory inventory;

    private ItemsMenu(IAEdit plugin, List<StandaloneData.CustomItem> items, String filter, int page) {
        this.plugin = plugin;
        this.items = items;
        this.filter = filter;
        int pages = Math.max(1, (items.size() + PER_PAGE - 1) / PER_PAGE);
        this.page = Math.max(0, Math.min(page, pages - 1));
        this.inventory = Bukkit.createInventory(this, SIZE, plugin.message("items-title"));
        for (int index = 0; index < PER_PAGE; index++) {
            int position = this.page * PER_PAGE + index;
            if (position >= items.size()) break;
            inventory.setItem(index, items.get(position).item().clone());
        }
        if (this.page > 0) inventory.setItem(SLOT_PREVIOUS, button(Material.ARROW, "items-previous"));
        if (this.page < pages - 1) inventory.setItem(SLOT_NEXT, button(Material.ARROW, "items-next"));
        inventory.setItem(SLOT_INFO, button(Material.BOOK, "items-info",
                Placeholder.unparsed("page", String.valueOf(this.page + 1)),
                Placeholder.unparsed("pages", String.valueOf(pages)),
                Placeholder.unparsed("count", String.valueOf(items.size()))));
    }

    static void open(IAEdit plugin, Player player, StandaloneData data, String filter, int page) {
        String needle = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
        List<StandaloneData.CustomItem> items = needle.isEmpty() ? data.items()
                : data.items().stream().filter(item -> item.id().toLowerCase(Locale.ROOT).contains(needle)).toList();
        if (items.isEmpty()) {
            player.sendMessage(plugin.message("items-empty"));
            return;
        }
        player.openInventory(new ItemsMenu(plugin, items, needle, page).inventory);
    }

    private ItemStack button(Material material, String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... resolvers) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.message(key, resolvers).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    private void click(Player player, int slot, ClickType click) {
        if (slot == SLOT_PREVIOUS && page > 0) {
            player.openInventory(new ItemsMenu(plugin, items, filter, page - 1).inventory);
            return;
        }
        if (slot == SLOT_NEXT) {
            player.openInventory(new ItemsMenu(plugin, items, filter, page + 1).inventory);
            return;
        }
        if (slot >= PER_PAGE) return;
        int position = page * PER_PAGE + slot;
        if (position >= items.size()) return;
        ItemStack item = items.get(position).item().clone();
        switch (click) {
            case MIDDLE -> {
                if (player.getGameMode() != GameMode.CREATIVE) return;
                item.setAmount(item.getMaxStackSize());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.getOpenInventory().getTopInventory() == inventory) player.setItemOnCursor(item);
                });
                return;
            }
            case SHIFT_LEFT, SHIFT_RIGHT -> item.setAmount(item.getMaxStackSize());
            case LEFT, RIGHT -> item.setAmount(1);
            default -> {
                return;
            }
        }
        player.getInventory().addItem(item).values().forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
        player.playSound(player, Sound.ENTITY_ITEM_PICKUP, 0.4f, 1.4f);
    }

    static final class Clicks implements Listener {

        @EventHandler
        public void onClick(InventoryClickEvent event) {
            if (!(event.getView().getTopInventory().getHolder() instanceof ItemsMenu menu)) return;
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            menu.click(player, event.getSlot(), event.getClick());
        }

        @EventHandler
        public void onDrag(InventoryDragEvent event) {
            if (event.getView().getTopInventory().getHolder() instanceof ItemsMenu) event.setCancelled(true);
        }
    }
}
