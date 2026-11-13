package com.epicplayera10.iaedit.standalone;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

final class StandaloneListener implements Listener {

    private final Supplier<StandaloneData> data;

    StandaloneListener(Supplier<StandaloneData> data) {
        this.data = data;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        if (data.get().isBaseMaterial(event.getBlock().getType())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onNote(NotePlayEvent event) {
        if (data.get().isBaseMaterial(Material.NOTE_BLOCK)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getHand() == null) return;
        StandaloneData current = data.get();
        ItemStack item = event.getItem();
        StandaloneData.CustomBlock custom = item == null ? null : current.byItem(item);
        Block target = event.getClickedBlock();
        if (custom != null && !event.getPlayer().isSneaking() && target.getType().isInteractable() && !current.isBaseMaterial(target.getType())) {
            return;
        }
        if (custom != null) {
            event.setCancelled(true);
            place(event.getPlayer(), event.getHand(), item, custom, event.getClickedBlock(), event.getBlockFace());
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked.getType() == Material.NOTE_BLOCK && current.isBaseMaterial(Material.NOTE_BLOCK)) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    private void place(Player player, EquipmentSlot hand, ItemStack item, StandaloneData.CustomBlock custom, Block clicked, BlockFace face) {
        Block target = clicked.isReplaceable() ? clicked : clicked.getRelative(face);
        if (!target.isReplaceable()) return;
        BoundingBox box = BoundingBox.of(target);
        for (Entity entity : target.getWorld().getNearbyEntities(box)) {
            if (entity instanceof LivingEntity living && living.getBoundingBox().overlaps(box)) return;
        }
        BlockState previous = target.getState();
        target.setBlockData(custom.state(), false);
        BlockPlaceEvent placed = new BlockPlaceEvent(target, previous, clicked, item, player, true, hand);
        Bukkit.getPluginManager().callEvent(placed);
        if (placed.isCancelled() || !placed.canBuild()) {
            previous.update(true, false);
            return;
        }
        if (player.getGameMode() != GameMode.CREATIVE) item.setAmount(item.getAmount() - 1);
        player.swingHand(hand);
        Location center = target.getLocation().add(0.5, 0.5, 0.5);
        target.getWorld().playSound(center, custom.state().getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 1f, 0.8f);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        StandaloneData.CustomBlock custom = data.get().byState(event.getBlock().getBlockData());
        if (custom == null) return;
        event.setDropItems(false);
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        Location center = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        event.getBlock().getWorld().dropItemNaturally(center, custom.item().clone());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        explode(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        explode(event.blockList());
    }

    private void explode(List<Block> blocks) {
        StandaloneData current = data.get();
        Iterator<Block> iterator = blocks.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            StandaloneData.CustomBlock custom = current.byState(block.getBlockData());
            if (custom == null) continue;
            iterator.remove();
            block.setType(Material.AIR, false);
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), custom.item().clone());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!isFurniture(event.getEntity())) return;
        event.setCancelled(true);
        if (event instanceof EntityDamageByEntityEvent byEntity && removes(byEntity.getDamager())) {
            event.getEntity().remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (!isFurniture(event.getEntity())) return;
        event.setCancelled(true);
        if (event instanceof HangingBreakByEntityEvent byEntity && removes(byEntity.getRemover())) {
            event.getEntity().remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onManipulate(PlayerArmorStandManipulateEvent event) {
        if (isFurniture(event.getRightClicked())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (isFurniture(event.getRightClicked())) event.setCancelled(true);
    }

    private static boolean removes(Entity actor) {
        return actor instanceof Player player && player.getGameMode() == GameMode.CREATIVE && player.isSneaking();
    }

    private boolean isFurniture(Entity entity) {
        ItemStack displayed = null;
        if (entity instanceof ArmorStand stand && stand.getEquipment() != null) displayed = stand.getEquipment().getHelmet();
        else if (entity instanceof ItemFrame frame) displayed = frame.getItem();
        return displayed != null && data.get().furniture(displayed) != null;
    }
}
