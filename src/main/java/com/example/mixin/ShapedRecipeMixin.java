package com.example.mixin;

import java.util.Optional;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.example.util.BlendedStatsHelper;
import com.example.util.FlexibleRecipeHelper;

@Mixin(ShapedRecipe.class)
public class ShapedRecipeMixin {

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
        var ingredients = this.pattern.ingredients();
        ItemStack firstX = null;
        ItemStack firstStick = null;
        boolean hasX = false;
        boolean hasStick = false;
        for (int y = 0; y < pHeight; y++) {
            for (int x = 0; x < pWidth; x++) {
                int idx = x + y * pWidth;
                var opt = ingredients.get(idx);
                if (opt.isEmpty()) continue;
                Ingredient ing = opt.get();
                boolean isStick = ing.test(new ItemStack(Items.STICK));
                ItemStack stack = null;
                if (x < input.width() && y < input.height()) {
                    stack = input.getItem(x, y);
                }
                if (stack == null || stack.isEmpty()) continue;
                if (isStick) {
                    hasStick = true;
                    if (!stack.is(Items.STICK)) return false;
                    if (firstStick == null) firstStick = stack;
                    else if (!ItemStack.isSameItemSameComponents(firstStick, stack)) return false;
                } else {
                    hasX = true;
                    if (firstX == null) firstX = stack;
                    else if (!ItemStack.isSameItemSameComponents(firstX, stack)) return false;
                }
            }
        }
        if (!hasX) return false;
        // Only exact vanilla materials, not blocks, count as vanilla (so netherite_block chestplate goes to blended)
        boolean isVanillaMat = isVanillaArmorToolMaterial(firstX.getItem());
        if (!isVanillaMat) return false;
        if (hasStick && firstStick != null && !firstStick.is(Items.STICK)) return false;
        return true;
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
        if (pWidth != iWidth || pHeight != iHeight) return false;
        var ingredients = this.pattern.ingredients();
        ItemStack firstX = null;
        ItemStack firstStick = null;
        for (int y = 0; y < pHeight; y++) {
            for (int x = 0; x < pWidth; x++) {
                int idx;
                if (mirrored) {
                    idx = (pWidth - 1 - x) + y * pWidth;
                } else {
                    idx = x + y * pWidth;
                }
                Optional<Ingredient> opt = ingredients.get(idx);
                ItemStack stack = input.getItem(x, y);
                boolean expectPresent = opt.isPresent();
                boolean hasStack = !stack.isEmpty();
                if (expectPresent != hasStack) {
                    return false;
                }
                if (expectPresent) {
                    Ingredient ing = opt.get();
                    boolean isStick = ing.test(new ItemStack(Items.STICK));
                    if (isStick) {
                        if (firstStick == null) firstStick = stack;
                        else if (!ItemStack.isSameItemSameComponents(firstStick, stack)) return false;
                    } else {
                        // Head can be mixed (blended), no same check
                    }
                }
            }
        }
        // Handle must be uniform (all sticks same), head can be mixed
        return true;
    }

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
            // Try non-mirrored first, then mirrored
            boolean collected = collectHeadHandle(input, ingredients, pW, pH, false, headStacks, handleStacks);
            if (!collected || (headStacks.isEmpty() && handleStacks.isEmpty())) {
                headStacks.clear();
                handleStacks.clear();
                collectHeadHandle(input, ingredients, pW, pH, true, headStacks, handleStacks);
            }
            CompoundTag tag = new CompoundTag();
            ListTag all = new ListTag();
            ListTag headTag = new ListTag();
            ListTag handleTag = new ListTag();
            for (ItemStack s : input.items()) {
                if (s.isEmpty()) continue;
                all.add(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(s.getItem()).toString()));
            }
            for (ItemStack s : headStacks) headTag.add(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(s.getItem()).toString()));
            for (ItemStack s : handleStacks) handleTag.add(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(s.getItem()).toString()));
            tag.put("blended_ingredients", all);
            tag.put("blended_head", headTag);
            tag.put("blended_handle", handleTag);
            StringBuilder keySb = new StringBuilder();
            for (int i = 0; i < all.size(); i++) {
                if (i > 0) keySb.append("+");
                keySb.append(all.getString(i).orElse(""));
            }
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
                boolean useBlended = !singleMat && !headSingle;
                // Actually per spec: if head is single, no Blended even if handle different
                if (headSingle && !effective.isEmpty()) {
                    // check if headStacks all same
                    String headMat = getDisplayNameForHead(effective.get(0).getItem());
                    String display = headMat + " " + baseType;
                    // But if handle is different material, should we still show? The user says handle and head are separate, head single should be just head name
                    modified.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(display));
                } else {
                    String display;
                    if (singleMat) {
                        display = (dominant != null ? dominant + " " + baseType : baseType);
                    } else if (dominant != null) {
                        display = "Blended " + dominant + " " + baseType;
                    } else {
                        display = "Blended " + baseType;
                    }
                    modified.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(display));
                }
            } catch (Exception ex2) { ex2.printStackTrace(); }
            // Fix stats: handle influences swing speed/durability, head influences mining/attack/durability
            try {
                if (!headStacks.isEmpty() && modified.get(DataComponents.TOOL) != null || modified.get(DataComponents.WEAPON) != null || com.example.util.FlexibleRecipeHelper.isFlexibleResult(modified)) {
                    // Compute head and handle stats separately
                    var headStats = averageStatsForList(headStacks);
                    var handleStats = handleStacks.isEmpty() ? headStats : averageStatsForList(handleStacks);
                    // Durability: average of head and handle
                    int blendedDur = Math.max(1, (headStats.durability() + handleStats.durability()) / 2);
                    // For armor, this is already handled, but for tools we override
                    boolean isArmor = modified.get(DataComponents.EQUIPPABLE) != null;
                    if (!isArmor) {
                        modified.set(DataComponents.MAX_DAMAGE, blendedDur);
                        // Mining speed from head
                        var tool = modified.get(DataComponents.TOOL);
                        if (tool != null) {
                            var newTool = new net.minecraft.world.item.component.Tool(tool.rules(), headStats.speed(), tool.damagePerBlock(), tool.canDestroyBlocksInCreative());
                            modified.set(DataComponents.TOOL, newTool);
                        }
                        // Attack damage from head, attack speed from handle
                        var existing = modified.get(DataComponents.ATTRIBUTE_MODIFIERS);
                        var builder = net.minecraft.world.item.component.ItemAttributeModifiers.builder();
                        if (existing != null) {
                            for (var e : existing.modifiers()) {
                                var attr = e.attribute();
                                if (attr.is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) || attr.is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED) || attr.is(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR) || attr.is(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS)) continue;
                                builder.add(attr, e.modifier(), e.slot());
                            }
                        }
                        float attackSpeed = handleStats.speed() > 0 ? (handleStats.speed() * 0.3f - 2.8f) : -2.8f; // map handle speed to attack speed, handle influences swing
                        // Clamp attack speed: handle obsidian (1.5) vs oak (2) etc.
                        // Use handle's attackSpeed directly? For now, use handle's speed as swing influence
                        // For display, we set attack damage from head, attack speed from handle
                        builder.add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, new net.minecraft.world.entity.ai.attributes.AttributeModifier(Identifier.withDefaultNamespace("base_attack_damage"), headStats.attackDamage(), net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE), net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND);
                        builder.add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED, new net.minecraft.world.entity.ai.attributes.AttributeModifier(Identifier.withDefaultNamespace("base_attack_speed"), attackSpeed, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE), net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND);
                        var built = builder.build();
                        if (!built.modifiers().isEmpty() || existing == null) modified.set(DataComponents.ATTRIBUTE_MODIFIERS, built);
                        modified.set(DataComponents.ENCHANTABLE, new net.minecraft.world.item.enchantment.Enchantable((headStats.enchantability() + handleStats.enchantability())/2));
                        // Also update dyed color to be average of head and handle? Keep existing
                    }
                }
            } catch (Exception ex3) { ex3.printStackTrace(); }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        cir.setReturnValue(modified);
    }

    private boolean collectHeadHandle(CraftingInput input, java.util.List<Optional<Ingredient>> ingredients, int pW, int pH, boolean mirrored, java.util.List<ItemStack> headOut, java.util.List<ItemStack> handleOut) {
        if (input.width() != pW || input.height() != pH) return false;
        for (int y = 0; y < pH; y++) {
            for (int x = 0; x < pW; x++) {
                int idx = mirrored ? (pW - 1 - x) + y * pW : x + y * pW;
                var opt = ingredients.get(idx);
                if (opt.isEmpty()) continue;
                Ingredient ing = opt.get();
                boolean isStick = ing.test(new ItemStack(Items.STICK));
                ItemStack stack = input.getItem(x, y);
                if (stack.isEmpty()) return false;
                if (isStick) handleOut.add(stack);
                else headOut.add(stack);
            }
        }
        return true;
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
        if (item instanceof net.minecraft.world.item.BlockItem bi) {
            try {
                float d = bi.getBlock().defaultDestroyTime();
                if (d < 0) return 50f;
                if (d == 0) return 0.2f;
                return d;
            } catch (Exception e) { return 1f; }
        }
        if (item == Items.DIAMOND) return net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK.defaultDestroyTime();
        if (item == Items.IRON_INGOT) return net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultDestroyTime();
        if (item == Items.GOLD_INGOT) return net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultDestroyTime();
        if (item == Items.COPPER_INGOT) return 3f;
        if (item == Items.NETHERITE_INGOT) return net.minecraft.world.level.block.Blocks.NETHERITE_BLOCK.defaultDestroyTime();
        if (item == Items.EMERALD) return net.minecraft.world.level.block.Blocks.EMERALD_BLOCK.defaultDestroyTime();
        if (item == Items.BEDROCK) return 50f;
        if (item == Items.OBSIDIAN) return 50f;
        String id = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (id.contains("bedrock")) return 50f;
        if (id.contains("obsidian")) return 50f;
        if (id.contains("netherite")) return 50f;
        if (id.contains("diamond")) return 5f;
        if (id.contains("iron")) return 5f;
        if (id.contains("gold")) return 3f;
        if (id.contains("copper")) return 3f;
        return 1f;
    }

    private int getDurabilityForMixin(net.minecraft.world.item.Item item) {
        if (item == Items.DIAMOND) return 1561;
        if (item == Items.NETHERITE_INGOT) return 2031;
        if (item == Items.IRON_INGOT) return 250;
        if (item == Items.GOLD_INGOT) return 32;
        if (item == Items.COPPER_INGOT) return 190;
        if (item == Items.EMERALD) return 500;
        if (item == Items.BEDROCK) return 3000;
        if (item == Items.OBSIDIAN) return 1200;
        float h = getHardnessForMixin(item);
        if (h >= 50f) return 2400;
        if (h >= 3f) return (int)(250 + (h - 3f)*500);
        if (h >= 1.5f) return (int)(100 + h*40);
        return (int)(30 + h*40);
    }

    private com.example.util.BlendedStatsHelper.MaterialStats averageStatsForList(java.util.List<ItemStack> list) {
        if (list == null || list.isEmpty()) return new com.example.util.BlendedStatsHelper.MaterialStats(100, 2f, 1f, 10, 1, 0f, 0f);
        int totalDur = 0; float totalSpeed = 0; float totalDamage = 0; int totalEnchant = 0; int cnt = 0;
        for (ItemStack s : list) {
            if (s.isEmpty()) continue;
            var st = getStatsForMixin(s.getItem());
            totalDur += st.durability();
            totalSpeed += st.speed();
            totalDamage += st.attackDamage();
            totalEnchant += st.enchantability();
            cnt++;
        }
        if (cnt == 0) return new com.example.util.BlendedStatsHelper.MaterialStats(100, 2f, 1f, 10, 1, 0f, 0f);
        return new com.example.util.BlendedStatsHelper.MaterialStats(totalDur / cnt, totalSpeed / cnt, totalDamage / cnt, totalEnchant / cnt, 0, 0f, 0f);
    }

    private com.example.util.BlendedStatsHelper.MaterialStats getStatsForMixin(net.minecraft.world.item.Item item) {
        // Duplicate of BlendedStatsHelper.statsForItem but accessible
        float hard = getHardnessForMixin(item);
        if (item == Items.DIAMOND) return new com.example.util.BlendedStatsHelper.MaterialStats(1561, 8f, 3f, 10, 3, 2f, 0f);
        if (item == Items.NETHERITE_INGOT) return new com.example.util.BlendedStatsHelper.MaterialStats(2031, 9f, 4f, 15, 3, 3f, 0.1f);
        if (item == Items.IRON_INGOT) return new com.example.util.BlendedStatsHelper.MaterialStats(250, 6f, 2f, 14, 2, 0f, 0f);
        if (item == Items.GOLD_INGOT) return new com.example.util.BlendedStatsHelper.MaterialStats(32, 12f, 0f, 22, 1, 0f, 0f);
        if (item == Items.COPPER_INGOT) return new com.example.util.BlendedStatsHelper.MaterialStats(190, 5f, 1.5f, 10, 2, 0f, 0f);
        if (item == Items.EMERALD) return new com.example.util.BlendedStatsHelper.MaterialStats(500, 6.5f, 2.5f, 12, 2, 0f, 0f);
        if (item == Items.LEATHER) return new com.example.util.BlendedStatsHelper.MaterialStats(80, 1f, 0f, 15, 1, 0f, 0f);
        if (hard >= 50f) return new com.example.util.BlendedStatsHelper.MaterialStats((int)(2300 + hard*2), 9.5f, 4.5f, 12, 4, 3.5f, 0.15f);
        if (hard >= 3f) return new com.example.util.BlendedStatsHelper.MaterialStats((int)(250 + (hard - 3f)*500), 6f + (hard - 3f)*0.8f, 2f + (hard - 3f)*0.4f, 12, (int)Math.min(4, 2 + (hard - 3f)), hard >= 4f ? 2f : 0f, 0f);
        if (hard >= 1.5f) return new com.example.util.BlendedStatsHelper.MaterialStats((int)(100 + hard*40), 3f + hard*0.5f, 1f, 8, 1, 0f, 0f);
        return new com.example.util.BlendedStatsHelper.MaterialStats((int)(30 + hard*40), 1f + hard, 0.5f, 5, 0, 0f, 0f);
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
}
