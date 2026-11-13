package com.epicplayera10.iaedit.standalone;

import com.epicplayera10.iaedit.utils.ItemKeys;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class StandaloneData {

    record CustomBlock(String id, BlockData state, ItemStack item) {}

    static final StandaloneData EMPTY = new StandaloneData(Map.of(), Map.of(), Set.of(), Map.of());

    private final Map<BlockData, CustomBlock> byState;
    private final Map<String, CustomBlock> byItem;
    private final Set<Material> baseMaterials;
    private final Map<String, ItemStack> furniture;

    private StandaloneData(Map<BlockData, CustomBlock> byState, Map<String, CustomBlock> byItem,
                           Set<Material> baseMaterials, Map<String, ItemStack> furniture) {
        this.byState = byState;
        this.byItem = byItem;
        this.baseMaterials = baseMaterials;
        this.furniture = furniture;
    }

    static StandaloneData load(Path file) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        Map<BlockData, CustomBlock> byState = new HashMap<>();
        Map<String, CustomBlock> byItem = new HashMap<>();
        Set<Material> baseMaterials = EnumSet.noneOf(Material.class);
        Map<String, ItemStack> furniture = new HashMap<>();
        if (root.has("blocks")) {
            for (JsonElement element : root.getAsJsonArray("blocks")) {
                JsonObject entry = element.getAsJsonObject();
                if (!entry.has("state") || !entry.has("item")) continue;
                try {
                    BlockData state = Bukkit.createBlockData(entry.get("state").getAsString());
                    ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(entry.get("item").getAsString()));
                    CustomBlock block = new CustomBlock(entry.get("id").getAsString(), state, item);
                    byState.put(state, block);
                    baseMaterials.add(state.getMaterial());
                    String key = ItemKeys.of(item);
                    if (key != null) byItem.put(key, block);
                } catch (RuntimeException ignored) {
                    continue;
                }
            }
        }
        if (root.has("furniture")) {
            for (JsonElement element : root.getAsJsonArray("furniture")) {
                JsonObject entry = element.getAsJsonObject();
                try {
                    ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(entry.get("item").getAsString()));
                    String key = ItemKeys.of(item);
                    if (key != null) furniture.put(key, item);
                } catch (RuntimeException ignored) {
                    continue;
                }
            }
        }
        return new StandaloneData(Collections.unmodifiableMap(byState), Collections.unmodifiableMap(byItem),
                Collections.unmodifiableSet(baseMaterials), Collections.unmodifiableMap(furniture));
    }

    @Nullable
    CustomBlock byState(BlockData data) {
        return byState.get(data);
    }

    @Nullable
    CustomBlock byItem(ItemStack item) {
        String key = ItemKeys.of(item);
        return key == null ? null : byItem.get(key);
    }

    boolean isBaseMaterial(Material material) {
        return baseMaterials.contains(material);
    }

    @Nullable
    ItemStack furniture(ItemStack displayed) {
        String key = ItemKeys.of(displayed);
        return key == null ? null : furniture.get(key);
    }

    int blocks() {
        return byState.size();
    }

    int furnitureCount() {
        return furniture.size();
    }
}
