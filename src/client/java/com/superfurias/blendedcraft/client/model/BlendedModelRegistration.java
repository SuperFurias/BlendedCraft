package com.superfurias.blendedcraft.client.model;

import com.superfurias.blendedcraft.client.mixin.CuboidItemModelWrapperAccessor;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4fc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers the blended item model wrapper via Fabric's model loading API
 * (ModelModifier.AfterBakeItem, WRAP phase), Tinkers'-style: the baked vanilla
 * model is wrapped so every render context (GUI, first-person hand, dropped item
 * entities, third-person players, item frames) renders the runtime-blended texture.
 */
public final class BlendedModelRegistration {
    private static final Logger LOGGER = LoggerFactory.getLogger("blendedcraft/BlendedModel");
    private static boolean registered = false;

    private BlendedModelRegistration() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ModelLoadingPlugin.register(context -> context.modifyItemModelAfterBake().register((model, modContext) -> {
            Identifier itemId = modContext.itemId();
            if (!"blendedcraft".equals(itemId.getNamespace())) return model;
            String path = itemId.getPath();
            if (!FLEXIBLE_PATHS.contains(path)) return model;

            ModelRenderProperties properties = null;
            Matrix4fc transformation = null;
            if (model instanceof CuboidItemModelWrapperAccessor accessor) {
                properties = accessor.blendedcraft$getProperties();
                transformation = accessor.blendedcraft$getTransformation();
            }
            if (properties == null) {
                LOGGER.warn("Blended model {} was not a CuboidItemModelWrapper ({}), using fallback properties", itemId, model.getClass().getName());
                properties = new ModelRenderProperties(true, null, net.minecraft.client.resources.model.cuboid.ItemTransforms.NO_TRANSFORMS);
                transformation = new org.joml.Matrix4f().identity();
            }
            return new BlendedItemModel(properties, transformation, model);
        }));
        LOGGER.info("Blended item model wrapper registered (held/dropped/third-person custom textures)");
    }

    private static final java.util.Set<String> FLEXIBLE_PATHS = java.util.Set.of(
            "blended_pickaxe", "blended_axe", "blended_shovel", "blended_hoe", "blended_sword",
            "blended_helmet", "blended_chestplate", "blended_leggings", "blended_boots"
    );
}
