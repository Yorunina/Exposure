package io.github.mortuusars.exposure.forge.integration.kubejs.event;

import io.github.mortuusars.exposure.forge.api.event.FrameAddedEvent;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.entity.CameraHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Fired at the very end of a shot, when frame is added to the film.
 * Fired only on the server side.
 */
public class FrameAddedEventJS extends FrameAddedEvent /*implements KubeEntityEvent*/ {
    public FrameAddedEventJS(CameraHolder cameraHolder, ItemStack camera, Frame frame,
                             List<BlockPos> positionsInFrame, List<Entity> entitiesInFrame) {
        super(cameraHolder, camera, frame, positionsInFrame, entitiesInFrame);
    }

    //@Override
    public Entity getEntity() {
        return getCameraHolderEntity();
    }
}
