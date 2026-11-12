package com.epicplayera10.iaedit.hook.process;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

public class WorldEditCustomBlocksExtent extends AbstractDelegateExtent {

    private final World world;
    private final RotationFix rotationFix;

    public WorldEditCustomBlocksExtent(Extent extent, World world, @Nullable RotationFix rotationFix) {
        super(extent);
        this.world = world;
        this.rotationFix = rotationFix;
    }

    @Override
    public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 position, T stateToSet) throws WorldEditException {
        BlockState repaired = rotationFix == null || stateToSet.toBaseBlock().hasNbtData() ? null : rotationFix.repair(stateToSet.toImmutableState());
        if (repaired != null) return place(position, repaired);
        return place(position, stateToSet);
    }

    private <T extends BlockStateHolder<T>> boolean place(BlockVector3 position, T stateToSet) throws WorldEditException {
        Location location = BukkitAdapter.adapt(this.world, position);
        BlockState stateBefore = getExtent().getBlock(position);
        boolean changed = super.setBlock(position, stateToSet);
        if (changed) {
            try {
                CustomBlocksWorldEditUtils.processBlock(location, stateToSet, stateBefore, stateToSet.toBaseBlock().getNbtData());
            } catch (RuntimeException ignored) {
                return true;
            }
        }
        return changed;
    }
}
