package com.superfurias.blendedcraft.util;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class BlendedStatsHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger("blendedcraft/BlendedStatsHelper");

    public record MaterialStats(int durability, float speed, float attackDamage, int enchantability, int defense, float toughness, float knockbackResistance) {}

    // Hardness-based: get destroyTime for block, or tier for ingots - exposed for mixin reuse
    public static float getHardnessForItem(Item item) {
        // Try BlockItem
        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            try {
                float d = block.defaultDestroyTime();
                if (d < 0) return 50f; // bedrock - unbreakable -> max
                if (d == 0) return 0.2f;
                return d;
            } catch (Exception e) {
                return 1.0f;
            }
        }
        // For ingots/gems, map to block hardness
        if (item == Items.DIAMOND) return Blocks.DIAMOND_BLOCK.defaultDestroyTime(); // 5.0
        if (item == Items.IRON_INGOT) return Blocks.IRON_BLOCK.defaultDestroyTime(); // 5.0
        if (item == Items.GOLD_INGOT) return Blocks.GOLD_BLOCK.defaultDestroyTime(); // 3.0
        if (item == Items.COPPER_INGOT) return 3.0f; // copper block 3.0
        if (item == Items.NETHERITE_INGOT) return Blocks.NETHERITE_BLOCK.defaultDestroyTime(); // 50.0
        if (item == Items.EMERALD) return Blocks.EMERALD_BLOCK.defaultDestroyTime(); // 5.0
        if (item == Items.AMETHYST_SHARD) return Blocks.AMETHYST_BLOCK.defaultDestroyTime(); // 1.5
        if (item == Items.NETHERITE_SCRAP) return 50f;
        if (item == Items.QUARTZ) return 0.8f;
        if (item == Items.STICK) return 0.5f;
        if (item == Items.LEATHER) return 0.3f;
        if (item == Items.TURTLE_SCUTE) return 0.5f;
        if (item == Items.ARMADILLO_SCUTE) return 0.6f;
        if (item == Items.BEDROCK) return 50f; // bedrock item? Actually bedrock is block item
        if (item == Items.OBSIDIAN) return 50f;
        // For planks, stone etc.
        String id = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (id.contains("bedrock")) return 50f;
        if (id.contains("obsidian")) return 50f;
        if (id.contains("ancient_debris")) return 30f;
        if (id.contains("netherite")) return 50f;
        if (id.contains("diamond")) return 5f;
        if (id.contains("emerald")) return 5f;
        if (id.contains("iron")) return 5f;
        if (id.contains("gold")) return 3f;
        if (id.contains("copper")) return 3f;
        if (id.contains("sand")) return 0.5f;
        if (id.contains("cactus")) return 0.4f;
        if (id.contains("dirt")) return 0.5f;
        if (id.contains("stone") || id.contains("cobble")) return 1.5f;
        if (id.contains("wood") || id.contains("plank") || id.contains("log")) return 2.0f;
        if (id.contains("leather")) return 0.3f;
        return 1.0f;
    }

    public static Rarity getRarityForItem(Item item) {
        try {
            ItemStack def = item.getDefaultInstance();
            Rarity r = def.get(DataComponents.RARITY);
            if (r != null) return r;
        } catch (Exception ignored) {}
        String id = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (id.equals("nether_star") || id.equals("dragon_egg") || id.equals("elytra") || id.equals("totem_of_undying") || id.equals("heart_of_the_sea") || id.equals("conduit") || id.equals("beacon") || id.equals("enchanted_golden_apple") || id.equals("trident") || id.equals("netherite_block") || id.equals("ancient_debris") || id.equals("dragon_breath") || id.equals("shulker_shell"))
            return id.equals("nether_star") || id.equals("dragon_egg") || id.equals("enchanted_golden_apple") ? Rarity.EPIC : Rarity.RARE;
        return Rarity.COMMON;
    }

    private static float getRarityMultiplier(Rarity r) {
        return switch (r) {
            case COMMON -> 1.0f;
            case UNCOMMON -> 1.35f;
            case RARE -> 1.85f;
            case EPIC -> 2.6f;
        };
    }

    private static boolean isFireResistant(Item item) {
        try {
            // In 26.2 fire resistance is via DAMAGE_RESISTANT component; fallback to path check for netherite
            var stack = item.getDefaultInstance();
            if (stack.has(DataComponents.DAMAGE_RESISTANT)) return true;
            String p = BuiltInRegistries.ITEM.getKey(item).getPath();
            return p.contains("netherite") || p.contains("ancient_debris");
        } catch (Exception e) { return false; }
    }

    public static MaterialStats getSpecialStatsForRareItem(Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (id.equals("nether_star")) return new MaterialStats(3200, 11.0f, 6.0f, 25, 5, 4.5f, 0.25f);
        if (id.equals("dragon_egg")) return new MaterialStats(2800, 10.5f, 5.5f, 22, 5, 4.0f, 0.2f);
        if (id.equals("elytra")) return new MaterialStats(900, 7.0f, 2.0f, 18, 3, 1.5f, 0.05f);
        if (id.equals("totem_of_undying")) return new MaterialStats(1200, 8.0f, 3.0f, 20, 3, 2.0f, 0.1f);
        if (id.equals("heart_of_the_sea")) return new MaterialStats(1500, 8.5f, 3.5f, 20, 3, 2.5f, 0.1f);
        if (id.equals("conduit")) return new MaterialStats(1800, 9.0f, 4.0f, 18, 4, 3.0f, 0.15f);
        if (id.equals("beacon")) return new MaterialStats(2000, 9.5f, 4.5f, 18, 4, 3.0f, 0.15f);
        if (id.equals("enchanted_golden_apple")) return new MaterialStats(600, 7.5f, 2.5f, 22, 2, 1.0f, 0f);
        if (id.equals("trident")) return new MaterialStats(1100, 8.0f, 4.0f, 15, 3, 2.0f, 0.05f);
        if (id.equals("netherite_block")) return new MaterialStats(2800, 10.0f, 5.0f, 18, 5, 4.0f, 0.2f);
        if (id.equals("ancient_debris")) return new MaterialStats(2200, 9.5f, 4.5f, 16, 4, 3.5f, 0.15f);
        if (id.equals("dragon_breath")) return new MaterialStats(800, 6.5f, 2.0f, 15, 2, 1.0f, 0f);
        if (id.equals("shulker_shell")) return new MaterialStats(700, 6.0f, 1.5f, 12, 2, 1.0f, 0.05f);
        return null;
    }

    public static float getToolBaseDamage(String type) {
        return switch (type) {
            case "pickaxe" -> 1.0f;
            case "axe" -> 6.0f;
            case "shovel" -> 1.5f;
            case "hoe" -> 0.0f;
            case "sword" -> 3.0f;
            default -> 1.0f;
        };
    }

    public static float getToolBaseSpeed(String type) {
        return switch (type) {
            case "pickaxe" -> -2.8f;
            case "axe" -> -3.2f;
            case "shovel" -> -3.0f;
            case "hoe" -> -3.0f;
            case "sword" -> -2.4f;
            default -> -2.8f;
        };
    }

    public static net.minecraft.world.item.equipment.ArmorMaterial getEffectiveArmorMaterial(Item item) {
        net.minecraft.world.item.equipment.ArmorMaterial mat = getArmorMaterialForItem(item);
        if (mat != null) return mat;
        Rarity rarity = getRarityForItem(item);
        float hard = getHardnessForItem(item);
        ToolMaterial tier = getToolMaterialForItem(item);
        if (tier == null) {
            if (hard >= 50f) tier = ToolMaterial.NETHERITE;
            else if (hard >= 5f) tier = ToolMaterial.DIAMOND;
            else if (hard >= 3f) tier = ToolMaterial.IRON;
            else if (hard >= 1.5f) tier = ToolMaterial.STONE;
            else tier = ToolMaterial.WOOD;
        }
        if (tier == ToolMaterial.NETHERITE) return ArmorMaterials.NETHERITE;
        if (tier == ToolMaterial.DIAMOND) return ArmorMaterials.DIAMOND;
        if (tier == ToolMaterial.IRON) return ArmorMaterials.IRON;
        if (tier == ToolMaterial.GOLD) return ArmorMaterials.GOLD;
        if (tier == ToolMaterial.COPPER) return ArmorMaterials.COPPER;
        if (tier == ToolMaterial.STONE) return ArmorMaterials.CHAINMAIL;
        if (rarity == Rarity.EPIC) return ArmorMaterials.NETHERITE;
        if (rarity == Rarity.RARE) return ArmorMaterials.DIAMOND;
        if (rarity == Rarity.UNCOMMON) return ArmorMaterials.IRON;
        return ArmorMaterials.LEATHER;
    }

    public static int getArmorDefenseForItem(Item item, net.minecraft.world.item.equipment.ArmorType type) {
        net.minecraft.world.item.equipment.ArmorMaterial mat = getEffectiveArmorMaterial(item);
        MaterialStats special = getSpecialStatsForRareItem(item);
        if (special != null) {
            int base = special.defense();
            return switch (type) {
                case HELMET -> Math.max(1, (int)(base * 0.6f));
                case CHESTPLATE -> base + (special.toughness() > 3 ? 3 : 0);
                case LEGGINGS -> Math.max(1, (int)(base * 0.9f));
                case BOOTS -> Math.max(1, (int)(base * 0.5f));
                case BODY -> base;
            };
        }
        Integer d = mat.defense().get(type);
        if (d != null) return d;
        MaterialStats s = statsForItem(item);
        int baseDef = s.defense();
        return switch (type) {
            case HELMET -> Math.max(0, (int)(baseDef * 0.6f));
            case CHESTPLATE -> baseDef;
            case LEGGINGS -> Math.max(0, (int)(baseDef * 0.8f));
            case BOOTS -> Math.max(0, (int)(baseDef * 0.4f));
            default -> baseDef;
        };
    }

    public static float[] getVanillaToolStats(ToolMaterial tier, String toolType) {
        // Returns {attackDamageDisplayed, attackSpeedDisplayed} for vanilla tool of tier and type (from wiki + Items.java)
        // Pickaxe: Wood 2/1.2, Stone 3/1.2, Copper 3/1.2, Iron 4/1.2, Gold 2/1.2, Diamond 5/1.2, Netherite 6/1.2
        // Axe: Wood 7/0.8, Stone 9/0.8, Copper 7/0.8, Iron 9/0.9, Gold 7/1.0, Diamond 9/1.0, Netherite 10/1.0
        // Shovel: Wood 2.5/1.0, Stone 3.5/1.0, Copper 3.5/1.0, Iron 4.5/1.0, Gold 2.5/1.0, Diamond 5.5/1.0, Netherite 6.5/1.0
        // Hoe: Wood 1/1.0, Stone 1/2.0, Copper 1/2.0, Iron 1/3.0, Gold 1/1.0, Diamond 1/4.0, Netherite 1/4.0
        // Sword: Wood 4/1.6, Stone 5/1.6, Copper 5/1.6, Iron 6/1.6, Gold 4/1.6, Diamond 7/1.6, Netherite 8/1.6
        // Based on ToolMaterial attackDamageBonus + toolBase and Items.java ldc values
        int tierOrd = 0;
        if (tier == ToolMaterial.WOOD) tierOrd = 0;
        else if (tier == ToolMaterial.STONE) tierOrd = 1;
        else if (tier == ToolMaterial.COPPER) tierOrd = 2;
        else if (tier == ToolMaterial.IRON) tierOrd = 3;
        else if (tier == ToolMaterial.GOLD) tierOrd = 4;
        else if (tier == ToolMaterial.DIAMOND) tierOrd = 5;
        else if (tier == ToolMaterial.NETHERITE) tierOrd = 6;
        else tierOrd = 3; // default iron
        return switch (toolType) {
            case "pickaxe" -> switch (tierOrd) {
                case 0 -> new float[]{2.0f, 1.2f};
                case 1 -> new float[]{3.0f, 1.2f};
                case 2 -> new float[]{3.0f, 1.2f};
                case 3 -> new float[]{4.0f, 1.2f};
                case 4 -> new float[]{2.0f, 1.2f};
                case 5 -> new float[]{5.0f, 1.2f};
                case 6 -> new float[]{6.0f, 1.2f};
                default -> new float[]{4.0f, 1.2f};
            };
            case "axe" -> switch (tierOrd) {
                case 0 -> new float[]{7.0f, 0.8f};
                case 1 -> new float[]{9.0f, 0.8f};
                case 2 -> new float[]{7.0f, 0.8f};
                case 3 -> new float[]{9.0f, 0.9f};
                case 4 -> new float[]{7.0f, 1.0f};
                case 5 -> new float[]{9.0f, 1.0f};
                case 6 -> new float[]{10.0f, 1.0f};
                default -> new float[]{9.0f, 0.9f};
            };
            case "shovel" -> switch (tierOrd) {
                case 0 -> new float[]{2.5f, 1.0f};
                case 1 -> new float[]{3.5f, 1.0f};
                case 2 -> new float[]{3.5f, 1.0f};
                case 3 -> new float[]{4.5f, 1.0f};
                case 4 -> new float[]{2.5f, 1.0f};
                case 5 -> new float[]{5.5f, 1.0f};
                case 6 -> new float[]{6.5f, 1.0f};
                default -> new float[]{4.5f, 1.0f};
            };
            case "hoe" -> switch (tierOrd) {
                case 0 -> new float[]{1.0f, 1.0f};
                case 1 -> new float[]{1.0f, 2.0f};
                case 2 -> new float[]{1.0f, 2.0f};
                case 3 -> new float[]{1.0f, 3.0f};
                case 4 -> new float[]{1.0f, 1.0f};
                case 5 -> new float[]{1.0f, 4.0f};
                case 6 -> new float[]{1.0f, 4.0f};
                default -> new float[]{1.0f, 1.0f};
            };
            case "sword" -> switch (tierOrd) {
                case 0 -> new float[]{4.0f, 1.6f};
                case 1 -> new float[]{5.0f, 1.6f};
                case 2 -> new float[]{5.0f, 1.6f};
                case 3 -> new float[]{6.0f, 1.6f};
                case 4 -> new float[]{4.0f, 1.6f};
                case 5 -> new float[]{7.0f, 1.6f};
                case 6 -> new float[]{8.0f, 1.6f};
                default -> new float[]{6.0f, 1.6f};
            };
            default -> new float[]{4.0f, 1.6f};
        };
    }

    public static float[] getBlendedToolStats(ToolMaterial tier, String toolType, MaterialStats headStats) {
        float[] vanilla = getVanillaToolStats(tier, toolType);
        float vanillaDmgDisplayed = vanilla[0];
        float vanillaSpdDisplayed = vanilla[1];
        float tierBonus = tier.attackDamageBonus();
        float headBonus = headStats.attackDamage();
        float excessDmg = headBonus - tierBonus;
        // For pickaxe base 1, netherite bonus 4, head 6 => excess 2 => 6+2=8
        // For axe base 6, netherite bonus 4, head 6 => excess 2 => 10+2=12 -> but displayed 13 for nether_star axe is 6+6+1=13, so we need to handle that the vanillaDmg already includes base+bonus+1, so excess should be added directly
        float blendedDmgDisplayed = vanillaDmgDisplayed + excessDmg;
        // Clamp and ensure at least vanilla
        blendedDmgDisplayed = Math.max(vanillaDmgDisplayed, blendedDmgDisplayed);
        // For attack speed, keep vanilla's displayed speed (tier-dependent for axe/hoe) – handle does not affect
        float blendedSpdDisplayed = vanillaSpdDisplayed;
        return new float[]{blendedDmgDisplayed, blendedSpdDisplayed};
    }

    private static int getColorForItem(Item item) {
        // Hardcoded colors for common items (average texture color)
        if (item == Items.BEDROCK) return 0x535353;
        if (item == Items.OBSIDIAN) return 0x150B1E;
        if (item == Items.DIAMOND) return 0x5CDBD5;
        if (item == Items.IRON_INGOT) return 0xD8D8D8;
        if (item == Items.GOLD_INGOT) return 0xFAEE4D;
        if (item == Items.COPPER_INGOT) return 0xC4621B;
        if (item == Items.NETHERITE_INGOT) return 0x443A3B;
        if (item == Items.EMERALD) return 0x17C37B;
        if (item == Items.AMETHYST_SHARD) return 0x9A5CC6;
        if (item == Items.SAND) return 0xE2C58B;
        if (item == Items.CACTUS) return 0x5A9C3E;
        if (item == Items.STICK) return 0x9C6B2E;
        if (item == Items.LEATHER) return 0xA0652F;
        if (item == Items.COBBLESTONE) return 0x7F7F7F;
        if (item == Items.STONE) return 0x8A8A8A;
        if (item == Items.OAK_PLANKS) return 0xC49A3C;
        if (item == Items.TURTLE_SCUTE) return 0x5DB260;
        if (item == Items.ARMADILLO_SCUTE) return 0x8E5A3A;
        if (item == Items.QUARTZ) return 0xE8E8E8;
        if (item == Blocks.BEDROCK.asItem()) return 0x535353;
        // Try to get from block map color or fallback
        String id = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (id.contains("bedrock")) return 0x535353;
        if (id.contains("obsidian")) return 0x150B1E;
        if (id.contains("diamond")) return 0x5CDBD5;
        if (id.contains("iron")) return 0xD8D8D8;
        if (id.contains("gold")) return 0xFAEE4D;
        if (id.contains("copper")) return 0xC4621B;
        if (id.contains("netherite")) return 0x443A3B;
        if (id.contains("emerald")) return 0x17C37B;
        if (id.contains("sand")) return 0xE2C58B;
        if (id.contains("cactus")) return 0x5A9C3E;
        if (id.contains("redstone")) return 0x9B1A1A;
        if (id.contains("lapis")) return 0x1A4D9B;
        if (id.contains("coal")) return 0x2B2B2B;
        // Fallback: hash to color
        int hash = id.hashCode();
        int r = (hash & 0xFF0000) >> 16;
        int g = (hash & 0x00FF00) >> 8;
        int b = hash & 0x0000FF;
        r = 100 + (r % 100);
        g = 100 + (g % 100);
        b = 100 + (b % 100);
        return (r << 16) | (g << 8) | b;
    }

    public static int getBlendedColor(CraftingInput input) {
        List<ItemStack> items = input.items();
        int rSum = 0, gSum = 0, bSum = 0, count = 0;
        for (ItemStack s : items) {
            if (s.isEmpty()) continue;
            int col = getColorForItem(s.getItem());
            rSum += (col >> 16) & 0xFF;
            gSum += (col >> 8) & 0xFF;
            bSum += col & 0xFF;
            count++;
        }
        if (count == 0) return 0xA0652F;
        int r = rSum / count;
        int g = gSum / count;
        int b = bSum / count;
        return (r << 16) | (g << 8) | b;
    }

    public static MaterialStats statsForItem(Item item) {
        MaterialStats special = getSpecialStatsForRareItem(item);
        if (special != null) return special;
        float hardness = getHardnessForItem(item);
        Rarity rarity = getRarityForItem(item);
        float rarityMult = getRarityMultiplier(rarity);
        boolean fireResistant = isFireResistant(item);
        // Map hardness to stats: bedrock 50 -> max, diamond 5 -> high, sand 0.5 -> low
        // Use formula: durability = 50 + hardness*40 (sand 70, stone 110, iron/diamond 250/250, obsidian/bedrock 2050)
        // But we want bedrock > diamond, so obsidian/bedrock should be highest
        // For netherite, hardness 50 -> 2050, diamond 5 -> 250, iron 5 -> 250, gold 3 -> 170, sand 0.5 -> 70, etc.
        // To differentiate diamond vs iron (both hardness 5), we add tier bonus
        int baseDur;
        float baseSpeed;
        float baseDamage;
        int baseEnchant;
        int baseDefense;
        float baseTough;
        float baseKB = 0f;

        // Tier bonus for known ingots
        if (item == Items.DIAMOND) {
            baseDur = 1561; baseSpeed = 8.0f; baseDamage = 3.0f; baseEnchant = 10; baseDefense = 3; baseTough = 2.0f;
        } else if (item == Items.NETHERITE_INGOT) {
            baseDur = 2031; baseSpeed = 9.0f; baseDamage = 4.0f; baseEnchant = 15; baseDefense = 3; baseTough = 3.0f; baseKB = 0.1f;
        } else if (item == Items.IRON_INGOT) {
            baseDur = 250; baseSpeed = 6.0f; baseDamage = 2.0f; baseEnchant = 14; baseDefense = 2; baseTough = 0f;
        } else if (item == Items.GOLD_INGOT) {
            baseDur = 32; baseSpeed = 12.0f; baseDamage = 0f; baseEnchant = 22; baseDefense = 1; baseTough = 0f;
        } else if (item == Items.COPPER_INGOT) {
            baseDur = 190; baseSpeed = 5.0f; baseDamage = 1.5f; baseEnchant = 10; baseDefense = 2; baseTough = 0f;
        } else if (item == Items.EMERALD) {
            baseDur = 500; baseSpeed = 6.5f; baseDamage = 2.5f; baseEnchant = 12; baseDefense = 2; baseTough = 0f;
        } else if (item == Items.LEATHER) {
            baseDur = 80; baseSpeed = 1.0f; baseDamage = 0f; baseEnchant = 15; baseDefense = 1; baseTough = 0f;
        } else {
            // Generic hardness-based
            // hardness 0.5 -> low, 50 -> high
            // For bedrock/obsidian (50), we want highest: use netherite tier
            if (hardness >= 50f) {
                // Unbreakable / obsidian tier -> stronger than netherite
                baseDur = (int)(2300 + hardness*2); // 2400 for 50
                baseSpeed = 9.5f;
                baseDamage = 4.5f;
                baseEnchant = 12;
                baseDefense = 4; // higher than diamond 3
                baseTough = 3.5f;
                baseKB = 0.15f;
            } else if (hardness >= 3f) {
                // Diamond/iron tier
                baseDur = (int)(150 + hardness*200); // 5 -> 1150, but we want 250-1561, so use tiered
                // Instead map hardness 3-5 to iron-diamond
                baseDur = (int)(250 + (hardness - 3f)*500); // 3->250, 5->1250
                baseSpeed = 6.0f + (hardness - 3f)*0.8f; // 3->6.0, 5->7.6
                baseDamage = 2.0f + (hardness - 3f)*0.4f;
                baseEnchant = 12;
                baseDefense = (int)Math.min(4, 2 + (hardness - 3f));
                baseTough = hardness >= 4f ? 2.0f : 0f;
            } else if (hardness >= 1.5f) {
                baseDur = (int)(100 + hardness*40); // 1.5->160
                baseSpeed = 3.0f + hardness*0.5f;
                baseDamage = 1.0f;
                baseEnchant = 8;
                baseDefense = 1;
                baseTough = 0f;
            } else {
                baseDur = (int)(30 + hardness*40); // 0.5->50, 0.4->46
                baseSpeed = 1.0f + hardness;
                baseDamage = 0.5f;
                baseEnchant = 5;
                baseDefense = 0;
                baseTough = 0f;
            }
            // Special for sand/cactus low
            if (item == Items.SAND) { baseDur=40; baseSpeed=1.5f; baseDefense=0; }
            if (item == Items.CACTUS) { baseDur=60; baseSpeed=2.0f; baseDefense=0; }
            if (item == Items.OBSIDIAN) { baseDur=1200; baseSpeed=6.5f; baseDamage=2.5f; baseDefense=3; baseTough=2.0f; }
            if (BuiltInRegistries.ITEM.getKey(item).getPath().contains("bedrock")) { baseDur=3000; baseSpeed=10f; baseDamage=5f; baseDefense=4; baseTough=4f; baseKB=0.2f; }
        }
        // Apply rarity multiplier (so nether star etc are OP even if hardness low) and fire resistant bonus
        if (rarityMult != 1.0f) {
            baseDur = (int)(baseDur * rarityMult);
            baseSpeed = baseSpeed * (0.75f + 0.25f * rarityMult);
            baseDamage = baseDamage * (0.8f + 0.2f * rarityMult) + (rarity == Rarity.EPIC ? 1.0f : rarity == Rarity.RARE ? 0.5f : 0f);
            baseEnchant = (int)(baseEnchant * (0.85f + 0.15f * rarityMult)) + (rarity == Rarity.EPIC ? 3 : rarity == Rarity.RARE ? 1 : 0);
            baseDefense = Math.min(6, (int)(baseDefense * rarityMult + (rarity == Rarity.EPIC ? 1 : 0)));
            baseTough = baseTough * rarityMult + (rarity == Rarity.EPIC ? 0.5f : 0f);
            baseKB = Math.min(0.3f, baseKB * rarityMult + (rarity == Rarity.RARE ? 0.02f : 0f));
        }
        if (fireResistant) {
            baseDur = (int)(baseDur * 1.12f);
            baseTough += 0.3f;
            baseKB = Math.min(0.3f, baseKB + 0.03f);
        }
        baseDur = Math.min(4000, Math.max(1, baseDur));
        baseSpeed = Math.min(12.0f, baseSpeed);
        baseDamage = Math.min(7.0f, baseDamage);
        baseEnchant = Math.min(30, baseEnchant);
        baseDefense = Math.min(6, baseDefense);
        baseTough = Math.min(5.0f, baseTough);
        return new MaterialStats(baseDur, baseSpeed, baseDamage, baseEnchant, baseDefense, baseTough, baseKB);
    }

    @SuppressWarnings("unused")
    private static int getArmorDefenseForType(Item item, ArmorType type) {
        var mat = getArmorMaterialForItem(item);
        if (mat != null) {
            return mat.defense().getOrDefault(type, 0);
        }
        return statsForItem(item).defense();
    }

    public static net.minecraft.world.item.equipment.ArmorMaterial getArmorMaterialForItem(Item item) {
        if (item == Items.LEATHER) return ArmorMaterials.LEATHER;
        if (item == Items.COPPER_INGOT) return ArmorMaterials.COPPER;
        if (item == Items.IRON_INGOT) return ArmorMaterials.IRON;
        if (item == Items.GOLD_INGOT) return ArmorMaterials.GOLD;
        if (item == Items.DIAMOND) return ArmorMaterials.DIAMOND;
        if (item == Items.NETHERITE_INGOT) return ArmorMaterials.NETHERITE;
        if (item == Items.TURTLE_SCUTE) return ArmorMaterials.TURTLE_SCUTE;
        if (item == Items.ARMADILLO_SCUTE) return ArmorMaterials.ARMADILLO_SCUTE;
        String id = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (id.contains("leather")) return ArmorMaterials.LEATHER;
        if (id.contains("copper")) return ArmorMaterials.COPPER;
        if (id.contains("iron")) return ArmorMaterials.IRON;
        if (id.contains("gold")) return ArmorMaterials.GOLD;
        if (id.contains("diamond")) return ArmorMaterials.DIAMOND;
        if (id.contains("netherite")) return ArmorMaterials.NETHERITE;
        if (id.contains("turtle")) return ArmorMaterials.TURTLE_SCUTE;
        if (id.contains("armadillo")) return ArmorMaterials.ARMADILLO_SCUTE;
        if (id.contains("bedrock") || id.contains("obsidian")) return ArmorMaterials.NETHERITE; // treat as netherite+ for fallback
        return null;
    }

    public static MaterialStats averageStats(CraftingInput input) {
        List<ItemStack> items = input.items();
        int count = 0;
        int totalDur = 0;
        float totalSpeed = 0;
        float totalDamage = 0;
        int totalEnchant = 0;
        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;
            MaterialStats s = statsForItem(stack.getItem());
            totalDur += s.durability();
            totalSpeed += s.speed();
            totalDamage += s.attackDamage();
            totalEnchant += s.enchantability();
            count++;
        }
        if (count == 0) return new MaterialStats(100, 3.0f, 1.0f, 10, 1, 0f, 0f);
        return new MaterialStats(
                Math.max(1, totalDur / count),
                totalSpeed / count,
                totalDamage / count,
                Math.max(1, totalEnchant / count),
                0, 0f, 0f
        );
    }

    public static MaterialStats averageStatsForStacks(java.util.List<ItemStack> list) {
        if (list == null || list.isEmpty()) return new MaterialStats(100, 2f, 1f, 10, 1, 0f, 0f);
        int totalDur = 0; float totalSpeed = 0; float totalDamage = 0; int totalEnchant = 0; int cnt = 0;
        for (ItemStack s : list) {
            if (s.isEmpty()) continue;
            var st = statsForItem(s.getItem());
            totalDur += st.durability();
            totalSpeed += st.speed();
            totalDamage += st.attackDamage();
            totalEnchant += st.enchantability();
            cnt++;
        }
        if (cnt == 0) return new MaterialStats(100, 2f, 1f, 10, 1, 0f, 0f);
        return new MaterialStats(totalDur / cnt, totalSpeed / cnt, totalDamage / cnt, totalEnchant / cnt, 0, 0f, 0f);
    }

    private static ArmorType getArmorTypeForStack(ItemStack result) {
        Equippable eq = result.get(DataComponents.EQUIPPABLE);
        if (eq == null) return null;
        EquipmentSlot slot = eq.slot();
        return switch (slot) {
            case HEAD -> ArmorType.HELMET;
            case CHEST -> ArmorType.CHESTPLATE;
            case LEGS -> ArmorType.LEGGINGS;
            case FEET -> ArmorType.BOOTS;
            case BODY -> ArmorType.BODY;
            default -> null;
        };
    }

    public static ItemStack applyBlendedStats(ItemStack result, CraftingInput input) {
        if (result.isEmpty()) return result;
        MaterialStats avg = averageStats(input);
        try {
            ArmorType armorType = getArmorTypeForStack(result);
            boolean isArmor = armorType != null && result.get(DataComponents.EQUIPPABLE) != null;
            boolean isTool = result.get(DataComponents.TOOL) != null || result.get(DataComponents.WEAPON) != null;

            boolean hasBedrock = false;
            int ingredientCount = 0;
            int netherStarCount = 0;
            for (ItemStack s : input.items()) {
                if (s.isEmpty()) continue;
                ingredientCount++;
                Item it = s.getItem();
                String path = BuiltInRegistries.ITEM.getKey(it).getPath();
                if (it == Items.BEDROCK || it == Blocks.BEDROCK.asItem() || path.contains("bedrock")) {
                    hasBedrock = true;
                }
                if (path.equals("nether_star")) {
                    netherStarCount++;
                }
            }
            // Full nether star = unbreakable (like full bedrock). Blended = normal durability.
            boolean fullNetherStar = ingredientCount > 0 && netherStarCount >= ingredientCount;

            if (isArmor) {
                int totalDefense = 0;
                float totalToughness = 0;
                float totalKB = 0;
                int totalDurBase = 0;
                int count = 0;
                for (ItemStack s : input.items()) {
                    if (s.isEmpty()) continue;
                    int def = getArmorDefenseForItem(s.getItem(), armorType);
                    var effMat = getEffectiveArmorMaterial(s.getItem());
                    float tough = effMat.toughness();
                    float kb = effMat.knockbackResistance();
                    int durBase = effMat.durability();
                    MaterialStats special = getSpecialStatsForRareItem(s.getItem());
                    if (special != null) {
                        tough = special.toughness();
                        kb = special.knockbackResistance();
                        durBase = Math.max(5, special.durability() / 80);
                        // def already from getArmorDefenseForItem which handles special
                    } else {
                        Rarity r = getRarityForItem(s.getItem());
                        float mult = getRarityMultiplier(r);
                        if (mult != 1.0f) {
                            tough = tough * mult;
                            kb = Math.min(0.3f, kb * mult);
                        }
                        if (isFireResistant(s.getItem())) {
                            tough += 0.3f;
                            kb = Math.min(0.3f, kb + 0.03f);
                        }
                    }
                    totalDefense += def;
                    totalToughness += tough;
                    totalKB += kb;
                    totalDurBase += durBase;
                    count++;
                }
                if (count > 0) {
                    int avgDefense = Math.max(0, totalDefense / count);
                    float avgTough = totalToughness / count;
                    float avgKB = totalKB / count;
                    int avgDurBase = Math.max(1, totalDurBase / count);
                    int maxDamage = armorType.getDurability(avgDurBase);
                    result.set(DataComponents.MAX_DAMAGE, maxDamage);
                    result.set(DataComponents.DAMAGE, 0);
                    result.set(DataComponents.ENCHANTABLE, new Enchantable(avg.enchantability()));
                    Identifier id = Identifier.withDefaultNamespace(armorType.getName());
                    var builder = ItemAttributeModifiers.builder();
                    EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(armorType.getSlot());
                    builder.add(Attributes.ARMOR, new AttributeModifier(id, avgDefense, AttributeModifier.Operation.ADD_VALUE), group);
                    builder.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, avgTough, AttributeModifier.Operation.ADD_VALUE), group);
                    if (avgKB > 0) {
                        builder.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(id, avgKB, AttributeModifier.Operation.ADD_VALUE), group);
                    }
                    result.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
                    if (hasBedrock || fullNetherStar) {
                        result.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
                    }
                    try {
                        CompoundTag tag = new CompoundTag();
                        ListTag ingList = new ListTag();
                        for (ItemStack s : input.items()) {
                            if (s.isEmpty()) continue;
                            String idStr = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
                            ingList.add(StringTag.valueOf(idStr));
                        }
                        tag.put("blended_ingredients", ingList);
                        StringBuilder keySb = new StringBuilder();
                        for (int i = 0; i < ingList.size(); i++) {
                            if (i > 0) keySb.append("+");
                            keySb.append(ingList.getString(i).orElse(""));
                        }
                        tag.putString("blended_key", keySb.toString());
                        result.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                    } catch (Exception ex) {
                        LOGGER.warn("Failed to write blended custom data (armor)", ex);
                    }
                    String dominant = getDominantMaterialName(input);
                    String baseType = getBaseTypeName(result);
                    boolean singleMat = isSingleMaterial(input);
                    String displayName;
                    if (singleMat) {
                        displayName = (dominant != null ? dominant + " " + baseType : baseType);
                    } else if (dominant != null) {
                        displayName = "Blended " + dominant + " " + baseType;
                    } else {
                        displayName = "Blended " + baseType;
                    }
                    result.set(DataComponents.CUSTOM_NAME, Component.literal(displayName).withStyle(s -> s.withItalic(false)));
                    return result;
                }
            }

            // Vanilla parity: copy dominant vanilla tool's components (only texture differs)
            ItemStack vanillaCopy = getVanillaCopyForDominant(input, result);
            if (vanillaCopy != null) {
                var vTool = vanillaCopy.get(DataComponents.TOOL);
                if (vTool != null) result.set(DataComponents.TOOL, vTool);
                var vAttrs = vanillaCopy.get(DataComponents.ATTRIBUTE_MODIFIERS);
                if (vAttrs != null) result.set(DataComponents.ATTRIBUTE_MODIFIERS, vAttrs);
                Integer vMax = vanillaCopy.get(DataComponents.MAX_DAMAGE);
                if (vMax != null) result.set(DataComponents.MAX_DAMAGE, vMax);
                result.set(DataComponents.DAMAGE, 0);
                var vEnchant = vanillaCopy.get(DataComponents.ENCHANTABLE);
                if (vEnchant != null) result.set(DataComponents.ENCHANTABLE, vEnchant);
                var vSwing = vanillaCopy.get(DataComponents.SWING_ANIMATION);
                if (vSwing != null) result.set(DataComponents.SWING_ANIMATION, vSwing);
                else result.set(DataComponents.SWING_ANIMATION, new SwingAnimation(SwingAnimationType.WHACK, 6));
                var vRepair = vanillaCopy.get(DataComponents.REPAIRABLE);
                if (vRepair != null) result.set(DataComponents.REPAIRABLE, vRepair);
                if (hasBedrock || fullNetherStar) result.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
                // Ensure whack for tools if not already set
                if (result.get(DataComponents.SWING_ANIMATION) == null) {
                    result.set(DataComponents.SWING_ANIMATION, new SwingAnimation(SwingAnimationType.WHACK, 6));
                }
            } else {
                result.set(DataComponents.MAX_DAMAGE, avg.durability());
                result.set(DataComponents.DAMAGE, 0);
                result.set(DataComponents.ENCHANTABLE, new Enchantable(avg.enchantability()));
                if (hasBedrock || fullNetherStar) {
                    result.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
                }
                // Fix tool correctly (head speed, proper mineable tag, default 1.0)
                fixToolComponent(result, input, avg);
                ItemAttributeModifiers existing = result.get(DataComponents.ATTRIBUTE_MODIFIERS);
                var builder = ItemAttributeModifiers.builder();
                if (existing != null) {
                    for (var entry : existing.modifiers()) {
                        Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr = entry.attribute();
                        if (attr.value() == Attributes.ATTACK_DAMAGE.value() || attr.value() == Attributes.ATTACK_SPEED.value() || attr.value() == Attributes.ARMOR.value() || attr.value() == Attributes.ARMOR_TOUGHNESS.value()) {
                            continue;
                        }
                        builder.add(attr, entry.modifier(), entry.slot());
                    }
                }
                if (isTool || FlexibleRecipeHelper.isFlexibleResult(result)) {
                    Identifier dmgId = Identifier.withDefaultNamespace("base_attack_damage");
                    Identifier speedId = Identifier.withDefaultNamespace("base_attack_speed");
                    String tPath = BuiltInRegistries.ITEM.getKey(result.getItem()).getPath();
                    String toolType = tPath.startsWith("blended_") ? tPath.substring(8) : tPath;
                    java.util.List<ItemStack> headForDmg = new java.util.ArrayList<>();
                    for (ItemStack s : input.items()) if (!s.isEmpty() && !s.is(Items.STICK)) headForDmg.add(s);
                    var headStatsForDmg = headForDmg.isEmpty() ? avg : averageStatsForStacks(headForDmg);
                    ToolMaterial tierForDmg = selectTierMaterial(headForDmg, headStatsForDmg);
                    float[] blendedStatsDmg = getBlendedToolStats(tierForDmg, toolType, headStatsForDmg);
                    // getBlendedToolStats returns displayed values; modifier = displayed - 1 (player base) / - 4 (speed offset)
                    float finalDmg = blendedStatsDmg[0] - 1.0f;
                    float finalSpd = blendedStatsDmg[1] - 4.0f;
                    builder.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(dmgId, finalDmg, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
                    builder.add(Attributes.ATTACK_SPEED, new AttributeModifier(speedId, finalSpd, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
                }
                ItemAttributeModifiers built = builder.build();
                if (!built.modifiers().isEmpty() || existing == null) {
                    result.set(DataComponents.ATTRIBUTE_MODIFIERS, built);
                }
                if (result.get(DataComponents.SWING_ANIMATION) == null) {
                    result.set(DataComponents.SWING_ANIMATION, new SwingAnimation(SwingAnimationType.WHACK, 6));
                }
            }
            // Ensure Tool component is correct for blended tools (fixes “works on grass” bug: default speed 1.0, correct mineable tag and tier)
            if (!isArmor) {
                fixToolComponent(result, input, avg);
                // Fix per-tool attack damage/speed that was incorrectly uniform (all nether_star had 7/1.65)
                String tPathFix = BuiltInRegistries.ITEM.getKey(result.getItem()).getPath();
                String toolTypeFix = tPathFix.startsWith("blended_") ? tPathFix.substring(8) : tPathFix;
                if (toolTypeFix.equals("pickaxe") || toolTypeFix.equals("axe") || toolTypeFix.equals("shovel") || toolTypeFix.equals("hoe") || toolTypeFix.equals("sword")) {
                    java.util.List<ItemStack> headForFix = new java.util.ArrayList<>();
                    for (ItemStack s : input.items()) if (!s.isEmpty() && !s.is(Items.STICK)) headForFix.add(s);
                    var headStatsForFix = headForFix.isEmpty() ? avg : averageStatsForStacks(headForFix);
                    ToolMaterial tierForFix = selectTierMaterial(headForFix, headStatsForFix);
                    float[] blendedFix = getBlendedToolStats(tierForFix, toolTypeFix, headStatsForFix);
                    float expectedDmgFix = blendedFix[0] - 1.0f;
                    float expectedSpdFix = blendedFix[1] - 4.0f;
                    var curAttrsFix = result.get(DataComponents.ATTRIBUTE_MODIFIERS);
                    boolean needsFix2 = false;
                    if (curAttrsFix != null) {
                        boolean hasDmg = false, hasSpd = false;
                        for (var e : curAttrsFix.modifiers()) {
                            String n = e.attribute().value().getDescriptionId();
                            double amt = e.modifier().amount();
                            if (n.contains("attack_damage")) { hasDmg = true; if (Math.abs(amt - expectedDmgFix) > 0.01) needsFix2 = true; }
                            if (n.contains("attack_speed")) { hasSpd = true; if (Math.abs(amt - expectedSpdFix) > 0.01) needsFix2 = true; }
                        }
                        if (!hasDmg || !hasSpd) needsFix2 = true;
                    } else needsFix2 = true;
                    if (needsFix2) {
                        var builder2 = ItemAttributeModifiers.builder();
                        if (curAttrsFix != null) {
                            for (var e : curAttrsFix.modifiers()) {
                                var attr = e.attribute();
                                if (attr.value() == Attributes.ATTACK_DAMAGE.value() || attr.value() == Attributes.ATTACK_SPEED.value()) continue;
                                builder2.add(attr, e.modifier(), e.slot());
                            }
                        }
                        builder2.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Identifier.withDefaultNamespace("base_attack_damage"), expectedDmgFix, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
                        builder2.add(Attributes.ATTACK_SPEED, new AttributeModifier(Identifier.withDefaultNamespace("base_attack_speed"), expectedSpdFix, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
                        result.set(DataComponents.ATTRIBUTE_MODIFIERS, builder2.build());
                    }
                }
            }
            String dominant2 = getDominantMaterialName(input);
            String baseType2 = getBaseTypeName(result);
            boolean singleMat2 = isSingleMaterial(input);
            String displayName2;
            if (singleMat2) {
                displayName2 = (dominant2 != null ? dominant2 + " " + baseType2 : baseType2);
            } else if (dominant2 != null) {
                displayName2 = "Blended " + dominant2 + " " + baseType2;
            } else {
                displayName2 = "Blended " + baseType2;
            }
            result.set(DataComponents.CUSTOM_NAME, Component.literal(displayName2).withStyle(s -> s.withItalic(false)));
            try {
                CompoundTag tag2 = new CompoundTag();
                ListTag ingList2 = new ListTag();
                for (ItemStack s : input.items()) {
                    if (s.isEmpty()) continue;
                    String idStr = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
                    ingList2.add(StringTag.valueOf(idStr));
                }
                tag2.put("blended_ingredients", ingList2);
                StringBuilder keySb2 = new StringBuilder();
                for (int i = 0; i < ingList2.size(); i++) {
                    if (i > 0) keySb2.append("+");
                    keySb2.append(ingList2.getString(i).orElse(""));
                }
                tag2.putString("blended_key", keySb2.toString());
                result.set(DataComponents.CUSTOM_DATA, CustomData.of(tag2));
            } catch (Exception ex) {
                LOGGER.warn("Failed to write blended custom data", ex);
            }

        } catch (Exception e) {
            result.set(DataComponents.CUSTOM_NAME, Component.literal("Blended " + getBaseTypeName(result)).withStyle(s -> s.withItalic(false)));
            LOGGER.error("Failed to apply blended stats", e);
        }
        return result;
    }

    private static String getDominantMaterialName(CraftingInput input) {
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        java.util.Map<String, Item> itemMap = new java.util.HashMap<>();
        int total = 0;
        for (ItemStack s : input.items()) {
            if (s.isEmpty()) continue;
            String path = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
            counts.put(path, counts.getOrDefault(path, 0) + 1);
            itemMap.putIfAbsent(path, s.getItem());
            total++;
        }
        if (total == 0 || counts.isEmpty()) return null;
        // Find max count
        int max = 0;
        for (int c : counts.values()) if (c > max) max = c;
        // Collect candidates with max
        java.util.List<String> candidates = new java.util.ArrayList<>();
        for (var e : counts.entrySet()) if (e.getValue() == max) candidates.add(e.getKey());
        // If single dominant and >50%, return it
        if (candidates.size() == 1 && max * 2 > total) {
            return getDisplayNameForItem(itemMap.get(candidates.get(0)));
        }
        // If exactly 50% tie (e.g., 2 vs 2 for total 4, or 1 vs 1 for total 2) or multiple with same max
        // Choose strongest by hardness then durability
        if (candidates.size() >= 1) {
            // For >50% case we already returned, but for tie where max == total/2, we need to choose strongest
            // Also for cases where max <= total/2 but still most frequent, we fallback to strongest among max
            String best = null;
            float bestHard = -1;
            int bestDur = -1;
            for (String cand : candidates) {
                Item it = itemMap.get(cand);
                float hard = getHardnessForItem(it);
                int dur = statsForItem(it).durability();
                if (hard > bestHard || (hard == bestHard && dur > bestDur)) {
                    bestHard = hard;
                    bestDur = dur;
                    best = cand;
                }
            }
            if (max * 2 > total) return getDisplayNameForItem(itemMap.get(best));
            if (max * 2 == total && candidates.size() == 2) return getDisplayNameForItem(itemMap.get(best));
            if (max * 2 >= total) return getDisplayNameForItem(itemMap.get(best));
            return null;
        }
        return null;
    }

    private static boolean isSingleMaterial(CraftingInput input) {
        java.util.Set<String> uniq = new java.util.HashSet<>();
        for (ItemStack s : input.items()) {
            if (s.isEmpty()) continue;
            String path = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
            uniq.add(path);
            if (uniq.size() > 1) return false;
        }
        return uniq.size() == 1;
    }

    private static String getDisplayNameForItem(Item item) {
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        // Keep block distinction: netherite_block -> Netherite Block, iron_ingot -> Iron
        if (path.endsWith("_block")) {
            return formatMaterialName(path); // keeps Block
        }
        String base = stripToBaseMaterial(path);
        return formatMaterialName(base);
    }

    private static String stripToBaseMaterial(String path) {
        String p = path;
        if (p.endsWith("_ingot")) p = p.substring(0, p.length() - 6);
        else if (p.endsWith("_planks")) p = p.substring(0, p.length() - 7);
        else if (p.endsWith("_log")) p = p.substring(0, p.length() - 4);
        else if (p.endsWith("_scute")) p = p.substring(0, p.length() - 6);
        else if (p.endsWith("_shard")) p = p.substring(0, p.length() - 6);
        // Keep _block for display distinction
        return p;
    }

    private static String formatMaterialName(String baseMat) {
        if (baseMat == null || baseMat.isEmpty()) return null;
        String[] parts = baseMat.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1));
        }
        return sb.toString();
    }

    private static ItemStack getVanillaCopyForDominant(CraftingInput input, ItemStack result) {
        // Find dominant ingredient item
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        java.util.Map<String, Item> itemMap = new java.util.HashMap<>();
        int total = 0;
        for (ItemStack s : input.items()) {
            if (s.isEmpty()) continue;
            String path = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
            counts.put(path, counts.getOrDefault(path, 0) + 1);
            itemMap.putIfAbsent(path, s.getItem());
            total++;
        }
        if (total == 0 || counts.isEmpty()) return null;
        int max = 0; for (int c : counts.values()) if (c > max) max = c;
        java.util.List<String> cands = new java.util.ArrayList<>();
        for (var e : counts.entrySet()) if (e.getValue() == max) cands.add(e.getKey());
        String dominantPath = null;
        if (cands.size() == 1 && max * 2 > total) dominantPath = cands.get(0);
        else if (!cands.isEmpty()) {
            String best = null; float bestHard = -1; int bestDur = -1;
            for (String cand : cands) {
                Item it = itemMap.get(cand);
                float hard = getHardnessForItem(it);
                int dur = statsForItem(it).durability();
                if (hard > bestHard || (hard == bestHard && dur > bestDur)) { bestHard = hard; bestDur = dur; best = cand; }
            }
            dominantPath = best;
        }
        if (dominantPath == null) return null;
        String domBase = stripToBaseMaterial(dominantPath);
        // Normalize domBase to material name like iron, diamond, netherite, gold, copper
        String mat = domBase.toLowerCase();
        // Handle special cases: netherite_ingot -> netherite, gold_ingot -> gold, etc. strip already
        // Map to vanilla tool/armor for result type
        String resultPath = BuiltInRegistries.ITEM.getKey(result.getItem()).getPath();
        String type = resultPath.startsWith("blended_") ? resultPath.substring(8) : resultPath;
        // type is pickaxe, axe, shovel, sword, hoe, helmet, chestplate, leggings, boots
        String vanillaId = "minecraft:" + mat + "_" + type;
        // For ingot materials, mat is like iron, diamond, netherite, gold, copper, emerald
        // Try direct lookup
        var vanillaItem = BuiltInRegistries.ITEM.getValue(Identifier.parse(vanillaId));
        if (vanillaItem != null && vanillaItem != net.minecraft.world.item.Items.AIR) {
            // Check if it's actually registered (not air)
            if (!BuiltInRegistries.ITEM.getKey(vanillaItem).getPath().equals("air")) {
                return new ItemStack(vanillaItem);
            }
        }
        // Fallback: try netherite_block etc. but for tools we already handled
        // For armor with netherite, mat is netherite, type chestplate -> netherite_chestplate exists
        // For planks/log etc., fallback to wooden tool
        String fallbackId = "minecraft:wooden_" + type;
        if (type.equals("helmet") || type.equals("chestplate") || type.equals("leggings") || type.equals("boots")) {
            fallbackId = "minecraft:leather_" + type;
            // leather helmet etc. exists, but try iron as generic
            var fb2 = BuiltInRegistries.ITEM.getValue(Identifier.parse("minecraft:iron_" + type));
            if (fb2 != null && !BuiltInRegistries.ITEM.getKey(fb2).getPath().equals("air")) return new ItemStack(fb2);
        }
        var fb = BuiltInRegistries.ITEM.getValue(Identifier.parse(fallbackId));
        if (fb != null && !BuiltInRegistries.ITEM.getKey(fb).getPath().equals("air")) return new ItemStack(fb);
        return null;
    }

    // --- Tool fix helpers (corrects “pickaxe works on grass” etc) - uses vanilla template to avoid bootstrap lookup bugs ---
    private static void fixToolComponent(ItemStack result, CraftingInput input, MaterialStats avg) {
        try {
            String path = BuiltInRegistries.ITEM.getKey(result.getItem()).getPath();
            String type = path.startsWith("blended_") ? path.substring(8) : path;
            if (!(type.equals("pickaxe") || type.equals("axe") || type.equals("shovel") || type.equals("hoe") || type.equals("sword"))) {
                return;
            }
            java.util.List<ItemStack> headStacks = new java.util.ArrayList<>();
            for (ItemStack s : input.items()) {
                if (s.isEmpty()) continue;
                if (s.is(Items.STICK)) continue;
                headStacks.add(s);
            }
            MaterialStats headStats = headStacks.isEmpty() ? avg : averageStatsForStacks(headStacks);
            float miningSpeed = headStats.speed();
            // Sword: use vanilla sword template (same rules across tiers, speed not material dependent)
            if (type.equals("sword")) {
                ItemStack tmpl = getVanillaTemplate("sword", ToolMaterial.DIAMOND);
                Tool tmplTool = tmpl != null ? tmpl.get(DataComponents.TOOL) : null;
                if (tmplTool != null) {
                    result.set(DataComponents.TOOL, tmplTool);
                } else {
                    // fallback to manual sword rules if template missing
                    HolderGetter<Block> getter = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
                    HolderSet<Block> cobweb = HolderSet.direct(BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.COBWEB));
                    HolderSet<Block> instant = getter.getOrThrow(BlockTags.SWORD_INSTANTLY_MINES);
                    HolderSet<Block> efficient = getter.getOrThrow(BlockTags.SWORD_EFFICIENT);
                    java.util.List<Tool.Rule> rules = java.util.List.of(
                            Tool.Rule.minesAndDrops(cobweb, 15.0F),
                            Tool.Rule.overrideSpeed(instant, Float.MAX_VALUE),
                            Tool.Rule.overrideSpeed(efficient, 1.5F)
                    );
                    result.set(DataComponents.TOOL, new Tool(rules, 1.0F, 2, false));
                }
                return;
            }
            // Determine tier material from head (highest among ingredients to preserve best capability)
            ToolMaterial tier = selectTierMaterial(headStacks, headStats);
            ItemStack template = getVanillaTemplate(type, tier);
            if (template == null) {
                LOGGER.warn("No vanilla template for {} tier {}", type, tier);
                return;
            }
            Tool tmplTool = template.get(DataComponents.TOOL);
            if (tmplTool == null || tmplTool.rules().size() < 2) {
                LOGGER.warn("Template {} has no tool rules", BuiltInRegistries.ITEM.getKey(template.getItem()));
                return;
            }
            // Reuse HolderSets from template, replace speed with blended head speed
            HolderSet<Block> incorrectSet = tmplTool.rules().get(0).blocks();
            HolderSet<Block> mineableSet = tmplTool.rules().get(1).blocks();
            java.util.List<Tool.Rule> newRules = java.util.List.of(
                    Tool.Rule.deniesDrops(incorrectSet),
                    Tool.Rule.minesAndDrops(mineableSet, miningSpeed)
            );
            Tool newTool = new Tool(newRules, 1.0F, tmplTool.damagePerBlock(), tmplTool.canDestroyBlocksInCreative());
            result.set(DataComponents.TOOL, newTool);
        } catch (Exception e) {
            LOGGER.warn("Failed to fix tool component for {}: {}", BuiltInRegistries.ITEM.getKey(result.getItem()), e.toString(), e);
        }
    }

    private static ToolMaterial selectTierMaterial(java.util.List<ItemStack> headStacks, MaterialStats headStats) {
        try {
            java.util.Map<String, Integer> counts = new java.util.HashMap<>();
            java.util.Map<String, Item> itemMap = new java.util.HashMap<>();
            for (ItemStack s : headStacks) {
                if (s.isEmpty()) continue;
                String p = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
                counts.put(p, counts.getOrDefault(p, 0) + 1);
                itemMap.putIfAbsent(p, s.getItem());
            }
            Item dominant = null;
            if (!counts.isEmpty()) {
                int max = 0; for (int c : counts.values()) if (c > max) max = c;
                java.util.List<String> cands = new java.util.ArrayList<>();
                for (var e : counts.entrySet()) if (e.getValue() == max) cands.add(e.getKey());
                String best = null; float bestHard = -1; int bestDur = -1;
                for (String cand : cands) {
                    Item it = itemMap.get(cand);
                    float hard = getHardnessForItem(it);
                    int dur = statsForItem(it).durability();
                    if (hard > bestHard || (hard == bestHard && dur > bestDur)) { bestHard = hard; bestDur = dur; best = cand; }
                }
                if (best != null) dominant = itemMap.get(best);
            }
            ToolMaterial domMat = dominant != null ? getToolMaterialForItem(dominant) : null;
            ToolMaterial highest = domMat;
            int highestDur = highest != null ? highest.durability() : -1;
            for (ItemStack s : headStacks) {
                ToolMaterial m = getToolMaterialForItem(s.getItem());
                if (m == null) {
                    float hard = getHardnessForItem(s.getItem());
                    if (hard >= 50f) m = ToolMaterial.NETHERITE;
                    else if (hard >= 5f) m = ToolMaterial.DIAMOND;
                    else if (hard >= 3f) m = ToolMaterial.IRON;
                    else if (hard >= 1.5f) m = ToolMaterial.STONE;
                }
                if (m != null && m.durability() > highestDur) { highest = m; highestDur = m.durability(); }
            }
            if (highest != null) return highest;
        } catch (Exception ignored) {}
        int dur = headStats.durability();
        float speed = headStats.speed();
        if (dur >= 1800 || speed >= 9.0f) return ToolMaterial.NETHERITE;
        if (dur >= 1000 || speed >= 8.0f) return ToolMaterial.DIAMOND;
        if (dur >= 230) return ToolMaterial.IRON;
        if (dur >= 150) return ToolMaterial.COPPER;
        if (dur >= 100) return ToolMaterial.STONE;
        if (speed >= 11f && dur < 80) return ToolMaterial.GOLD;
        return ToolMaterial.WOOD;
    }

    public static ToolMaterial getToolMaterialForItem(Item item) {
        if (item == Items.DIAMOND) return ToolMaterial.DIAMOND;
        if (item == Items.NETHERITE_INGOT) return ToolMaterial.NETHERITE;
        if (item == Items.IRON_INGOT) return ToolMaterial.IRON;
        if (item == Items.GOLD_INGOT) return ToolMaterial.GOLD;
        if (item == Items.COPPER_INGOT) return ToolMaterial.COPPER;
        if (item == Items.COBBLESTONE || item == Items.STONE) return ToolMaterial.STONE;
        if (item == Items.OAK_PLANKS || item == Items.STICK) return ToolMaterial.WOOD;
        String id = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (id.contains("netherite")) return ToolMaterial.NETHERITE;
        if (id.contains("diamond")) return ToolMaterial.DIAMOND;
        if (id.contains("iron")) return ToolMaterial.IRON;
        if (id.contains("gold")) return ToolMaterial.GOLD;
        if (id.contains("copper")) return ToolMaterial.COPPER;
        if (id.contains("stone") || id.contains("cobble")) return ToolMaterial.STONE;
        if (id.contains("plank") || id.contains("wood") || id.contains("log")) return ToolMaterial.WOOD;
        return null;
    }

    private static ItemStack getVanillaTemplate(String type, ToolMaterial tier) {
        String prefix;
        if (tier == ToolMaterial.WOOD) prefix = "wooden";
        else if (tier == ToolMaterial.STONE) prefix = "stone";
        else if (tier == ToolMaterial.COPPER) prefix = "copper";
        else if (tier == ToolMaterial.IRON) prefix = "iron";
        else if (tier == ToolMaterial.GOLD) prefix = "golden";
        else if (tier == ToolMaterial.DIAMOND) prefix = "diamond";
        else if (tier == ToolMaterial.NETHERITE) prefix = "netherite";
        else prefix = "iron";
        String[] tryPrefixes;
        // order to try: desired first, then fallbacks by strength
        if (prefix.equals("netherite")) tryPrefixes = new String[]{"netherite","diamond","iron","copper","stone","wooden","golden"};
        else if (prefix.equals("diamond")) tryPrefixes = new String[]{"diamond","iron","copper","stone","wooden","netherite","golden"};
        else if (prefix.equals("iron")) tryPrefixes = new String[]{"iron","copper","stone","wooden","diamond","netherite","golden"};
        else if (prefix.equals("copper")) tryPrefixes = new String[]{"copper","iron","stone","wooden","diamond","netherite","golden"};
        else if (prefix.equals("stone")) tryPrefixes = new String[]{"stone","wooden","copper","iron","diamond","netherite","golden"};
        else if (prefix.equals("golden")) tryPrefixes = new String[]{"golden","wooden","stone","copper","iron","diamond","netherite"};
        else tryPrefixes = new String[]{"wooden","stone","copper","iron","diamond","netherite","golden"};
        for (String p : tryPrefixes) {
            String id = "minecraft:" + p + "_" + type;
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
            if (item != null && item != Items.AIR && !BuiltInRegistries.ITEM.getKey(item).getPath().equals("air")) {
                ItemStack stack = new ItemStack(item);
                if (stack.get(DataComponents.TOOL) != null) return stack;
            }
        }
        // ultimate fallback: pickaxe
        if (!type.equals("pickaxe")) {
            return getVanillaTemplate("pickaxe", tier);
        }
        return null;
    }

    private static String getBaseTypeName(ItemStack result) {
        String path = BuiltInRegistries.ITEM.getKey(result.getItem()).getPath();
        String base = path.startsWith("blended_") ? path.substring(8) : path;
        // e.g., chestplate -> Chestplate, leggings -> Leggings
        return formatMaterialName(base);
    }
}
