package com.example.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.example.client.BlendedTextureManager;
import com.example.util.FlexibleRecipeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ensures blended tools render as 3D in third-person (other players / outside perspective)
 * Research: vanilla handheld uses display: thirdperson_righthand/lefthand with scale 0.85,
 * but mixins for ItemInHandRenderer only covered first-person. ItemInHandLayer is used
 * for third-person view of other players. This mixin mirrors ItemInHandRendererMixin
 * voxel extrusion so blended items appear 3D from outside.
 * Sources: minecraft.wiki/w/Model handeld vs generated, docs.fabric custom-tools,
 * planetminecraft 1.21.4 handheld fix, forums.minecraftforge third-person sword fix.
 */
@Mixin(ItemInHandLayer.class)
public class ItemInHandLayerMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("blendedcraft/ItemInHandLayerMixin");

    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void onSubmitArmWithItem(ArmedEntityRenderState state, ItemStackRenderState renderState, ItemStack stack, HumanoidArm arm, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        if (stack.isEmpty()) return;
        if (!FlexibleRecipeHelper.isFlexibleResult(stack)) return;
        if (!stack.has(DataComponents.CUSTOM_DATA)) return;
        var cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) return;
        var tag = cd.copyTag();
        if (!tag.contains("blended_ingredients") && !tag.contains("blended_head") && !tag.contains("blended_handle")) return;
        Identifier blendedId = BlendedTextureManager.getOrCreateBlendedTexture(stack);
        if (blendedId == null) return;

        // Cancel vanilla rendering (which would use flat generated or placeholder)
        ci.cancel();

        poseStack.pushPose();

        // Replicate vanilla ItemInHandLayer.submitArmWithItem positioning so blended tools
        // appear at same coordinates as iron_pickaxe / iron_sword etc. in third-person.
        // Vanilla does: translateToHand -> mulPose XP -90 -> mulPose YP 180 -> translate(arm * 1/16, 2/16, -10/16)
        // Then ItemStackRenderState would apply display transforms for THIRD_PERSON.
        // We reproduce hand positioning here, then apply handheld thirdperson display manually
        // so parent iron_pickaxe/handheld coordinates are matched.
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90f));
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f));
        // Adult hand offset (from vanilla ItemInHandLayer: f9=1, f10=2, f11=-10 for non-baby)
        boolean isLeft = arm == HumanoidArm.LEFT;
        float offsetX = (isLeft ? -1f : 1f) * 1f / 16f;
        float offsetY = 2f / 16f;
        float offsetZ = -10f / 16f;
        // Check baby? ArmedEntityRenderState has isBaby? Use reflection fallback
        try {
            var f = state.getClass().getField("isBaby");
            boolean isBaby = f.getBoolean(state);
            if (isBaby) {
                offsetX = (isLeft ? -1f : 1f) * 0f;
                offsetY = 1f / 16f;
                offsetZ = -4.5f / 16f;
            }
        } catch (Exception ignored) { LOGGER.trace("baby check", ignored); }
        poseStack.translate(offsetX, offsetY, offsetZ);

        // Now apply handheld thirdperson display (minecraft:models/item/handheld.json)
        // thirdperson_righthand [0,-90,55] [0,4,0.5] 0.85, thirdperson_lefthand [0,90,-55] [0,4,0.5] 0.85
        if (arm == HumanoidArm.RIGHT) {
            poseStack.translate(0f, 0.25f, 0.03125f);
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-90f));
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(55f));
        } else {
            poseStack.translate(0f, 0.25f, 0.03125f);
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90f));
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-55f));
        }
        poseStack.scale(0.85f, 0.85f, 0.85f);
        // Center the quad slightly? Vanilla handheld is centered; no extra translate needed.
        // For ground-like items (non-handheld like armor?) but all blended tools are handheld.
        var rt = RenderTypes.itemTranslucent(blendedId);
        com.mojang.blaze3d.platform.NativeImage cached = BlendedTextureManager.getCachedImage(blendedId);
        final float thickness = 0.04f;
        int overlay = OverlayTexture.NO_OVERLAY;

        collector.submitCustomGeometry(poseStack, rt, (pose, consumer) -> {
            // front + back
            consumer.addVertex(pose, -0.5f, -0.5f, 0f).setColor(-1).setUv(0f, 1f).setOverlay(overlay).setLight(light).setNormal(pose, 0f, 0f, 1f);
            consumer.addVertex(pose, 0.5f, -0.5f, 0f).setColor(-1).setUv(1f, 1f).setOverlay(overlay).setLight(light).setNormal(pose, 0f, 0f, 1f);
            consumer.addVertex(pose, 0.5f, 0.5f, 0f).setColor(-1).setUv(1f, 0f).setOverlay(overlay).setLight(light).setNormal(pose, 0f, 0f, 1f);
            consumer.addVertex(pose, -0.5f, 0.5f, 0f).setColor(-1).setUv(0f, 0f).setOverlay(overlay).setLight(light).setNormal(pose, 0f, 0f, 1f);
            consumer.addVertex(pose, -0.5f, 0.5f, thickness).setColor(-1).setUv(0f, 0f).setOverlay(overlay).setLight(light).setNormal(pose, 0f, 0f, -1f);
            consumer.addVertex(pose, 0.5f, 0.5f, thickness).setColor(-1).setUv(1f, 0f).setOverlay(overlay).setLight(light).setNormal(pose, 0f, 0f, -1f);
            consumer.addVertex(pose, 0.5f, -0.5f, thickness).setColor(-1).setUv(1f, 1f).setOverlay(overlay).setLight(light).setNormal(pose, 0f, 0f, -1f);
            consumer.addVertex(pose, -0.5f, -0.5f, thickness).setColor(-1).setUv(0f, 1f).setOverlay(overlay).setLight(light).setNormal(pose, 0f, 0f, -1f);
            // Voxel sides for handheld tools (matches ItemInHandRendererMixin third-person expected thickness)
            if (cached != null) {
                try {
                    for (int py = 0; py < 16; py++) {
                        for (int px = 0; px < 16; px++) {
                            int col = cached.getPixel(px, py);
                            int a = (col >> 24) & 0xFF;
                            if (a < 10) continue;
                            int r = (col >> 16) & 0xFF; int g = (col >> 8) & 0xFF; int b = col & 0xFF;
                            int dark = (0xFF << 24) | ((r * 180 / 255) << 16) | ((g * 180 / 255) << 8) | (b * 180 / 255);
                            float x0 = px / 16f - 0.5f;
                            float x1 = (px + 1) / 16f - 0.5f;
                            float y0 = 0.5f - py / 16f;
                            float y1 = 0.5f - (py + 1) / 16f;
                            float u0 = px / 16f; float u1 = (px + 1) / 16f; float v0 = py / 16f; float v1 = (py + 1) / 16f;
                            if (px == 0 || ((cached.getPixel(px - 1, py) >> 24) & 0xFF) < 10) {
                                consumer.addVertex(pose, x0, y1, 0f).setColor(dark).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(pose, -1f, 0f, 0f);
                                consumer.addVertex(pose, x0, y0, 0f).setColor(dark).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(pose, -1f, 0f, 0f);
                                consumer.addVertex(pose, x0, y0, thickness).setColor(dark).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(pose, -1f, 0f, 0f);
                                consumer.addVertex(pose, x0, y1, thickness).setColor(dark).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(pose, -1f, 0f, 0f);
                            }
                            if (px == 15 || ((cached.getPixel(px + 1, py) >> 24) & 0xFF) < 10) {
                                consumer.addVertex(pose, x1, y1, thickness).setColor(dark).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(pose, 1f, 0f, 0f);
                                consumer.addVertex(pose, x1, y0, thickness).setColor(dark).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(pose, 1f, 0f, 0f);
                                consumer.addVertex(pose, x1, y0, 0f).setColor(dark).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(pose, 1f, 0f, 0f);
                                consumer.addVertex(pose, x1, y1, 0f).setColor(dark).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(pose, 1f, 0f, 0f);
                            }
                            if (py == 0 || ((cached.getPixel(px, py - 1) >> 24) & 0xFF) < 10) {
                                consumer.addVertex(pose, x0, y0, thickness).setColor(dark).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(pose, 0f, 1f, 0f);
                                consumer.addVertex(pose, x1, y0, thickness).setColor(dark).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(pose, 0f, 1f, 0f);
                                consumer.addVertex(pose, x1, y0, 0f).setColor(dark).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(pose, 0f, 1f, 0f);
                                consumer.addVertex(pose, x0, y0, 0f).setColor(dark).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(pose, 0f, 1f, 0f);
                            }
                            if (py == 15 || ((cached.getPixel(px, py + 1) >> 24) & 0xFF) < 10) {
                                consumer.addVertex(pose, x0, y1, 0f).setColor(dark).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(pose, 0f, -1f, 0f);
                                consumer.addVertex(pose, x1, y1, 0f).setColor(dark).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(pose, 0f, -1f, 0f);
                                consumer.addVertex(pose, x1, y1, thickness).setColor(dark).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(pose, 0f, -1f, 0f);
                                consumer.addVertex(pose, x0, y1, thickness).setColor(dark).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(pose, 0f, -1f, 0f);
                            }
                        }
                    }
                } catch (Exception ex) { LOGGER.trace("Ignored handling third-person voxel", ex); }
            }
        });

        poseStack.popPose();
    }
}
