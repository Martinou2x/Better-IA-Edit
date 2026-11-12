package com.epicplayera10.iaedit.hook.process;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

public final class FaweCompatibility {

    private FaweCompatibility() {}

    public static void addProcessor(EditSessionEvent event, @Nullable RotationFix rotationFix) {
        World world = BukkitAdapter.adapt(event.getWorld());
        if (rotationFix != null) event.getExtent().addProcessor(new FaweRotationProcessor(world, rotationFix));
        event.getExtent().addPostProcessor(new FaweCustomBlocksProcessor(world));
    }
}
