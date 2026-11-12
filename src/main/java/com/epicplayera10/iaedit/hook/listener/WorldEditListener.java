package com.epicplayera10.iaedit.hook.listener;

import com.epicplayera10.iaedit.IAEdit;
import com.epicplayera10.iaedit.hook.process.FaweCompatibility;
import com.epicplayera10.iaedit.hook.process.PasteTracker;
import com.epicplayera10.iaedit.hook.process.RotationFix;
import com.epicplayera10.iaedit.hook.process.WorldEditCustomBlocksExtent;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.event.platform.CommandEvent;
import com.sk89q.worldedit.util.eventbus.EventHandler;
import com.sk89q.worldedit.util.eventbus.Subscribe;

public class WorldEditListener {

    @Subscribe(priority = EventHandler.Priority.VERY_EARLY)
    public void onCommand(CommandEvent event) {
        PasteTracker.onCommand(event.getActor(), event.getArguments());
    }

    @Subscribe
    public void onEditSession(EditSessionEvent event) {
        if (event.getStage() != EditSession.Stage.BEFORE_CHANGE || event.getWorld() == null) return;
        RotationFix rotationFix = RotationFix.forActor(event.getActor());
        if (IAEdit.instance().isFaweEnabled()) {
            FaweCompatibility.addProcessor(event, rotationFix);
        } else {
            event.setExtent(new WorldEditCustomBlocksExtent(event.getExtent(), BukkitAdapter.adapt(event.getWorld()), rotationFix));
        }
    }
}
