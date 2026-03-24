package io.github.mortuusars.exposure.advancements.trigger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.mortuusars.exposure.Exposure;
import io.github.mortuusars.exposure.advancements.predicate.CameraPredicate;
import io.github.mortuusars.exposure.advancements.predicate.FramePredicate;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.entity.CameraHolder;
import net.minecraft.advancements.critereon.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class FrameExposedTrigger extends SimpleCriterionTrigger<FrameExposedTrigger.TriggerInstance> {

    public static final ResourceLocation ID = Exposure.resource("frame_exposed");

    public void trigger(ServerPlayer player,
                        CameraHolder cameraHolder,
                        ItemStack cameraStack,
                        Frame frame,
                        List<BlockPos> locationsInFrame,
                        List<Entity> entitiesInFrame) {
        this.trigger(player, triggerInstance ->
                triggerInstance.matches(player, cameraHolder, cameraStack, frame, locationsInFrame, entitiesInFrame));
    }

    @Override
    protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate predicate, DeserializationContext deserializationContext) {
        CameraPredicate camera = CameraPredicate.fromJson(json.get("camera"));
        FramePredicate frame = FramePredicate.fromJson(json.get("frame"));
        LocationPredicate location = LocationPredicate.fromJson(json.get("location_in_frame"));

        List<ContextAwarePredicate> entitiesInFrame = new ArrayList<>();
        if (json.has("entities_in_frame")) {
            JsonArray array = GsonHelper.getAsJsonArray(json,"entities_in_frame");
            for (JsonElement element : array) {
                entitiesInFrame.add(ContextAwarePredicate.fromElement(null,deserializationContext,element,LootContextParamSets.ADVANCEMENT_LOCATION));
            }
        }

        return new TriggerInstance(predicate, camera, frame, location,entitiesInFrame);
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    public static final class TriggerInstance extends AbstractCriterionTriggerInstance {

        private final CameraPredicate camera;
        private final FramePredicate frame;
        private final LocationPredicate locationInFrame;
        private final List<ContextAwarePredicate> entitiesInFrame;

        public TriggerInstance(ContextAwarePredicate player,
                               CameraPredicate camera,
                               FramePredicate frame,
                               LocationPredicate locationInFrame,
                               List<ContextAwarePredicate> entitiesInFrame) {
            super(ID, player);
            this.camera = camera;
            this.frame = frame;
            this.locationInFrame = locationInFrame;
            this.entitiesInFrame = entitiesInFrame;
        }

            public boolean matches(ServerPlayer player,
                                   CameraHolder cameraHolder,
                                   ItemStack cameraStack,
                                   Frame frame,
                                   List<BlockPos> locationsInFrame,
                                   List<Entity> entitiesInFrame) {
                return (camera.matches(player.serverLevel(), cameraStack, cameraHolder.asHolderEntity().position()))
                        && (this.frame.matches(frame))
                        && locationsMatch(player, locationsInFrame)
                        && entitiesInFrameMatch(player, cameraHolder, entitiesInFrame);
            }

            private boolean locationsMatch(ServerPlayer player, List<BlockPos> locationsInFrame) {
                return locationInFrame == LocationPredicate.ANY || locationsInFrame.stream().anyMatch(pos ->
                        locationInFrame.matches(player.serverLevel(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            }

            private boolean entitiesInFrameMatch(ServerPlayer player, CameraHolder cameraHolder, List<Entity> entitiesInFrame) {
                return this.entitiesInFrame.isEmpty() || this.entitiesInFrame.stream().allMatch(predicate ->
                        entitiesInFrame.stream().anyMatch(entity -> {
                            LootContext context = createContextForHolder(player.serverLevel(), cameraHolder, entity);
                            return predicate.matches(context);
                        }));
            }

            public static LootContext createContextForHolder(ServerLevel level, CameraHolder holder, Entity entity) {
                LootParams lootParams = new LootParams.Builder(level)
                        .withParameter(LootContextParams.THIS_ENTITY, entity)
                        .withParameter(LootContextParams.ORIGIN, holder.asHolderEntity().position())
                        .create(LootContextParamSets.ADVANCEMENT_ENTITY);
                return new LootContext.Builder(lootParams).create(ID);
            }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (TriggerInstance) obj;
            return
                    Objects.equals(this.camera, that.camera) &&
                    Objects.equals(this.frame, that.frame) &&
                    Objects.equals(this.locationInFrame, that.locationInFrame) &&
                    Objects.equals(this.entitiesInFrame, that.entitiesInFrame);
        }

        @Override
        public int hashCode() {
            return Objects.hash(camera, frame, locationInFrame, entitiesInFrame);
        }

        @Override
        public JsonObject serializeToJson(SerializationContext context) {
            JsonObject jsonObject = super.serializeToJson(context);
            if (camera != CameraPredicate.ANY) {
                jsonObject.add("camera", camera.serializeToJson());
            }
            if (frame != FramePredicate.ANY) {
                jsonObject.add("frame", frame.serializeToJson());
            }
            if (locationInFrame != LocationPredicate.ANY) {
                jsonObject.add("location_in_frame",locationInFrame.serializeToJson());
            }
            if (!entitiesInFrame.isEmpty()) {
                JsonArray jsonArray = new JsonArray();
                for (ContextAwarePredicate contextAwarePredicate : entitiesInFrame) {
                    jsonArray.add(contextAwarePredicate.toJson(context));
                }
                jsonObject.add("entities_in_frame",jsonArray);
            }
            return jsonObject;
        }

        @Override
        public String toString() {
            return "TriggerInstance[" +
                    "camera=" + camera + ", " +
                    "frame=" + frame + ", " +
                    "locationInFrame=" + locationInFrame + ", " +
                    "entitiesInFrame=" + entitiesInFrame + ']';
        }

        }
}