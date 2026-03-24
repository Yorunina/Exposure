package io.github.mortuusars.exposure.forge.mixin.create;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import io.github.mortuusars.exposure.world.item.FilmItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles developing Films with spouts.
 * Since it changes only FillingBySpout, other types of steps in sequenced recipe won't work, and will remove data from film as usual.
 * But it's enough for my current needs. Chances that someone will want to change it to have pressing or sawing are slim anyway.
 */
@Mixin(FillingBySpout.class)
public class FillingBySpoutMixin {
    @WrapOperation(method = "fillItem", at = @At(value = "INVOKE",
          target = "Lcom/simibubi/create/content/fluids/transfer/FillingRecipe;rollResults()Ljava/util/List;"), remap = false)
    private static List<ItemStack> onFillItem(FillingRecipe instance, Operation<List<ItemStack>> original,
                                              @Local(argsOnly = true) ItemStack stack) {
        List<ItemStack> results = original.call(instance);

        if (!(stack.getItem() instanceof FilmItem) || stack.getTag() == null) {
            return results;
        }

        results = new ArrayList<>(results);
        for (ItemStack result : results) {
            if (result.getItem() instanceof FilmItem) {
                CompoundTag existingTag = stack.getOrCreateTag().copy();
                existingTag.remove("SequencedAssembly"); // Prevent overriding steps with previous values
                result.getOrCreateTag().merge(existingTag);
                break;
            }
        }

        return results;
    }
}