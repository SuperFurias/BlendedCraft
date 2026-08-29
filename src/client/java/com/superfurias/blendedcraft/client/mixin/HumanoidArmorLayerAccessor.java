package com.superfurias.blendedcraft.client.mixin;

import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;

@Mixin(HumanoidArmorLayer.class)
public interface HumanoidArmorLayerAccessor {
    @Accessor("modelSet")
    ArmorModelSet<?> getModelSet();

    @Accessor("babyModelSet")
    ArmorModelSet<?> getBabyModelSet();

    @Accessor("equipmentRenderer")
    EquipmentLayerRenderer getEquipmentRenderer();
}
