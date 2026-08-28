package com.example.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.item.ItemStack;
import com.example.client.BlendedItemEntityState;

@Mixin(ItemEntityRenderState.class)
public class ItemEntityRenderStateMixin implements BlendedItemEntityState {
    @Unique
    private ItemStack blendedcraft$stack = ItemStack.EMPTY;

    @Override
    public ItemStack blendedcraft$getBlendedStack() {
        return blendedcraft$stack;
    }

    @Override
    public void blendedcraft$setBlendedStack(ItemStack stack) {
        this.blendedcraft$stack = stack == null ? ItemStack.EMPTY : stack.copy();
    }
}
