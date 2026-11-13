package com.epicplayera10.iaedit.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

public final class ItemKeys {

    private ItemKeys() {}

    @Nullable
    public static String of(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String material = item.getType().getKey().toString();
        if (meta.hasCustomModelData()) return of(material, meta.getCustomModelData());
        try {
            if (meta.hasItemModel() && meta.getItemModel() != null) return of(material, meta.getItemModel().toString());
        } catch (NoSuchMethodError ignored) {
            return null;
        }
        return null;
    }

    public static String of(String material, int customModelData) {
        return material + "#" + customModelData;
    }

    public static String of(String material, String itemModel) {
        return material + "@" + itemModel;
    }
}
