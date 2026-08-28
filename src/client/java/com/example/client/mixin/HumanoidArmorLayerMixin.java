package com.example.client.mixin;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.example.client.BlendedArmorTextureManager;
import com.example.util.FlexibleRecipeHelper;
import net.minecraft.client.model.HumanoidModel;

@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin<S extends HumanoidRenderState, M extends HumanoidModel<S>, A extends HumanoidModel<S>> {
    private static final Logger LOGGER = LoggerFactory.getLogger("blendedcraft/HumanoidArmorLayerMixin");

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void onRenderArmorPiece(PoseStack poseStack, SubmitNodeCollector collector, ItemStack stack, EquipmentSlot slot, int light, S state, CallbackInfo ci) {
        if (stack.isEmpty()) return;
        if (!FlexibleRecipeHelper.isFlexibleResult(stack)) return;
        // Only for armor
        if (stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE) == null) return;
        try {
            Identifier armorTex = BlendedArmorTextureManager.getOrCreateArmorTexture(stack, slot);
            if (armorTex == null) return;
            // Cancel vanilla and render with our dynamic texture
            // We need to get the HumanoidArmorLayer's equipmentRenderer and modelSet to render
            // Instead of re-implementing, we can set the stack's equippable asset to our dynamic one? But we use a mixin to directly submit
            // For now, we will let the vanilla render but the texture will be our dynamic one via the blended asset
            // The blended asset's texture is blendedcraft:blended, which we need to make point to our dynamic texture
            // Instead, we can just not cancel, but ensure the dynamic texture is registered at the expected location
            // The expected location for blendedcraft:blended humanoid is assets/blendedcraft/textures/entity/equipment/humanoid/blended.png
            // Our dynamic texture is at blendedcraft:armor_blended/<hash>, not the same.
            // So we need to cancel and render manually with our texture
            // Get the layer type and model
            // We will use the EquipmentLayerRenderer directly with our dynamic texture
            // To avoid complex, we will just not cancel, but ensure the blended asset's texture is our dynamic one
            // The simplest: do not cancel, but Register our dynamic texture at the expected Identifier for blended
            // Expected: Identifier.fromNamespaceAndPath("blendedcraft", "textures/entity/equipment/humanoid/blended.png") is the file for blendedcraft:blended
            // Our dynamic is at blendedcraft:armor_blended/<hash>, not the same. So we need to override the lookup.
            // For now, we will cancel and do a simple blit? But armor is 3D, not 2D.
            // We will use the EquipmentLayerRenderer with our dynamic texture via the Identifier overload
            // We need to get the HumanoidArmorLayer's fields via shadow
            ci.cancel();
            // Use reflection to get the armor model and render with our texture
            // For simplicity, we will just not render and let the user see the blended texture via the dynamic armor manager
            // Actually, we can just set the stack's custom data to point to our armor texture and let the vanilla handle it
            // But we need to actually render something, so we will call the equipmentRenderer with our dynamic texture
            // Get the HumanoidArmorLayer's equipmentRenderer field
            // Use Mixin accessor
            HumanoidArmorLayerAccessor accessor = (HumanoidArmorLayerAccessor) (Object) this;
            net.minecraft.client.renderer.entity.ArmorModelSet<A> modelSet = (net.minecraft.client.renderer.entity.ArmorModelSet<A>) accessor.getModelSet();
            net.minecraft.client.renderer.entity.ArmorModelSet<A> babyModelSet = (net.minecraft.client.renderer.entity.ArmorModelSet<A>) accessor.getBabyModelSet();
            boolean isBaby = ((HumanoidRenderState) state).isBaby;
            A model = isBaby ? babyModelSet.get(slot) : modelSet.get(slot);
            int color = -1;
            var renderType = net.minecraft.client.renderer.rendertype.RenderTypes.armorCutoutNoCull(armorTex);
            int order = 0;
            collector.order(order).submitModel((net.minecraft.client.model.Model) model, (HumanoidRenderState) state, poseStack, renderType, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, color, null, 0, null);
            if (stack.hasFoil()) {
                var glintType = net.minecraft.client.renderer.rendertype.RenderTypes.armorEntityGlint();
                collector.order(order + 1).submitModel((net.minecraft.client.model.Model) model, (HumanoidRenderState) state, poseStack, glintType, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, color, null, 0, null);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to render blended armor", e);
        }
    }
}

