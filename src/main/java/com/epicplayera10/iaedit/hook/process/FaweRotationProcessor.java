package com.epicplayera10.iaedit.hook.process;

import com.fastasyncworldedit.core.extent.processor.ProcessorScope;
import com.fastasyncworldedit.core.queue.IBatchProcessor;
import com.fastasyncworldedit.core.queue.IChunk;
import com.fastasyncworldedit.core.queue.IChunkGet;
import com.fastasyncworldedit.core.queue.IChunkSet;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypesCache;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

public class FaweRotationProcessor implements IBatchProcessor {

    private final World world;
    private final RotationFix rotationFix;

    public FaweRotationProcessor(World world, RotationFix rotationFix) {
        this.world = world;
        this.rotationFix = rotationFix;
    }

    @Override
    public IChunkSet processSet(IChunk chunk, IChunkGet get, IChunkSet set) {
        for (int layer = set.getMinSectionPosition(); layer <= set.getMaxSectionPosition(); layer++) {
            if (!set.hasSection(layer)) continue;
            char[] blocks = set.loadIfPresent(layer);
            if (blocks == null) continue;
            for (int index = 0; index < blocks.length; index++) {
                char raw = blocks[index];
                if (raw == BlockTypesCache.ReservedIDs.__RESERVED__) continue;
                BlockState repaired = rotationFix.repair(BlockTypesCache.states[raw]);
                if (repaired != null) blocks[index] = repaired.getOrdinalChar();
            }
        }
        return set;
    }

    @Nullable
    @Override
    public Extent construct(Extent child) {
        return new WorldEditCustomBlocksExtent(child, this.world, rotationFix);
    }

    @Override
    public ProcessorScope getScope() {
        return ProcessorScope.CHANGING_BLOCKS;
    }
}
