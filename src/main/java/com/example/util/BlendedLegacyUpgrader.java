package com.example.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Block;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlendedLegacyUpgrader {
    private static final Logger LOGGER = LoggerFactory.getLogger("blendedcraft/LegacyUpgrader");

    /**
     * Upgrades a single stack if needed.
     * - Fixes italic custom name (forces withItalic(false))
     * - Adds missing positional tags for old items so future texture generation is deterministic and handle-independent.
     * Returns true if stack was modified.
     */
    public static boolean upgradeStack(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null || custom.isEmpty()) return false;
        var tag = custom.copyTag();
        boolean isBlended = tag.contains("blended_ingredients") || tag.contains("blended_head") || tag.contains("blended_handle");
        if (!isBlended && !FlexibleRecipeHelper.isFlexibleResult(stack)) return false;

        boolean changed = false;
        CompoundTag newTag = tag; // will mutate and write back if changed
        boolean tagChanged = false;

        // 1) Fix italic name: old items stored with default italic=true, now should be false
        var customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName != null) {
            // Old items were saved with italic (empty style defaults to italic in rendering). Force explicit non-italic.
            Component forced = Component.literal(customName.getString()).withStyle(s -> s.withItalic(false));
            // Preserve color if present
            var col = customName.getStyle().getColor();
            if (col != null) forced = forced.copy().withStyle(s -> s.withColor(col));
            // Only write if different (idempotent)
            if (!forced.equals(customName)) {
                stack.set(DataComponents.CUSTOM_NAME, forced);
                changed = true;
                LOGGER.debug("Fixed italic name for {}", BuiltInRegistries.ITEM.getKey(stack.getItem()));
            } else {
                // Even if equals, old items with missing pos should still get name forced to explicit false once
                // Check if tag missing pos and style was not explicitly false (empty style equals false but not explicit)
                // We'll force fix if this is an old item (missing pos) and name style is empty
                if (!tag.contains("blended_head_pos") && customName.getStyle().isItalic() != forced.getStyle().isItalic()) {
                    stack.set(DataComponents.CUSTOM_NAME, forced);
                    changed = true;
                }
            }
        }

        // 2) Backfill missing positional tags for old items (so they get deterministic new patchwork instead of random fallback)
        // Only if missing; generate default positions based on item type's known pattern
        boolean hasHeadPos = tag.contains("blended_head_pos");
        boolean hasHandlePos = tag.contains("blended_handle_pos");
        boolean hasPattern = tag.contains("blended_pattern_width");
        if (!hasHeadPos || !hasHandlePos || !hasPattern) {
            // Infer pattern dimensions from item id
            String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            String base = path.startsWith("blended_") ? path.substring(8) : path;
            PatternInfo info = getPatternInfo(base);
            int pW = info.w;
            int pH = info.h;
            List<int[]> headCells = info.headCells;
            List<int[]> handleCells = info.handleCells;

            // Read existing head/handle lists to size the pos lists
            ListTag headList = tag.getListOrEmpty("blended_head");
            ListTag handleList = tag.getListOrEmpty("blended_handle");
            ListTag allList = tag.getListOrEmpty("blended_ingredients");
            int headCount = headList.size();
            int handleCount = handleList.size();
            // Fallback: if head empty but all exists (armor), treat all as head
            if (headCount == 0 && allList.size() > 0 && handleCount == 0) {
                headCount = allList.size();
                // For armor, head cells count may be larger than headCount, but we map sequentially
            }

            if (!hasHeadPos && headCount > 0) {
                ListTag headPosTag = new ListTag();
                for (int i = 0; i < headCount; i++) {
                    int[] cell;
                    if (i < headCells.size()) cell = headCells.get(i);
                    else {
                        // Fallback sequential within pW
                        int gx = i % pW;
                        int gy = i / pW;
                        // Clamp gy within pH
                        gy = Math.min(gy, pH - 1);
                        cell = new int[]{gx, gy};
                    }
                    int pos = cell[0] + cell[1] * 16;
                    headPosTag.add(IntTag.valueOf(pos));
                }
                newTag.put("blended_head_pos", headPosTag);
                tagChanged = true;
            }
            if (!hasHandlePos && handleCount > 0) {
                ListTag handlePosTag = new ListTag();
                for (int i = 0; i < handleCount; i++) {
                    int[] cell;
                    if (i < handleCells.size()) cell = handleCells.get(i);
                    else {
                        int gx = i % pW;
                        int gy = i / pW;
                        gy = Math.min(gy, pH - 1);
                        cell = new int[]{gx, gy};
                    }
                    int pos = cell[0] + cell[1] * 16;
                    handlePosTag.add(IntTag.valueOf(pos));
                }
                newTag.put("blended_handle_pos", handlePosTag);
                tagChanged = true;
            }
            if (!hasPattern) {
                newTag.putInt("blended_pattern_width", pW);
                newTag.putInt("blended_pattern_height", pH);
                tagChanged = true;
            }
            // Also ensure blended_ingredients_pos exists for armor fallback
            if (!tag.contains("blended_ingredients_pos") && allList.size() > 0) {
                ListTag allPosTag = new ListTag();
                // For old items, allPos was not stored; we can infer as sequential grid positions for all ingredients
                // Use head+handle cells combined in order: for armor, all cells are headCells
                // For tools, all = head + handle but order in allList is input order (row-major). We can approximate
                // by using the same head/handle cells merged, but simpler: generate sequential positions
                // This tag is mainly for armor manager fallback; if missing, armor manager will use head pos.
                // We'll create a best-effort: map each all entry to a distinct cell in pattern
                List<int[]> allCells = new ArrayList<>();
                allCells.addAll(headCells);
                allCells.addAll(handleCells);
                for (int i = 0; i < allList.size(); i++) {
                    int[] cell;
                    if (i < allCells.size()) cell = allCells.get(i);
                    else {
                        int gx = i % pW;
                        int gy = i / pW;
                        gy = Math.min(gy, pH - 1);
                        cell = new int[]{gx, gy};
                    }
                    int pos = cell[0] + cell[1] * 16;
                    allPosTag.add(IntTag.valueOf(pos));
                }
                newTag.put("blended_ingredients_pos", allPosTag);
                tagChanged = true;
            }
            if (!tag.contains("blended_pos_key") && (hasHeadPos || hasHandlePos || tagChanged)) {
                // Build a posKey similar to new items
                StringBuilder posKeySb = new StringBuilder();
                var hpTag = newTag.getListOrEmpty("blended_head_pos");
                for (int i = 0; i < hpTag.size(); i++) {
                    if (i > 0) posKeySb.append(",");
                    try {
                        var t = hpTag.get(i);
                        if (t instanceof IntTag it) posKeySb.append(it.value());
                        else posKeySb.append(t.toString());
                    } catch (Exception e) { posKeySb.append("0"); }
                }
                posKeySb.append("|");
                var hdlTag = newTag.getListOrEmpty("blended_handle_pos");
                for (int i = 0; i < hdlTag.size(); i++) {
                    if (i > 0) posKeySb.append(",");
                    try {
                        var t = hdlTag.get(i);
                        if (t instanceof IntTag it) posKeySb.append(it.value());
                        else posKeySb.append(t.toString());
                    } catch (Exception e) { posKeySb.append("0"); }
                }
                newTag.putString("blended_pos_key", posKeySb.toString());
                tagChanged = true;
            }
            if (tagChanged) {
                // Write back custom data
                stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(newTag));
                changed = true;
                LOGGER.info("Backfilled positional tags for old {} (head {} handle {} p {}x{})", path, headCount, handleCount, pW, pH);
            }
        } else if (tagChanged) {
            // Already handled
        }

        // 3) Backwards compat: fix Tool component for old blended tools (was defaultMiningSpeed = blended speed, now 1.0 + correct mineable + tier)
        // This also fixes newly-crafted bedrock/emerald block tools that were slow (speed 1 on stone) due to bootstrap HolderSet bug
        try {
            String itemPath = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            boolean isToolType = itemPath.startsWith("blended_") && (itemPath.endsWith("pickaxe") || itemPath.endsWith("axe") || itemPath.endsWith("shovel") || itemPath.endsWith("hoe") || itemPath.endsWith("sword"));
            boolean isFlexible = FlexibleRecipeHelper.isFlexibleResult(stack);
            if ((isToolType || isFlexible) && (stack.get(DataComponents.TOOL) != null || itemPath.endsWith("sword"))) {
                // Old bug had defaultMiningSpeed !=1.0; new bug had incorrect HolderSet leading to speed 1 on mineable blocks
                // Always recompute for blended tools if we have ingredient info
                if (newTag.contains("blended_head") || newTag.contains("blended_ingredients") || newTag.contains("blended_ingredients_pos")) {
                    if (upgradeToolComponent(stack, newTag)) {
                        changed = true;
                        LOGGER.info("Fixed Tool component for legacy {} ({})", itemPath, BuiltInRegistries.ITEM.getKey(stack.getItem()));
                    }
                } else {
                    // No head info but check if tool is wrong: defaultMiningSpeed !=1.0 => needs fix
                    var curTool = stack.get(DataComponents.TOOL);
                    if (curTool != null && Math.abs(curTool.defaultMiningSpeed() - 1.0f) > 0.001f) {
                        // Try to infer head from custom_data if possible, else fallback to generic
                        if (upgradeToolComponent(stack, newTag)) changed = true;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to upgrade tool for {}: {}", BuiltInRegistries.ITEM.getKey(stack.getItem()), e.toString());
        }

        // 3b) Fix armor stats for legacy armor (nether_star had +1 armor, should be per-type + rarity)
        try {
            var eq = stack.get(DataComponents.EQUIPPABLE);
            if (eq != null) {
                EquipmentSlot slot = eq.slot();
                ArmorType armorType = switch (slot) {
                    case HEAD -> ArmorType.HELMET;
                    case CHEST -> ArmorType.CHESTPLATE;
                    case LEGS -> ArmorType.LEGGINGS;
                    case FEET -> ArmorType.BOOTS;
                    case BODY -> ArmorType.BODY;
                    default -> null;
                };
                if (armorType != null) {
                    java.util.List<ItemStack> armorMats = new java.util.ArrayList<>();
                    ListTag headTag2 = newTag.getListOrEmpty("blended_head");
                    if (!headTag2.isEmpty()) {
                        for (int i = 0; i < headTag2.size(); i++) {
                            String idStr = headTag2.getString(i).orElse("");
                            if (idStr.isEmpty()) continue;
                            Identifier id = Identifier.parse(idStr);
                            Item it = BuiltInRegistries.ITEM.getValue(id);
                            if (it != null && it != Items.AIR) armorMats.add(new ItemStack(it));
                        }
                    } else {
                        ListTag allTag2 = newTag.getListOrEmpty("blended_ingredients");
                        for (int i = 0; i < allTag2.size(); i++) {
                            String idStr = allTag2.getString(i).orElse("");
                            if (idStr.isEmpty()) continue;
                            Identifier id = Identifier.parse(idStr);
                            Item it = BuiltInRegistries.ITEM.getValue(id);
                            if (it != null && it != Items.AIR) armorMats.add(new ItemStack(it));
                        }
                    }
                    if (!armorMats.isEmpty()) {
                        int totalDef = 0; float totalTough = 0; float totalKB = 0; int totalDur = 0;
                        for (ItemStack s : armorMats) {
                            int d = BlendedStatsHelper.getArmorDefenseForItem(s.getItem(), armorType);
                            var effMat = BlendedStatsHelper.getEffectiveArmorMaterial(s.getItem());
                            float t = effMat.toughness();
                            float k = effMat.knockbackResistance();
                            int dur = effMat.durability();
                            var sp = BlendedStatsHelper.getSpecialStatsForRareItem(s.getItem());
                            if (sp != null) { t = sp.toughness(); k = sp.knockbackResistance(); dur = Math.max(5, sp.durability() / 80); }
                            else {
                                var r = BlendedStatsHelper.getRarityForItem(s.getItem());
                                float mult = 1.0f;
                                if (r == net.minecraft.world.item.Rarity.RARE) mult = 1.85f;
                                else if (r == net.minecraft.world.item.Rarity.EPIC) mult = 2.6f;
                                else if (r == net.minecraft.world.item.Rarity.UNCOMMON) mult = 1.35f;
                                if (mult != 1.0f) { t = t * mult; k = Math.min(0.3f, k * mult); }
                                String p = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
                                if (p.contains("netherite") || p.contains("ancient_debris")) { t += 0.3f; k = Math.min(0.3f, k + 0.03f); }
                            }
                            totalDef += d; totalTough += t; totalKB += k; totalDur += dur;
                        }
                        int cnt = armorMats.size();
                        int avgDef = Math.max(0, totalDef / cnt);
                        float avgTough = totalTough / cnt;
                        float avgKB = totalKB / cnt;
                        int avgDurBase = Math.max(1, totalDur / cnt);
                        int expectedMaxDamage = armorType.getDurability(avgDurBase);
                        var curAttrs = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
                        boolean needsFix = false;
                        if (curAttrs != null) {
                            for (var e : curAttrs.modifiers()) {
                                String n = e.attribute().value().getDescriptionId();
                                double amt = e.modifier().amount();
                                if (n.contains("armor") && !n.contains("toughness") && Math.abs(amt - avgDef) > 0.01) needsFix = true;
                                if (n.contains("armor_toughness") && Math.abs(amt - avgTough) > 0.01) needsFix = true;
                                if (n.contains("knockback") && Math.abs(amt - avgKB) > 0.01) needsFix = true;
                            }
                        } else needsFix = true;
                        Integer curMax = stack.get(DataComponents.MAX_DAMAGE);
                        if (curMax == null || Math.abs(curMax - expectedMaxDamage) > 2) needsFix = true;
                        if (needsFix) {
                            var builder = ItemAttributeModifiers.builder();
                            if (curAttrs != null) {
                                for (var e : curAttrs.modifiers()) {
                                    var attr = e.attribute();
                                    if (attr.is(Attributes.ARMOR) || attr.is(Attributes.ARMOR_TOUGHNESS) || attr.is(Attributes.KNOCKBACK_RESISTANCE)) continue;
                                    builder.add(attr, e.modifier(), e.slot());
                                }
                            }
                            Identifier id = Identifier.withDefaultNamespace(armorType.getName());
                            EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(armorType.getSlot());
                            builder.add(Attributes.ARMOR, new AttributeModifier(id, avgDef, AttributeModifier.Operation.ADD_VALUE), group);
                            builder.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, avgTough, AttributeModifier.Operation.ADD_VALUE), group);
                            if (avgKB > 0) builder.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(id, avgKB, AttributeModifier.Operation.ADD_VALUE), group);
                            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
                            stack.set(DataComponents.MAX_DAMAGE, expectedMaxDamage);
                            stack.set(DataComponents.DAMAGE, 0);
                            int totalEnch = 0; for (ItemStack s : armorMats) totalEnch += BlendedStatsHelper.statsForItem(s.getItem()).enchantability();
                            int avgEnch = Math.max(1, totalEnch / cnt);
                            stack.set(DataComponents.ENCHANTABLE, new net.minecraft.world.item.enchantment.Enchantable(avgEnch));
                            changed = true;
                            LOGGER.info("Fixed armor stats for legacy {} type {} def {} tough {} kb {}", BuiltInRegistries.ITEM.getKey(stack.getItem()), armorType, avgDef, avgTough, avgKB);
                        }
                    }
                }
            }
        } catch (Exception e) { LOGGER.warn("Failed to fix armor for {}: {}", BuiltInRegistries.ITEM.getKey(stack.getItem()), e.toString()); }

        // 4) Cleanup: remove ugly Mining Speed lore that was added by previous buggy fix (inside Temp folder is now forbidden, so we don't create outside files)
        try {
            var lore = stack.get(DataComponents.LORE);
            if (lore != null) {
                java.util.List<Component> filtered = new java.util.ArrayList<>();
                boolean removed = false;
                for (Component c : lore.lines()) {
                    String s = c.getString();
                    if (s.startsWith("Mining Speed")) { removed = true; continue; }
                    if (s.startsWith("Block Break Speed")) { removed = true; continue; }
                    filtered.add(c);
                }
                if (removed) {
                    if (filtered.isEmpty()) stack.remove(DataComponents.LORE);
                    else stack.set(DataComponents.LORE, new ItemLore(filtered));
                    changed = true;
                    LOGGER.info("Removed old Mining Speed lore for {}", BuiltInRegistries.ITEM.getKey(stack.getItem()));
                }
            }
            var disp = stack.get(DataComponents.TOOLTIP_DISPLAY);
            if (disp != null && disp.hiddenComponents().contains(DataComponents.TOOL)) {
                var newHidden = new java.util.LinkedHashSet<>(disp.hiddenComponents());
                newHidden.remove(DataComponents.TOOL);
                var newDisp = new TooltipDisplay(disp.hideTooltip(), newHidden);
                if (newHidden.isEmpty() && !disp.hideTooltip()) stack.remove(DataComponents.TOOLTIP_DISPLAY);
                else stack.set(DataComponents.TOOLTIP_DISPLAY, newDisp);
                changed = true;
            }
        } catch (Exception e) { LOGGER.warn("Failed to cleanup lore for {}: {}", BuiltInRegistries.ITEM.getKey(stack.getItem()), e.toString()); }

        return changed;
    }

    private static boolean upgradeToolComponent(ItemStack stack, CompoundTag tag) {
        try {
            String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            String type = path.startsWith("blended_") ? path.substring(8) : path;
            if (!(type.equals("pickaxe") || type.equals("axe") || type.equals("shovel") || type.equals("hoe") || type.equals("sword"))) {
                // infer from FlexibleRecipeHelper result type
                if (type.contains("pickaxe")) type = "pickaxe";
                else if (type.contains("axe")) type = "axe";
                else if (type.contains("shovel")) type = "shovel";
                else if (type.contains("hoe")) type = "hoe";
                else if (type.contains("sword")) type = "sword";
                else return false;
            }
            // Build head stacks from stored tags
            java.util.List<ItemStack> headStacks = new java.util.ArrayList<>();
            ListTag headTag = tag.getListOrEmpty("blended_head");
            if (!headTag.isEmpty()) {
                for (int i = 0; i < headTag.size(); i++) {
                    String idStr = headTag.getString(i).orElse("");
                    if (idStr.isEmpty()) continue;
                    try {
                        Identifier id = Identifier.parse(idStr);
                        Item it = BuiltInRegistries.ITEM.getValue(id);
                        if (it != null && it != Items.AIR) headStacks.add(new ItemStack(it));
                    } catch (Exception ignored) {}
                }
            } else {
                // fallback: use blended_ingredients filtering sticks
                ListTag allTag = tag.getListOrEmpty("blended_ingredients");
                if (!allTag.isEmpty()) {
                    for (int i = 0; i < allTag.size(); i++) {
                        String idStr = allTag.getString(i).orElse("");
                        if (idStr.isEmpty()) continue;
                        if (idStr.equals("minecraft:stick") || idStr.endsWith(":stick")) continue;
                        try {
                            Identifier id = Identifier.parse(idStr);
                            Item it = BuiltInRegistries.ITEM.getValue(id);
                            if (it != null && it != Items.AIR) headStacks.add(new ItemStack(it));
                        } catch (Exception ignored) {}
                    }
                }
            }
            if (headStacks.isEmpty()) {
                // Cannot infer head, use current tool's speed as fallback? Try to keep existing but fix tier/default
                // Use blended_ingredients_pos not reliable; fallback to generic bedrock->netherite handling
                return false;
            }
            var headStats = BlendedStatsHelper.averageStatsForStacks(headStacks);
            float miningSpeed = headStats.speed();
            // Sword: fixed rules
            if (type.equals("sword")) {
                ItemStack tmpl = getVanillaTemplateForLegacy("sword", ToolMaterial.DIAMOND);
                Tool tmplTool = tmpl != null ? tmpl.get(DataComponents.TOOL) : null;
                if (tmplTool != null) {
                    Tool cur = stack.get(DataComponents.TOOL);
                    if (cur == null || !cur.equals(tmplTool)) {
                        stack.set(DataComponents.TOOL, tmplTool);
                        return true;
                    }
                }
                return false;
            }
            ToolMaterial tier = selectTierForLegacy(headStacks, headStats);
            ItemStack template = getVanillaTemplateForLegacy(type, tier);
            if (template == null) return false;
            Tool tmplTool = template.get(DataComponents.TOOL);
            if (tmplTool == null || tmplTool.rules().size() < 2) return false;
            HolderSet<Block> incorrectSet = tmplTool.rules().get(0).blocks();
            HolderSet<Block> mineableSet = tmplTool.rules().get(1).blocks();
            java.util.List<Tool.Rule> newRules = java.util.List.of(Tool.Rule.deniesDrops(incorrectSet), Tool.Rule.minesAndDrops(mineableSet, miningSpeed));
            Tool newTool = new Tool(newRules, 1.0F, tmplTool.damagePerBlock(), tmplTool.canDestroyBlocksInCreative());
            Tool curTool = stack.get(DataComponents.TOOL);
            boolean needsToolFix = false;
            if (curTool == null) needsToolFix = true;
            else {
                boolean same = Math.abs(curTool.defaultMiningSpeed() - 1.0f) < 0.001f && curTool.rules().size() == 2
                        && Math.abs(curTool.rules().get(1).speed().orElse(0f) - miningSpeed) < 0.001f
                        && curTool.rules().get(0).blocks().size() == incorrectSet.size()
                        && curTool.rules().get(1).blocks().size() == mineableSet.size();
                if (!same) needsToolFix = true;
            }
            boolean needsAttrFix = false;
            try {
                var curAttrs = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
                String tPathAttr = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
                String tTypeAttr = tPathAttr.startsWith("blended_") ? tPathAttr.substring(8) : tPathAttr;
                ToolMaterial tierAttr = selectTierForLegacy(headStacks, headStats);
                float[] blendedAttr = BlendedStatsHelper.getBlendedToolStats(tierAttr, tTypeAttr, headStats);
                float expectedDmg = blendedAttr[0] - 1.0f;
                float expectedSpd = blendedAttr[1] - 4.0f;
                java.util.List<ItemStack> handleStacksAttr = new java.util.ArrayList<>();
                ListTag handleTagAttr = tag.getListOrEmpty("blended_handle");
                for (int i = 0; i < handleTagAttr.size(); i++) {
                    String idStr = handleTagAttr.getString(i).orElse("");
                    if (idStr.isEmpty()) continue;
                    Identifier hid = Identifier.parse(idStr);
                    Item hit = BuiltInRegistries.ITEM.getValue(hid);
                    if (hit != null && hit != Items.AIR) handleStacksAttr.add(new ItemStack(hit));
                }
                var handleStatsAttr = handleStacksAttr.isEmpty() ? headStats : BlendedStatsHelper.averageStatsForStacks(handleStacksAttr);
                if (curAttrs != null) {
                    boolean hasDmg = false, hasSpd = false;
                    for (var e : curAttrs.modifiers()) {
                        String n = e.attribute().value().getDescriptionId();
                        double amt = e.modifier().amount();
                        if (n.contains("attack_damage")) { hasDmg = true; if (Math.abs(amt - expectedDmg) > 0.01) needsAttrFix = true; }
                        if (n.contains("attack_speed")) { hasSpd = true; if (Math.abs(amt - expectedSpd) > 0.01) needsAttrFix = true; }
                    }
                    if (!hasDmg || !hasSpd) needsAttrFix = true;
                } else needsAttrFix = true;
            } catch (Exception ignored) {}
            if (needsToolFix || needsAttrFix) {
                if (needsToolFix) stack.set(DataComponents.TOOL, newTool);
                try {
                    java.util.List<ItemStack> handleStacks = new java.util.ArrayList<>();
                    ListTag handleTag = tag.getListOrEmpty("blended_handle");
                    for (int i = 0; i < handleTag.size(); i++) {
                        String idStr = handleTag.getString(i).orElse("");
                        if (idStr.isEmpty()) continue;
                        Identifier id = Identifier.parse(idStr);
                        Item it = BuiltInRegistries.ITEM.getValue(id);
                        if (it != null && it != Items.AIR) handleStacks.add(new ItemStack(it));
                    }
                    var handleStats = handleStacks.isEmpty() ? headStats : BlendedStatsHelper.averageStatsForStacks(handleStacks);
                    int blendedDur = Math.max(1, (headStats.durability() + handleStats.durability()) / 2);
                    if (!stack.getItem().toString().contains("helmet") && stack.get(DataComponents.EQUIPPABLE) == null) {
                        stack.set(DataComponents.MAX_DAMAGE, blendedDur);
                    }
                    String tPath2 = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
                    String tType2 = tPath2.startsWith("blended_") ? tPath2.substring(8) : tPath2;
                    ToolMaterial tier2b = selectTierForLegacy(headStacks, headStats);
                    float[] blended2b = BlendedStatsHelper.getBlendedToolStats(tier2b, tType2, headStats);
                    float finalDmg2 = blended2b[0] - 1.0f;
                    float finalSpd2 = blended2b[1] - 4.0f;
                    var existing = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
                    var builder = ItemAttributeModifiers.builder();
                    if (existing != null) {
                        for (var e : existing.modifiers()) {
                            var attr = e.attribute();
                            if (attr.is(Attributes.ATTACK_DAMAGE) || attr.is(Attributes.ATTACK_SPEED) || attr.is(Attributes.ARMOR) || attr.is(Attributes.ARMOR_TOUGHNESS)) continue;
                            builder.add(attr, e.modifier(), e.slot());
                        }
                    }
                    builder.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Identifier.withDefaultNamespace("base_attack_damage"), finalDmg2, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
                    builder.add(Attributes.ATTACK_SPEED, new AttributeModifier(Identifier.withDefaultNamespace("base_attack_speed"), finalSpd2, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
                    var built = builder.build();
                    if (!built.modifiers().isEmpty() || existing == null) stack.set(DataComponents.ATTRIBUTE_MODIFIERS, built);
                    int totalEnch = 0; for (ItemStack s : headStacks) totalEnch += BlendedStatsHelper.statsForItem(s.getItem()).enchantability();
                    java.util.List<ItemStack> handleForEnch = new java.util.ArrayList<>();
                    ListTag handleTagEnch = tag.getListOrEmpty("blended_handle");
                    for (int i = 0; i < handleTagEnch.size(); i++) { String idStr = handleTagEnch.getString(i).orElse(""); if (idStr.isEmpty()) continue; Identifier hid = Identifier.parse(idStr); Item hit = BuiltInRegistries.ITEM.getValue(hid); if (hit != null && hit != Items.AIR) handleForEnch.add(new ItemStack(hit)); }
                    var handleStatsEnch = handleForEnch.isEmpty() ? headStats : BlendedStatsHelper.averageStatsForStacks(handleForEnch);
                    int avgEnch = Math.max(1, (headStats.enchantability() + handleStatsEnch.enchantability()) / 2);
                    stack.set(DataComponents.ENCHANTABLE, new net.minecraft.world.item.enchantment.Enchantable(avgEnch));
                } catch (Exception ignored) {}
                return true;
            }
            return false;
        } catch (Exception e) {
            LOGGER.warn("upgradeToolComponent failed: {}", e.toString());
            return false;
        }
    }

    private static ToolMaterial selectTierForLegacy(java.util.List<ItemStack> headStacks, BlendedStatsHelper.MaterialStats headStats) {
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
                    float hard = BlendedStatsHelper.getHardnessForItem(it);
                    int dur = BlendedStatsHelper.statsForItem(it).durability();
                    if (hard > bestHard || (hard == bestHard && dur > bestDur)) { bestHard = hard; bestDur = dur; best = cand; }
                }
                if (best != null) dominant = itemMap.get(best);
            }
            ToolMaterial domMat = dominant != null ? BlendedStatsHelper.getToolMaterialForItem(dominant) : null;
            ToolMaterial highest = domMat;
            int highestDur = highest != null ? highest.durability() : -1;
            for (ItemStack s : headStacks) {
                ToolMaterial m = BlendedStatsHelper.getToolMaterialForItem(s.getItem());
                if (m == null) {
                    float hard = BlendedStatsHelper.getHardnessForItem(s.getItem());
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

    private static ItemStack getVanillaTemplateForLegacy(String type, ToolMaterial tier) {
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
        if (!type.equals("pickaxe")) return getVanillaTemplateForLegacy("pickaxe", tier);
        return null;
    }

    public static int upgradeInventory(Player player) {
        int upgraded = 0;
        var inv = player.getInventory();
        // getContainerSize() covers main (36) + armor (4) + offhand (1) = 41 in 26.2
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (upgradeStack(s)) {
                inv.setItem(i, s);
                upgraded++;
            }
        }
        if (upgraded > 0) {
            inv.setChanged();
            LOGGER.info("Upgraded {} blended stacks for player {} on login", upgraded, player.getName().getString());
        }
        return upgraded;
    }

    private static record PatternInfo(int w, int h, List<int[]> headCells, List<int[]> handleCells) {}

    private static PatternInfo getPatternInfo(String base) {
        // base is like "pickaxe", "shovel", "helmet" etc.
        return switch (base) {
            case "pickaxe" -> new PatternInfo(3, 3,
                    List.of(new int[]{0,0}, new int[]{1,0}, new int[]{2,0}),
                    List.of(new int[]{1,1}, new int[]{1,2}));
            case "axe" -> new PatternInfo(2, 3,
                    List.of(new int[]{0,0}, new int[]{1,0}, new int[]{0,1}),
                    List.of(new int[]{1,1}, new int[]{1,2}));
            case "shovel" -> new PatternInfo(1, 3,
                    List.of(new int[]{0,0}),
                    List.of(new int[]{0,1}, new int[]{0,2}));
            case "sword" -> new PatternInfo(1, 3,
                    List.of(new int[]{0,0}, new int[]{0,1}),
                    List.of(new int[]{0,2}));
            case "hoe" -> new PatternInfo(2, 3,
                    List.of(new int[]{0,0}, new int[]{1,0}),
                    List.of(new int[]{1,1}, new int[]{1,2}));
            case "helmet" -> new PatternInfo(3, 2,
                    List.of(new int[]{0,0}, new int[]{1,0}, new int[]{2,0}, new int[]{0,1}, new int[]{2,1}),
                    List.of());
            case "chestplate" -> new PatternInfo(3, 3,
                    List.of(new int[]{0,0}, new int[]{2,0}, new int[]{0,1}, new int[]{1,1}, new int[]{2,1}, new int[]{0,2}, new int[]{1,2}, new int[]{2,2}),
                    List.of());
            case "leggings" -> new PatternInfo(3, 3,
                    List.of(new int[]{0,0}, new int[]{1,0}, new int[]{2,0}, new int[]{0,1}, new int[]{2,1}, new int[]{0,2}, new int[]{2,2}),
                    List.of());
            case "boots" -> new PatternInfo(3, 2,
                    List.of(new int[]{0,0}, new int[]{2,0}, new int[]{0,1}, new int[]{2,1}),
                    List.of());
            default -> new PatternInfo(3, 3,
                    List.of(new int[]{0,0}, new int[]{1,0}, new int[]{2,0}, new int[]{0,1}, new int[]{1,1}, new int[]{2,1}, new int[]{0,2}, new int[]{1,2}, new int[]{2,2}),
                    List.of());
        };
    }
}
