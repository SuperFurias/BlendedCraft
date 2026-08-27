package com.example.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

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

        return changed;
    }

    public static int upgradeInventory(Player player) {
        int upgraded = 0;
        var inv = player.getInventory();
        // getContainerSize() covers main (36) + armor (4) + offhand (1) = 41 in 1.21
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
