package com.example.client;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class BlendedTextureManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("blendedcraft/BlendedTextureManager");
    private static final Map<String, Identifier> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> GENERATING = new ConcurrentHashMap<>();
    private static final Map<Identifier, NativeImage> IMAGE_CACHE = new ConcurrentHashMap<>();
    public static NativeImage getCachedImage(Identifier id) { return IMAGE_CACHE.get(id); }

    public static Identifier getOrCreateBlendedTexture(ItemStack stack) {
        if (stack.isEmpty()) return null;
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null || custom.isEmpty()) return null;
        var tag = custom.copyTag();
        if (!tag.contains("blended_ingredients") && !tag.contains("blended_head") && !tag.contains("blended_handle")) return null;
        ListTag allList = tag.getListOrEmpty("blended_ingredients");
        ListTag headList = tag.getListOrEmpty("blended_head");
        ListTag handleList = tag.getListOrEmpty("blended_handle");
        List<String> allIds = new ArrayList<>();
        for (int i = 0; i < allList.size(); i++) allList.getString(i).ifPresent(allIds::add);
        List<String> headIds = new ArrayList<>();
        for (int i = 0; i < headList.size(); i++) headList.getString(i).ifPresent(headIds::add);
        List<String> handleIds = new ArrayList<>();
        for (int i = 0; i < handleList.size(); i++) handleList.getString(i).ifPresent(handleIds::add);
        if (allIds.isEmpty() && headIds.isEmpty() && handleIds.isEmpty()) return null;
        if (headIds.isEmpty() && !allIds.isEmpty()) headIds = new ArrayList<>(allIds);

        // Position-aware: read stored grid positions
        ListTag headPosTag = tag.getListOrEmpty("blended_head_pos");
        List<Integer> headPos = new ArrayList<>();
        for (int i = 0; i < headPosTag.size(); i++) {
            Tag t = headPosTag.get(i);
            if (t instanceof IntTag it) headPos.add(it.value());
            else {
                // Fallback try getInt via optional
                try { var opt = headPosTag.getInt(i); if (opt.isPresent()) headPos.add(opt.get()); } catch (Exception ignored) { LOGGER.trace("Ignored", ignored); }
            }
        }
        ListTag handlePosTag = tag.getListOrEmpty("blended_handle_pos");
        List<Integer> handlePos = new ArrayList<>();
        for (int i = 0; i < handlePosTag.size(); i++) {
            Tag t = handlePosTag.get(i);
            if (t instanceof IntTag it) handlePos.add(it.value());
            else {
                try { var opt = handlePosTag.getInt(i); if (opt.isPresent()) handlePos.add(opt.get()); } catch (Exception ignored) { LOGGER.trace("Ignored", ignored); }
            }
        }
        int pW = 3, pH = 3;
        try { var ow = tag.getInt("blended_pattern_width"); if (ow.isPresent()) pW = ow.get(); } catch (Exception ignored) { LOGGER.trace("Ignored", ignored); }
        try { var oh = tag.getInt("blended_pattern_height"); if (oh.isPresent()) pH = oh.get(); } catch (Exception ignored) { LOGGER.trace("Ignored", ignored); }
        String posKey = tag.getString("blended_pos_key").orElse("");

        String resultIdStr = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        // Position-aware key: head/handle order matters now, plus explicit positions
        String headKey = String.join("+", headIds);
        String handleKey = String.join("+", handleIds);
        String allKey = String.join("+", allIds);
        String key;
        if (!posKey.isEmpty() || !headPos.isEmpty() || !handlePos.isEmpty()) {
            String headPosStr = headPos.isEmpty() ? "" : headPos.toString();
            String handlePosStr = handlePos.isEmpty() ? "" : handlePos.toString();
            key = resultIdStr + "|head:" + headKey + "|hpos:" + headPosStr + "|handle:" + handleKey + "|hposH:" + handlePosStr + "|p:" + pW + "x" + pH + "|posKey:" + posKey + "|all:" + allKey;
        } else {
            // Fallback legacy (sorted) for old items without pos
            List<String> sortedAll = new ArrayList<>(allIds); sortedAll.sort(String::compareTo);
            List<String> sortedHead = new ArrayList<>(headIds); sortedHead.sort(String::compareTo);
            List<String> sortedHandle = new ArrayList<>(handleIds); sortedHandle.sort(String::compareTo);
            key = resultIdStr + "|head:" + String.join("+", sortedHead) + "|handle:" + String.join("+", sortedHandle) + "|all:" + String.join("+", sortedAll);
        }
        if (CACHE.containsKey(key)) {
            return CACHE.get(key);
        }
        if (GENERATING.putIfAbsent(key, Boolean.TRUE) != null) {
            return CACHE.get(key);
        }
        try {
            Identifier generated = generateBlendedTexture(key, headIds, handleIds, headPos, handlePos, pW, pH, allIds, stack, tag);
            if (generated != null) {
                CACHE.put(key, generated);
            }
            return generated;
        } finally {
            GENERATING.remove(key);
        }
    }

    private static Identifier generateBlendedTexture(String key, List<String> headIdsRaw, List<String> handleIdsRaw, List<Integer> headPosRaw, List<Integer> handlePosRaw, int pW, int pH, List<String> allIds, ItemStack resultStack, CompoundTag tag) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;
        var rm = mc.getResourceManager();
        NativeImage baseMask = loadBaseMask(rm, resultStack);

        List<String> headIds = headIdsRaw.isEmpty() ? allIds : headIdsRaw;
        List<String> handleIds = handleIdsRaw;

        if (headIds.isEmpty() && handleIds.isEmpty()) {
            if (baseMask != null) try { baseMask.close(); } catch (Exception ignored) { LOGGER.trace("Ignored", ignored); }
            return null;
        }

        // Load unique images
        Map<String, NativeImage> headUnique = new HashMap<>();
        Map<String, int[]> headBboxMap = new HashMap<>();
        Map<String, Integer> headAvgMap = new HashMap<>();
        Map<String, Boolean> headSmallMap = new HashMap<>();
        for (String id : headIds) {
            if (headUnique.containsKey(id)) continue;
            NativeImage img = loadImageForId(id, rm);
            if (img == null) {
                LOGGER.debug("Texture missing for {} (key {}), using fallback color", id, key);
                img = createFallbackImage(id);
            }
            headUnique.put(id, img);
            headBboxMap.put(id, computeBbox(img));
            headAvgMap.put(id, computeAverageForImage(img));
            headSmallMap.put(id, isSmallImage(img));
        }
        Map<String, NativeImage> handleUnique = new HashMap<>();
        Map<String, int[]> handleBboxMap = new HashMap<>();
        Map<String, Integer> handleAvgMap = new HashMap<>();
        Map<String, Boolean> handleSmallMap = new HashMap<>();
        for (String id : handleIds) {
            if (handleUnique.containsKey(id)) continue;
            NativeImage img = loadImageForId(id, rm);
            if (img == null) {
                LOGGER.debug("Texture missing for {} (key {}), using fallback color", id, key);
                img = createFallbackImage(id);
            }
            handleUnique.put(id, img);
            handleBboxMap.put(id, computeBbox(img));
            handleAvgMap.put(id, computeAverageForImage(img));
            handleSmallMap.put(id, isSmallImage(img));
        }

        if (headUnique.isEmpty() && handleUnique.isEmpty()) {
            LOGGER.warn("No textures found for key {} (fallback failed)", key);
            if (baseMask != null) try { baseMask.close(); } catch (Exception ignored) { LOGGER.trace("Ignored", ignored); }
            return null;
        }

        // Per-instance lists (preserve grid order, not sorted)
        List<String> headInstanceIds = new ArrayList<>(headIds);
        List<NativeImage> headInstanceImgs = new ArrayList<>();
        List<int[]> headInstanceBboxes = new ArrayList<>();
        List<Integer> headInstanceAvgs = new ArrayList<>();
        List<Boolean> headInstanceSmalls = new ArrayList<>();
        List<Integer> headInstancePos = new ArrayList<>();
        for (int i = 0; i < headInstanceIds.size(); i++) {
            String id = headInstanceIds.get(i);
            NativeImage img = headUnique.get(id);
            if (img == null) {
                if (!headUnique.isEmpty()) {
                    String any = headUnique.keySet().iterator().next();
                    img = headUnique.get(any);
                    id = any;
                } else continue;
            }
            headInstanceImgs.add(img);
            headInstanceBboxes.add(headBboxMap.getOrDefault(id, new int[]{0,0,15,15}));
            headInstanceAvgs.add(headAvgMap.getOrDefault(id, 0xFFFFFFFF));
            headInstanceSmalls.add(headSmallMap.getOrDefault(id, false));
            int pos = (i < headPosRaw.size()) ? headPosRaw.get(i) : -1;
            headInstancePos.add(pos);
        }
        List<String> handleInstanceIds = new ArrayList<>(handleIds);
        List<NativeImage> handleInstanceImgs = new ArrayList<>();
        List<int[]> handleInstanceBboxes = new ArrayList<>();
        List<Integer> handleInstanceAvgs = new ArrayList<>();
        List<Boolean> handleInstanceSmalls = new ArrayList<>();
        List<Integer> handleInstancePos = new ArrayList<>();
        for (int i = 0; i < handleInstanceIds.size(); i++) {
            String id = handleInstanceIds.get(i);
            NativeImage img = handleUnique.get(id);
            if (img == null) {
                if (!handleUnique.isEmpty()) {
                    String any = handleUnique.keySet().iterator().next();
                    img = handleUnique.get(any);
                    id = any;
                } else continue;
            }
            handleInstanceImgs.add(img);
            handleInstanceBboxes.add(handleBboxMap.getOrDefault(id, new int[]{0,0,15,15}));
            handleInstanceAvgs.add(handleAvgMap.getOrDefault(id, 0xFFFFFFFF));
            handleInstanceSmalls.add(handleSmallMap.getOrDefault(id, false));
            int pos = (i < handlePosRaw.size()) ? handlePosRaw.get(i) : -1;
            handleInstancePos.add(pos);
        }

        // Compute masks for handle independence (with 1px dark-outline dilation, as in log-success version)
        boolean[][] handleFinalMask = new boolean[16][16];
        int headMinX = 16, headMinY = 16, headMaxX = -1, headMaxY = -1;
        int handleMinX = 16, handleMinY = 16, handleMaxX = -1, handleMaxY = -1;
        boolean[][] isHandleInterior = new boolean[16][16];
        if (baseMask != null) {
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int argb = baseMask.getPixel(x, y);
                    int a = (argb >> 24) & 0xFF;
                    if (a < 10) continue;
                    boolean isHandle = !handleInstanceIds.isEmpty() && isHandlePixel(argb);
                    if (isHandle) {
                        isHandleInterior[y][x] = true;
                        handleFinalMask[y][x] = true;
                        if (x < handleMinX) handleMinX = x;
                        if (y < handleMinY) handleMinY = y;
                        if (x > handleMaxX) handleMaxX = x;
                        if (y > handleMaxY) handleMaxY = y;
                    } else {
                        if (x < headMinX) headMinX = x;
                        if (y < headMinY) headMinY = y;
                        if (x > headMaxX) headMaxX = x;
                        if (y > headMaxY) headMaxY = y;
                    }
                }
            }
            if (handleMaxX >= 0) {
                boolean[][] dilated = new boolean[16][16];
                for (int y = 0; y < 16; y++) System.arraycopy(handleFinalMask[y], 0, dilated[y], 0, 16);
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) if (isHandleInterior[y][x]) {
                        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx < 0 || nx >= 16 || ny < 0 || ny >= 16) continue;
                            if (dilated[ny][nx]) continue;
                            if (handleFinalMask[ny][nx]) continue;
                            int nArgb = baseMask.getPixel(nx, ny);
                            int nA = (nArgb >> 24) & 0xFF;
                            if (nA < 10) continue;
                            int r = (nArgb >> 16) & 0xFF, g = (nArgb >> 8) & 0xFF, b = nArgb & 0xFF;
                            int brightness = (r + g + b) / 3;
                            if (brightness > 90 && !isHandlePixel(nArgb)) continue;
                            if (nx < handleMinX - 1 || nx > handleMaxX + 1 || ny < handleMinY - 1 || ny > handleMaxY + 1) continue;
                            dilated[ny][nx] = true;
                        }
                    }
                }
                handleFinalMask = dilated;
                handleMinX = 16; handleMinY = 16; handleMaxX = -1; handleMaxY = -1;
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) if (handleFinalMask[y][x]) {
                    if (x < handleMinX) handleMinX = x;
                    if (y < handleMinY) handleMinY = y;
                    if (x > handleMaxX) handleMaxX = x;
                    if (y > handleMaxY) handleMaxY = y;
                }
                headMinX = 16; headMinY = 16; headMaxX = -1; headMaxY = -1;
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                    int a = (baseMask.getPixel(x,y) >> 24) & 0xFF;
                    if (a < 10) continue;
                    if (handleFinalMask[y][x]) continue;
                    if (x < headMinX) headMinX = x;
                    if (y < headMinY) headMinY = y;
                    if (x > headMaxX) headMaxX = x;
                    if (y > headMaxY) headMaxY = y;
                }
            }
        }
        if (headMaxX < 0) { headMinX = 0; headMinY = 0; headMaxX = 15; headMaxY = 15; }
        if (handleMaxX < 0 && !handleInstanceIds.isEmpty()) { handleMinX = 5; handleMinY = 6; handleMaxX = 10; handleMaxY = 15; }

        // Generate position-aware Voronoi seeds
        List<int[]> headSeeds = new ArrayList<>();
        if (!headInstanceIds.isEmpty()) {
            if (!headPosRaw.isEmpty() && headPosRaw.size() == headInstanceIds.size()) {
                // Map each head instance's grid pos (x,y) -> seed within head bbox
                for (int i = 0; i < headInstanceIds.size(); i++) {
                    int pos = headPosRaw.get(i);
                    int gx = pos % 16;
                    int gy = pos / 16;
                    double normX = (gx + 0.5) / Math.max(1, pW);
                    double normY = (gy + 0.5) / Math.max(1, pH);
                    // Clamp gx/gy within pW/pH (in case pos encoding used full 16 stride)
                    // For tools, pW=3, pH=3 -> gx 0..2, gy 0..2
                    normX = Math.max(0, Math.min(0.999, normX));
                    normY = Math.max(0, Math.min(0.999, normY));
                    int w = headMaxX - headMinX + 1;
                    int h = headMaxY - headMinY + 1;
                    if (w <= 0) w = 16;
                    if (h <= 0) h = 16;
                    int sx = headMinX + (int)(normX * (w - 1));
                    int sy = headMinY + (int)(normY * (h - 1));
                    // Add small deterministic jitter to avoid perfect grid alignment (±1)
                    int jitterX = ((key.hashCode() + i * 374761393) & 1) == 0 ? 0 : 1;
                    int jitterY = ((key.hashCode() + i * 668265263) & 1) == 0 ? 0 : 1;
                    sx = Math.max(headMinX, Math.min(headMaxX, sx + jitterX));
                    sy = Math.max(headMinY, Math.min(headMaxY, sy + jitterY));
                    // Ensure seeds not coincident: push if too close to existing
                    for (int attempt = 0; attempt < 3; attempt++) {
                        boolean tooClose = false;
                        for (int[] s : headSeeds) if (Math.abs(s[0]-sx) < 2 && Math.abs(s[1]-sy) < 2) { tooClose = true; break; }
                        if (!tooClose) break;
                        sx = Math.max(headMinX, Math.min(headMaxX, sx + (attempt+1)));
                        sy = Math.max(headMinY, Math.min(headMaxY, sy + (attempt+1)));
                    }
                    headSeeds.add(new int[]{sx, sy});
                }
            } else {
                // Fallback random within head bbox
                Random rnd = new Random(key.hashCode() ^ 0x9E3779B97F4A7C15L);
                for (int i = 0; i < headInstanceIds.size(); i++) {
                    int w = headMaxX - headMinX + 1; int h = headMaxY - headMinY + 1;
                    if (w <= 0) w = 1; if (h <= 0) h = 1;
                    int sx = headMinX + rnd.nextInt(w);
                    int sy = headMinY + rnd.nextInt(h);
                    for (int attempt = 0; attempt < 4; attempt++) {
                        boolean tooClose = false;
                        for (int[] s : headSeeds) if ((s[0]-sx)*(s[0]-sx)+(s[1]-sy)*(s[1]-sy) < 4) { tooClose = true; break; }
                        if (!tooClose) break;
                        sx = headMinX + rnd.nextInt(w);
                        sy = headMinY + rnd.nextInt(h);
                    }
                    headSeeds.add(new int[]{sx, sy});
                }
            }
        }
        List<int[]> handleSeeds = new ArrayList<>();
        if (!handleInstanceIds.isEmpty()) {
            if (!handlePosRaw.isEmpty() && handlePosRaw.size() == handleInstanceIds.size()) {
                for (int i = 0; i < handleInstanceIds.size(); i++) {
                    int pos = handlePosRaw.get(i);
                    int gx = pos % 16;
                    int gy = pos / 16;
                    double normX = (gx + 0.5) / Math.max(1, pW);
                    double normY = (gy + 0.5) / Math.max(1, pH);
                    normX = Math.max(0, Math.min(0.999, normX));
                    normY = Math.max(0, Math.min(0.999, normY));
                    int w = handleMaxX - handleMinX + 1;
                    int h = handleMaxY - handleMinY + 1;
                    if (w <= 0) w = 16; if (h <= 0) h = 16;
                    int sx = handleMinX + (int)(normX * (w - 1));
                    int sy = handleMinY + (int)(normY * (h - 1));
                    sx = Math.max(handleMinX, Math.min(handleMaxX, sx));
                    sy = Math.max(handleMinY, Math.min(handleMaxY, sy));
                    handleSeeds.add(new int[]{sx, sy});
                }
            } else {
                Random rnd = new Random(key.hashCode() ^ 0xBF58476D1CE4E5B9L);
                for (int i = 0; i < handleInstanceIds.size(); i++) {
                    int w = handleMaxX - handleMinX + 1; int h = handleMaxY - handleMinY + 1;
                    if (w <= 0) w = 1; if (h <= 0) h = 1;
                    int sx = handleMinX + rnd.nextInt(w);
                    int sy = handleMinY + rnd.nextInt(h);
                    handleSeeds.add(new int[]{sx, sy});
                }
            }
        }

        NativeImage blended = new NativeImage(16, 16, false);

        if (baseMask == null) {
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                if (headSeeds.isEmpty()) { blended.setPixel(x, y, 0); continue; }
                int bestIdx = findNearestSeed(x, y, headSeeds, key.hashCode());
                NativeImage src = headInstanceImgs.get(bestIdx);
                int[] bbox = headInstanceBboxes.get(bestIdx);
                boolean isSmall = headInstanceSmalls.get(bestIdx);
                int avg = headInstanceAvgs.get(bestIdx);
                int[] seed = headSeeds.get(bestIdx);
                int col = sampleForTool(src, x, y, seed, bbox, isSmall, avg);
                blended.setPixel(x, y, col);
            }
        } else {
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int baseArgb = baseMask.getPixel(x, y);
                int baseA = (baseArgb >> 24) & 0xFF;
                if (baseA < 10) { blended.setPixel(x, y, 0); continue; }
                boolean isHandle = handleFinalMask[y][x];
                List<int[]> seeds = isHandle ? handleSeeds : headSeeds;
                List<NativeImage> imgs = isHandle ? handleInstanceImgs : headInstanceImgs;
                List<int[]> bboxes = isHandle ? handleInstanceBboxes : headInstanceBboxes;
                List<Integer> avgs = isHandle ? handleInstanceAvgs : headInstanceAvgs;
                List<Boolean> smalls = isHandle ? handleInstanceSmalls : headInstanceSmalls;
                if (seeds.isEmpty()) {
                    seeds = headSeeds; imgs = headInstanceImgs; bboxes = headInstanceBboxes; avgs = headInstanceAvgs; smalls = headInstanceSmalls;
                    if (seeds.isEmpty()) { blended.setPixel(x, y, 0); continue; }
                }
                int bestIdx = findNearestSeed(x, y, seeds, key.hashCode() + (isHandle ? 7919 : 0));
                NativeImage src = imgs.get(bestIdx);
                if (src == null) { blended.setPixel(x, y, 0); continue; }
                int[] bbox = bboxes.get(bestIdx);
                boolean isSmall = smalls.get(bestIdx);
                int avg = avgs.get(bestIdx);
                int[] seed = seeds.get(bestIdx);
                int col = sampleForTool(src, x, y, seed, bbox, isSmall, avg);
                // Force 100% opaque inside shape – no semi-transparency aside from outside shape (outside is 0, inside is 0xFF)
                col = 0xFF000000 | (col & 0x00FFFFFF);
                blended.setPixel(x, y, col);
            }
        }

        for (NativeImage img : headUnique.values()) try { img.close(); } catch (Exception ignored) { LOGGER.trace("Ignored", ignored); }
        for (NativeImage img : handleUnique.values()) try { img.close(); } catch (Exception ignored) { LOGGER.trace("Ignored", ignored); }
        if (baseMask != null) try { baseMask.close(); } catch (Exception ignored) { LOGGER.trace("Ignored", ignored); }

        String hash = Integer.toHexString(key.hashCode());
        Identifier outId = Identifier.fromNamespaceAndPath("blendedcraft", "blended/" + hash);
        var tm = mc.getTextureManager();
        try {
            try {
                NativeImage copyFor3D = new NativeImage(blended.getWidth(), blended.getHeight(), false);
                copyFor3D.copyFrom(blended);
                IMAGE_CACHE.put(outId, copyFor3D);
            } catch (Exception e) { LOGGER.debug("Failed to cache copy for 3D for {}: {}", outId, e.toString()); }
            DynamicTexture dyn = new DynamicTexture(() -> outId.toString(), blended);
            tm.register(outId, dyn);
            LOGGER.info("Generated PATCHWORK blended texture {} for key {} (head {} handle {} )", outId, key, headInstanceIds.size(), handleInstanceIds.size());
            return outId;
        } catch (Exception e) {
            LOGGER.error("Failed to register blended texture {}: {}", outId, e.toString());
            try { blended.close(); } catch (Exception ignored) { LOGGER.trace("Ignored", ignored); }
            return null;
        }
    }

    private static int findNearestSeed(int x, int y, List<int[]> seeds, int salt) {
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < seeds.size(); i++) {
            int sx = seeds.get(i)[0];
            int sy = seeds.get(i)[1];
            double dx = x - sx;
            double dy = y - sy;
            double dist = dx*dx + dy*dy;
            int n = (x * 374761393 ^ y * 668265263 ^ i * 1013904223 ^ salt);
            n = (n >>> 1) & 0x7;
            dist += (n - 3) * 1.2;
            if (dist < bestDist) { bestDist = dist; best = i; }
        }
        return best;
    }

    private static int sampleForTool(NativeImage src, int x, int y, int[] seed, int[] bbox, boolean isSmall, int avg) {
        int srcX, srcY;
        if (isSmall) {
            int bw = bbox[2] - bbox[0] + 1;
            int bh = bbox[3] - bbox[1] + 1;
            if (bw <= 0) bw = 16;
            if (bh <= 0) bh = 16;
            if (bbox[0] < 0) bbox[0] = 0;
            if (bbox[1] < 0) bbox[1] = 0;
            srcX = bbox[0] + Math.floorMod(x + seed[0], bw);
            srcY = bbox[1] + Math.floorMod(y + seed[1], bh);
        } else {
            srcX = x;
            srcY = y;
        }
        srcX = Math.max(0, Math.min(15, srcX));
        srcY = Math.max(0, Math.min(15, srcY));
        int col = src.getPixel(srcX, srcY);
        int a = (col >> 24) & 0xFF;
        if (a < 10) {
            int cx = (bbox[0] + bbox[2]) / 2;
            int cy = (bbox[1] + bbox[3]) / 2;
            cx = Math.max(0, Math.min(15, cx));
            cy = Math.max(0, Math.min(15, cy));
            col = src.getPixel(cx, cy);
            a = (col >> 24) & 0xFF;
            if (a < 10) {
                col = avg;
                col = (0xFF << 24) | (col & 0x00FFFFFF);
                return col;
            } else return col;
        }
        return col;
    }

    private static NativeImage loadImageForId(String idStr, net.minecraft.server.packs.resources.ResourceManager rm) {
        NativeImage direct = tryLoadTexture(idStr, rm);
        if (direct != null) return direct;
        // Fallback for items without direct item/block texture (e.g., oak_button -> oak_planks)
        try {
            Identifier itemId = Identifier.parse(idStr);
            String path = itemId.getPath();
            String ns = itemId.getNamespace();
            java.util.List<String> alts = new java.util.ArrayList<>();
            if (path.endsWith("_button")) {
                String base = path.substring(0, path.length() - 7);
                alts.add(base + "_planks");
                alts.add(base);
            } else if (path.endsWith("_pressure_plate")) {
                String base = path.substring(0, path.length() - 15);
                alts.add(base + "_planks");
                alts.add(base);
            } else if (path.endsWith("_fence")) {
                String base = path.substring(0, path.length() - 6);
                alts.add(base + "_planks");
                alts.add(base);
            } else if (path.endsWith("_wall")) {
                String base = path.substring(0, path.length() - 5);
                alts.add(base);
            } else if (path.endsWith("_carpet")) {
                String base = path.substring(0, path.length() - 7);
                alts.add(base + "_wool");
                alts.add(base);
            } else if (path.endsWith("_trapdoor")) {
                String base = path.substring(0, path.length() - 9);
                alts.add(base + "_planks");
                alts.add(base);
            } else if (path.endsWith("_door")) {
                String base = path.substring(0, path.length() - 5);
                alts.add(base + "_planks");
                alts.add(base);
            } else if (path.endsWith("_sign")) {
                String base = path.substring(0, path.length() - 5);
                alts.add(base + "_planks");
                alts.add(base);
            }
            for (String altPath : alts) {
                String altId = ns + ":" + altPath;
                NativeImage altImg = tryLoadTexture(altId, rm);
                if (altImg != null) {
                    LOGGER.debug("Resolved fallback texture for {} -> {}", idStr, altId);
                    return altImg;
                }
            }
        } catch (Exception ignored) { LOGGER.trace("Ignored", ignored); }
        return null;
    }

    private static NativeImage tryLoadTexture(String idStr, net.minecraft.server.packs.resources.ResourceManager rm) {
        try {
            Identifier itemId = Identifier.parse(idStr);
            Identifier texItem = Identifier.fromNamespaceAndPath(itemId.getNamespace(), "textures/item/" + itemId.getPath() + ".png");
            Identifier texBlock = Identifier.fromNamespaceAndPath(itemId.getNamespace(), "textures/block/" + itemId.getPath() + ".png");
            Optional<Resource> res = rm.getResource(texItem);
            if (res.isEmpty()) res = rm.getResource(texBlock);
            if (res.isEmpty()) return null;
            try (InputStream in = res.get().open()) {
                NativeImage img = NativeImage.read(in);
                if (img.getWidth() != 16 || img.getHeight() != 16) {
                    NativeImage scaled = new NativeImage(16, 16, false);
                    for (int yy = 0; yy < 16; yy++) for (int xx = 0; xx < 16; xx++) {
                        int srcX = xx * img.getWidth() / 16;
                        int srcY = yy * img.getHeight() / 16;
                        scaled.setPixel(xx, yy, img.getPixel(srcX, srcY));
                    }
                    img.close();
                    return scaled;
                }
                return img;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to load texture for {}: {}", idStr, e.toString());
            return null;
        }
    }

    private static int[] computeBbox(NativeImage img) {
        int minX = 16, minY = 16, maxX = -1, maxY = -1;
        for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) if (((img.getPixel(x, y) >> 24) & 0xFF) > 10) {
            if (x < minX) minX = x; if (y < minY) minY = y; if (x > maxX) maxX = x; if (y > maxY) maxY = y;
        }
        if (maxX < 0) return new int[]{0,0,15,15};
        return new int[]{minX, minY, maxX, maxY};
    }

    private static boolean isSmallImage(NativeImage img) {
        int trans = 0, total = img.getWidth()*img.getHeight();
        for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) if (((img.getPixel(x, y) >> 24) & 0xFF) < 10) trans++;
        return trans*2 > total;
    }

    private static int computeAverageForImage(NativeImage img) {
        long rSum=0,gSum=0,bSum=0; int cnt=0;
        for (int y=0;y<img.getHeight();y++) for (int x=0;x<img.getWidth();x++) {
            int argb=img.getPixel(x,y);
            if (((argb>>24)&0xFF)<10) continue;
            rSum+= (argb>>16)&0xFF; gSum+=(argb>>8)&0xFF; bSum+=argb&0xFF; cnt++;
        }
        if (cnt==0) return 0xFFFFFFFF;
        int r=(int)(rSum/cnt), g=(int)(gSum/cnt), b=(int)(bSum/cnt);
        return (0xFF<<24)|(r<<16)|(g<<8)|b;
    }

    private static boolean isHandlePixel(int baseArgb) {
        int a = (baseArgb >> 24) & 0xFF;
        if (a < 10) return false;
        int r = (baseArgb >> 16) & 0xFF, g = (baseArgb >> 8) & 0xFF, b = baseArgb & 0xFF;
        if (r < 90 || b >= 90) return false;
        if (r <= g || g <= b) return false;
        if (r - b < 30) return false;
        if (Math.abs(r - g) < 10 && Math.abs(g - b) < 10) return false;
        return true;
    }

    private static NativeImage loadBaseMask(net.minecraft.server.packs.resources.ResourceManager rm, ItemStack stack) {
        try {
            String resultPath = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            String suffix = resultPath.startsWith("blended_") ? resultPath.substring(8) : resultPath;
            String[] prefixes = {"iron_", "diamond_", "netherite_", "gold_", "copper_", "leather_", "chain_", ""};
            for (String p : prefixes) {
                String tryPath = p + suffix;
                Identifier cand = Identifier.fromNamespaceAndPath("minecraft", "textures/item/" + tryPath + ".png");
                Optional<Resource> res = rm.getResource(cand);
                if (res.isPresent()) {
                    try (InputStream in = res.get().open()) {
                        NativeImage img = NativeImage.read(in);
                        if (img.getWidth() != 16 || img.getHeight() != 16) {
                            NativeImage scaled = new NativeImage(16, 16, false);
                            for (int yy = 0; yy < 16; yy++) for (int xx = 0; xx < 16; xx++) {
                                int srcX = xx * img.getWidth() / 16; int srcY = yy * img.getHeight() / 16;
                                scaled.setPixel(xx, yy, img.getPixel(srcX, srcY));
                            }
                            img.close(); return scaled;
                        }
                        return img;
                    } catch (Exception ignored) { LOGGER.trace("Ignored", ignored); }
                }
            }
            Identifier modTex = Identifier.fromNamespaceAndPath("blendedcraft", "textures/item/" + resultPath + ".png");
            Optional<Resource> modRes = rm.getResource(modTex);
            if (modRes.isPresent()) {
                try (InputStream in = modRes.get().open()) {
                    NativeImage img = NativeImage.read(in);
                    if (img.getWidth() != 16 || img.getHeight() != 16) {
                        NativeImage scaled = new NativeImage(16, 16, false);
                        for (int yy = 0; yy < 16; yy++) for (int xx = 0; xx < 16; xx++) {
                            int srcX = xx * img.getWidth() / 16; int srcY = yy * img.getHeight() / 16;
                            scaled.setPixel(xx, yy, img.getPixel(srcX, srcY));
                        }
                        img.close(); return scaled;
                    }
                    return img;
                } catch (Exception ignored) { LOGGER.trace("Ignored", ignored); }
            }
            Identifier blockTex = Identifier.fromNamespaceAndPath("minecraft", "textures/block/" + suffix + ".png");
            Optional<Resource> blockRes = rm.getResource(blockTex);
            if (blockRes.isPresent()) {
                try (InputStream in = blockRes.get().open()) {
                    NativeImage img = NativeImage.read(in);
                    if (img.getWidth() != 16 || img.getHeight() != 16) {
                        NativeImage scaled = new NativeImage(16, 16, false);
                        for (int yy = 0; yy < 16; yy++) for (int xx = 0; xx < 16; xx++) {
                            int srcX = xx * img.getWidth() / 16; int srcY = yy * img.getHeight() / 16;
                            scaled.setPixel(xx, yy, img.getPixel(srcX, srcY));
                        }
                        img.close(); return scaled;
                    }
                    return img;
                } catch (Exception ignored) { LOGGER.trace("Ignored", ignored); }
            }
        } catch (Exception e) { LOGGER.warn("Failed to load base mask for {}: {}", stack, e.toString()); }
        return null;
    }

    private static NativeImage createFallbackImage(String idStr) {
        int hash = idStr.hashCode();
        int r = (hash >> 16) & 0xFF;
        int g = (hash >> 8) & 0xFF;
        int b = hash & 0xFF;
        r = (r + 128) % 256;
        g = (g + 128) % 256;
        b = (b + 128) % 256;
        // Avoid too dark
        r = Math.max(60, r); g = Math.max(60, g); b = Math.max(60, b);
        NativeImage img = new NativeImage(16, 16, false);
        int col = (0xFF << 24) | (r << 16) | (g << 8) | b;
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) img.setPixel(x, y, col);
        // Add subtle checker to distinguish fallback
        for (int y = 0; y < 16; y += 4) for (int x = 0; x < 16; x += 4) img.setPixel(x, y, (0xFF << 24) | ((r+30)%256 << 16) | ((g+30)%256 << 8) | (b+30)%256);
        return img;
    }

    public static void clearCache() {
        CACHE.clear();
        for (NativeImage img : IMAGE_CACHE.values()) try { img.close(); } catch (Exception ignored) { LOGGER.trace("Ignored", ignored); }
        IMAGE_CACHE.clear();
    }
}
