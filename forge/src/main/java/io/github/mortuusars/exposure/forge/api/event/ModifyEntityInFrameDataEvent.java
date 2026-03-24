package io.github.mortuusars.exposure.forge.api.event;

import io.github.mortuusars.exposure.world.entity.CameraHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;

/**
 * Can be used to add/modify custom entity in frame NBT data that is stored in a frame.
 * Fired server side on NeoForge.EVENT_BUS.
 */
public class ModifyEntityInFrameDataEvent extends Event {
    private final CameraHolder cameraHolder;
    private final ItemStack camera;
    private final Entity entityInFrame;
    private final CompoundTag data;

    public ModifyEntityInFrameDataEvent(CameraHolder cameraHolder,
                                        ItemStack camera,
                                        Entity entityInFrame,
                                        CompoundTag data) {
        this.cameraHolder = cameraHolder;
        this.camera = camera;
        this.entityInFrame = entityInFrame;
        this.data = data;
    }

    public CameraHolder getCameraHolder() {
        return cameraHolder;
    }

    public Entity getCameraHolderEntity() {
        return cameraHolder.asHolderEntity();
    }

    public ItemStack getCamera() {
        return camera;
    }

    public Entity getEntityInFrame() {
        return entityInFrame;
    }

    public CompoundTag getData() {
        return data;
    }
}