package com.example.util;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
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
        float hardness = getHardnessForItem(item);
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

    @SuppressWarnings("deprecation")
    public static ItemStack applyBlendedStats(ItemStack result, CraftingInput input) {
        if (result.isEmpty()) return result;
        MaterialStats avg = averageStats(input);
        try {
            ArmorType armorType = getArmorTypeForStack(result);
            boolean isArmor = armorType != null && result.get(DataComponents.EQUIPPABLE) != null;
            boolean isTool = result.get(DataComponents.TOOL) != null || result.get(DataComponents.WEAPON) != null;

            boolean hasBedrock = false;
            for (ItemStack s : input.items()) {
                if (s.isEmpty()) continue;
                Item it = s.getItem();
                String path = BuiltInRegistries.ITEM.getKey(it).getPath();
                if (it == Items.BEDROCK || it == Blocks.BEDROCK.asItem() || path.contains("bedrock")) {
                    hasBedrock = true;
                    break;
                }
            }

            if (isArmor) {
                int totalDefense = 0;
                float totalToughness = 0;
                float totalKB = 0;
                int totalDurBase = 0;
                int count = 0;
                for (ItemStack s : input.items()) {
                    if (s.isEmpty()) continue;
                    var mat = getArmorMaterialForItem(s.getItem());
                    int def = 0;
                    float tough = 0;
                    float kb = 0;
                    int durBase = statsForItem(s.getItem()).durability();
                    if (mat != null) {
                        def = mat.defense().getOrDefault(armorType, 0);
                        tough = mat.toughness();
                        kb = mat.knockbackResistance();
                        durBase = mat.durability();
                    } else {
                        // For generic blocks like bedrock/obsidian, use hardness-based defense
                        float hardness = getHardnessForItem(s.getItem());
                        if (hardness >= 50f) { def = 4; tough = 3.5f; kb = 0.15f; durBase = 40; }
                        else if (hardness >= 3f) { def = 3; tough = 2.0f; kb = 0f; durBase = 30; }
                        else if (hardness >= 1f) { def = 1; tough = 0f; kb = 0f; durBase = 15; }
                        else { def = 0; tough = 0f; kb = 0f; durBase = 5; }
                        // Override for special
                        if (s.getItem() == Items.BEDROCK || BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().contains("bedrock")) { def = 5; tough = 4f; kb = 0.2f; durBase = 50; }
                        if (s.getItem() == Items.OBSIDIAN) { def = 4; tough = 3f; kb = 0.1f; durBase = 35; }
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
                    if (hasBedrock) {
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

            result.set(DataComponents.MAX_DAMAGE, avg.durability());
            result.set(DataComponents.DAMAGE, 0);
            result.set(DataComponents.ENCHANTABLE, new Enchantable(avg.enchantability()));
            if (hasBedrock) {
                result.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
            }
            Tool tool = result.get(DataComponents.TOOL);
            if (tool != null) {
                Tool newTool = new Tool(tool.rules(), avg.speed(), tool.damagePerBlock(), tool.canDestroyBlocksInCreative());
                result.set(DataComponents.TOOL, newTool);
            }
            ItemAttributeModifiers existing = result.get(DataComponents.ATTRIBUTE_MODIFIERS);
            var builder = ItemAttributeModifiers.builder();
            if (existing != null) {
                for (var entry : existing.modifiers()) {
                    Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr = entry.attribute();
                    if (attr.is(Attributes.ATTACK_DAMAGE) || attr.is(Attributes.ATTACK_SPEED) || attr.is(Attributes.ARMOR) || attr.is(Attributes.ARMOR_TOUGHNESS)) {
                        continue;
                    }
                    builder.add(attr, entry.modifier(), entry.slot());
                }
            }
            if (isTool || FlexibleRecipeHelper.isFlexibleResult(result)) {
                Identifier dmgId = Identifier.withDefaultNamespace("base_attack_damage");
                Identifier speedId = Identifier.withDefaultNamespace("base_attack_speed");
                float attackSpeed = -2.8f;
                if (existing != null) {
                    for (var e : existing.modifiers()) {
                        if (e.attribute().is(Attributes.ATTACK_SPEED)) {
                            attackSpeed = (float) e.modifier().amount();
                            break;
                        }
                    }
                }
                builder.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(dmgId, avg.attackDamage(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
                builder.add(Attributes.ATTACK_SPEED, new AttributeModifier(speedId, attackSpeed, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
            }
            ItemAttributeModifiers built = builder.build();
            if (!built.modifiers().isEmpty() || existing == null) {
                result.set(DataComponents.ATTRIBUTE_MODIFIERS, built);
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

    private static String getBaseTypeName(ItemStack result) {
        String path = BuiltInRegistries.ITEM.getKey(result.getItem()).getPath();
        String base = path.startsWith("blended_") ? path.substring(8) : path;
        // e.g., chestplate -> Chestplate, leggings -> Leggings
        return formatMaterialName(base);
    }
}
