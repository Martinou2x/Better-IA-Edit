package com.epicplayera10.iaedit.hook;

import com.epicplayera10.iaedit.utils.IACache;
import com.epicplayera10.iaedit.utils.SpawnerNbt;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.internal.registry.InputParser;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import dev.lone.itemsadder.api.CustomBlock;

import java.util.Locale;
import java.util.stream.Stream;

public class CustomBlocksFactory extends InputParser<BaseBlock> {

    public CustomBlocksFactory() {
        super(WorldEdit.getInstance());
    }

    public static boolean isCustomBlockType(BlockType blockType) {
        return blockType == BlockTypes.NOTE_BLOCK
                || blockType == BlockTypes.MUSHROOM_STEM
                || blockType == BlockTypes.BROWN_MUSHROOM_BLOCK
                || blockType == BlockTypes.RED_MUSHROOM_BLOCK
                || blockType == BlockTypes.CHORUS_PLANT
                || blockType == BlockTypes.TRIPWIRE
                || blockType == BlockTypes.SPAWNER;
    }

    @Override
    public Stream<String> getSuggestions(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return IACache.ids().stream().filter(id -> id.contains(lower));
    }

    @Override
    public BaseBlock parseFromInput(String input, ParserContext context) {
        if (input == null || !input.contains(":") || input.contains("[")) return null;
        CustomBlock customBlock = lookup(input.toLowerCase(Locale.ROOT));
        if (customBlock == null) return null;
        try {
            if (customBlock.getBaseBlockData() == null) {
                return BlockTypes.SPAWNER.getDefaultState().toBaseBlock(SpawnerNbt.create(customBlock));
            }
            return BukkitAdapter.adapt(customBlock.getBaseBlockData()).toBaseBlock();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static CustomBlock lookup(String id) {
        CustomBlock cached = IACache.getCustomBlockById(id);
        if (cached != null) return cached;
        try {
            return CustomBlock.getInstance(id);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
