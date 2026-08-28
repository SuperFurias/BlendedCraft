package com.example.client.mixin;

import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CuboidItemModelWrapper.class)
public interface CuboidItemModelWrapperAccessor {
    @Accessor("properties")
    ModelRenderProperties blendedcraft$getProperties();

    @Accessor("transformation")
    Matrix4fc blendedcraft$getTransformation();
}
