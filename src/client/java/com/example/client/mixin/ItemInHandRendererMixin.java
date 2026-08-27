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
        // Only handle hand contexts to avoid affecting GUI/ground/fixed which are handled elsewhere or vanilla
        if (displayContext == ItemDisplayContext.GUI || displayContext == ItemDisplayContext.GROUND || displayContext == ItemDisplayContext.FIXED) return;
        Identifier blendedId = BlendedTextureManager.getOrCreateBlendedTexture(stack);
        if (blendedId == null) return;
        poseStack.pushPose();
        RenderType renderType = RenderTypes.itemTranslucent(blendedId);
        collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            // 1x1 quad centered, as in previous visible version (e5527d4) but with correct overlay/normal
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

    private boolean isHandheld(ItemStack stack) {
        String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        // handheld parent used for pickaxe/axe/shovel/sword, generated for hoe/armor
        return path.endsWith("_pickaxe") || path.endsWith("_axe") || path.endsWith("_shovel") || path.endsWith("_sword");
    }
}
