package com.example.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.example.client.BlendedItemEntityState;
import com.example.client.BlendedTextureManager;
import com.example.util.FlexibleRecipeHelper;

@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtract(net.minecraft.world.entity.item.ItemEntity entity, ItemEntityRenderState state, float tickDelta, CallbackInfo ci) {
        ItemStack stack = entity.getItem();
        if (stack.isEmpty()) return;
        if (!FlexibleRecipeHelper.isFlexibleResult(stack)) return;
        if (!stack.has(DataComponents.CUSTOM_DATA)) return;
        var tag = stack.get(DataComponents.CUSTOM_DATA);
        if (tag == null || tag.isEmpty()) return;
        var copy = tag.copyTag();
        if (!copy.contains("blended_ingredients") && !copy.contains("blended_head") && !copy.contains("blended_handle")) return;
        ((BlendedItemEntityState) (Object) state).blendedcraft$setBlendedStack(stack);
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void onSubmit(ItemEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState, CallbackInfo ci) {
        ItemStack blended = ((BlendedItemEntityState) (Object) state).blendedcraft$getBlendedStack();
        if (blended == null || blended.isEmpty()) return;
        if (!FlexibleRecipeHelper.isFlexibleResult(blended)) return;
        Identifier blendedId = BlendedTextureManager.getOrCreateBlendedTexture(blended);
        if (blendedId == null) return;

        // Cancel vanilla and render blended texture with proper bob/rotation and count handling
        ci.cancel();

        poseStack.pushPose();

        // Replicate vanilla bob + spin
        // vanilla: y = -minY + 0.0625 + sin((ageInTicks/10 + bobOffset))*0.1+0.1
        // For custom flat quad we approximate minY as 0 (flat item centered). Use vanilla formula but with 0 minY.
        float age = state.ageInTicks;
        float bob = state.bobOffset;
        float yBob = Mth.sin((age / 10.0f + bob) ) * 0.1f + 0.1f;
        // vanilla also adds -minY+0.0625; for flat we just add yBob + 0.1f offset similar to vanilla's 0.0625
        float yOffset = yBob + 0.15f;
        // spin
        float spin = net.minecraft.world.entity.item.ItemEntity.getSpin(age, bob);
        poseStack.translate(0.0f, yOffset, 0.0f);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotation(spin));

        int count = state.count;
        if (count <= 0) count = blended.getCount();
        if (count <= 0) count = 1;
        int light = state.lightCoords;
        int overlay = OverlayTexture.NO_OVERLAY;
        RandomSource random = RandomSource.create();
        random.setSeed(state.seed);

        // If count==1 simple render
        if (count == 1) {
            renderBlendedQuad(poseStack, collector, blendedId, light, overlay);
        } else {
            // Flat item clumping similar to vanilla: first + random offsets
            // vanilla flat path uses 0.5 scale for extra items
            renderBlendedQuad(poseStack, collector, blendedId, light, overlay);
            for (int i = 1; i < count; i++) {
                poseStack.pushPose();
                float offX = (random.nextFloat() * 2.0f - 1.0f) * 0.15f * 0.5f;
                float offZ = (random.nextFloat() * 2.0f - 1.0f) * 0.15f * 0.5f;
                // slight y jitter for stacking
                float offY = random.nextFloat() * 0.02f;
                poseStack.translate(offX, offY, offZ);
                renderBlendedQuad(poseStack, collector, blendedId, light, overlay);
                poseStack.popPose();
            }
        }

        poseStack.popPose();
        // also call super submit for shadow? vanilla does EntityRenderer.submit at end - we skip to avoid double
    }

    private void renderBlendedQuad(PoseStack poseStack, SubmitNodeCollector collector, Identifier tex, int light, int overlay) {
        RenderType rt = RenderTypes.itemTranslucent(tex);
        // Scale to match vanilla flat item ~0.5? Hand uses 0.5-0.68, ground should be ~0.5
        // Vanilla items on ground are ~0.5 scale when flat? We use 0.5 to avoid huge
        poseStack.pushPose();
        poseStack.scale(0.5f, 0.5f, 0.5f);
        // Slight tilt like vanilla flat items lay flat but spin already handled; add slight X rotation to lay flat if needed?
        // For now keep billboard facing up (rotate X 90 to lay horizontal?). Vanilla flat items are rotated to face camera but we do Y spin already.
        // To make it lay flat on ground like vanilla, we don't extra rotate.
        collector.submitCustomGeometry(poseStack, rt, (pose, consumer) -> {
            // front face
            consumer.addVertex(pose, -0.5f, -0.5f, 0f).setColor(-1).setUv(0f, 1f).setOverlay(overlay).setLight(light).setNormal(pose, 0f, 0f, 1f);
            consumer.addVertex(pose, 0.5f, -0.5f, 0f).setColor(-1).setUv(1f, 1f).setOverlay(overlay).setLight(light).setNormal(pose, 0f, 0f, 1f);
            consumer.addVertex(pose, 0.5f, 0.5f, 0f).setColor(-1).setUv(1f, 0f).setOverlay(overlay).setLight(light).setNormal(pose, 0f, 0f, 1f);
            consumer.addVertex(pose, -0.5f, 0.5f, 0f).setColor(-1).setUv(0f, 0f).setOverlay(overlay).setLight(light).setNormal(pose, 0f, 0f, 1f);
            // back face slightly offset to give thickness
            consumer.addVertex(pose, -0.5f, 0.5f, 0.015f).setColor(-1).setUv(0f, 0f).setOverlay(overlay).setLight(light).setNormal(pose, 0f, 0f, -1f);
            consumer.addVertex(pose, 0.5f, 0.5f, 0.015f).setColor(-1).setUv(1f, 0f).setOverlay(overlay).setLight(light).setNormal(pose, 0f, 0f, -1f);
            consumer.addVertex(pose, 0.5f, -0.5f, 0.015f).setColor(-1).setUv(1f, 1f).setOverlay(overlay).setLight(light).setNormal(pose, 0f, 0f, -1f);
            consumer.addVertex(pose, -0.5f, -0.5f, 0.015f).setColor(-1).setUv(0f, 1f).setOverlay(overlay).setLight(light).setNormal(pose, 0f, 0f, -1f);
        });
        poseStack.popPose();
    }
}
