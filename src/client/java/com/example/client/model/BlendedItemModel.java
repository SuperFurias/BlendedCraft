package com.example.client.model;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fc;

/**
 * Wraps the baked vanilla item model (CuboidItemModelWrapper) of each blended item.
 * Delegates to the vanilla model for stacks without blended data (placeholders),
 * and otherwise renders the runtime-blended texture via {@link BlendedSpecialRenderer}
 * using the vanilla model's display transforms, so hand / dropped / third-person
 * rendering matches vanilla tool geometry exactly (Tinkers' MaterialModel approach).
 */
public final class BlendedItemModel implements ItemModel {
    private static final BlendedSpecialRenderer RENDERER = BlendedSpecialRenderer.INSTANCE;

    private final ModelRenderProperties properties;
    private final Matrix4fc transformation;
    private final ItemModel fallback;

    public BlendedItemModel(ModelRenderProperties properties, Matrix4fc transformation, ItemModel fallback) {
        this.properties = properties;
        this.transformation = transformation;
        this.fallback = fallback;
    }

    @Override
    public void update(ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver, ItemDisplayContext displayContext, ClientLevel level, ItemOwner owner, int seed) {
        state.appendModelIdentityElement(this);
        BlendedSpecialRenderer.BlendArg arg = RENDERER.extractArgument(stack);
        if (arg == null) {
            fallback.update(state, stack, resolver, displayContext, level, owner, seed);
            return;
        }
        ItemStackRenderState.LayerRenderState layer = state.newLayer();
        if (stack.hasFoil()) {
            layer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
            state.setAnimated();
            state.appendModelIdentityElement(ItemStackRenderState.FoilType.STANDARD);
        }
        layer.setExtents(arg.mesh().extents());
        layer.setLocalTransform(transformation);
        layer.setupSpecialModel(RENDERER, arg);
        properties.applyToLayer(layer, displayContext);
    }
}
