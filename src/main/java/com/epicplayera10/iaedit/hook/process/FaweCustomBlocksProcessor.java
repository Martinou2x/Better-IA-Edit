package com.epicplayera10.iaedit.hook.process;

import com.fastasyncworldedit.core.extent.processor.ProcessorScope;
import com.fastasyncworldedit.core.queue.IBatchProcessor;
import com.fastasyncworldedit.core.queue.IChunk;
import com.fastasyncworldedit.core.queue.IChunkGet;
import com.fastasyncworldedit.core.queue.IChunkSet;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldedit.world.block.BlockTypesCache;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

public class FaweCustomBlocksProcessor implements IBatchProcessor {

    private final World world;

    public FaweCustomBlocksProcessor(World world) {
        this.world = world;
    }

    @Override
    public IChunkSet processSet(IChunk chunk, IChunkGet get, IChunkSet set) {
        return set;
    }

    @Override
    public void postProcess(IChunk chunk, IChunkGet get, IChunkSet set) {
        int bx = chunk.getX() << 4;
        int bz = chunk.getZ() << 4;
        for (int layer = get.getMinSectionPosition(); layer <= get.getMaxSectionPosition(); layer++) {
            if (!set.hasSection(layer)) continue;
            char[] blocksSet = set.loadIfPresent(layer);
            char[] blocksGet = get.loadIfPresent(layer);
            if (blocksSet == null) continue;
            int by = layer << 4;
            for (int y = 0, index = 0; y < 16; y++) {
                int yy = y + by;
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++, index++) {
                        processIndex(blocksSet, blocksGet, index, bx, bz, x, yy, z, set);
                    }
                }
            }
        }
    }

    @SuppressWarnings("removal")
    private void processIndex(char[] blocksSet, char[] blocksGet, int index, int bx, int bz, int x, int y, int z, IChunkSet set) {
        int rawStateSet = blocksSet[index];
        if (rawStateSet == BlockTypesCache.ReservedIDs.__RESERVED__) return;
        int rawStateGet = blocksGet == null ? BlockTypesCache.ReservedIDs.AIR : blocksGet[index];
        BlockState stateSet = BlockTypesCache.states[rawStateSet];
        if (rawStateSet == rawStateGet && stateSet.getBlockType() != BlockTypes.SPAWNER) return;
        BlockState stateGet = BlockTypesCache.states[rawStateGet];
        Location location = new Location(this.world, bx + x, y, bz + z);
        try {
            CustomBlocksWorldEditUtils.processBlock(location, stateSet, stateGet, set.getTile(x, y, z));
        } catch (RuntimeException ignored) {
            return;
        }
    }

    @Nullable
    @Override
    public Extent construct(Extent child) {
        return new WorldEditCustomBlocksExtent(child, this.world, null);
    }

    @Override
    @SuppressWarnings("removal")
    public ProcessorScope getScope() {
        try {
            return ProcessorScope.READING_BLOCKS;
        } catch (NoSuchFieldError ex) {
            return ProcessorScope.READING_SET_BLOCKS;
        }
    }
}
