package com.epicplayera10.iaedit.hook.process;

import com.epicplayera10.iaedit.hook.CustomBlocksFactory;
import com.epicplayera10.iaedit.utils.IACache;
import com.epicplayera10.iaedit.utils.SpawnerNbt;
import com.sk89q.jnbt.CompoundTag;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.sk89q.worldedit.world.block.BlockTypes;
import dev.lone.itemsadder.api.CustomBlock;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CustomBlocksWorldEditUtils {

    private CustomBlocksWorldEditUtils() {}

    public static void processBlock(Location location, BlockStateHolder<?> stateToSet, BlockStateHolder<?> stateBefore, @Nullable CompoundTag nbtToSet) {
        if (stateBefore != null && CustomBlocksFactory.isCustomBlockType(stateBefore.getBlockType())) {
            RegionWriter.remove(location);
        }
        if (stateToSet == null || !CustomBlocksFactory.isCustomBlockType(stateToSet.getBlockType())) return;
        CustomBlock customBlock = getCustomBlockByBlockState(stateToSet, nbtToSet);
        if (customBlock != null) RegionWriter.place(customBlock, location);
    }

    @Nullable
    public static CustomBlock getCustomBlockByBlockState(@NotNull BlockStateHolder<?> blockState, @Nullable CompoundTag nbt) {
        try {
            if (blockState.getBlockType() == BlockTypes.SPAWNER) return SpawnerNbt.read(nbt);
            return IACache.getCustomBlockByBlockData(BukkitAdapter.adapt(blockState.toImmutableState()));
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
