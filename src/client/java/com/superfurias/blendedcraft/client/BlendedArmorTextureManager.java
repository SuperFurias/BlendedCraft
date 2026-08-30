package com.superfurias.blendedcraft.client;

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
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class BlendedArmorTextureManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("blendedcraft/BlendedArmorTextureManager");
    private static final Map<String, Identifier> CACHE = new ConcurrentHashMap<>();

    public static Identifier getOrCreateArmorTexture(ItemStack stack, net.minecraft.world.entity.EquipmentSlot slot) {
        if (stack.isEmpty()) return null;
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) return null;
        var tag = cd.copyTag();
        if (!tag.contains("blended_ingredients")) return null;
        var list = tag.getListOrEmpty("blended_ingredients");
        if (list.isEmpty()) return null;
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) list.getString(i).ifPresent(ids::add);
        if (ids.isEmpty()) return null;

        // Position-aware
        ListTag posTag = tag.getListOrEmpty("blended_ingredients_pos");
        if (posTag.isEmpty()) posTag = tag.getListOrEmpty("blended_head_pos");
        List<Integer> posList = new ArrayList<>();
        for (int i = 0; i < posTag.size(); i++) {
            Tag t = posTag.get(i);
            if (t instanceof IntTag it) posList.add(it.value());
            else {
                try { var opt = posTag.getInt(i); if (opt.isPresent()) posList.add(opt.get()); } catch (Exception ignored) { LOGGER.trace("Ignored", ignored); }
            }
        }
        int pW = 3, pH = 3;
        try { var ow = tag.getInt("blended_pattern_width"); if (ow.isPresent()) pW = ow.get(); } catch (Exception ignored) { LOGGER.trace("Ignored", ignored); }
        try { var oh = tag.getInt("blended_pattern_height"); if (oh.isPresent()) pH = oh.get(); } catch (Exception ignored) { LOGGER.trace("Ignored", ignored); }
        String posKey = tag.getString("blended_pos_key").orElse("");

        String slotKey = slot == null ? "unknown" : slot.getName();
        String key;
        if (!posList.isEmpty() || !posKey.isEmpty()) {
            // Use original order (ids as stored, which is grid order)
            key = String.join("+", ids) + "|pos:" + posList.toString() + "|p:" + pW + "x" + pH + "|armor|" + slotKey + "|posKey:" + posKey;
        } else {
            List<String> sorted = new ArrayList<>(ids); sorted.sort(String::compareTo);
            key = String.join("+", sorted) + "|armor|" + slotKey;
        }
        if (CACHE.containsKey(key)) return CACHE.get(key);
        Identifier gen = generateArmorTexture(key, ids, posList, pW, pH, slot);
        if (gen != null) CACHE.put(key, gen);
        return gen;
    }

    public static Identifier getOrCreateArmorTexture(ItemStack stack) {
        return getOrCreateArmorTexture(stack, null);
    }

    private static Identifier generateArmorTexture(String key, List<String> idsInOrder, List<Integer> posInOrder, int pW, int pH, net.minecraft.world.entity.EquipmentSlot slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;
        var rm = mc.getResourceManager();
        NativeImage baseMask = loadBaseArmorMask(rm, slot);
        if (baseMask == null) {
            baseMask = new NativeImage(64, 32, false);
            for (int y = 0; y < 32; y++) for (int x = 0; x < 64; x++) baseMask.setPixel(x, y, 0xFFFFFFFF);
        }
        int baseW = baseMask.getWidth();
        int baseH = baseMask.getHeight();
        NativeImage armorTex = new NativeImage(baseW, baseH, false);
        for (int y = 0; y < baseH; y++) for (int x = 0; x < baseW; x++) armorTex.setPixel(x, y, 0);

        // Load unique images
        Map<String, NativeImage> uniqueMap = new HashMap<>();
        Map<String, int[]> bboxMap = new HashMap<>();
        Map<String, Integer> avgMap = new HashMap<>();
        Map<String, Boolean> smallMap = new HashMap<>();
        for (String idStr : idsInOrder) {
            if (uniqueMap.containsKey(idStr)) continue;
            NativeImage img = loadImageForId(idStr, rm);
            if (img == null) {
                LOGGER.debug("Armor texture missing for {} (key {}), using fallback", idStr, key);
                img = createFallbackImage(idStr);
            }
            uniqueMap.put(idStr, img);
            bboxMap.put(idStr, computeBbox(img));
            avgMap.put(idStr, computeAverageForImage(img));
            smallMap.put(idStr, isSmallImage(img));
        }
        if (uniqueMap.isEmpty()) {
            try { armorTex.close(); } catch (Exception ignored) { LOGGER.trace("Ignored", ignored); }
            try { baseMask.close(); } catch (Exception ignored) { LOGGER.trace("Ignored", ignored); }
            return null;
        }

        List<String> instanceIds = new ArrayList<>(idsInOrder);
        List<NativeImage> instanceImgs = new ArrayList<>();
        List<int[]> instanceBboxes = new ArrayList<>();
        List<Integer> instanceAvgs = new ArrayList<>();
        List<Boolean> instanceSmalls = new ArrayList<>();
        for (String id : instanceIds) {
            NativeImage img = uniqueMap.get(id);
            if (img == null) {
                String any = uniqueMap.keySet().iterator().next();
                img = uniqueMap.get(any);
                id = any;
            }
            instanceImgs.add(img);
            instanceBboxes.add(bboxMap.getOrDefault(id, new int[]{0,0,15,15}));
            instanceAvgs.add(avgMap.getOrDefault(id, 0xFFFFFFFF));
            instanceSmalls.add(smallMap.getOrDefault(id, false));
        }

        // Compute armor occupied bbox for seed mapping
        int armorMinX = baseW, armorMinY = baseH, armorMaxX = -1, armorMaxY = -1;
        for (int y = 0; y < baseH; y++) for (int x = 0; x < baseW; x++) {
            int a = (baseMask.getPixel(x,y) >> 24) & 0xFF;
            if (a >= 10) {
                if (x < armorMinX) armorMinX = x;
                if (y < armorMinY) armorMinY = y;
                if (x > armorMaxX) armorMaxX = x;
                if (y > armorMaxY) armorMaxY = y;
            }
        }
        if (armorMaxX < 0) { armorMinX = 0; armorMinY = 0; armorMaxX = baseW-1; armorMaxY = baseH-1; }

        List<int[]> seeds = new ArrayList<>();
        if (!posInOrder.isEmpty() && posInOrder.size() == instanceIds.size()) {
            for (int i = 0; i < instanceIds.size(); i++) {
                int pos = posInOrder.get(i);
                int gx = pos % 16;
                int gy = pos / 16;
                double normX = (gx + 0.5) / Math.max(1, pW);
                double normY = (gy + 0.5) / Math.max(1, pH);
                normX = Math.max(0, Math.min(0.999, normX));
                normY = Math.max(0, Math.min(0.999, normY));
                int w = armorMaxX - armorMinX + 1;
                int h = armorMaxY - armorMinY + 1;
                if (w <= 0) w = baseW;
                if (h <= 0) h = baseH;
                int sx = armorMinX + (int)(normX * (w - 1));
                int sy = armorMinY + (int)(normY * (h - 1));
                // Add small jitter to avoid perfect grid
                sx = Math.max(armorMinX, Math.min(armorMaxX, sx + ((i & 1) == 0 ? 0 : 2)));
                sy = Math.max(armorMinY, Math.min(armorMaxY, sy + ((i & 2) == 0 ? 0 : 2)));
                seeds.add(new int[]{sx, sy});
            }
        } else {
            Random rnd = new Random(key.hashCode() ^ (slot == null ? 0 : slot.hashCode()));
            for (int i = 0; i < instanceIds.size(); i++) {
                int sx, sy;
                int attempts = 0;
                do {
                    sx = rnd.nextInt(baseW);
                    sy = rnd.nextInt(baseH);
                    attempts++;
                    if (attempts > 20) break;
                    int baseA = (baseMask.getPixel(sx % baseW, sy % baseH) >> 24) & 0xFF;
                    if (baseA >= 10) break;
                } while (attempts < 10);
                for (int attempt = 0; attempt < 4; attempt++) {
                    boolean tooClose = false;
                    for (int[] s : seeds) if ((s[0]-sx)*(s[0]-sx)+(s[1]-sy)*(s[1]-sy) < 36) { tooClose = true; break; }
                    if (!tooClose) break;
                    sx = rnd.nextInt(baseW); sy = rnd.nextInt(baseH);
                }
                seeds.add(new int[]{sx, sy});
            }
        }

        for (int y = 0; y < baseH; y++) {
            for (int x = 0; x < baseW; x++) {
                int baseArgb = baseMask.getPixel(x, y);
                int baseA = (baseArgb >> 24) & 0xFF;
                if (baseA < 10) { armorTex.setPixel(x, y, 0); continue; }
                int bestIdx = findNearestSeed(x, y, seeds, key.hashCode());
                NativeImage src = instanceImgs.get(bestIdx);
                int[] bbox = instanceBboxes.get(bestIdx);
                boolean isSmall = instanceSmalls.get(bestIdx);
                int avg = instanceAvgs.get(bestIdx);
                int[] seed = seeds.get(bestIdx);
                int col;
                if (isSmall) {
                    int bw = bbox[2] - bbox[0] + 1;
                    int bh = bbox[3] - bbox[1] + 1;
                    if (bw <= 0) bw = 16; if (bh <= 0) bh = 16;
                    int srcX = bbox[0] + Math.floorMod(x + seed[0], bw);
                    int srcY = bbox[1] + Math.floorMod(y + seed[1], bh);
                    srcX = Math.max(0, Math.min(15, srcX)); srcY = Math.max(0, Math.min(15, srcY));
                    col = src.getPixel(srcX, srcY);
                    int a = (col >> 24) & 0xFF;
                    if (a < 10) {
                        int cx = (bbox[0] + bbox[2]) / 2, cy = (bbox[1] + bbox[3]) / 2;
                        col = src.getPixel(Math.max(0, Math.min(15,cx)), Math.max(0, Math.min(15,cy)));
                        a = (col >> 24) & 0xFF;
                        if (a < 10) col = avg;
                    }
                } else {
                    int srcX = Math.floorMod(x + seed[0], 16);
                    int srcY = Math.floorMod(y + seed[1], 16);
                    col = src.getPixel(srcX, srcY);
                    int a = (col >> 24) & 0xFF;
                    if (a < 10) {
                        int cx = (bbox[0] + bbox[2]) / 2, cy = (bbox[1] + bbox[3]) / 2;
                        col = src.getPixel(Math.max(0, Math.min(15,cx)), Math.max(0, Math.min(15,cy)));
                        a = (col >> 24) & 0xFF;
                        if (a < 10) col = avg;
                    }
                }
                // Force 100% opaque inside shape – no semi-transparency aside from outside shape
                col = 0xFF000000 | (col & 0x00FFFFFF);
                armorTex.setPixel(x, y, col);
            }
        }

        // Vanilla-style per-pixel shading transfer (borders, bevels, helmet visor band)
        // NOTE: must run BEFORE baseMask.close()
        BlendedTextureManager.applyVanillaShadeTransfer(armorTex, baseMask, null);

        for (NativeImage img : uniqueMap.values()) try { img.close(); } catch (Exception ignored) { LOGGER.trace("Ignored", ignored); }
        try { baseMask.close(); } catch (Exception ignored) { LOGGER.trace("Ignored", ignored); }
        String hash = Integer.toHexString(key.hashCode());
        Identifier outId = Identifier.fromNamespaceAndPath("blendedcraft", "armor_blended/" + hash);
        var tm = Minecraft.getInstance().getTextureManager();
        try {
            DynamicTexture dyn = new DynamicTexture(() -> outId.toString(), armorTex);
            tm.register(outId, dyn);
            LOGGER.info("Generated PATCHWORK blended armor texture {} for {}", outId, key);
            return outId;
        } catch (Exception e) {
            LOGGER.error("Failed to register armor texture {}: {}", outId, e.toString());
            try { armorTex.close(); } catch (Exception ignored) { LOGGER.trace("Ignored", ignored); }
            return null;
        }
    }

    private static int findNearestSeed(int x, int y, List<int[]> seeds, int salt) {
        int best = 0; double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < seeds.size(); i++) {
            int sx = seeds.get(i)[0], sy = seeds.get(i)[1];
            double dx = x - sx, dy = y - sy;
            double dist = dx*dx + dy*dy;
            int n = (x * 374761393 ^ y * 668265263 ^ i * 1013904223 ^ salt);
            n = (n >>> 1) & 0x7;
            dist += (n - 3) * 2.5;
            if (dist < bestDist) { bestDist = dist; best = i; }
        }
        return best;
    }

    private static NativeImage loadImageForId(String idStr, net.minecraft.server.packs.resources.ResourceManager rm) {
        NativeImage direct = tryLoadTexture(idStr, rm);
        if (direct != null) return direct;
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
            } else             if (path.endsWith("_sign")) {
                String base = path.substring(0, path.length() - 5);
                alts.add(base + "_planks");
                alts.add(base);
            } else if (path.endsWith("_block")) {
                // e.g. magma_block -> texture is block/magma.png
                alts.add(path.substring(0, path.length() - 6));
            }
            // Multi-part block textures: most blocks (cactus, grass, pumpkin, ...) have no
            // textures/block/<name>.png - their sprite is <name>_side (or _top). Trying the
            // side texture keeps the ingredient's real look instead of a random fallback color.
            alts.add(path + "_side");
            alts.add(path + "_top");
            for (String altPath : alts) {
                String altId = ns + ":" + altPath;
                NativeImage altImg = tryLoadTexture(altId, rm);
                if (altImg != null) {
                    LOGGER.debug("Resolved fallback armor texture for {} -> {}", idStr, altId);
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
                NativeImage normalized = normalizeTexture(img);
                if (normalized != img) {
                    img.close();
                    return normalized;
                }
                return img;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to load armor mat {}: {}", idStr, e.toString());
            return null;
        }
    }

    /**
     * Animated block textures (magma, sea lantern, prismarine, fire...) are vertical frame strips
     * (e.g. 16x512). Take the FIRST frame instead of squeezing all frames together, which produced
     * garbled colors. Non-square odd sizes keep the old squeeze behavior.
     */
    private static NativeImage normalizeTexture(NativeImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        if (w == 16 && h == 16) return img;
        if (h > w && h % w == 0) {
            // vertical animation strip -> first frame
            NativeImage frame = new NativeImage(w, w, false);
            for (int y = 0; y < w; y++) for (int x = 0; x < w; x++) frame.setPixel(x, y, img.getPixel(x, y));
            return frame;
        }
        if (w > h && w % h == 0) {
            // horizontal strip -> first frame
            NativeImage frame = new NativeImage(h, h, false);
            for (int y = 0; y < h; y++) for (int x = 0; x < h; x++) frame.setPixel(x, y, img.getPixel(x, y));
            return frame;
        }
        NativeImage scaled = new NativeImage(16, 16, false);
        for (int yy = 0; yy < 16; yy++) for (int xx = 0; xx < 16; xx++) {
            scaled.setPixel(xx, yy, img.getPixel(xx * w / 16, yy * h / 16));
        }
        return scaled;
    }

    private static int[] computeBbox(NativeImage img) {
        int minX=16,minY=16,maxX=-1,maxY=-1;
        for (int y=0;y<img.getHeight();y++) for (int x=0;x<img.getWidth();x++) if (((img.getPixel(x,y)>>24)&0xFF)>10) { if(x<minX)minX=x; if(y<minY)minY=y; if(x>maxX)maxX=x; if(y>maxY)maxY=y; }
        if (maxX<0) return new int[]{0,0,15,15};
        return new int[]{minX,minY,maxX,maxY};
    }

    private static boolean isSmallImage(NativeImage img) {
        int trans=0, total=img.getWidth()*img.getHeight();
        for (int y=0;y<img.getHeight();y++) for (int x=0;x<img.getWidth();x++) if (((img.getPixel(x,y)>>24)&0xFF)<10) trans++;
        return trans*2>total;
    }

    private static int computeAverageForImage(NativeImage img) {
        long rSum=0,gSum=0,bSum=0; int cnt=0;
        for (int y=0;y<img.getHeight();y++) for (int x=0;x<img.getWidth();x++) {
            int argb=img.getPixel(x,y);
            if (((argb>>24)&0xFF)<10) continue;
            rSum+=(argb>>16)&0xFF; gSum+=(argb>>8)&0xFF; bSum+=argb&0xFF; cnt++;
        }
        if (cnt==0) return 0xFFFFFFFF;
        return (0xFF<<24)|((int)(rSum/cnt)<<16)|((int)(gSum/cnt)<<8)|(int)(bSum/cnt);
    }

    private static NativeImage createFallbackImage(String idStr) {
        int hash = idStr.hashCode();
        int r = (hash >> 16) & 0xFF;
        int g = (hash >> 8) & 0xFF;
        int b = hash & 0xFF;
        r = (r + 128) % 256; g = (g + 128) % 256; b = (b + 128) % 256;
        r = Math.max(60, r); g = Math.max(60, g); b = Math.max(60, b);
        NativeImage img = new NativeImage(16, 16, false);
        int col = (0xFF << 24) | (r << 16) | (g << 8) | b;
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) img.setPixel(x, y, col);
        for (int y = 0; y < 16; y += 4) for (int x = 0; x < 16; x += 4) img.setPixel(x, y, (0xFF << 24) | ((r+30)%256 << 16) | ((g+30)%256 << 8) | (b+30)%256);
        return img;
    }

    private static NativeImage loadBaseArmorMask(net.minecraft.server.packs.resources.ResourceManager rm, net.minecraft.world.entity.EquipmentSlot slot) {
        boolean isLeggings = slot == net.minecraft.world.entity.EquipmentSlot.LEGS;
        String layer = isLeggings ? "humanoid_leggings" : "humanoid";
        String[] candidates = {
            "textures/entity/equipment/" + layer + "/diamond.png",
            "textures/entity/equipment/" + layer + "/netherite.png",
            "textures/entity/equipment/" + layer + "/iron.png",
            "textures/entity/equipment/" + layer + "/gold.png",
            "textures/entity/equipment/" + layer + "/chainmail.png",
            "textures/entity/equipment/" + layer + "/leather.png"
        };
        for (String p : candidates) {
            Identifier id = Identifier.fromNamespaceAndPath("minecraft", p);
            Optional<Resource> res = rm.getResource(id);
            if (res.isPresent()) {
                try (InputStream in = res.get().open()) { NativeImage img = NativeImage.read(in); return img; } catch (Exception ignored) { LOGGER.trace("Ignored", ignored); }
            }
        }
        return null;
    }
}
