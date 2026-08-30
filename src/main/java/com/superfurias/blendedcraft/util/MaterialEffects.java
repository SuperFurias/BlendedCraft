package com.superfurias.blendedcraft.util;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Material traits: crafting with special materials grants built-in perks
 * (Tinkers'-style). Traits come from the HEAD ingredients of tools and from
 * ALL ingredients of armor. Effects are stored in the item's custom data as
 * "blended_effects" (internal ids) and applied by custom code (mixins + tick
 * handler) - they are INDEPENDENT from the vanilla enchantment system, so
 * they never block or consume real enchanting.
 *
 * Effect levels scale with how many pieces contain the material:
 * 1 piece = tier 1, 2 pieces = tier 2, 3+ pieces (or full set) = tier 3.
 */
public final class MaterialEffects {
    private static final Logger LOGGER = LoggerFactory.getLogger("blendedcraft/MaterialEffects");

    private MaterialEffects() {}

    // ------------------------------------------------------------------
    // Material matching (by item id path)
    // ------------------------------------------------------------------

    private static final Set<String> SLIME = Set.of("slime_block", "slime_ball");
    private static final Set<String> CACTUS = Set.of("cactus");
    private static final Set<String> LAPIS = Set.of("lapis_lazuli", "lapis_block");
    private static final Set<String> ICE = Set.of("ice", "packed_ice", "blue_ice");
    private static final Set<String> SOUL = Set.of("soul_sand", "soul_soil");
    private static final Set<String> FEATHER = Set.of("feather");
    private static final Set<String> FIERY = Set.of("magma_block", "magma_cream", "blaze_rod", "blaze_powder", "magma");
    private static final Set<String> REDSTONE = Set.of("redstone", "redstone_block");
    private static final Set<String> SUGAR = Set.of("sugar");
    private static final Set<String> EMERALD = Set.of("emerald", "emerald_block");
    private static final Set<String> PRISMARINE = Set.of("prismarine", "prismarine_bricks", "dark_prismarine", "prismarine_crystals", "prismarine_shard");
    private static final Set<String> GLOW = Set.of("sea_lantern", "glowstone", "glowstone_dust");
    private static final Set<String> QUARTZ = Set.of(
            "quartz", "quartz_block", "smooth_quartz", "quartz_bricks", "quartz_pillar",
            "chiseled_quartz_block", "quartz_stairs", "quartz_slab", "smooth_quartz_stairs",
            "smooth_quartz_slab", "nether_quartz_ore");

    // ------------------------------------------------------------------
    // Trait ids (stored in blended_effects)
    // ------------------------------------------------------------------

    public static final String T_SLIME_SWORD = "slime_sword";        // Knockback 2 on hit
    public static final String T_SLIME_BOOTS = "slime_boots";        // Bounce
    public static final String T_CACTUS_ARMOR = "cactus_armor";      // Thorns 1-3
    public static final String T_LAPIS_FORTUNE = "lapis_fortune";    // Fortune 1-3 on mining tools
    public static final String T_LAPIS_LOOTING = "lapis_looting";    // Looting 1-3 on swords
    public static final String T_QUARTZ_SHARPNESS = "quartz_sharpness"; // Sharpness 1-2 on swords
    public static final String T_ICE_FROSTWALKER = "ice_frostwalker";   // Frost walker 1-2 on boots
    public static final String T_SOUL_SOULSPEED = "soul_soulspeed";  // Soul speed 1-2 on boots
    public static final String T_FEATHER_FEATHERFALL = "feather_featherfall"; // Feather fall 1-4 on armor
    public static final String T_FIERY_SMELT = "fiery_smelt";        // Auto-smelt 1-3 on mining tools
    public static final String T_FIERY_FIREASPECT = "fiery_fireaspect"; // Fire aspect 1-2 on swords
    public static final String T_FIERY_ARMOR = "fiery_armor";        // Fire resistance on full fiery set
    public static final String T_REDSTONE_HASTE = "redstone_haste";  // Haste 1-2 while held
    public static final String T_SUGAR_SPEED = "sugar_speed";        // Speed 1-2 while held (tools AND armor)
    public static final String T_EMERALD_LOOT = "emerald_loot";      // Looting/Fortune 1-3 (tools) + Luck 1-2 (armor)
    public static final String T_PRISMARINE_WATER = "prismarine_water"; // Water breathing on helmet
    public static final String T_GLOW_NIGHTVISION = "glow_nightvision"; // Night vision on helmet

    /** Families keyed by trait id. */
    private static final Map<String, Set<String>> TRAIT_MATERIALS = new HashMap<>();
    static {
        TRAIT_MATERIALS.put(T_SLIME_SWORD, SLIME);
        TRAIT_MATERIALS.put(T_SLIME_BOOTS, SLIME);
        TRAIT_MATERIALS.put(T_CACTUS_ARMOR, CACTUS);
        TRAIT_MATERIALS.put(T_LAPIS_FORTUNE, LAPIS);
        TRAIT_MATERIALS.put(T_LAPIS_LOOTING, LAPIS);
        TRAIT_MATERIALS.put(T_QUARTZ_SHARPNESS, QUARTZ);
        TRAIT_MATERIALS.put(T_ICE_FROSTWALKER, ICE);
        TRAIT_MATERIALS.put(T_SOUL_SOULSPEED, SOUL);
        TRAIT_MATERIALS.put(T_FEATHER_FEATHERFALL, FEATHER);
        TRAIT_MATERIALS.put(T_FIERY_SMELT, FIERY);
        TRAIT_MATERIALS.put(T_FIERY_FIREASPECT, FIERY);
        TRAIT_MATERIALS.put(T_FIERY_ARMOR, FIERY);
        TRAIT_MATERIALS.put(T_REDSTONE_HASTE, REDSTONE);
        TRAIT_MATERIALS.put(T_SUGAR_SPEED, SUGAR);
        TRAIT_MATERIALS.put(T_EMERALD_LOOT, EMERALD);
        TRAIT_MATERIALS.put(T_PRISMARINE_WATER, PRISMARINE);
        TRAIT_MATERIALS.put(T_GLOW_NIGHTVISION, GLOW);
    }

    /**
     * Max vanilla level per multi-level trait. NOT blended (all ingredients of the
     * trait's material) -> this level; blended (partial) -> 1. Single-level traits
     * (set bonuses, toggles) are absent = level 1 only.
     */
    private static final Map<String, Integer> TRAIT_MAX_LEVEL = new HashMap<>();
    static {
        TRAIT_MAX_LEVEL.put(T_SLIME_SWORD, 2);            // vanilla Knockback max
        TRAIT_MAX_LEVEL.put(T_CACTUS_ARMOR, 3);           // vanilla Thorns max
        TRAIT_MAX_LEVEL.put(T_LAPIS_FORTUNE, 3);          // vanilla Fortune max
        TRAIT_MAX_LEVEL.put(T_LAPIS_LOOTING, 3);          // vanilla Looting max
        TRAIT_MAX_LEVEL.put(T_QUARTZ_SHARPNESS, 5);       // vanilla Sharpness max
        TRAIT_MAX_LEVEL.put(T_ICE_FROSTWALKER, 2);        // vanilla Frost Walker max
        TRAIT_MAX_LEVEL.put(T_SOUL_SOULSPEED, 3);         // vanilla Soul Speed max
        TRAIT_MAX_LEVEL.put(T_FEATHER_FEATHERFALL, 4);    // vanilla Feather Falling max
        TRAIT_MAX_LEVEL.put(T_FIERY_SMELT, 1);            // auto-smelt has no vanilla enchant; keep 1
        TRAIT_MAX_LEVEL.put(T_FIERY_FIREASPECT, 2);       // vanilla Fire Aspect max
        TRAIT_MAX_LEVEL.put(T_REDSTONE_HASTE, 2);         // vanilla Haste effect max obtainable in survival (beacon conduit-tier)
        TRAIT_MAX_LEVEL.put(T_SUGAR_SPEED, 2);            // vanilla Speed beacon max (no enchant); cap at 2
        TRAIT_MAX_LEVEL.put(T_EMERALD_LOOT, 3);           // vanilla Fortune/Looting max
    }

    /**
     * Whether a trait can exist on this item type at all. Traits are strictly
     * per category: smelt only on mining tools, fire aspect only on swords,
     * set bonuses only on armor, walker/enchant traits only on boots/helmets.
     */
    public static boolean appliesToItem(String traitId, ItemStack stack) {
        String t = toolType(stack);
        boolean sword = t.equals("sword");
        boolean armor = t.equals("helmet") || t.equals("chestplate") || t.equals("leggings") || t.equals("boots");
        boolean miningTool = t.equals("pickaxe") || t.equals("axe") || t.equals("shovel") || t.equals("hoe");
        boolean handTool = miningTool || sword;
        return switch (traitId) {
            case T_SLIME_SWORD -> sword;
            case T_SLIME_BOOTS -> t.equals("boots");
            case T_CACTUS_ARMOR -> armor;
            case T_LAPIS_FORTUNE -> miningTool;
            case T_LAPIS_LOOTING -> sword;
            case T_QUARTZ_SHARPNESS -> sword;
            case T_ICE_FROSTWALKER -> t.equals("boots");
            case T_SOUL_SOULSPEED -> t.equals("boots");
            case T_FEATHER_FEATHERFALL -> t.equals("boots");
            case T_FIERY_SMELT -> miningTool;
            case T_FIERY_FIREASPECT -> sword;
            case T_FIERY_ARMOR -> armor;
            case T_REDSTONE_HASTE -> handTool;
            case T_SUGAR_SPEED -> handTool || armor;
            case T_EMERALD_LOOT -> handTool || armor;
            case T_PRISMARINE_WATER -> t.equals("helmet");
            case T_GLOW_NIGHTVISION -> t.equals("helmet");
            default -> true;
        };
    }

    /** True if the stack's trait list contains traits that cannot exist on this item type. */
    public static boolean hasNonApplicableTraits(ItemStack stack) {
        for (String id : readEffects(stack).keySet()) {
            if (!appliesToItem(id, stack)) return true;
        }
        return false;
    }

    /**
     * Backward compat: old items can carry emerald trait levels from the
     * piece-count era (e.g. Looting II on a 2-emerald-block sword). Normalizes
     * hand tools to the current rule: full emerald -> 3, blended -> 1.
     * Returns true if the stack was changed.
     */
    public static boolean normalizeEmeraldTrait(ItemStack stack) {
        try {
            if (stack.isEmpty()) return false;
            String t = toolType(stack);
            if (!t.matches("^(sword|pickaxe|axe|shovel|hoe)$")) return false;
            Map<String, Integer> levels = readEffects(stack);
            int lvl = levels.getOrDefault(T_EMERALD_LOOT, 0);
            if (lvl == 0 || lvl == 1 || lvl == 3) return false;
            // level 2 (or anything weird): decide by checking the recorded materials
            var custom = stack.get(DataComponents.CUSTOM_DATA);
            if (custom == null) return false;
            var tag = custom.copyTag();
            ListTag head = tag.getListOrEmpty("blended_head");
            ListTag all = tag.getListOrEmpty("blended_ingredients");
            ListTag source = head.isEmpty() ? all : head;
            if (source.isEmpty()) return false;
            int emerald = 0;
            int total = 0;
            for (int i = 0; i < source.size(); i++) {
                String idStr = source.getString(i).orElse("");
                if (idStr.isEmpty()) continue;
                total++;
                String p = idStr.contains(":") ? idStr.substring(idStr.indexOf(':') + 1) : idStr;
                if (EMERALD.contains(p)) emerald++;
            }
            if (total == 0 || emerald == 0) return false;
            boolean fullEmerald = emerald >= total;
            levels.put(T_EMERALD_LOOT, fullEmerald ? 3 : 1);
            writeEffects(stack, levels);
            return true;
        } catch (Exception e) {
            LOGGER.debug("normalizeEmeraldTrait failed: {}", e.toString());
            return false;
        }
    }

    /**
     * Backward compat: re-level every multi-level trait to the current rule
     * (all ingredients of the trait's material -> vanilla max level, blended -> 1).
     * Fixes items from the piece-count era, e.g. Feather Falling 3 on full-feather
     * boots (now 4) or Fire Aspect 1 on a blended fiery sword (stays 1).
     * Returns true if the stack was changed.
     */
    public static boolean normalizeTraitLevels(ItemStack stack) {
        try {
            if (stack.isEmpty()) return false;
            Map<String, Integer> levels = readEffects(stack);
            if (levels.isEmpty()) return false;
            var custom = stack.get(DataComponents.CUSTOM_DATA);
            if (custom == null) return false;
            var tag = custom.copyTag();
            ListTag head = tag.getListOrEmpty("blended_head");
            ListTag all = tag.getListOrEmpty("blended_ingredients");
            ListTag source = head.isEmpty() ? all : head;
            if (source.isEmpty()) return false;
            // count material family membership per trait
            Map<String, Integer> counts = new HashMap<>();
            int total = 0;
            for (int i = 0; i < source.size(); i++) {
                String idStr = source.getString(i).orElse("");
                if (idStr.isEmpty()) continue;
                total++;
                String p = idStr.contains(":") ? idStr.substring(idStr.indexOf(':') + 1) : idStr;
                for (Map.Entry<String, Set<String>> trait : TRAIT_MATERIALS.entrySet()) {
                    if (trait.getValue().contains(p)) counts.merge(trait.getKey(), 1, Integer::sum);
                }
            }
            if (total == 0) return false;
            boolean changed = false;
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                String traitId = e.getKey();
                int lvl = levels.getOrDefault(traitId, 0);
                if (lvl == 0) continue;
                int max = TRAIT_MAX_LEVEL.getOrDefault(traitId, 1);
                if (max <= 1) continue;
                int want = e.getValue() >= total ? max : 1;
                if (lvl != want) {
                    levels.put(traitId, want);
                    changed = true;
                }
            }
            if (changed) writeEffects(stack, levels);
            return changed;
        } catch (Exception e) {
            LOGGER.debug("normalizeTraitLevels failed: {}", e.toString());
            return false;
        }
    }

    /** Human-readable description lines for a stack (from its blended_effects). */
    public static List<String> describe(ItemStack stack) {
        List<String> out = new ArrayList<>();
        Map<String, Integer> levels = readEffects(stack);
        for (Map.Entry<String, Integer> e : levels.entrySet()) {
            int lvl = e.getValue();
            switch (e.getKey()) {
                case T_SLIME_SWORD -> out.add("Bouncy Blade: Knockback " + roman(lvl));
                case T_SLIME_BOOTS -> out.add("Bouncy: no fall damage, jump like a slime block");
                case T_CACTUS_ARMOR -> out.add("Prickly: Thorns " + roman(lvl));
                case T_LAPIS_FORTUNE -> out.add("Enchanting Fortune: Fortune " + roman(lvl));
                case T_LAPIS_LOOTING -> out.add("Enchanting Fortune: Looting " + roman(lvl));
                case T_QUARTZ_SHARPNESS -> out.add("Razor Edge: Sharpness " + roman(lvl));
                case T_ICE_FROSTWALKER -> out.add("Frozen Sole: Frost Walker " + roman(lvl));
                case T_SOUL_SOULSPEED -> out.add("Soulbound Stride: Soul Speed " + roman(lvl));
                case T_FEATHER_FEATHERFALL -> out.add("Featherweight: Feather Falling " + roman(lvl) + " (boots)");
                case T_FIERY_SMELT -> out.add("Blazing Harvest: auto-smelts mined blocks (stacks with Fortune)");
                case T_FIERY_FIREASPECT -> out.add("Scorching Blade: Fire Aspect " + roman(lvl));
                case T_FIERY_ARMOR -> out.add("Molten Skin: Fire Resistance (full fiery set)");
                case T_REDSTONE_HASTE -> out.add("Energized: Haste " + roman(lvl) + " while held");
                case T_SUGAR_SPEED -> out.add("Sugar Rush: Speed " + roman(lvl) + " while held/worn");
                case T_EMERALD_LOOT -> {
                    if (isArmor(stack)) out.add("Lucky Charm: Luck while worn");
                    else out.add("Enchanting Fortune: " + (isSword(stack) ? "Looting " : "Fortune ") + roman(lvl));
                }
                case T_PRISMARINE_WATER -> out.add("Oceanborn: Water Breathing while worn");
                case T_GLOW_NIGHTVISION -> out.add("Luminous: Night Vision while worn");
                default -> out.add(e.getKey());
            }
        }
        return out;
    }

    private static String roman(int lvl) {
        return switch (lvl) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> String.valueOf(lvl);
        };
    }

    private static boolean isArmor(ItemStack stack) {
        String t = toolType(stack);
        return t.equals("helmet") || t.equals("chestplate") || t.equals("leggings") || t.equals("boots");
    }

    private static boolean isSword(ItemStack stack) {
        return toolType(stack).equals("sword");
    }

    private static String toolType(ItemStack stack) {
        String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return path.startsWith("blended_") ? path.substring(8) : path;
    }

    // ------------------------------------------------------------------
    // Compute + persist traits at craft time (called from ShapedRecipeMixin)
    // ------------------------------------------------------------------

    /**
     * Computes traits from ingredients and stores them in the stack's custom data.
     * ingredients: the HEAD stacks for tools, ALL stacks for armor.
     * Effect tier = how many pieces are made of the material (1 piece = tier 1, 2 = tier 2, 3+ = tier 3).
     */
    public static void applyToStack(ItemStack result, List<ItemStack> ingredients) {
        try {
            if (result.isEmpty() || ingredients == null || ingredients.isEmpty()) return;
            Map<String, Integer> counts = new HashMap<>();
            int total = 0;
            for (ItemStack s : ingredients) {
                if (s.isEmpty()) continue;
                total++;
                String p = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
                for (Map.Entry<String, Set<String>> trait : TRAIT_MATERIALS.entrySet()) {
                    if (trait.getValue().contains(p)) counts.merge(trait.getKey(), 1, Integer::sum);
                }
            }
            if (counts.isEmpty()) return;

            // Emerald rule for hand tools: full emerald (every ingredient emerald/emerald
            // block) -> trait at max level 3. Blended (partial emerald) -> trait level 1.
            boolean handTool = toolType(result).matches("^(sword|pickaxe|axe|shovel|hoe)$");
            int emeraldCount = counts.getOrDefault(T_EMERALD_LOOT, 0);
            boolean fullEmerald = handTool && total > 0 && emeraldCount >= total;

            Map<String, Integer> levels = readEffects(result);
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                int tier = Math.max(1, Math.min(3, e.getValue()));
                levels.merge(e.getKey(), tier, Math::max);
            }
            if (handTool && emeraldCount > 0) {
                levels.put(T_EMERALD_LOOT, fullEmerald ? 3 : 1);
            }
            // General rule for every multi-level trait: NOT blended (every ingredient is
            // of that trait's material family) -> max vanilla level; blended (partial) -> 1.
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                String traitId = e.getKey();
                int max = TRAIT_MAX_LEVEL.getOrDefault(traitId, 1);
                if (max <= 1) continue;
                boolean fullMaterial = total > 0 && e.getValue() >= total;
                levels.put(traitId, fullMaterial ? max : 1);
            }
            // Drop traits that make no sense on this item type (e.g. no auto-smelt on swords,
            // no fire aspect on armor) so each trait only appears on its intended item
            levels.entrySet().removeIf(e -> !appliesToItem(e.getKey(), result));
            writeEffects(result, levels);
            syncAttributeModifiers(result);
        } catch (Exception e) {
            LOGGER.debug("applyToStack failed: {}", e.toString());
        }
    }

    // ------------------------------------------------------------------
    // Slime boots use the vanilla BOUNCINESS attribute so vanilla's bounce
    // physics run identically on client (prediction) and server - this is what
    // makes the boing feel right. BouncinessMixin gates WHEN vanilla may use
    // the value (only real landings of 3+ blocks) to avoid wall-flips and
    // micro-hop bounces.
    // ------------------------------------------------------------------

    private static final net.minecraft.resources.Identifier SLIME_BOUNCE_ID =
            com.superfurias.blendedcraft.BlendedCraft.id("slime_bounce");

    /** Adds/removes the BOUNCINESS attribute modifier so it matches the stack's traits. */
    public static void syncAttributeModifiers(ItemStack stack) {
        try {
            if (stack.isEmpty()) return;
            boolean wantBounce = effectLevel(stack, T_SLIME_BOOTS) > 0;
            var existing = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
            boolean hasBounce = false;
            var builder = net.minecraft.world.item.component.ItemAttributeModifiers.builder();
            if (existing != null) {
                for (var e : existing.modifiers()) {
                    if (e.attribute().value() == net.minecraft.world.entity.ai.attributes.Attributes.BOUNCINESS.value()) {
                        if (e.modifier().is(SLIME_BOUNCE_ID)) {
                            hasBounce = true;
                            if (wantBounce) builder.add(e.attribute(), e.modifier(), e.slot());
                        } else {
                            builder.add(e.attribute(), e.modifier(), e.slot());
                        }
                        continue;
                    }
                    builder.add(e.attribute(), e.modifier(), e.slot());
                }
            }
            if (wantBounce && !hasBounce) {
                builder.add(
                        net.minecraft.world.entity.ai.attributes.Attributes.BOUNCINESS,
                        new net.minecraft.world.entity.ai.attributes.AttributeModifier(SLIME_BOUNCE_ID, 1.0,
                                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
                        net.minecraft.world.entity.EquipmentSlotGroup.FEET);
            }
            var built = builder.build();
            if (existing == null || !built.equals(existing) || (wantBounce && !hasBounce)) {
                stack.set(DataComponents.ATTRIBUTE_MODIFIERS, built);
            }
        } catch (Exception e) {
            LOGGER.debug("syncAttributeModifiers failed: {}", e.toString());
        }
    }

    // ------------------------------------------------------------------
    // Read / write blended_effects
    // ------------------------------------------------------------------

    public static Map<String, Integer> readEffects(ItemStack stack) {
        Map<String, Integer> out = new HashMap<>();
        try {
            var custom = stack.get(DataComponents.CUSTOM_DATA);
            if (custom == null || custom.isEmpty()) return out;
            var tag = custom.copyTag();
            ListTag list = tag.getListOrEmpty("blended_effects");
            for (int i = 0; i < list.size(); i++) {
                var compoundOpt = list.getCompound(i);
                if (compoundOpt.isEmpty()) continue;
                var compound = compoundOpt.get();
                String id = compound.getStringOr("id", "");
                int lvl = compound.getIntOr("lvl", 1);
                if (!id.isEmpty()) out.put(id, lvl);
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static void writeEffects(ItemStack stack, Map<String, Integer> levels) {
        try {
            var custom = stack.get(DataComponents.CUSTOM_DATA);
            if (custom == null || custom.isEmpty()) return;
            var tag = custom.copyTag();
            ListTag list = new ListTag();
            for (Map.Entry<String, Integer> e : levels.entrySet()) {
                var c = new net.minecraft.nbt.CompoundTag();
                c.putString("id", e.getKey());
                c.putInt("lvl", e.getValue());
                list.add(c);
            }
            tag.put("blended_effects", list);
            stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        } catch (Exception e) {
            LOGGER.debug("writeEffects failed: {}", e.toString());
        }
    }

    public static int effectLevel(ItemStack stack, String traitId) {
        return readEffects(stack).getOrDefault(traitId, 0);
    }

    public static boolean hasEffect(ItemStack stack, String traitId) {
        return effectLevel(stack, traitId) > 0;
    }

    // ------------------------------------------------------------------
    // Server tick: held / worn effect traits
    // ------------------------------------------------------------------

    /** Called once per server tick. */
    public static void tick(MinecraftServer server) {
        try {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                // held (main hand) traits
                ItemStack main = player.getMainHandItem();
                int haste = effectLevel(main, T_REDSTONE_HASTE);
                if (haste > 0) applyEffect(player, MobEffects.HASTE, haste - 1);
                int sugar = effectLevel(main, T_SUGAR_SPEED);
                if (sugar > 0) applyEffect(player, MobEffects.SPEED, sugar - 1);
                // worn traits
                int sugarArmor = 0;
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    ItemStack piece = player.getItemBySlot(slot);
                    sugarArmor = Math.max(sugarArmor, effectLevel(piece, T_SUGAR_SPEED));
                }
                if (sugarArmor > 0) applyEffect(player, MobEffects.SPEED, sugarArmor - 1);

                ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
                if (effectLevel(helmet, T_PRISMARINE_WATER) > 0) applyEffect(player, MobEffects.WATER_BREATHING, 0);
                if (effectLevel(helmet, T_GLOW_NIGHTVISION) > 0) applyEffect(player, MobEffects.NIGHT_VISION, 0);

                // full fiery set -> fire resistance
                boolean fullFiery = true;
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    if (effectLevel(player.getItemBySlot(slot), T_FIERY_ARMOR) <= 0) { fullFiery = false; break; }
                }
                if (fullFiery) applyEffect(player, MobEffects.FIRE_RESISTANCE, 0);

                // emerald armor: Luck (vanilla caps the effect at level 0 = Luck I;
                // higher attribute amplifiers would produce non-vanilla Luck II/III)
                int luck = 0;
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    luck = Math.max(luck, effectLevel(player.getItemBySlot(slot), T_EMERALD_LOOT));
                }
                if (luck > 0) applyEffect(player, MobEffects.LUCK, 0);
            }
        } catch (Exception e) {
            LOGGER.debug("tick failed: {}", e.toString());
        }
    }

    private static void applyEffect(ServerPlayer player, Holder<net.minecraft.world.effect.MobEffect> effect, int amplifier) {
        player.addEffect(new MobEffectInstance(effect, 219, amplifier, true, false, true));
    }

    // ------------------------------------------------------------------
    // Slime boots bounce (used by LivingEntityMixin)
    // ------------------------------------------------------------------

    private static final Map<UUID, Object[]> LOOTING_HITS = new HashMap<>();

    /** Records that the victim was recently hit by a Looting sword of the given tier. */
    public static void recordLootingHit(java.util.UUID victim, int tier) {
        LOOTING_HITS.put(victim, new Object[]{tier, System.currentTimeMillis() + 5000});
        if (LOOTING_HITS.size() > 512) {
            long now = System.currentTimeMillis();
            LOOTING_HITS.entrySet().removeIf(e -> ((long) e.getValue()[1]) < now);
        }
    }

    /** Returns the recorded Looting tier (0 if none/expired) and clears the entry. */
    public static int takeLootingTier(java.util.UUID victim) {
        Object[] entry = LOOTING_HITS.get(victim);
        if (entry == null) return 0;
        LOOTING_HITS.remove(victim);
        if ((long) entry[1] < System.currentTimeMillis()) return 0;
        return (int) entry[0];
    }

    public static boolean hasSlimeBoots(LivingEntity entity) {
        ItemStack boots = entity.getItemBySlot(EquipmentSlot.FEET);
        return !boots.isEmpty() && effectLevel(boots, T_SLIME_BOOTS) > 0;
    }
}
