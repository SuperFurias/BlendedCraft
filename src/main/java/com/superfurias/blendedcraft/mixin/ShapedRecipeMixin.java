package com.superfurias.blendedcraft.mixin;

import java.util.Optional;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.superfurias.blendedcraft.util.BlendedStatsHelper;
import com.superfurias.blendedcraft.util.FlexibleRecipeHelper;

@Mixin(ShapedRecipe.class)
public class ShapedRecipeMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("blendedcraft/ShapedRecipeMixin");

    @Shadow @Final private ShapedRecipePattern pattern;
    @Shadow @Final private ItemStackTemplate result;

    @Inject(method = "matches(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z", at = @At("HEAD"), cancellable = true)
    private void onMatches(CraftingInput input, Level level, CallbackInfoReturnable<Boolean> cir) {
        if (!FlexibleRecipeHelper.isFlexibleResult(this.result)) {
            return;
        }
        // If input exactly matches a vanilla recipe for this shape, let vanilla handle it (so full vanilla keeps default)
        if (isVanillaExactMatch(input)) {
            cir.setReturnValue(false);
            return;
        }
        long requiredCount = this.pattern.ingredients().stream().filter(Optional::isPresent).count();
        if (input.ingredientCount() != requiredCount) {
            cir.setReturnValue(false);
            return;
        }
        boolean matched = flexibleMatches(input, false) || flexibleMatches(input, true);
        cir.setReturnValue(matched);
    }

    private boolean isVanillaExactMatch(CraftingInput input) {
        int pWidth = this.pattern.width();
        int pHeight = this.pattern.height();
        int iWidth = input.width();
        int iHeight = input.height();
        if (pWidth > iWidth || pHeight > iHeight) return false;
        var ingredients = this.pattern.ingredients();
        // Search all offsets and mirrored variants - vanilla exact means uniform head+handle and vanilla material
        for (boolean mirrored : new boolean[]{false, true}) {
            for (int oy = 0; oy <= iHeight - pHeight; oy++) {
                for (int ox = 0; ox <= iWidth - pWidth; ox++) {
                    ItemStack firstX = null;
                    ItemStack firstStick = null;
                    boolean hasX = false;
                    boolean hasStick = false;
                    boolean ok = true;
                    // Check pattern cells
                    for (int y = 0; y < pHeight && ok; y++) {
                        for (int x = 0; x < pWidth && ok; x++) {
                            int idx = mirrored ? (pWidth - 1 - x) + y * pWidth : x + y * pWidth;
                            var opt = ingredients.get(idx);
                            if (opt.isEmpty()) {
                                if (!input.getItem(x + ox, y + oy).isEmpty()) ok = false;
                                continue;
                            }
                            Ingredient ing = opt.get();
                            boolean isStick = ing.test(new ItemStack(Items.STICK));
                            ItemStack stack = input.getItem(x + ox, y + oy);
                            if (stack.isEmpty()) { ok = false; break; }
                            if (isStick) {
                                hasStick = true;
                                if (!stack.is(Items.STICK)) { ok = false; break; }
                                if (firstStick == null) firstStick = stack;
                                else if (!ItemStack.isSameItemSameComponents(firstStick, stack)) { ok = false; break; }
                            } else {
                                hasX = true;
                                if (firstX == null) firstX = stack;
                                else if (!ItemStack.isSameItemSameComponents(firstX, stack)) { ok = false; break; }
                            }
                        }
                    }
                    if (!ok) continue;
                    // Ensure outside pattern empty
                    for (int y = 0; y < iHeight && ok; y++) {
                        for (int x = 0; x < iWidth && ok; x++) {
                            boolean inside = x >= ox && x < ox + pWidth && y >= oy && y < oy + pHeight;
                            if (inside) continue;
                            if (!input.getItem(x, y).isEmpty()) ok = false;
                        }
                    }
                    if (!ok || !hasX) continue;
                    boolean isVanillaMat = isVanillaArmorToolMaterial(firstX.getItem());
                    if (!isVanillaMat) continue;
                    if (hasStick && firstStick != null && !firstStick.is(Items.STICK)) continue;
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isVanillaArmorToolMaterial(net.minecraft.world.item.Item item) {
        if (item == Items.LEATHER) return true;
        if (item == Items.IRON_INGOT) return true;
        if (item == Items.GOLD_INGOT) return true;
        if (item == Items.DIAMOND) return true;
        if (item == Items.NETHERITE_INGOT) return true;
        if (item == Items.COPPER_INGOT) return true;
        if (item == Items.TURTLE_SCUTE) return true;
        if (item == Items.ARMADILLO_SCUTE) return true;
        if (item == Items.OAK_PLANKS) return true;
        if (item == Items.COBBLESTONE) return true;
        if (item == Items.STONE) return true;
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (path.endsWith("_planks")) return true;
        if (path.contains("chain")) return true;
        if (path.equals("stick")) return false;
        return false;
    }

    private boolean flexibleMatches(CraftingInput input, boolean mirrored) {
        int pWidth = this.pattern.width();
        int pHeight = this.pattern.height();
        int iWidth = input.width();
        int iHeight = input.height();
        if (pWidth > iWidth || pHeight > iHeight) return false;
        var ingredients = this.pattern.ingredients();
        for (int oy = 0; oy <= iHeight - pHeight; oy++) {
            for (int ox = 0; ox <= iWidth - pWidth; ox++) {
                boolean ok = true;
                ItemStack firstStick = null;
                // Check pattern area
                for (int y = 0; y < pHeight && ok; y++) {
                    for (int x = 0; x < pWidth && ok; x++) {
                        int idx = mirrored ? (pWidth - 1 - x) + y * pWidth : x + y * pWidth;
                        Optional<Ingredient> opt = ingredients.get(idx);
                        ItemStack stack = input.getItem(x + ox, y + oy);
                        boolean expectPresent = opt.isPresent();
                        boolean hasStack = !stack.isEmpty();
                        if (expectPresent != hasStack) { ok = false; break; }
                        if (expectPresent) {
                            Ingredient ing = opt.get();
                            boolean isStick = ing.test(new ItemStack(Items.STICK));
                            if (isStick) {
                                if (firstStick == null) firstStick = stack;
                                else if (!ItemStack.isSameItemSameComponents(firstStick, stack)) { ok = false; break; }
                            }
                        }
                    }
                }
                if (!ok) continue;
                // Ensure outside pattern empty
                for (int y = 0; y < iHeight && ok; y++) {
                    for (int x = 0; x < iWidth && ok; x++) {
                        boolean inside = x >= ox && x < ox + pWidth && y >= oy && y < oy + pHeight;
                        if (inside) continue;
                        if (!input.getItem(x, y).isEmpty()) ok = false;
                    }
                }
                if (ok) return true;
            }
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    @Inject(method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"), cancellable = true)
    private void onAssemble(CraftingInput input, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack resultStack = cir.getReturnValue();
        if (resultStack.isEmpty()) return;
        if (!FlexibleRecipeHelper.isFlexibleResult(this.result)) return;
        if (input.ingredientCount() == 0) return;
        ItemStack modified = BlendedStatsHelper.applyBlendedStats(resultStack, input);
        // Store handle vs head split for texture (so handle can be obsidian, head bedrock etc.)
        try {
            var ingredients = this.pattern.ingredients();
            int pW = this.pattern.width();
            int pH = this.pattern.height();
            java.util.List<ItemStack> headStacks = new java.util.ArrayList<>();
            java.util.List<ItemStack> handleStacks = new java.util.ArrayList<>();
            java.util.List<Integer> headPos = new java.util.ArrayList<>();
            java.util.List<Integer> handlePos = new java.util.ArrayList<>();
            // Try non-mirrored first, then mirrored
            boolean collected = collectHeadHandle(input, ingredients, pW, pH, false, headStacks, handleStacks, headPos, handlePos);
            if (!collected || (headStacks.isEmpty() && handleStacks.isEmpty())) {
                headStacks.clear();
                handleStacks.clear();
                headPos.clear();
                handlePos.clear();
                collectHeadHandle(input, ingredients, pW, pH, true, headStacks, handleStacks, headPos, handlePos);
            }
            CompoundTag tag = new CompoundTag();
            ListTag all = new ListTag();
            ListTag headTag = new ListTag();
            ListTag handleTag = new ListTag();
            ListTag headPosTag = new ListTag();
            ListTag handlePosTag = new ListTag();
            ListTag allPosTag = new ListTag();
            for (int y = 0; y < input.height(); y++) {
                for (int x = 0; x < input.width(); x++) {
                    ItemStack s = input.getItem(x, y);
                    if (s.isEmpty()) continue;
                    all.add(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(s.getItem()).toString()));
                    allPosTag.add(net.minecraft.nbt.IntTag.valueOf(x + y * 16));
                }
            }
            for (ItemStack s : headStacks) headTag.add(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(s.getItem()).toString()));
            for (ItemStack s : handleStacks) handleTag.add(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(s.getItem()).toString()));
            for (int p : headPos) headPosTag.add(net.minecraft.nbt.IntTag.valueOf(p));
            for (int p : handlePos) handlePosTag.add(net.minecraft.nbt.IntTag.valueOf(p));
            tag.put("blended_ingredients", all);
            tag.put("blended_head", headTag);
            tag.put("blended_handle", handleTag);
            tag.put("blended_head_pos", headPosTag);
            tag.put("blended_handle_pos", handlePosTag);
            tag.put("blended_ingredients_pos", allPosTag);
            tag.putInt("blended_pattern_width", pW);
            tag.putInt("blended_pattern_height", pH);
            StringBuilder keySb = new StringBuilder();
            for (int i = 0; i < all.size(); i++) {
                if (i > 0) keySb.append("+");
                keySb.append(all.getString(i).orElse(""));
            }
            StringBuilder posKeySb = new StringBuilder();
            for (int i = 0; i < headPos.size(); i++) {
                if (i > 0) posKeySb.append(",");
                posKeySb.append(headPos.get(i));
            }
            posKeySb.append("|");
            for (int i = 0; i < handlePos.size(); i++) {
                if (i > 0) posKeySb.append(",");
                posKeySb.append(handlePos.get(i));
            }
            tag.putString("blended_pos_key", posKeySb.toString());
            tag.putString("blended_key", keySb.toString());
            modified.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            // Fix naming: handle uniform, head can be mixed. Dominant based on head (>50% or 50% tie strongest)
            try {
                String baseType = getBaseTypeName(modified);
                java.util.List<ItemStack> effective = headStacks.isEmpty() ? allStacks(input) : headStacks;
                String dominant = getDominantHeadName(effective);
                boolean singleMat = isSingleMaterial(input);
                // For tools, if head is single material, don't show Blended even if handle different
                boolean headSingle = isSingleMaterialList(headStacks.isEmpty() ? allStacks(input) : headStacks);
                // Actually per spec: if head is single, no Blended even if handle different
                if (headSingle && !effective.isEmpty()) {
                    // check if headStacks all same
                    String headMat = getDisplayNameForHead(effective.get(0).getItem());
                    String display = headMat + " " + baseType;
                    // But if handle is different material, should we still show? The user says handle and head are separate, head single should be just head name
                    modified.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(display).withStyle(s -> s.withItalic(false)));
                } else {
                    String display;
                    if (singleMat) {
                        display = (dominant != null ? dominant + " " + baseType : baseType);
                    } else if (dominant != null) {
                        display = "Blended " + dominant + " " + baseType;
                    } else {
                        display = "Blended " + baseType;
                    }
                    modified.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(display).withStyle(s -> s.withItalic(false)));
                }
            } catch (Exception ex2) { LOGGER.warn("Failed to set custom name: {}", ex2.toString(), ex2); }
            // Fix stats: handle influences swing speed/durability, head influences mining/attack/durability
            try {
                if (!headStacks.isEmpty() && modified.get(DataComponents.TOOL) != null || modified.get(DataComponents.WEAPON) != null || com.superfurias.blendedcraft.util.FlexibleRecipeHelper.isFlexibleResult(modified)) {
                    // Compute head and handle stats separately
                    var headStats = averageStatsForList(headStacks);
                    var handleStats = handleStacks.isEmpty() ? headStats : averageStatsForList(handleStacks);
                    // Durability: average of head and handle
                    int blendedDur = Math.max(1, (headStats.durability() + handleStats.durability()) / 2);
                    // For armor, this is already handled, but for tools we override
                    boolean isArmor = modified.get(DataComponents.EQUIPPABLE) != null;
                    if (!isArmor) {
                        modified.set(DataComponents.MAX_DAMAGE, blendedDur);
                        // Mining speed from head - FIX: correct Tool rules (default 1.0, correct mineable tag, correct tier)
                        fixToolForHead(modified, headStats, headStacks, handleStacks);
                        // Attack damage from head, attack speed from handle
                        var existing = modified.get(DataComponents.ATTRIBUTE_MODIFIERS);
                        var builder = net.minecraft.world.item.component.ItemAttributeModifiers.builder();
                        if (existing != null) {
                            for (var e : existing.modifiers()) {
                                var attr = e.attribute();
                                boolean isDeprecated = attr.is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) || attr.is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED) || attr.is(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) || attr.is(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);
                                if (isDeprecated) continue;
                                builder.add(attr, e.modifier(), e.slot());
                            }
                        }
                        String tPath2 = BuiltInRegistries.ITEM.getKey(modified.getItem()).getPath();
                        String toolType2 = tPath2.startsWith("blended_") ? tPath2.substring(8) : tPath2;
                        ToolMaterial tier2 = BlendedStatsHelper.getToolMaterialForItem(headStacks.isEmpty() ? modified.getItem() : headStacks.get(0).getItem());
                        if (tier2 == null) tier2 = selectTierMaterialForMixin(headStacks, headStats);
                        float[] blended2 = BlendedStatsHelper.getBlendedToolStats(tier2, toolType2, headStats);
                        float finalDmg2 = blended2[0] - 1.0f;
                        float finalSpd2 = blended2[1] - 4.0f;
                        builder.add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, new net.minecraft.world.entity.ai.attributes.AttributeModifier(Identifier.withDefaultNamespace("base_attack_damage"), finalDmg2, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE), net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND);
                        builder.add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED, new net.minecraft.world.entity.ai.attributes.AttributeModifier(Identifier.withDefaultNamespace("base_attack_speed"), finalSpd2, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE), net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND);
                        var built = builder.build();
                        if (!built.modifiers().isEmpty() || existing == null) modified.set(DataComponents.ATTRIBUTE_MODIFIERS, built);
                        modified.set(DataComponents.ENCHANTABLE, new net.minecraft.world.item.enchantment.Enchantable((headStats.enchantability() + handleStats.enchantability())/2));
                        // Also update dyed color to be average of head and handle? Keep existing
                    }
                }
            } catch (Exception ex3) { LOGGER.warn("Failed to apply head/handle stats: {}", ex3.toString(), ex3); }
        } catch (Exception ex) {
            LOGGER.error("Failed to apply blended stats in assemble", ex);
        }
        cir.setReturnValue(modified);
    }

    @SuppressWarnings("unused")
    private boolean collectHeadHandle(CraftingInput input, java.util.List<Optional<Ingredient>> ingredients, int pW, int pH, boolean mirrored, java.util.List<ItemStack> headOut, java.util.List<ItemStack> handleOut) {
        return collectHeadHandle(input, ingredients, pW, pH, mirrored, headOut, handleOut, null, null);
    }

    private boolean collectHeadHandle(CraftingInput input, java.util.List<Optional<Ingredient>> ingredients, int pW, int pH, boolean mirrored, java.util.List<ItemStack> headOut, java.util.List<ItemStack> handleOut, java.util.List<Integer> headPosOut, java.util.List<Integer> handlePosOut) {
        int iW = input.width();
        int iH = input.height();
        if (pW > iW || pH > iH) return false;
        for (int oy = 0; oy <= iH - pH; oy++) {
            for (int ox = 0; ox <= iW - pW; ox++) {
                // Check if this offset matches (including outside empty)
                boolean fits = true;
                // quick pattern check
                for (int y = 0; y < pH && fits; y++) {
                    for (int x = 0; x < pW && fits; x++) {
                        int idx = mirrored ? (pW - 1 - x) + y * pW : x + y * pW;
                        var opt = ingredients.get(idx);
                        ItemStack s = input.getItem(x + ox, y + oy);
                        boolean expect = opt.isPresent();
                        boolean has = !s.isEmpty();
                        if (expect != has) fits = false;
                    }
                }
                if (!fits) continue;
                // check outside empty
                for (int y = 0; y < iH && fits; y++) {
                    for (int x = 0; x < iW && fits; x++) {
                        boolean inside = x >= ox && x < ox + pW && y >= oy && y < oy + pH;
                        if (inside) continue;
                        if (!input.getItem(x, y).isEmpty()) fits = false;
                    }
                }
                if (!fits) continue;
                // This offset matches - collect
                headOut.clear(); handleOut.clear();
                if (headPosOut != null) headPosOut.clear();
                if (handlePosOut != null) handlePosOut.clear();
                for (int y = 0; y < pH; y++) {
                    for (int x = 0; x < pW; x++) {
                        int idx = mirrored ? (pW - 1 - x) + y * pW : x + y * pW;
                        var opt = ingredients.get(idx);
                        if (opt.isEmpty()) continue;
                        Ingredient ing = opt.get();
                        boolean isStick = ing.test(new ItemStack(Items.STICK));
                        ItemStack stack = input.getItem(x + ox, y + oy);
                        if (stack.isEmpty()) return false;
                        int pos = x + y * 16; // pattern-local for Voronoi seed stability
                        if (isStick) {
                            handleOut.add(stack);
                            if (handlePosOut != null) handlePosOut.add(pos);
                        } else {
                            headOut.add(stack);
                            if (headPosOut != null) headPosOut.add(pos);
                        }
                    }
                }
                return true;
            }
        }
        return false;
    }

    private java.util.List<ItemStack> allStacks(CraftingInput input) {
        java.util.List<ItemStack> all = new java.util.ArrayList<>();
        for (ItemStack s : input.items()) if (!s.isEmpty()) all.add(s);
        return all;
    }

    private boolean isSingleMaterial(CraftingInput input) {
        java.util.Set<String> uniq = new java.util.HashSet<>();
        for (ItemStack s : input.items()) {
            if (s.isEmpty()) continue;
            String base = stripToBase(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath());
            uniq.add(base);
            if (uniq.size() > 1) return false;
        }
        return uniq.size() == 1;
    }

    private String stripToBase(String path) {
        String p = path;
        if (p.endsWith("_ingot")) p = p.substring(0, p.length() - 6);
        else if (p.endsWith("_planks")) p = p.substring(0, p.length() - 7);
        else if (p.endsWith("_log")) p = p.substring(0, p.length() - 4);
        else if (p.endsWith("_scute")) p = p.substring(0, p.length() - 6);
        else if (p.endsWith("_shard")) p = p.substring(0, p.length() - 6);
        return p;
    }

    private String formatMat(String baseMat) {
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

    private String getBaseTypeName(ItemStack result) {
        String path = BuiltInRegistries.ITEM.getKey(result.getItem()).getPath();
        String base = path.startsWith("blended_") ? path.substring(8) : path;
        return formatMat(base);
    }

    private String getDominantHeadName(java.util.List<ItemStack> effective) {
        if (effective == null || effective.isEmpty()) return null;
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        java.util.Map<String, ItemStack> rep = new java.util.HashMap<>();
        for (ItemStack s : effective) {
            String base = stripToBase(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath());
            counts.put(base, counts.getOrDefault(base, 0) + 1);
            rep.putIfAbsent(base, s);
        }
        int total = effective.size();
        int max = 0;
        for (int c : counts.values()) if (c > max) max = c;
        java.util.List<String> cands = new java.util.ArrayList<>();
        for (var e : counts.entrySet()) if (e.getValue() == max) cands.add(e.getKey());
        if (cands.isEmpty()) return null;
        // Choose strongest among candidates where max is at least 50%
        String best = null;
        float bestHard = -1;
        int bestDur = -1;
        for (String cand : cands) {
            ItemStack rs = rep.get(cand);
            float hard = getHardnessForMixin(rs.getItem());
            int dur = getDurabilityForMixin(rs.getItem());
            if (hard > bestHard || (hard == bestHard && dur > bestDur)) {
                bestHard = hard;
                bestDur = dur;
                best = cand;
            }
        }
        if (max * 2 > total) return formatMat(best);
        if (max * 2 == total && cands.size() == 2) return formatMat(best);
        if (max * 2 >= total) return formatMat(best);
        return null;
    }

    private float getHardnessForMixin(net.minecraft.world.item.Item item) {
        return com.superfurias.blendedcraft.util.BlendedStatsHelper.getHardnessForItem(item);
    }

    private int getDurabilityForMixin(net.minecraft.world.item.Item item) {
        return com.superfurias.blendedcraft.util.BlendedStatsHelper.statsForItem(item).durability();
    }

    private com.superfurias.blendedcraft.util.BlendedStatsHelper.MaterialStats averageStatsForList(java.util.List<ItemStack> list) {
        return com.superfurias.blendedcraft.util.BlendedStatsHelper.averageStatsForStacks(list);
    }

    @SuppressWarnings("unused")
    private com.superfurias.blendedcraft.util.BlendedStatsHelper.MaterialStats getStatsForMixin(net.minecraft.world.item.Item item) {
        return com.superfurias.blendedcraft.util.BlendedStatsHelper.statsForItem(item);
    }

    private boolean isSingleMaterialList(java.util.List<ItemStack> list) {
        java.util.Set<String> uniq = new java.util.HashSet<>();
        for (ItemStack s : list) {
            if (s.isEmpty()) continue;
            String base = stripToBase(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath());
            uniq.add(base);
            if (uniq.size() > 1) return false;
        }
        return uniq.size() == 1;
    }

    private String getDisplayNameForHead(net.minecraft.world.item.Item item) {
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (path.endsWith("_block")) return formatMat(path);
        String base = stripToBase(path);
        return formatMat(base);
    }

    private void fixToolForHead(ItemStack stack, com.superfurias.blendedcraft.util.BlendedStatsHelper.MaterialStats headStats, java.util.List<ItemStack> headStacks, java.util.List<ItemStack> handleStacks) {
        try {
            String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            String type = path.startsWith("blended_") ? path.substring(8) : path;
            if (type.equals("sword")) {
                // Sword rules are identical across tiers (Fabric docs GUIDITE sword example); copy from any sword template
                ItemStack tmpl = getVanillaTemplateForMixin("sword", ToolMaterial.DIAMOND);
                Tool tmplTool = tmpl != null ? tmpl.get(DataComponents.TOOL) : null;
                if (tmplTool != null) {
                    stack.set(DataComponents.TOOL, tmplTool);
                } else {
                    HolderGetter<Block> getter = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);
                    HolderSet<Block> cobweb = HolderSet.direct(BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.COBWEB));
                    HolderSet<Block> instant = getter.getOrThrow(BlockTags.SWORD_INSTANTLY_MINES);
                    HolderSet<Block> efficient = getter.getOrThrow(BlockTags.SWORD_EFFICIENT);
                    java.util.List<Tool.Rule> rules = java.util.List.of(
                            Tool.Rule.minesAndDrops(cobweb, 15.0F),
                            Tool.Rule.overrideSpeed(instant, Float.MAX_VALUE),
                            Tool.Rule.overrideSpeed(efficient, 1.5F)
                    );
                    stack.set(DataComponents.TOOL, new Tool(rules, 1.0F, 2, false));
                }
                return;
            }
            ToolMaterial tier = selectTierMaterialForMixin(headStacks, headStats);
            ItemStack template = getVanillaTemplateForMixin(type, tier);
            if (template == null) {
                LOGGER.warn("No template for {} tier {}", type, tier);
                return;
            }
            Tool tmplTool = template.get(DataComponents.TOOL);
            if (tmplTool == null || tmplTool.rules().size() < 2) {
                LOGGER.warn("Template {} has no tool rules", BuiltInRegistries.ITEM.getKey(template.getItem()));
                return;
            }
            HolderSet<Block> incorrectSet = tmplTool.rules().get(0).blocks();
            HolderSet<Block> mineableSet = tmplTool.rules().get(1).blocks();
            java.util.List<Tool.Rule> newRules = java.util.List.of(
                    Tool.Rule.deniesDrops(incorrectSet),
                    Tool.Rule.minesAndDrops(mineableSet, headStats.speed())
            );
            Tool newTool = new Tool(newRules, 1.0F, tmplTool.damagePerBlock(), tmplTool.canDestroyBlocksInCreative());
            stack.set(DataComponents.TOOL, newTool);
        } catch (Exception e) {
            LOGGER.warn("Failed to fix tool for {}: {}", BuiltInRegistries.ITEM.getKey(stack.getItem()), e.toString(), e);
        }
    }

    private ToolMaterial selectTierMaterialForMixin(java.util.List<ItemStack> headStacks, com.superfurias.blendedcraft.util.BlendedStatsHelper.MaterialStats headStats) {
        try {
            java.util.Map<String, Integer> counts = new java.util.HashMap<>();
            java.util.Map<String, net.minecraft.world.item.Item> itemMap = new java.util.HashMap<>();
            for (ItemStack s : headStacks) {
                if (s.isEmpty()) continue;
                String p = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
                counts.put(p, counts.getOrDefault(p, 0) + 1);
                itemMap.putIfAbsent(p, s.getItem());
            }
            net.minecraft.world.item.Item dominant = null;
            if (!counts.isEmpty()) {
                int max = 0; for (int c : counts.values()) if (c > max) max = c;
                java.util.List<String> cands = new java.util.ArrayList<>();
                for (var e : counts.entrySet()) if (e.getValue() == max) cands.add(e.getKey());
                String best = null; float bestHard = -1; int bestDur = -1;
                for (String cand : cands) {
                    net.minecraft.world.item.Item it = itemMap.get(cand);
                    float hard = com.superfurias.blendedcraft.util.BlendedStatsHelper.getHardnessForItem(it);
                    int dur = com.superfurias.blendedcraft.util.BlendedStatsHelper.statsForItem(it).durability();
                    if (hard > bestHard || (hard == bestHard && dur > bestDur)) { bestHard = hard; bestDur = dur; best = cand; }
                }
                if (best != null) dominant = itemMap.get(best);
            }
            ToolMaterial domMat = dominant != null ? getToolMaterialForMixin(dominant) : null;
            ToolMaterial highest = domMat;
            int highestDur = highest != null ? highest.durability() : -1;
            for (ItemStack s : headStacks) {
                ToolMaterial m = getToolMaterialForMixin(s.getItem());
                if (m == null) {
                    float hard = com.superfurias.blendedcraft.util.BlendedStatsHelper.getHardnessForItem(s.getItem());
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

    private ToolMaterial getToolMaterialForMixin(net.minecraft.world.item.Item item) {
        return com.superfurias.blendedcraft.util.BlendedStatsHelper.getToolMaterialForItem(item);
    }

    private ItemStack getVanillaTemplateForMixin(String type, ToolMaterial tier) {
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
            net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
            if (item != null && item != Items.AIR && !BuiltInRegistries.ITEM.getKey(item).getPath().equals("air")) {
                ItemStack stack = new ItemStack(item);
                if (stack.get(DataComponents.TOOL) != null) return stack;
            }
        }
        if (!type.equals("pickaxe")) return getVanillaTemplateForMixin("pickaxe", tier);
        return null;
    }
}
