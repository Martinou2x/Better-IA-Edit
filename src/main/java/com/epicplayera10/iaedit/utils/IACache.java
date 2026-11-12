package com.epicplayera10.iaedit.utils;

import dev.lone.itemsadder.api.CustomBlock;
import dev.lone.itemsadder.api.ItemsAdder;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class IACache {

    private record Snapshot(Map<BlockData, CustomBlock> byBlockData, Map<String, CustomBlock> byItemKey,
                            Map<String, CustomBlock> byId, Set<Material> baseMaterials) {}

    private static volatile Snapshot snapshot = new Snapshot(Map.of(), Map.of(), Map.of(), Set.of());

    private IACache() {}

    public static synchronized void init(Logger logger) {
        Map<BlockData, CustomBlock> byBlockData = new HashMap<>();
        Map<String, CustomBlock> byItemKey = new HashMap<>();
        Map<String, CustomBlock> byId = new HashMap<>();
        Set<Material> baseMaterials = EnumSet.noneOf(Material.class);
        int failures = 0;
        for (String id : CustomBlock.getNamespacedIdsInRegistry()) {
            try {
                CustomBlock customBlock = CustomBlock.getInstance(id);
                if (customBlock == null) continue;
                byId.put(id, customBlock);
                BlockData baseBlockData = customBlock.getBaseBlockData();
                if (baseBlockData == null) {
                    String key = itemKey(customBlock.getItemStack());
                    if (key != null) byItemKey.put(key, customBlock);
                } else {
                    byBlockData.put(baseBlockData, customBlock);
                    baseMaterials.add(baseBlockData.getMaterial());
                }
            } catch (RuntimeException failure) {
                failures++;
                if (failures == 1) logger.log(Level.WARNING, "Could not cache custom block " + id, failure);
            }
        }
        snapshot = new Snapshot(Collections.unmodifiableMap(byBlockData), Collections.unmodifiableMap(byItemKey),
                Collections.unmodifiableMap(byId), Collections.unmodifiableSet(baseMaterials));
        logger.info("Cached " + byId.size() + " ItemsAdder custom blocks (" + byBlockData.size() + " block states, "
                + byItemKey.size() + " tile blocks)" + (failures > 0 ? ", " + failures + " skipped" : ""));
    }

    public static void initIfLoaded(Logger logger) {
        try {
            if (ItemsAdder.areItemsLoaded()) init(logger);
        } catch (RuntimeException | LinkageError ignored) {
            snapshot = new Snapshot(Map.of(), Map.of(), Map.of(), Set.of());
        }
    }

    public static boolean isEmpty() {
        return snapshot.byId().isEmpty();
    }

    public static int size() {
        return snapshot.byId().size();
    }

    public static Set<String> ids() {
        return snapshot.byId().keySet();
    }

    public static boolean isBaseMaterial(Material material) {
        return snapshot.baseMaterials().contains(material);
    }

    @Nullable
    public static CustomBlock getCustomBlockByBlockData(BlockData blockData) {
        return snapshot.byBlockData().get(blockData);
    }

    @Nullable
    public static CustomBlock getCustomBlockByItemKey(String itemKey) {
        return snapshot.byItemKey().get(itemKey);
    }

    @Nullable
    public static CustomBlock getCustomBlockById(String id) {
        return snapshot.byId().get(id);
    }

    @Nullable
    public static String itemKey(ItemStack item) {
        if (item == null) return null;
        String material = item.getType().getKey().toString();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        if (meta.hasCustomModelData()) return itemKey(material, meta.getCustomModelData());
        try {
            if (meta.hasItemModel() && meta.getItemModel() != null) return itemKey(material, meta.getItemModel().toString());
        } catch (NoSuchMethodError ignored) {
            return null;
        }
        return null;
    }

    public static String itemKey(String material, int customModelData) {
        return material + "#" + customModelData;
    }

    public static String itemKey(String material, String itemModel) {
        return material + "@" + itemModel;
    }
}
