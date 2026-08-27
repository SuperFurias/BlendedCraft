package com.example.client.mixin;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.example.client.BlendedTextureManager;
import com.example.util.FlexibleRecipeHelper;
import com.mojang.blaze3d.vertex.PoseStack;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void onRenderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        if (stack.isEmpty()) return;
        if (!FlexibleRecipeHelper.isFlexibleResult(stack)) return;
        if (!stack.has(DataComponents.CUSTOM_DATA)) return;
        // Only handle hand contexts to avoid affecting GUI/ground/fixed which are handled elsewhere or vanilla
        if (displayContext == ItemDisplayContext.GUI || displayContext == ItemDisplayContext.GROUND || displayContext == ItemDisplayContext.FIXED) return;
        Identifier blendedId = BlendedTextureManager.getOrCreateBlendedTexture(stack);
        if (blendedId == null) return;
        // Render the same 16x16 blended texture as inventory, as a flat quad in hand
        poseStack.pushPose();
        // Use itemTranslucent which supports our DynamicTexture; entityTranslucentCull would also work but itemTranslucent is for 2D items
        RenderType renderType = RenderTypes.itemTranslucent(blendedId);
        collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            // Front face
            consumer.addVertex(pose, -0.5f, -0.5f, 0f).setColor(-1).setUv(0f, 1f).setLight(light);
            consumer.addVertex(pose, 0.5f, -0.5f, 0f).setColor(-1).setUv(1f, 1f).setLight(light);
            consumer.addVertex(pose, 0.5f, 0.5f, 0f).setColor(-1).setUv(1f, 0f).setLight(light);
            consumer.addVertex(pose, -0.5f, 0.5f, 0f).setColor(-1).setUv(0f, 0f).setLight(light);
            // Back face slightly offset to avoid z-fighting and give thickness
            consumer.addVertex(pose, -0.5f, 0.5f, 0.015f).setColor(-1).setUv(0f, 0f).setLight(light);
            consumer.addVertex(pose, 0.5f, 0.5f, 0.015f).setColor(-1).setUv(1f, 0f).setLight(light);
            consumer.addVertex(pose, 0.5f, -0.5f, 0.015f).setColor(-1).setUv(1f, 1f).setLight(light);
            consumer.addVertex(pose, -0.5f, -0.5f, 0.015f).setColor(-1).setUv(0f, 1f).setLight(light);
        });
        poseStack.popPose();
        ci.cancel();
    }
}
