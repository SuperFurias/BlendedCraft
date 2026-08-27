package com.example.client.mixin;

import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public class ItemEntityRenderStateMixin {
    @Unique
    public ItemStack blendedcraft$stack;
}
