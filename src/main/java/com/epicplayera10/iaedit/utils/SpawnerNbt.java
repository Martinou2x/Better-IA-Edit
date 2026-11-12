package com.epicplayera10.iaedit.utils;

import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.CompoundTagBuilder;
import com.sk89q.jnbt.FloatTag;
import com.sk89q.jnbt.IntTag;
import com.sk89q.jnbt.ListTag;
import com.sk89q.jnbt.StringTag;
import com.sk89q.jnbt.Tag;
import dev.lone.itemsadder.api.CustomBlock;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;

public final class SpawnerNbt {

    private static final int COMPONENTS_DATA_VERSION = 3837;
    private static final String CUSTOM_MODEL_DATA = "minecraft:custom_model_data";
    private static final String ITEM_MODEL = "minecraft:item_model";

    private SpawnerNbt() {}

    private static boolean usesComponents() {
        try {
            return Bukkit.getUnsafe().getDataVersion() >= COMPONENTS_DATA_VERSION;
        } catch (RuntimeException | LinkageError ignored) {
            return true;
        }
    }

    public static CompoundTag create(CustomBlock customBlock) {
        CompoundTag entity = usesComponents() ? modernArmorStand(customBlock.getItemStack()) : legacyArmorStand(customBlock.getItemStack());
        return new CompoundTag(new HashMap<>()).createBuilder()
                .put("SpawnData", new CompoundTag(new HashMap<>()).createBuilder().put("entity", entity).build())
                .put("SpawnPotentials", new ListTag(CompoundTag.class, List.of(
                        new CompoundTag(new HashMap<>()).createBuilder()
                                .putInt("weight", 1)
                                .put("data", new CompoundTag(new HashMap<>()).createBuilder().put("entity", entity).build())
                                .build())))
                .putString("id", "minecraft:mob_spawner")
                .putShort("MaxNearbyEntities", (short) 0)
                .putShort("MinSpawnDelay", (short) 1337)
                .putShort("SpawnRange", (short) 4)
                .putShort("MaxSpawnDelay", (short) 0)
                .putShort("RequiredPlayerRange", (short) 0)
                .putShort("SpawnCount", (short) 0)
                .putShort("Delay", (short) 0)
                .build();
    }

    private static CompoundTag legacyArmorStand(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        CompoundTagBuilder tag = new CompoundTag(new HashMap<>()).createBuilder();
        if (meta != null && meta.hasCustomModelData()) tag.putInt("CustomModelData", meta.getCustomModelData());
        CompoundTag head = new CompoundTag(new HashMap<>()).createBuilder()
                .putString("id", item.getType().getKey().toString())
                .putByte("Count", (byte) 1)
                .put("tag", tag.build())
                .build();
        return armorStand().put("ArmorItems", new ListTag(CompoundTag.class, List.of(
                new CompoundTag(new HashMap<>()), new CompoundTag(new HashMap<>()), new CompoundTag(new HashMap<>()), head))).build();
    }

    private static CompoundTag modernArmorStand(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        CompoundTagBuilder components = new CompoundTag(new HashMap<>()).createBuilder();
        if (meta != null && meta.hasCustomModelData()) {
            components.put(CUSTOM_MODEL_DATA, new CompoundTag(new HashMap<>()).createBuilder()
                    .put("floats", new ListTag(FloatTag.class, List.of(new FloatTag((float) meta.getCustomModelData()))))
                    .build());
        }
        try {
            if (meta != null && meta.hasItemModel() && meta.getItemModel() != null) {
                components.putString(ITEM_MODEL, meta.getItemModel().toString());
            }
        } catch (NoSuchMethodError ignored) {
            meta = null;
        }
        CompoundTag head = new CompoundTag(new HashMap<>()).createBuilder()
                .putString("id", item.getType().getKey().toString())
                .putInt("count", 1)
                .put("components", components.build())
                .build();
        return armorStand().put("equipment", new CompoundTag(new HashMap<>()).createBuilder().put("head", head).build()).build();
    }

    private static CompoundTagBuilder armorStand() {
        return new CompoundTag(new HashMap<>()).createBuilder()
                .putByte("Marker", (byte) 1)
                .putString("id", "minecraft:armor_stand")
                .putByte("NoBasePlate", (byte) 1)
                .putByte("Invisible", (byte) 1);
    }

    @Nullable
    public static CustomBlock read(@Nullable CompoundTag spawner) {
        if (spawner == null) return null;
        CompoundTag spawnData = compound(spawner.getValue().get("SpawnData"));
        CompoundTag entity = spawnData == null ? null : compound(spawnData.getValue().get("entity"));
        if (entity == null) return null;
        CompoundTag head = headItem(entity);
        if (head == null) return null;
        Tag<?, ?> idTag = head.getValue().get("id");
        if (!(idTag instanceof StringTag id)) return null;
        String material = id.getValue();
        if (!material.contains(":")) material = "minecraft:" + material;

        CompoundTag components = compound(head.getValue().get("components"));
        if (components != null) {
            Integer customModelData = componentModelData(components.getValue().get(CUSTOM_MODEL_DATA));
            if (customModelData != null) {
                CustomBlock found = IACache.getCustomBlockByItemKey(IACache.itemKey(material, customModelData));
                if (found != null) return found;
            }
            Tag<?, ?> itemModel = components.getValue().get(ITEM_MODEL);
            if (itemModel instanceof StringTag model) {
                CustomBlock found = IACache.getCustomBlockByItemKey(IACache.itemKey(material, model.getValue()));
                if (found != null) return found;
            }
        }
        CompoundTag legacy = compound(head.getValue().get("tag"));
        if (legacy != null && legacy.getValue().get("CustomModelData") instanceof IntTag cmd) {
            return IACache.getCustomBlockByItemKey(IACache.itemKey(material, cmd.getValue()));
        }
        return null;
    }

    @Nullable
    private static CompoundTag headItem(CompoundTag entity) {
        CompoundTag equipment = compound(entity.getValue().get("equipment"));
        if (equipment != null) {
            CompoundTag head = compound(equipment.getValue().get("head"));
            if (head != null) return head;
        }
        Tag<?, ?> armorItems = entity.getValue().get("ArmorItems");
        if (armorItems instanceof ListTag list && list.getValue().size() >= 4) {
            return compound((Tag<?, ?>) list.getValue().get(3));
        }
        return null;
    }

    @Nullable
    private static Integer componentModelData(Tag<?, ?> tag) {
        if (tag instanceof IntTag value) return value.getValue();
        CompoundTag compound = compound(tag);
        if (compound == null) return null;
        Tag<?, ?> floats = compound.getValue().get("floats");
        if (floats instanceof ListTag list && !list.getValue().isEmpty()) {
            Object first = list.getValue().get(0);
            if (first instanceof FloatTag value) return Math.round(value.getValue());
            if (first instanceof IntTag value) return value.getValue();
        }
        return null;
    }

    @Nullable
    private static CompoundTag compound(Tag<?, ?> tag) {
        return tag instanceof CompoundTag compound ? compound : null;
    }
}
