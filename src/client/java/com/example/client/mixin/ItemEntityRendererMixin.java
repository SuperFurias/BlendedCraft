package com.example.client.mixin;

import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.example.util.FlexibleRecipeHelper;
import net.minecraft.core.registries.BuiltInRegistries;

@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtract(net.minecraft.world.entity.item.ItemEntity entity, ItemEntityRenderState state, float tickDelta, CallbackInfo ci) {
        ItemStack stack = entity.getItem();
        if (stack.isEmpty()) return;
        if (!FlexibleRecipeHelper.isFlexibleResult(stack)) return;
        if (!stack.has(DataComponents.CUSTOM_DATA)) return;
        // Fake the dropped item to look like the corresponding netherite item that works (3D like vanilla)
        // This prevents the huge 16x16 flat or the missing texture when dropped
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        ItemStack fake = null;
        if (path.endsWith("_pickaxe")) fake = new ItemStack(Items.NETHERITE_PICKAXE);
        else if (path.endsWith("_axe")) fake = new ItemStack(Items.NETHERITE_AXE);
        else if (path.endsWith("_shovel")) fake = new ItemStack(Items.NETHERITE_SHOVEL);
        else if (path.endsWith("_sword")) fake = new ItemStack(Items.NETHERITE_SWORD);
        else if (path.endsWith("_hoe")) fake = new ItemStack(Items.NETHERITE_HOE);
        else if (path.endsWith("_helmet")) fake = new ItemStack(Items.NETHERITE_HELMET);
        else if (path.endsWith("_chestplate")) fake = new ItemStack(Items.NETHERITE_CHESTPLATE);
        else if (path.endsWith("_leggings")) fake = new ItemStack(Items.NETHERITE_LEGGINGS);
        else if (path.endsWith("_boots")) fake = new ItemStack(Items.NETHERITE_BOOTS);
        else fake = new ItemStack(Items.NETHERITE_INGOT);
        if (fake != null) {
            fake.setCount(stack.getCount());
            try {
                var resolverField = ItemEntityRenderer.class.getDeclaredField("itemModelResolver");
                resolverField.setAccessible(true);
                var resolver = (net.minecraft.client.renderer.item.ItemModelResolver) resolverField.get(this);
                state.item.clear();
                ((net.minecraft.client.renderer.entity.state.ItemClusterRenderState) (Object) state).extractItemGroupRenderState(entity, fake, resolver);
            } catch (Exception e) {
                // Fallback: just keep vanilla (will be placeholder but at least not huge)
            }
        }
    }
}
