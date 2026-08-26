package com.example.client;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class BlendedTextureManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("blendedcraft/BlendedTextureManager");
    private static final Map<String, Identifier> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> GENERATING = new ConcurrentHashMap<>();

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
        List<String> sortedAll = new ArrayList<>(allIds);
        sortedAll.sort(String::compareTo);
        List<String> sortedHead = new ArrayList<>(headIds);
        sortedHead.sort(String::compareTo);
        List<String> sortedHandle = new ArrayList<>(handleIds);
        sortedHandle.sort(String::compareTo);
        String resultIdStr = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String key = resultIdStr + "|head:" + String.join("+", sortedHead) + "|handle:" + String.join("+", sortedHandle) + "|all:" + String.join("+", sortedAll);
        if (CACHE.containsKey(key)) {
            return CACHE.get(key);
        }
        if (GENERATING.putIfAbsent(key, Boolean.TRUE) != null) {
            return CACHE.get(key);
        }
        try {
            Identifier generated = generateBlendedTexture(key, sortedHead, sortedHandle, sortedAll, stack);
            if (generated != null) {
                CACHE.put(key, generated);
            }
            return generated;
        } finally {
            GENERATING.remove(key);
        }
    }

    private static Identifier generateBlendedTexture(String key, List<String> sortedHead, List<String> sortedHandle, List<String> sortedAll, ItemStack resultStack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;
        var rm = mc.getResourceManager();
        NativeImage baseMask = loadBaseMask(rm, resultStack);
        NativeImage headImg = createBlendedImage(sortedHead.isEmpty() ? sortedAll : sortedHead, rm, key + "_head");
        NativeImage handleImg = sortedHandle.isEmpty() ? null : createBlendedImage(sortedHandle, rm, key + "_handle");
        if (headImg == null && handleImg == null) {
            LOGGER.warn("No textures found for key {}", key);
            if (baseMask != null) try { baseMask.close(); } catch (Exception ignored) {}
            return null;
        }
        if (headImg == null) headImg = handleImg;
        if (handleImg == null) handleImg = headImg;
        // For small items (emerald, ender pearl, oak button) fill entire armor shape with tiled pattern of the item, not just average color
        // Keep average for fallback but also prepare tiled sampling
        NativeImage blended = new NativeImage(16, 16, false);
        // Cache ingredient images for tiled sampling
        java.util.Map<String, NativeImage> headImageCache = new java.util.HashMap<>();
        java.util.Map<String, NativeImage> handleImageCache = new java.util.HashMap<>();
        // Preload head images for tiling
        for (String id : sortedHead.isEmpty() ? sortedAll : sortedHead) {
            if (headImageCache.containsKey(id)) continue;
            try {
                Identifier itemId = Identifier.parse(id);
                Identifier texItem = Identifier.fromNamespaceAndPath(itemId.getNamespace(), "textures/item/" + itemId.getPath() + ".png");
                Identifier texBlock = Identifier.fromNamespaceAndPath(itemId.getNamespace(), "textures/block/" + itemId.getPath() + ".png");
                Optional<Resource> res = rm.getResource(texItem);
                if (res.isEmpty()) res = rm.getResource(texBlock);
                if (res.isEmpty()) continue;
                try (InputStream in = res.get().open()) {
                    NativeImage img = NativeImage.read(in);
                    if (img.getWidth() != 16 || img.getHeight() != 16) {
                        NativeImage scaled = new NativeImage(16, 16, false);
                        for (int yy = 0; yy < 16; yy++) for (int xx = 0; xx < 16; xx++) scaled.setPixel(xx, yy, img.getPixel(xx * img.getWidth() / 16, yy * img.getHeight() / 16));
                        img.close();
                        headImageCache.put(id, scaled);
                    } else headImageCache.put(id, img);
                }
            } catch (Exception ignored) {}
        }
        for (String id : sortedHandle) {
            if (handleImageCache.containsKey(id)) continue;
            try {
                Identifier itemId = Identifier.parse(id);
                Identifier texItem = Identifier.fromNamespaceAndPath(itemId.getNamespace(), "textures/item/" + itemId.getPath() + ".png");
                Identifier texBlock = Identifier.fromNamespaceAndPath(itemId.getNamespace(), "textures/block/" + itemId.getPath() + ".png");
                Optional<Resource> res = rm.getResource(texItem);
                if (res.isEmpty()) res = rm.getResource(texBlock);
                if (res.isEmpty()) continue;
                try (InputStream in = res.get().open()) {
                    NativeImage img = NativeImage.read(in);
                    if (img.getWidth() != 16 || img.getHeight() != 16) {
                        NativeImage scaled = new NativeImage(16, 16, false);
                        for (int yy = 0; yy < 16; yy++) for (int xx = 0; xx < 16; xx++) scaled.setPixel(xx, yy, img.getPixel(xx * img.getWidth() / 16, yy * img.getHeight() / 16));
                        img.close();
                        handleImageCache.put(id, scaled);
                    } else handleImageCache.put(id, img);
                }
            } catch (Exception ignored) {}
        }
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if (baseMask != null) {
                    int baseArgb = baseMask.getPixel(x, y);
                    int baseA = (baseArgb >> 24) & 0xFF;
                    if (baseA < 10) { blended.setPixel(x, y, 0); continue; }
                    boolean isHandle = isHandlePixel(baseArgb);
                    List<String> srcIds;
                    java.util.Map<String, NativeImage> cache;
                    if (!sortedHandle.isEmpty() && isHandle) { srcIds = sortedHandle; cache = handleImageCache; }
                    else { srcIds = sortedHead.isEmpty() ? sortedAll : sortedHead; cache = headImageCache; }
                    if (srcIds.isEmpty()) { blended.setPixel(x, y, 0); continue; }
                    // Pick a material for this pixel (for blended head with multiple materials, distribute randomly but deterministically)
                    String chosenId = srcIds.get((x * 31 + y * 17) % srcIds.size());
                    NativeImage srcImg = cache.get(chosenId);
                    if (srcImg == null) srcImg = headImg != null ? headImg : handleImg;
                    if (srcImg == null) { blended.setPixel(x, y, 0); continue; }
                    // Tiled sampling: repeat small item's texture across armor shape randomly
                    // Use tiled coordinates to create a bunch of buttons across the armor
                    int tileScale = 2; // 2x tiled = 4x repeats, good for small items like button
                    int srcX = (x * tileScale + y) % 16;
                    int srcY = (y * tileScale + x) % 16;
                    // Add per-pixel random offset for more random placement of buttons
                    int rnd = (x * 374761393 + y * 668265263) ^ chosenId.hashCode();
                    srcX = (srcX + (rnd & 3)) % 16;
                    srcY = (srcY + ((rnd >> 2) & 3)) % 16;
                    int col = srcImg.getPixel(srcX, srcY);
                    int a = (col >> 24) & 0xFF;
                    if (a < 10) {
                        // If sampled pixel is transparent (common for small items), try center of the item
                        // Sample the center of the ingredient's bbox
                        int cx = 8, cy = 8;
                        // Find bbox center
                        int minX = 16, minY = 16, maxX = -1, maxY = -1;
                        for (int yy = 0; yy < 16; yy++) for (int xx = 0; xx < 16; xx++) if (((srcImg.getPixel(xx, yy) >> 24) & 0xFF) > 10) { minX = Math.min(minX, xx); minY = Math.min(minY, yy); maxX = Math.max(maxX, xx); maxY = Math.max(maxY, yy); }
                        if (maxX >= 0) { cx = (minX + maxX) / 2; cy = (minY + maxY) / 2; }
                        col = srcImg.getPixel(cx, cy);
                        a = (col >> 24) & 0xFF;
                        if (a < 10) {
                            // Fallback to average color
                            col = computeAverageColor(srcIds, rm);
                            a = 0xFF;
                            int r = (col >> 16) & 0xFF, g = (col >> 8) & 0xFF, b = col & 0xFF;
                            col = (baseA << 24) | (r << 16) | (g << 8) | b;
                        } else {
                            col = (baseA << 24) | (col & 0x00FFFFFF);
                        }
                    } else {
                        col = (baseA << 24) | (col & 0x00FFFFFF);
                    }
                    blended.setPixel(x, y, col);
                } else {
                    // No base mask, just use head
                    int col = headImg != null ? headImg.getPixel(x, y) : 0;
                    int a = (col >> 24) & 0xFF;
                    if (a < 10) col = computeAverageColor(sortedHead.isEmpty() ? sortedAll : sortedHead, rm);
                    blended.setPixel(x, y, col);
                }
            }
        }
        // Close caches
        for (NativeImage img : headImageCache.values()) try { img.close(); } catch (Exception ignored) {}
        for (NativeImage img : handleImageCache.values()) try { img.close(); } catch (Exception ignored) {}
        if (headImg != null) try { headImg.close(); } catch (Exception ignored) {}
        if (handleImg != null && handleImg != headImg) try { handleImg.close(); } catch (Exception ignored) {}
        if (baseMask != null) try { baseMask.close(); } catch (Exception ignored) {}
        String hash = Integer.toHexString(key.hashCode());
        Identifier outId = Identifier.fromNamespaceAndPath("blendedcraft", "blended/" + hash);
        var tm = mc.getTextureManager();
        try {
            DynamicTexture dyn = new DynamicTexture(() -> outId.toString(), blended);
            tm.register(outId, dyn);
            LOGGER.info("Generated blended texture {} for key {} (head {} handle {} )", outId, key, sortedHead.size(), sortedHandle.size());
            return outId;
        } catch (Exception e) {
            LOGGER.error("Failed to register blended texture {}: {}", outId, e.toString());
            try { blended.close(); } catch (Exception ignored) {}
            return null;
        }
    }

    private static NativeImage createBlendedImage(List<String> ids, net.minecraft.server.packs.resources.ResourceManager rm, String debugKey) {
        if (ids.isEmpty()) return null;
        List<NativeImage> images = new ArrayList<>();
        for (String idStr : ids) {
            try {
                Identifier itemId = Identifier.parse(idStr);
                Identifier texItem = Identifier.fromNamespaceAndPath(itemId.getNamespace(), "textures/item/" + itemId.getPath() + ".png");
                Identifier texBlock = Identifier.fromNamespaceAndPath(itemId.getNamespace(), "textures/block/" + itemId.getPath() + ".png");
                Optional<Resource> res = rm.getResource(texItem);
                if (res.isEmpty()) res = rm.getResource(texBlock);
                if (res.isEmpty()) continue;
                try (InputStream in = res.get().open()) {
                    NativeImage img = NativeImage.read(in);
                    if (img.getWidth() != 16 || img.getHeight() != 16) {
                        NativeImage scaled = new NativeImage(16, 16, false);
                        for (int yy = 0; yy < 16; yy++) {
                            for (int xx = 0; xx < 16; xx++) {
                                int srcX = xx * img.getWidth() / 16;
                                int srcY = yy * img.getHeight() / 16;
                                scaled.setPixel(xx, yy, img.getPixel(srcX, srcY));
                            }
                        }
                        img.close();
                        images.add(scaled);
                    } else {
                        images.add(img);
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to load texture for {}: {}", idStr, e.toString());
            }
        }
        if (images.isEmpty()) return null;
        NativeImage out = new NativeImage(16, 16, false);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int rSum = 0, gSum = 0, bSum = 0, aSum = 0;
                int rSumNT = 0, gSumNT = 0, bSumNT = 0, aSumNT = 0;
                int ntCount = 0;
                for (NativeImage img : images) {
                    int argb = img.getPixel(x, y);
                    int a = (argb >> 24) & 0xFF;
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    rSum += r; gSum += g; bSum += b; aSum += a;
                    if (a > 10) { rSumNT += r; gSumNT += g; bSumNT += b; aSumNT += a; ntCount++; }
                }
                int outA, outR, outG, outB;
                if (ntCount > 0) { outA = aSumNT / ntCount; outR = rSumNT / ntCount; outG = gSumNT / ntCount; outB = bSumNT / ntCount; }
                else { outA = aSum / images.size(); outR = rSum / images.size(); outG = gSum / images.size(); outB = bSum / images.size(); }
                if (outA < 10 && ntCount == 0) out.setPixel(x, y, 0);
                else out.setPixel(x, y, (outA << 24) | (outR << 16) | (outG << 8) | outB);
            }
        }
        for (NativeImage img : images) try { img.close(); } catch (Exception ignored) {}
        return out;
    }

    private static int computeAverageColor(List<String> ids, net.minecraft.server.packs.resources.ResourceManager rm) {
        if (ids.isEmpty()) return 0xFFFFFFFF;
        long rSum = 0, gSum = 0, bSum = 0;
        int count = 0;
        for (String idStr : ids) {
            try {
                Identifier itemId = Identifier.parse(idStr);
                Identifier texItem = Identifier.fromNamespaceAndPath(itemId.getNamespace(), "textures/item/" + itemId.getPath() + ".png");
                Identifier texBlock = Identifier.fromNamespaceAndPath(itemId.getNamespace(), "textures/block/" + itemId.getPath() + ".png");
                Optional<Resource> res = rm.getResource(texItem);
                if (res.isEmpty()) res = rm.getResource(texBlock);
                if (res.isEmpty()) continue;
                try (InputStream in = res.get().open()) {
                    NativeImage img = NativeImage.read(in);
                    for (int y = 0; y < img.getHeight(); y++) {
                        for (int x = 0; x < img.getWidth(); x++) {
                            int argb = img.getPixel(x, y);
                            int a = (argb >> 24) & 0xFF;
                            if (a < 10) continue;
                            int r = (argb >> 16) & 0xFF;
                            int g = (argb >> 8) & 0xFF;
                            int b = argb & 0xFF;
                            rSum += r; gSum += g; bSum += b; count++;
                        }
                    }
                    img.close();
                }
            } catch (Exception ignored) {}
        }
        if (count == 0) return 0xFFFFFFFF;
        int r = (int)(rSum / count);
        int g = (int)(gSum / count);
        int b = (int)(bSum / count);
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    private static boolean isHandlePixel(int baseArgb) {
        int a = (baseArgb >> 24) & 0xFF;
        if (a < 10) return false;
        int r = (baseArgb >> 16) & 0xFF;
        int g = (baseArgb >> 8) & 0xFF;
        int b = baseArgb & 0xFF;
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
                            for (int yy = 0; yy < 16; yy++) {
                                for (int xx = 0; xx < 16; xx++) {
                                    int srcX = xx * img.getWidth() / 16;
                                    int srcY = yy * img.getHeight() / 16;
                                    scaled.setPixel(xx, yy, img.getPixel(srcX, srcY));
                                }
                            }
                            img.close();
                            return scaled;
                        }
                        return img;
                    } catch (Exception ignored) {}
                }
            }
            Identifier modTex = Identifier.fromNamespaceAndPath("blendedcraft", "textures/item/" + resultPath + ".png");
            Optional<Resource> modRes = rm.getResource(modTex);
            if (modRes.isPresent()) {
                try (InputStream in = modRes.get().open()) {
                    NativeImage img = NativeImage.read(in);
                    if (img.getWidth() != 16 || img.getHeight() != 16) {
                        NativeImage scaled = new NativeImage(16, 16, false);
                        for (int yy = 0; yy < 16; yy++) {
                            for (int xx = 0; xx < 16; xx++) {
                                int srcX = xx * img.getWidth() / 16;
                                int srcY = yy * img.getHeight() / 16;
                                scaled.setPixel(xx, yy, img.getPixel(srcX, srcY));
                            }
                        }
                        img.close();
                        return scaled;
                    }
                    return img;
                } catch (Exception ignored) {}
            }
            Identifier blockTex = Identifier.fromNamespaceAndPath("minecraft", "textures/block/" + suffix + ".png");
            Optional<Resource> blockRes = rm.getResource(blockTex);
            if (blockRes.isPresent()) {
                try (InputStream in = blockRes.get().open()) {
                    NativeImage img = NativeImage.read(in);
                    if (img.getWidth() != 16 || img.getHeight() != 16) {
                        NativeImage scaled = new NativeImage(16, 16, false);
                        for (int yy = 0; yy < 16; yy++) {
                            for (int xx = 0; xx < 16; xx++) {
                                int srcX = xx * img.getWidth() / 16;
                                int srcY = yy * img.getHeight() / 16;
                                scaled.setPixel(xx, yy, img.getPixel(srcX, srcY));
                            }
                        }
                        img.close();
                        return scaled;
                    }
                    return img;
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load base mask for {}: {}", stack, e.toString());
        }
        return null;
    }

    public static void clearCache() {
        CACHE.clear();
    }
}

