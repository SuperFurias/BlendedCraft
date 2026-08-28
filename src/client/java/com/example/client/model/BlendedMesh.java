package com.example.client.model;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Extruded 16x16 pixel mesh (vanilla "generated"-item style) built from a runtime-blended texture.
 * Front face at z=7.5/16, back face at z=8.5/16, plus side strips along alpha boundaries,
 * exactly like vanilla ItemModelGenerator output. All vertices in model space [0,1].
 */
public final class BlendedMesh {
    private static final float FRONT_Z = 7.5f / 16f;
    private static final float BACK_Z = 8.5f / 16f;
    private static final float EPS = 0.001f;

    private static final ConcurrentHashMap<Identifier, BlendedMesh> MESH_CACHE = new ConcurrentHashMap<>();

    private final float[] min = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
    private final float[] max = new float[]{-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
    private final List<Quad> quads = new ArrayList<>();
    private Supplier<org.joml.Vector3fc[]> extentsSupplier;

    private record Quad(float[] pos, float[] uv, float[] normal) {}

    private BlendedMesh() {}

    public static BlendedMesh getOrCreate(Identifier textureId, Supplier<NativeImage> imageSupplier) {
        return MESH_CACHE.computeIfAbsent(textureId, id -> {
            NativeImage img = imageSupplier.get();
            if (img == null) return empty();
            return build(img);
        });
    }

    private static BlendedMesh empty() {
        BlendedMesh m = new BlendedMesh();
        m.min[0] = m.min[1] = m.min[2] = 0f;
        m.max[0] = m.max[1] = m.max[2] = 1f;
        return m;
    }

    private static BlendedMesh build(NativeImage img) {
        BlendedMesh mesh = new BlendedMesh();
        int w = img.getWidth();
        int h = img.getHeight();
        boolean[][] opaque = new boolean[w][h];
        int opaqueCount = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean o = ((img.getPixel(x, y) >> 24) & 0xFF) > 16;
                opaque[x][y] = o;
                if (o) opaqueCount++;
            }
        }
        if (opaqueCount == 0) return empty();

        float uScale = 1f / w;
        float vScale = 1f / h;

        // Front face (-Z): u = x/w, v = y/h (v=0 is top row = y=1 in model space)
        mesh.addQuad(
                new float[]{0f, 1f, FRONT_Z, 1f, 1f, FRONT_Z, 1f, 0f, FRONT_Z, 0f, 0f, FRONT_Z},
                new float[]{0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f},
                new float[]{0f, 0f, -1f});

        // Back face (+Z): mirrored horizontally
        mesh.addQuad(
                new float[]{1f, 1f, BACK_Z, 0f, 1f, BACK_Z, 0f, 0f, BACK_Z, 1f, 0f, BACK_Z},
                new float[]{0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f},
                new float[]{0f, 0f, 1f});

        // Side strips along alpha boundaries (vanilla-style extrusion)
        // Top boundary: pixel opaque, pixel above (y-1) transparent/out of bounds
        for (int y = 0; y < h; y++) {
            int x = 0;
            while (x < w) {
                if (isEdge(opaque, x, y, w, h, 0, -1)) {
                    int x0 = x;
                    while (x < w && isEdge(opaque, x, y, w, h, 0, -1)) x++;
                    float yTop = 1f - y * vScale;
                    float v = (y + 0.5f) * vScale;
                    mesh.addQuad(
                            new float[]{x0 * uScale, yTop, FRONT_Z, x * uScale, yTop, FRONT_Z, x * uScale, yTop, BACK_Z, x0 * uScale, yTop, BACK_Z},
                            new float[]{x0 * uScale, v - EPS, x * uScale, v - EPS, x * uScale, v + EPS, x0 * uScale, v + EPS},
                            new float[]{0f, -1f, 0f});
                } else {
                    x++;
                }
            }
        }
        // Bottom boundary
        for (int y = 0; y < h; y++) {
            int x = 0;
            while (x < w) {
                if (isEdge(opaque, x, y, w, h, 0, 1)) {
                    int x0 = x;
                    while (x < w && isEdge(opaque, x, y, w, h, 0, 1)) x++;
                    float yBot = 1f - (y + 1) * vScale;
                    float v = (y + 0.5f) * vScale;
                    mesh.addQuad(
                            new float[]{x * uScale, yBot, FRONT_Z, x0 * uScale, yBot, FRONT_Z, x0 * uScale, yBot, BACK_Z, x * uScale, yBot, BACK_Z},
                            new float[]{x * uScale, v - EPS, x0 * uScale, v - EPS, x0 * uScale, v + EPS, x * uScale, v + EPS},
                            new float[]{0f, 1f, 0f});
                } else {
                    x++;
                }
            }
        }
        // Left boundary
        for (int x = 0; x < w; x++) {
            int y = 0;
            while (y < h) {
                if (isEdge(opaque, x, y, w, h, -1, 0)) {
                    int y0 = y;
                    while (y < h && isEdge(opaque, x, y, w, h, -1, 0)) y++;
                    float xL = x * uScale;
                    float u = (x + 0.5f) * uScale;
                    mesh.addQuad(
                            new float[]{xL, 1f - y0 * vScale, FRONT_Z, xL, 1f - y * vScale, FRONT_Z, xL, 1f - y * vScale, BACK_Z, xL, 1f - y0 * vScale, BACK_Z},
                            new float[]{u - EPS, y0 * vScale, u - EPS, y * vScale, u + EPS, y * vScale, u + EPS, y0 * vScale},
                            new float[]{-1f, 0f, 0f});
                } else {
                    y++;
                }
            }
        }
        // Right boundary
        for (int x = 0; x < w; x++) {
            int y = 0;
            while (y < h) {
                if (isEdge(opaque, x, y, w, h, 1, 0)) {
                    int y0 = y;
                    while (y < h && isEdge(opaque, x, y, w, h, 1, 0)) y++;
                    float xR = (x + 1) * uScale;
                    float u = (x + 0.5f) * uScale;
                    mesh.addQuad(
                            new float[]{xR, 1f - y * vScale, FRONT_Z, xR, 1f - y0 * vScale, FRONT_Z, xR, 1f - y0 * vScale, BACK_Z, xR, 1f - y * vScale, BACK_Z},
                            new float[]{u + EPS, y * vScale, u + EPS, y0 * vScale, u - EPS, y0 * vScale, u - EPS, y * vScale},
                            new float[]{1f, 0f, 0f});
                } else {
                    y++;
                }
            }
        }
        return mesh;
    }

    private static boolean isEdge(boolean[][] opaque, int x, int y, int w, int h, int dx, int dy) {
        if (!opaque[x][y]) return false;
        int nx = x + dx;
        int ny = y + dy;
        if (nx < 0 || ny < 0 || nx >= w || ny >= h) return true;
        return !opaque[nx][ny];
    }

    private void addQuad(float[] pos, float[] uv, float[] normal) {
        for (int i = 0; i < 12; i += 3) {
            min[0] = Math.min(min[0], pos[i]);
            min[1] = Math.min(min[1], pos[i + 1]);
            min[2] = Math.min(min[2], pos[i + 2]);
            max[0] = Math.max(max[0], pos[i]);
            max[1] = Math.max(max[1], pos[i + 1]);
            max[2] = Math.max(max[2], pos[i + 2]);
        }
        quads.add(new Quad(pos, uv, normal));
    }

    public void emit(PoseStack.Pose pose, VertexConsumer vc, int light, int overlay, boolean glint) {
        for (Quad q : quads) {
            for (int i = 0, j = 0, k = 0; i < 4; i++, j += 3, k += 2) {
                vc.addVertex(pose, q.pos()[j], q.pos()[j + 1], q.pos()[j + 2]);
                if (glint) {
                    vc.setColor(255, 255, 255, 255);
                } else {
                    vc.setColor(255, 255, 255, 255);
                }
                vc.setUv(q.uv()[k], q.uv()[k + 1]);
                vc.setOverlay(overlay);
                vc.setLight(light);
                vc.setNormal(pose, q.normal()[0], q.normal()[1], q.normal()[2]);
            }
        }
    }

    public RenderType renderType(Identifier textureId) {
        return RenderTypes.entityCutout(textureId, false);
    }

    public Supplier<org.joml.Vector3fc[]> extents() {
        if (extentsSupplier == null) {
            org.joml.Vector3fc[] corners = new org.joml.Vector3fc[]{
                    new org.joml.Vector3f(min[0], min[1], min[2]),
                    new org.joml.Vector3f(max[0], max[1], max[2])
            };
            extentsSupplier = () -> corners;
        }
        return extentsSupplier;
    }
}
