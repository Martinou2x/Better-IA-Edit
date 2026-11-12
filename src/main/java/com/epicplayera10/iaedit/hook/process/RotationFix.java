package com.epicplayera10.iaedit.hook.process;

import com.epicplayera10.iaedit.hook.CustomBlocksFactory;
import com.epicplayera10.iaedit.utils.IACache;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.transform.BlockTransformExtent;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class RotationFix {

    private static volatile boolean enabled = true;

    private final Transform inverse;
    private final Map<BlockState, Optional<BlockState>> repaired = new ConcurrentHashMap<>();

    private RotationFix(Transform inverse) {
        this.inverse = inverse;
    }

    public static void enabled(boolean value) {
        enabled = value;
    }

    @Nullable
    public static RotationFix forActor(@Nullable Actor actor) {
        if (!enabled || actor == null || !PasteTracker.consume(actor)) return null;
        try {
            ClipboardHolder holder = WorldEdit.getInstance().getSessionManager().get(actor).getClipboard();
            Transform transform = holder.getTransform();
            if (transform == null || transform.isIdentity()) return null;
            return new RotationFix(transform.inverse());
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    public BlockState repair(@Nullable BlockState state) {
        if (state == null || state.getBlockType() == BlockTypes.SPAWNER) return null;
        if (!CustomBlocksFactory.isCustomBlockType(state.getBlockType())) return null;
        return repaired.computeIfAbsent(state, this::compute).orElse(null);
    }

    private Optional<BlockState> compute(BlockState state) {
        try {
            BlockState original = BlockTransformExtent.transform(state, inverse);
            if (original == null || original.equals(state) || !matches(original)) return Optional.empty();
            return Optional.of(original);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static boolean matches(BlockState state) {
        return IACache.getCustomBlockByBlockData(BukkitAdapter.adapt(state)) != null;
    }
}
