package com.superfurias.blendedcraft.mixin;

import com.superfurias.blendedcraft.util.MaterialEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

/**
 * Blazing Harvest (fiery mining tools): auto-smelts mined blocks.
 * Enchanting Fortune (lapis/emerald mining tools): mined drops are multiplied by the trait tier.
 */
@Mixin(Block.class)
public abstract class BlockMixin {

    @Inject(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private static void blendedcraft$harvestTraits(BlockState state, Level level, BlockPos pos, BlockEntity blockEntity, Entity entity, ItemStack tool, CallbackInfo ci) {
        try {
            if (!(level instanceof ServerLevel server) || !(entity instanceof ServerPlayer player)) return;
            // Blazing Harvest ONLY auto-smelts; it does not multiply drops.
            // Fortune comes from the lapis/emerald traits (or a real Fortune enchantment via vanilla loot context)
            // and simply coexists with auto-smelt.
            int smelt = MaterialEffects.effectLevel(tool, MaterialEffects.T_FIERY_SMELT);
            int fortune = Math.max(MaterialEffects.effectLevel(tool, MaterialEffects.T_LAPIS_FORTUNE), MaterialEffects.effectLevel(tool, MaterialEffects.T_EMERALD_LOOT));
            if (smelt <= 0 && fortune <= 0) return;

            LootParams.Builder paramsBuilder = new LootParams.Builder(server)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .withParameter(LootContextParams.TOOL, tool)
                    .withParameter(LootContextParams.THIS_ENTITY, player);
            List<ItemStack> drops = state.getDrops(paramsBuilder);
            for (ItemStack drop : drops) {
                if (drop.isEmpty()) continue;
                if (fortune > 1 && drop.isStackable()) {
                    drop.setCount(Math.min(drop.getMaxStackSize(), drop.getCount() * fortune));
                }
                if (smelt > 0) {
                    Optional<RecipeHolder<SmeltingRecipe>> recipe = server.getServer().getRecipeManager()
                            .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(drop), server);
                    if (recipe.isPresent()) {
                        ItemStack smelted = recipe.get().value().assemble(new SingleRecipeInput(drop));
                        if (!smelted.isEmpty()) {
                            smelted.setCount(drop.getCount());
                            Block.popResource(level, pos, smelted);
                            continue;
                        }
                    }
                }
                Block.popResource(level, pos, drop);
            }
            ci.cancel();
        } catch (Exception e) {
            // never break vanilla dropping on unexpected failures
        }
    }
}
