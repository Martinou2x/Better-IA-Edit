package com.epicplayera10.iaedit.standalone;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

final class FurnitureHitboxes implements Listener {

    private static final float SIZE = 1.0f;

    private final Plugin plugin;
    private final Supplier<StandaloneData> data;
    private final NamespacedKey owner;

    FurnitureHitboxes(Plugin plugin, Supplier<StandaloneData> data) {
        this.plugin = plugin;
        this.data = data;
        this.owner = new NamespacedKey(plugin, "furniture");
    }

    void scanLoaded() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) scan(List.of(chunk.getEntities()));
        }
    }

    void removeAll() {
        for (World world : Bukkit.getWorlds()) {
            for (Interaction interaction : world.getEntitiesByClass(Interaction.class)) {
                if (interaction.getPersistentDataContainer().has(owner)) interaction.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        scan(event.getEntities());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdd(com.destroystokyo.paper.event.entity.EntityAddToWorldEvent event) {
        Entity entity = event.getEntity();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (entity.isValid()) scan(List.of(entity));
        });
    }

    private void scan(List<Entity> entities) {
        for (Entity entity : entities) {
            if (!needsHitbox(entity) || hasHitbox(entity)) continue;
            entity.getWorld().spawn(entity.getLocation(), Interaction.class, spawned -> {
                spawned.setPersistent(false);
                spawned.setInteractionWidth(SIZE);
                spawned.setInteractionHeight(SIZE);
                spawned.setResponsive(true);
                spawned.getPersistentDataContainer().set(owner, PersistentDataType.STRING, entity.getUniqueId().toString());
            });
        }
    }

    private boolean needsHitbox(Entity entity) {
        ItemStack displayed = null;
        if (entity instanceof ItemDisplay display) displayed = display.getItemStack();
        else if (entity instanceof ArmorStand stand && stand.isMarker() && stand.getEquipment() != null) displayed = stand.getEquipment().getHelmet();
        return displayed != null && data.get().furniture(displayed) != null;
    }

    private boolean hasHitbox(Entity furniture) {
        String id = furniture.getUniqueId().toString();
        for (Entity near : furniture.getNearbyEntities(0.5, 0.5, 0.5)) {
            if (near instanceof Interaction interaction && id.equals(interaction.getPersistentDataContainer().get(owner, PersistentDataType.STRING))) {
                return true;
            }
        }
        return false;
    }

    Entity furnitureOf(Entity entity) {
        if (!(entity instanceof Interaction interaction)) return null;
        String id = interaction.getPersistentDataContainer().get(owner, PersistentDataType.STRING);
        if (id == null) return null;
        try {
            Entity furniture = Bukkit.getEntity(UUID.fromString(id));
            return furniture != null && furniture.isValid() ? furniture : null;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Interaction interaction) || !interaction.getPersistentDataContainer().has(owner)) return;
        event.setCancelled(true);
        if (!(event.getDamager() instanceof Player player) || player.getGameMode() != GameMode.CREATIVE || !player.isSneaking()) return;
        Entity furniture = furnitureOf(interaction);
        if (furniture != null) furniture.remove();
        interaction.remove();
    }
}
