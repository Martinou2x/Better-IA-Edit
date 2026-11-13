package com.epicplayera10.iaedit.standalone;

import io.papermc.paper.event.player.PlayerPickBlockEvent;
import io.papermc.paper.event.player.PlayerPickEntityEvent;
import org.bukkit.GameMode;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.function.Supplier;

final class PickListener implements Listener {

    private static final int HOTBAR = 9;
    private static final int STORAGE = 36;

    private final Supplier<StandaloneData> data;

    PickListener(Supplier<StandaloneData> data) {
        this.data = data;
    }

    static boolean available() {
        try {
            Class.forName("io.papermc.paper.event.player.PlayerPickBlockEvent");
            return true;
        } catch (ClassNotFoundException missing) {
            return false;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickBlock(PlayerPickBlockEvent event) {
        StandaloneData.CustomBlock custom = data.get().byState(event.getBlock().getBlockData());
        if (custom == null) return;
        event.setCancelled(true);
        pick(event.getPlayer(), custom.item());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickEntity(PlayerPickEntityEvent event) {
        ItemStack furniture = furniture(event.getEntity());
        if (furniture == null) return;
        event.setCancelled(true);
        pick(event.getPlayer(), furniture);
    }

    private ItemStack furniture(Entity entity) {
        ItemStack displayed = null;
        if (entity instanceof ArmorStand stand && stand.getEquipment() != null) displayed = stand.getEquipment().getHelmet();
        else if (entity instanceof ItemFrame frame) displayed = frame.getItem();
        else if (entity instanceof ItemDisplay display) displayed = display.getItemStack();
        return displayed == null ? null : data.get().furniture(displayed);
    }

    static void pick(Player player, ItemStack item) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < HOTBAR; slot++) {
            if (item.isSimilar(inventory.getItem(slot))) {
                inventory.setHeldItemSlot(slot);
                return;
            }
        }
        int held = inventory.getHeldItemSlot();
        for (int slot = HOTBAR; slot < STORAGE; slot++) {
            ItemStack stored = inventory.getItem(slot);
            if (!item.isSimilar(stored)) continue;
            inventory.setItem(slot, inventory.getItem(held));
            inventory.setItem(held, stored);
            return;
        }
        if (player.getGameMode() != GameMode.CREATIVE) return;
        int target = held;
        if (!empty(inventory.getItem(held))) {
            for (int slot = 0; slot < HOTBAR; slot++) {
                if (empty(inventory.getItem(slot))) {
                    target = slot;
                    break;
                }
            }
        }
        ItemStack copy = item.clone();
        copy.setAmount(1);
        inventory.setItem(target, copy);
        inventory.setHeldItemSlot(target);
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getType().isAir();
    }
}
