package com.example.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.example.client.BlendedTextureManager;
import com.example.util.FlexibleRecipeHelper;

@Mixin(GuiGraphicsExtractor.class)
public class GuiGraphicsExtractorMixin {

    @Inject(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V", at = @At("HEAD"), cancellable = true)
    private void onItemHead(LivingEntity entity, Level level, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        if (stack.isEmpty()) return;
        if (!FlexibleRecipeHelper.isFlexibleResult(stack)) return;
        if (!stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) return;
        try {
            Identifier blendedId = BlendedTextureManager.getOrCreateBlendedTexture(stack);
            if (blendedId == null) return;
            GuiGraphicsExtractor self = (GuiGraphicsExtractor) (Object) this;
            // Replace vanilla placeholder (red T 148b) with shape-masked blended texture
            // Cancel vanilla TrackingItemStackRenderState so background is transparent and test item behind is not visible
            self.blit(RenderPipelines.GUI_TEXTURED, blendedId, x, y, 0.0F, 0.0F, 16, 16, 16, 16);
            ci.cancel();
        } catch (Exception e) {
        }
    }
}
