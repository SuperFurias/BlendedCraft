package com.example.util;

import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import com.example.item.ModItems;

public class FlexibleRecipeHelper {
    public static boolean isFlexibleResult(ItemStackTemplate template) {
        if (template == null) return false;
        Holder<Item> holder = template.item();
        Item item = holder.value();
        // Only blended items are flexible - vanilla stays strict
        if (item == ModItems.BLENDED_HELMET) return true;
        if (item == ModItems.BLENDED_CHESTPLATE) return true;
        if (item == ModItems.BLENDED_LEGGINGS) return true;
        if (item == ModItems.BLENDED_BOOTS) return true;
        if (item == ModItems.BLENDED_PICKAXE) return true;
        if (item == ModItems.BLENDED_AXE) return true;
        if (item == ModItems.BLENDED_SHOVEL) return true;
        if (item == ModItems.BLENDED_SWORD) return true;
        if (item == ModItems.BLENDED_HOE) return true;
        // Also keep mace excluded even if someone makes a blended mace (not needed)
        if (item == net.minecraft.world.item.Items.MACE) return false;
        return false;
    }

    public static boolean isFlexibleResult(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item == ModItems.BLENDED_HELMET) return true;
        if (item == ModItems.BLENDED_CHESTPLATE) return true;
        if (item == ModItems.BLENDED_LEGGINGS) return true;
        if (item == ModItems.BLENDED_BOOTS) return true;
        if (item == ModItems.BLENDED_PICKAXE) return true;
        if (item == ModItems.BLENDED_AXE) return true;
        if (item == ModItems.BLENDED_SHOVEL) return true;
        if (item == ModItems.BLENDED_SWORD) return true;
        if (item == ModItems.BLENDED_HOE) return true;
        if (item == net.minecraft.world.item.Items.MACE) return false;
        return false;
    }
}
