package com.example.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import com.example.ExampleMod;

public class ModItemIds {
    public static final ResourceKey<Item> TEST = create("test");
    public static final ResourceKey<Item> BLENDED_HELMET = create("blended_helmet");
    public static final ResourceKey<Item> BLENDED_CHESTPLATE = create("blended_chestplate");
    public static final ResourceKey<Item> BLENDED_LEGGINGS = create("blended_leggings");
    public static final ResourceKey<Item> BLENDED_BOOTS = create("blended_boots");
    public static final ResourceKey<Item> BLENDED_PICKAXE = create("blended_pickaxe");
    public static final ResourceKey<Item> BLENDED_AXE = create("blended_axe");
    public static final ResourceKey<Item> BLENDED_SHOVEL = create("blended_shovel");
    public static final ResourceKey<Item> BLENDED_SWORD = create("blended_sword");
    public static final ResourceKey<Item> BLENDED_HOE = create("blended_hoe");

    public static ResourceKey<Item> create(String name) {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(ExampleMod.MOD_ID, name));
    }
}
