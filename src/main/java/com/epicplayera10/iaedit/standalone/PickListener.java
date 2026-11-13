package com.epicplayera10.iaedit.standalone;

import io.papermc.paper.event.player.PlayerPickBlockEvent;
import io.papermc.paper.event.player.PlayerPickEntityEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
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
    private static final double SEARCH_RADIUS = 3.0;

    private final Supplier<StandaloneData> data;
    private final java.util.function.Function<Entity, Entity> hitboxOwner;

    PickListener(Supplier<StandaloneData> data, java.util.function.Function<Entity, Entity> hitboxOwner) {
        this.data = data;
        this.hitboxOwner = hitboxOwner;
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
        Block block = event.getBlock();
        if (block.getType() == Material.BARRIER) {
            ItemStack furniture = furnitureAround(block);
            if (furniture == null) return;
            event.setCancelled(true);
            pick(event.getPlayer(), furniture);
            return;
        }
        StandaloneData.CustomBlock custom = data.get().byState(block.getBlockData());
        if (custom == null) return;
        event.setCancelled(true);
        pick(event.getPlayer(), custom.item());
    }

    private ItemStack furnitureAround(Block block) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        ItemStack best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : block.getWorld().getNearbyEntities(center, SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS)) {
            ItemStack furniture = furniture(entity);
            if (furniture == null) continue;
            double distance = entity.getLocation().distanceSquared(center);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = furniture;
            }
        }
        return best;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickEntity(PlayerPickEntityEvent event) {
        Entity owner = hitboxOwner.apply(event.getEntity());
        ItemStack furniture = furniture(owner != null ? owner : event.getEntity());
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
