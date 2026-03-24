package io.github.mortuusars.exposure.world.item;

import io.github.mortuusars.exposure.Config;
import io.github.mortuusars.exposure.Exposure;
import io.github.mortuusars.exposure.world.level.storage.ExposureIdentifier;
import io.github.mortuusars.exposure.client.gui.ClientGUI;
import io.github.mortuusars.exposure.world.photograph.PhotographType;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.inventory.tooltip.PhotographTooltip;
import io.github.mortuusars.exposure.world.item.util.ItemAndStack;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class PhotographItem extends Item {
    public PhotographItem(Properties properties) {
        super(properties);
    }

    public PhotographType getType(ItemStack stack) {
        return PhotographType.REGULAR;
    }

    public Frame getFrame(ItemStack stack) {
        return Exposure.DataComponents.getPhotographFrame(stack, Frame.EMPTY);
    }

    public ExposureIdentifier getIdentifier(ItemStack stack) {
        return Exposure.DataComponents.getExposureIdentifier(stack);
    }

    @Override
    public @NotNull Optional<TooltipComponent> getTooltipImage(@NotNull ItemStack stack) {
        ExposureIdentifier identifier = getIdentifier(stack);
        return !identifier.isEmpty()
              ? Optional.of(new PhotographTooltip(List.of(new ItemAndStack<>(stack))))
              : Optional.empty();
    }

    @Override
    public void appendHoverText(ItemStack stack, Level context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        @Nullable Integer generation = Exposure.DataComponents.getPhotographGeneration(stack);
        if (generation != null && generation > 0) {
                tooltipComponents.add(Component.translatable("item.exposure.photograph.generation." + generation)
                        .withStyle(ChatFormatting.GRAY));
        }

        @Nullable Frame frame = Exposure.DataComponents.getPhotographFrame(stack);
        if (frame == null) {
            return;
        }

        String photographerName = frame.photographer().name();
        if (Config.Client.PHOTOGRAPH_SHOW_PHOTOGRAPHER_IN_TOOLTIP.get() && !photographerName.isEmpty()) {
            tooltipComponents.add(Component.translatable("item.exposure.photograph.photographer_tooltip",
                            Component.literal(photographerName).withStyle(ChatFormatting.WHITE))
                    .withStyle(ChatFormatting.GRAY));
        }

        /* Removed because of difficulties to show the correct copying recipe
           (it was always choosing color copying, as it's a base recipe (fallback))
        if ((generation == null || generation < 2)
              && Config.Client.RECIPE_TOOLTIPS_WITHOUT_JEI.get()
            *//*&& !PlatformHelper.isModLoaded("jei")*//*) {
            ClientGUI.addPhotographCopyingTooltip(stack, context, tooltipComponents, tooltipFlag);
        }*/

        if (tooltipFlag.isAdvanced()) {
            String identifier = frame.identifier().map(
                    id -> "Id: " + id,
                    texture -> "Texture: " + texture);
            tooltipComponents.add(Component.literal(identifier).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);

        ExposureIdentifier identifier = getIdentifier(itemInHand);
        if (identifier == ExposureIdentifier.EMPTY || identifier.isEmpty()) {
            return InteractionResultHolder.pass(itemInHand);
        }

        if (level.isClientSide) {
            int slot = hand == InteractionHand.OFF_HAND ? Inventory.SLOT_OFFHAND : player.getInventory().selected;
            ClientGUI.openPhotographsScreenFromItem(slot);
            player.playSound(Exposure.SoundEvents.PHOTOGRAPH_RUSTLE.get(), 0.6f, 1.1f);
        }

        return InteractionResultHolder.success(itemInHand);
    }

    @Override
    public boolean overrideOtherStackedOnMe(@NotNull ItemStack stack, @NotNull ItemStack other, @NotNull Slot slot, @NotNull ClickAction action, @NotNull Player player, @NotNull SlotAccess access) {
        if (action != ClickAction.SECONDARY)
            return false;

        if (other.getItem() instanceof PhotographItem) {
            StackedPhotographsItem stackedPhotographsItem = Exposure.Items.STACKED_PHOTOGRAPHS.get();
            ItemStack stackedPhotographsStack = new ItemStack(stackedPhotographsItem);

            stackedPhotographsItem.addPhotographOnTop(stackedPhotographsStack, stack);
            stackedPhotographsItem.addPhotographOnTop(stackedPhotographsStack, other);
            slot.set(stackedPhotographsStack);
            access.set(ItemStack.EMPTY);

            StackedPhotographsItem.playAddSoundClientside(player);

            return true;
        }

        return false;
    }
}
