package com.example.item;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;

public class ModArmorMaterials {
    public static final ResourceKey<net.minecraft.world.item.equipment.EquipmentAsset> BLENDED_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath("blendedcraft", "blended"));

    public static final ArmorMaterial BLENDED = new ArmorMaterial(
            15, // durability
            createDefense(2, 5, 6, 2),
            10, // enchantment
            SoundEvents.ARMOR_EQUIP_IRON,
            0f, 0f,
            ItemTags.REPAIRS_IRON_ARMOR,
            BLENDED_ASSET
    );

    private static Map<ArmorType, Integer> createDefense(int boots, int leggings, int chestplate, int helmet) {
        Map<ArmorType, Integer> map = new EnumMap<>(ArmorType.class);
        map.put(ArmorType.BOOTS, boots);
        map.put(ArmorType.LEGGINGS, leggings);
        map.put(ArmorType.CHESTPLATE, chestplate);
        map.put(ArmorType.HELMET, helmet);
        map.put(ArmorType.BODY, 4);
        return map;
    }
}

