package com.example.client.mixin;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.example.client.BlendedTextureManager;
import com.example.util.FlexibleRecipeHelper;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.texture.OverlayTexture;

@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {
    @Unique
    private static final ThreadLocal<ItemStack> blendedcraft$currentStack = new ThreadLocal<>();

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtract(net.minecraft.world.entity.item.ItemEntity entity, ItemEntityRenderState state, float tickDelta, CallbackInfo ci) {
        ItemStack stack = entity.getItem();
        if (stack.isEmpty()) {
            blendedcraft$currentStack.remove();
            return;
        }
        if (!FlexibleRecipeHelper.isFlexibleResult(stack)) {
            blendedcraft$currentStack.remove();
            return;
        }
        if (!stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) {
            blendedcraft$currentStack.remove();
            return;
        }
        blendedcraft$currentStack.set(stack.copy());
        BlendedTextureManager.getOrCreateBlendedTexture(stack);
    }

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void onSubmit(ItemEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, net.minecraft.client.renderer.state.level.CameraRenderState cameraState, CallbackInfo ci) {
        ItemStack stack = blendedcraft$currentStack.get();
        // Also try to get from ThreadLocal, but if not available, try to infer via state.item is not directly ItemStack
        // For safety, also check the state's item is not empty and try to find blended texture via the ThreadLocal
        if (stack == null || stack.isEmpty()) return;
        if (!FlexibleRecipeHelper.isFlexibleResult(stack)) return;
        Identifier blendedId = BlendedTextureManager.getOrCreateBlendedTexture(stack);
        if (blendedId == null) return;
        NativeImage img = BlendedTextureManager.getCachedImage(blendedId);
        if (img == null) return;
        // Only for flat items (handheld/generated) which have width, we want 3D
        poseStack.pushPose();
        poseStack.translate(0f, 0.1f, 0f);
        poseStack.scale(0.5f, 0.5f, 0.5f);
        RenderType renderType = RenderTypes.itemTranslucent(blendedId);
        collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int argb = img.getPixel(x, y);
                    int a = (argb >> 24) & 0xFF;
                    if (a < 10) continue;
                    float u0 = x / 16f, v0 = y / 16f, u1 = (x + 1) / 16f, v1 = (y + 1) / 16f;
                    consumer.addVertex(pose, x, 16 - y - 1, 0f).setColor(argb).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0f, 0f, 1f);
                    consumer.addVertex(pose, x + 1, 16 - y - 1, 0f).setColor(argb).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0f, 0f, 1f);
                    consumer.addVertex(pose, x + 1, 16 - y, 0f).setColor(argb).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0f, 0f, 1f);
                    consumer.addVertex(pose, x, 16 - y, 0f).setColor(argb).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0f, 0f, 1f);
                    consumer.addVertex(pose, x, 16 - y, 1f).setColor(argb).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0f, 0f, -1f);
                    consumer.addVertex(pose, x + 1, 16 - y, 1f).setColor(argb).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0f, 0f, -1f);
                    consumer.addVertex(pose, x + 1, 16 - y - 1, 1f).setColor(argb).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0f, 0f, -1f);
                    consumer.addVertex(pose, x, 16 - y - 1, 1f).setColor(argb).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0f, 0f, -1f);
                    int sRGB = argb & 0x00FFFFFF;
                    int sideColor = (0xFF << 24) | (int)(((sRGB >> 16) & 0xFF) * 0.7f) << 16 | (int)(((sRGB >> 8) & 0xFF) * 0.7f) << 8 | (int)((sRGB & 0xFF) * 0.7f);
                    if (x == 0 || ((img.getPixel(x - 1, y) >> 24) & 0xFF) < 10) {
                        consumer.addVertex(pose, x, 16 - y - 1, 0f).setColor(sideColor).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, -1f, 0f, 0f);
                        consumer.addVertex(pose, x, 16 - y, 0f).setColor(sideColor).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, -1f, 0f, 0f);
                        consumer.addVertex(pose, x, 16 - y, 1f).setColor(sideColor).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, -1f, 0f, 0f);
                        consumer.addVertex(pose, x, 16 - y - 1, 1f).setColor(sideColor).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, -1f, 0f, 0f);
                    }
                    if (x == 15 || ((img.getPixel(x + 1, y) >> 24) & 0xFF) < 10) {
                        consumer.addVertex(pose, x + 1, 16 - y, 0f).setColor(sideColor).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 1f, 0f, 0f);
                        consumer.addVertex(pose, x + 1, 16 - y - 1, 0f).setColor(sideColor).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 1f, 0f, 0f);
                        consumer.addVertex(pose, x + 1, 16 - y - 1, 1f).setColor(sideColor).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 1f, 0f, 0f);
                        consumer.addVertex(pose, x + 1, 16 - y, 1f).setColor(sideColor).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 1f, 0f, 0f);
                    }
                    if (y == 0 || ((img.getPixel(x, y - 1) >> 24) & 0xFF) < 10) {
                        consumer.addVertex(pose, x, 16 - y, 0f).setColor(sideColor).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0f, 1f, 0f);
                        consumer.addVertex(pose, x + 1, 16 - y, 0f).setColor(sideColor).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0f, 1f, 0f);
                        consumer.addVertex(pose, x + 1, 16 - y, 1f).setColor(sideColor).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0f, 1f, 0f);
                        consumer.addVertex(pose, x, 16 - y, 1f).setColor(sideColor).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0f, 1f, 0f);
                    }
                    if (y == 15 || ((img.getPixel(x, y + 1) >> 24) & 0xFF) < 10) {
                        consumer.addVertex(pose, x, 16 - y - 1, 1f).setColor(sideColor).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0f, -1f, 0f);
                        consumer.addVertex(pose, x + 1, 16 - y - 1, 1f).setColor(sideColor).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0f, -1f, 0f);
                        consumer.addVertex(pose, x + 1, 16 - y - 1, 0f).setColor(sideColor).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0f, -1f, 0f);
                        consumer.addVertex(pose, x, 16 - y - 1, 0f).setColor(sideColor).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0f, -1f, 0f);
                    }
                }
            }
        });
        poseStack.popPose();
        blendedcraft$currentStack.remove();
        ci.cancel();
    }
}
