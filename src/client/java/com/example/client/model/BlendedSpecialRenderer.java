package com.example.client.model;

import com.example.client.BlendedTextureManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * Tinkers'-style special renderer: submits the runtime-blended texture as an extruded
 * vanilla-like item mesh bound directly to the DynamicTexture id (no atlas needed).
 * Works in hand (first/third person), dropped items, item frames and any other
 * ItemModelResolver-driven context.
 */
public final class BlendedSpecialRenderer implements SpecialModelRenderer<BlendedSpecialRenderer.BlendArg> {
    public static final BlendedSpecialRenderer INSTANCE = new BlendedSpecialRenderer(false);
    /** GUI variant: uses the strong item glint (RenderType.glint) so inventory enchant glint matches vanilla strength. */
    public static final BlendedSpecialRenderer GUI = new BlendedSpecialRenderer(true);

    private final boolean strongGlint;

    private BlendedSpecialRenderer(boolean strongGlint) {
        this.strongGlint = strongGlint;
    }

    @Override
    public BlendArg extractArgument(ItemStack stack) {
        try {
            var custom = stack.get(DataComponents.CUSTOM_DATA);
            if (custom == null || custom.isEmpty()) return null;
            var tag = custom.copyTag();
            boolean blended = tag.contains("blended_ingredients") || tag.contains("blended_head") || tag.contains("blended_handle");
            if (!blended) return null;
            Identifier texId = BlendedTextureManager.getOrCreateBlendedTexture(stack);
            if (texId == null) return null;
            BlendedMesh mesh = BlendedMesh.getOrCreate(texId, () -> BlendedTextureManager.getCachedImage(texId));
            return new BlendArg(texId, mesh);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void submit(BlendArg arg, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, boolean foil, int seed) {
        if (arg == null) return;
        RenderType rt = arg.mesh().renderType(arg.texture());
        collector.order(0).submitCustomGeometry(poseStack, rt, (pose, vc) -> arg.mesh().emit(pose, vc, light, overlay, false));
        if (foil) {
            // world rendering keeps a single entityGlint pass (matches vanilla held/dropped strength);
            // GUI renders into the item atlas where a single pass reads weak, so draw it twice there
            RenderType glintRt = RenderTypes.entityGlint();
            collector.order(1).submitCustomGeometry(poseStack, glintRt, (pose, vc) -> arg.mesh().emit(pose, vc, light, overlay, true));
            if (strongGlint) {
                collector.order(2).submitCustomGeometry(poseStack, glintRt, (pose, vc) -> arg.mesh().emit(pose, vc, light, overlay, true));
            }
        }
    }

    @Override
    public void getExtents(Consumer<org.joml.Vector3fc> consumer) {
        consumer.accept(new org.joml.Vector3f(0f, 0f, 7.5f / 16f));
        consumer.accept(new org.joml.Vector3f(1f, 1f, 8.5f / 16f));
    }

    public record BlendArg(Identifier texture, BlendedMesh mesh) {}
}
