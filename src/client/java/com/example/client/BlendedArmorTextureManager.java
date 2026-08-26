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
        List<String> sorted = new ArrayList<>(ids);
        sorted.sort(String::compareTo);
        String slotKey = slot == null ? "unknown" : slot.getName();
        String key = String.join("+", sorted) + "|armor|" + slotKey;
        if (CACHE.containsKey(key)) return CACHE.get(key);
        Identifier gen = generateArmorTexture(key, sorted, slot);
        if (gen != null) CACHE.put(key, gen);
        return gen;
    }

    public static Identifier getOrCreateArmorTexture(ItemStack stack) {
        return getOrCreateArmorTexture(stack, null);
    }

    private static Identifier generateArmorTexture(String key, List<String> sortedIds, net.minecraft.world.entity.EquipmentSlot slot) {
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
        // Prepare material images for blended head (armor has no handle)
        List<NativeImage> matImages = new ArrayList<>();
        for (String idStr : sortedIds) {
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
                        for (int yy = 0; yy < 16; yy++) for (int xx = 0; xx < 16; xx++) scaled.setPixel(xx, yy, img.getPixel(xx * img.getWidth() / 16, yy * img.getHeight() / 16));
                        img.close();
                        matImages.add(scaled);
                    } else matImages.add(img);
                }
            } catch (Exception e) { LOGGER.warn("Failed to load armor mat {}: {}", idStr, e.toString()); }
        }
        if (matImages.isEmpty()) {
            try { armorTex.close(); } catch (Exception ignored) {}
            try { baseMask.close(); } catch (Exception ignored) {}
            return null;
        }
        // For small items, we will tile the button across the armor shape
        boolean isSmall = isSmallMaterial(sortedIds, rm);
        int avgColor = computeAverageColor(sortedIds, rm);
        // Cache for tiled sampling
        for (int y = 0; y < baseH; y++) {
            for (int x = 0; x < baseW; x++) {
                int baseArgb = baseMask.getPixel(x % baseMask.getWidth(), y % baseMask.getHeight());
                int baseA = (baseArgb >> 24) & 0xFF;
                if (baseA < 10) { armorTex.setPixel(x, y, 0); continue; }
                int srcX, srcY;
                if (isSmall) {
                    srcX = (x * 2 + y) % 16;
                    srcY = (y * 2 + x) % 16;
                    int rnd = (x * 374761393 + y * 668265263) % 16;
                    srcX = (srcX + (rnd & 3)) % 16;
                    srcY = (srcY + ((rnd >> 2) & 3)) % 16;
                } else {
                    srcX = x % 16;
                    srcY = y % 16;
                }
                // Pick a random material for this pixel (for blended head with multiple materials)
                String chosenId = sortedIds.get((x * 31 + y * 17) % sortedIds.size());
                NativeImage chosenImg = null;
                for (NativeImage img : matImages) {
                    // Find the image for chosenId (we need to map id to image, but we have list order same as sortedIds)
                    // So we can use index
                    int idx = sortedIds.indexOf(chosenId);
                    if (idx >= 0 && idx < matImages.size()) { chosenImg = matImages.get(idx); break; }
                }
                if (chosenImg == null) chosenImg = matImages.get(0);
                int col = chosenImg.getPixel(srcX, srcY);
                int a = (col >> 24) & 0xFF;
                if (a < 10) {
                    // Use average color for transparent parts of small item
                    int r = (avgColor >> 16) & 0xFF, g = (avgColor >> 8) & 0xFF, b = avgColor & 0xFF;
                    col = (baseA << 24) | (r << 16) | (g << 8) | b;
                } else {
                    col = (baseA << 24) | (col & 0x00FFFFFF);
                }
                armorTex.setPixel(x, y, col);
            }
        }
        for (NativeImage img : matImages) try { img.close(); } catch (Exception ignored) {}
        try { baseMask.close(); } catch (Exception ignored) {}
        String hash = Integer.toHexString(key.hashCode());
        Identifier outId = Identifier.fromNamespaceAndPath("blendedcraft", "armor_blended/" + hash);
        var tm = Minecraft.getInstance().getTextureManager();
        try {
            DynamicTexture dyn = new DynamicTexture(() -> outId.toString(), armorTex);
            tm.register(outId, dyn);
            LOGGER.info("Generated blended armor texture {} for {}", outId, key);
            return outId;
        } catch (Exception e) {
            LOGGER.error("Failed to register armor texture {}: {}", outId, e.toString());
            try { armorTex.close(); } catch (Exception ignored) {}
            return null;
        }
    }

    private static boolean isSmallMaterial(List<String> ids, net.minecraft.server.packs.resources.ResourceManager rm) {
        if (ids.isEmpty()) return false;
        int transparent = 0, total = 0;
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
                    for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) {
                        int a = (img.getPixel(x, y) >> 24) & 0xFF;
                        if (a < 10) transparent++;
                        total++;
                    }
                    img.close();
                }
            } catch (Exception ignored) {}
        }
        return total > 0 && transparent * 2 > total;
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
                try (InputStream in = res.get().open()) {
                    NativeImage img = NativeImage.read(in);
                    return img;
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static NativeImage loadBaseArmorMask(net.minecraft.server.packs.resources.ResourceManager rm) {
        return loadBaseArmorMask(rm, null);
    }

    private static int computeAverageColor(List<String> ids, net.minecraft.server.packs.resources.ResourceManager rm) {
        long rSum = 0, gSum = 0, bSum = 0;
        int cnt = 0;
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
                    for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) {
                        int argb = img.getPixel(x, y);
                        if (((argb >> 24) & 0xFF) < 10) continue;
                        rSum += (argb >> 16) & 0xFF; gSum += (argb >> 8) & 0xFF; bSum += argb & 0xFF; cnt++;
                    }
                    img.close();
                }
            } catch (Exception ignored) {}
        }
        if (cnt == 0) return 0xFFFFFFFF;
        return (0xFF << 24) | ((int)(rSum / cnt) << 16) | ((int)(gSum / cnt) << 8) | (int)(bSum / cnt);
    }
}

