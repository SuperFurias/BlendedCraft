package com.example.item;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;

import com.example.ExampleMod;

public class ModItems {

    // Blended armor - custom blended material, equipped texture matches inventory blended texture
    public static final Item BLENDED_HELMET = register(ModItemIds.BLENDED_HELMET, Item::new,
            new Item.Properties().humanoidArmor(ModArmorMaterials.BLENDED, ArmorType.HELMET));
    public static final Item BLENDED_CHESTPLATE = register(ModItemIds.BLENDED_CHESTPLATE, Item::new,
            new Item.Properties().humanoidArmor(ModArmorMaterials.BLENDED, ArmorType.CHESTPLATE));
    public static final Item BLENDED_LEGGINGS = register(ModItemIds.BLENDED_LEGGINGS, Item::new,
            new Item.Properties().humanoidArmor(ModArmorMaterials.BLENDED, ArmorType.LEGGINGS));
    public static final Item BLENDED_BOOTS = register(ModItemIds.BLENDED_BOOTS, Item::new,
            new Item.Properties().humanoidArmor(ModArmorMaterials.BLENDED, ArmorType.BOOTS));

    // Blended tools - base on wood, stats overridden
    public static final Item BLENDED_PICKAXE = register(ModItemIds.BLENDED_PICKAXE, Item::new,
            new Item.Properties().pickaxe(net.minecraft.world.item.ToolMaterial.WOOD, 1.0f, -2.8f));
    public static final Item BLENDED_AXE = register(ModItemIds.BLENDED_AXE, Item::new,
            new Item.Properties().axe(net.minecraft.world.item.ToolMaterial.WOOD, 6.0f, -3.1f));
    public static final Item BLENDED_SHOVEL = register(ModItemIds.BLENDED_SHOVEL, Item::new,
            new Item.Properties().shovel(net.minecraft.world.item.ToolMaterial.WOOD, 1.5f, -3.0f));
    public static final Item BLENDED_SWORD = register(ModItemIds.BLENDED_SWORD, Item::new,
            new Item.Properties().sword(net.minecraft.world.item.ToolMaterial.WOOD, 3.0f, -2.4f));
    public static final Item BLENDED_HOE = register(ModItemIds.BLENDED_HOE, Item::new,
            new Item.Properties().hoe(net.minecraft.world.item.ToolMaterial.WOOD, 0f, -3.0f));

    public static final ResourceKey<CreativeModeTab> TEST_TAB_KEY = ResourceKey.create(
            BuiltInRegistries.CREATIVE_MODE_TAB.key(),
            Identifier.fromNamespaceAndPath(ExampleMod.MOD_ID, "test_tab")
    );

    public static final CreativeModeTab TEST_TAB = FabricCreativeModeTab.builder()
            .icon(() -> new ItemStack(ModItems.BLENDED_PICKAXE))
            .title(Component.translatable("itemGroup.blendedcraft.test"))
            .displayItems((params, output) -> {
                output.accept(ModItems.BLENDED_HELMET);
                output.accept(ModItems.BLENDED_CHESTPLATE);
                output.accept(ModItems.BLENDED_LEGGINGS);
                output.accept(ModItems.BLENDED_BOOTS);
                output.accept(ModItems.BLENDED_PICKAXE);
                output.accept(ModItems.BLENDED_AXE);
                output.accept(ModItems.BLENDED_SHOVEL);
                output.accept(ModItems.BLENDED_SWORD);
                output.accept(ModItems.BLENDED_HOE);
            })
            .build();

    public static Item register(ResourceKey<Item> itemKey, Function<Item.Properties, Item> itemFactory, Item.Properties settings) {
        Item item = itemFactory.apply(settings.setId(itemKey));
        Registry.register(BuiltInRegistries.ITEM, itemKey, item);
        return item;
    }

    public static void initialize() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TEST_TAB_KEY, TEST_TAB);
    }
}

