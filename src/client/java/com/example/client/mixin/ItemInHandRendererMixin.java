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
import net.minecraft.client.renderer.texture.OverlayTexture;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void onRenderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        if (stack.isEmpty()) return;
        if (!FlexibleRecipeHelper.isFlexibleResult(stack)) return;
        if (!stack.has(DataComponents.CUSTOM_DATA)) return;
        if (displayContext == ItemDisplayContext.GUI || displayContext == ItemDisplayContext.GROUND || displayContext == ItemDisplayContext.FIXED) return;
        Identifier blendedId = BlendedTextureManager.getOrCreateBlendedTexture(stack);
        if (blendedId == null) return;
        poseStack.pushPose();
        String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        boolean isHandheld = path.endsWith("_pickaxe") || path.endsWith("_axe") || path.endsWith("_shovel") || path.endsWith("_sword") || path.endsWith("_hoe");
        // Apply vanilla display transform for handheld (tools) vs generated (armor/hoe)
        if (displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            poseStack.translate(1.13f / 16f, 3.2f / 16f, 1.13f / 16f);
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-90));
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(25));
            poseStack.scale(0.68f, 0.68f, 0.68f);
            if (displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
        } else if (displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
            if (isHandheld) {
                poseStack.translate(0f, 4.0f / 16f, 0.5f / 16f);
                poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-90));
                poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(55));
                poseStack.scale(0.85f, 0.85f, 0.85f);
            } else {
                poseStack.translate(0f, 3f / 16f, 1f / 16f);
                poseStack.scale(0.55f, 0.55f, 0.55f);
            }
            if (displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
        } else if (displayContext == ItemDisplayContext.HEAD) {
            poseStack.translate(0f, 13f / 16f, 7f / 16f);
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
        }
        RenderType renderType = RenderTypes.itemTranslucent(blendedId);
        collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            consumer.addVertex(pose, -0.5f, -0.5f, 0f).setColor(-1).setUv(0f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0f, 0f, 1f);
            consumer.addVertex(pose, 0.5f, -0.5f, 0f).setColor(-1).setUv(1f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0f, 0f, 1f);
            consumer.addVertex(pose, 0.5f, 0.5f, 0f).setColor(-1).setUv(1f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0f, 0f, 1f);
            consumer.addVertex(pose, -0.5f, 0.5f, 0f).setColor(-1).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0f, 0f, 1f);
            consumer.addVertex(pose, -0.5f, 0.5f, 0.015f).setColor(-1).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0f, 0f, -1f);
            consumer.addVertex(pose, 0.5f, 0.5f, 0.015f).setColor(-1).setUv(1f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0f, 0f, -1f);
            consumer.addVertex(pose, 0.5f, -0.5f, 0.015f).setColor(-1).setUv(1f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0f, 0f, -1f);
            consumer.addVertex(pose, -0.5f, -0.5f, 0.015f).setColor(-1).setUv(0f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0f, 0f, -1f);
        });
        poseStack.popPose();
        ci.cancel();
    }
}
