package io.github.mortuusars.exposure.forge;

import io.github.mortuusars.exposure.integration.Mods;
import io.github.mortuusars.exposure.forge.api.event.ModifyEntityInFrameDataEvent;
import io.github.mortuusars.exposure.forge.api.event.FrameAddedEvent;
import io.github.mortuusars.exposure.forge.api.event.ModifyFrameExtraDataEvent;
import io.github.mortuusars.exposure.forge.integration.CreateIntegration;
import io.github.mortuusars.exposure.world.camera.capture.CaptureParameters;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.entity.CameraHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public class PlatformHelperImpl {
    public static @Nullable MinecraftServer getServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    public static boolean canShear(ItemStack stack) {
        return stack.canPerformAction(ToolActions.SHEARS_CARVE);
    }

    public static boolean canStrip(ItemStack stack) {
        return stack.canPerformAction(ToolActions.AXE_STRIP);
    }

    public static void openMenu(ServerPlayer serverPlayer, MenuProvider menuProvider, Consumer<FriendlyByteBuf> extraDataWriter) {
        NetworkHooks.openScreen(serverPlayer,menuProvider,extraDataWriter);
    }

    public static List<String> getDefaultSpoutDevelopmentColorSequence() {
        return List.of(
                "{FluidName:\"create:potion\",Amount:250,Tag:{Potion:\"minecraft:awkward\"}}",
                "{FluidName:\"create:potion\",Amount:250,Tag:{Potion:\"minecraft:thick\"}}",
                "{FluidName:\"create:potion\",Amount:250,Tag:{Potion:\"minecraft:mundane\"}}");
    }

    public static List<String> getDefaultSpoutDevelopmentBWSequence() {
        return List.of(
                "{FluidName:\"minecraft:water\",Amount:250}");
    }

    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    public static boolean isModLoading(String modId) {
        return LoadingModList.get().getModFileById(modId) != null;
    }

    public static boolean isInDevEnv() {
        return !FMLEnvironment.production;
    }

    // --

    public static boolean isCreateDeployer(Player player, InteractionHand hand) {
        if (Mods.CREATE.isLoaded()) {
            return CreateIntegration.isDeployer(player);
        }
        return false;
    }

    // --

    public static void postModifyEntityInFrameExtraDataEvent(CameraHolder cameraHolder, ItemStack camera, Entity entityInFrame, CompoundTag data) {
        MinecraftForge.EVENT_BUS.post(new ModifyEntityInFrameDataEvent(cameraHolder, camera, entityInFrame, data));
    }

    public static void postModifyFrameExtraDataEvent(CameraHolder cameraHolder, ItemStack camera, CaptureParameters captureParameters,
                                                     List<BlockPos> positionsInFrame, List<Entity> entitiesInFrame, CompoundTag data) {
        MinecraftForge.EVENT_BUS.post(new ModifyFrameExtraDataEvent(cameraHolder, camera, captureParameters, positionsInFrame, entitiesInFrame, data));
    }

    public static void postFrameAddedEvent(CameraHolder holder, ItemStack camera, Frame frame,
                                           List<BlockPos> positionsInFrame, List<Entity> entitiesInFrame) {
        MinecraftForge.EVENT_BUS.post(new FrameAddedEvent(holder, camera, frame, positionsInFrame, entitiesInFrame));
    }
}