package com.gtnewhorizons.angelica.models;

import com.gtnewhorizons.angelica.compat.nd.Quad;
import java.util.Arrays;

import com.gtnewhorizons.angelica.models.material.RenderMaterial;
import com.gtnewhorizons.angelica.models.material.RenderMaterialImpl;
import com.gtnewhorizons.angelica.models.renderer.IndigoRenderer;
import me.jellysquid.mods.sodium.client.render.pipeline.BlockRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import static com.gtnewhorizons.angelica.models.EncodingFormat.*;

public class NdQuadBuilder {

    // x, y, z, u, v, color, normal, lightmap
    public static final int INTS_PER_VERTEX = 8;
    public static final int QUAD_STRIDE = INTS_PER_VERTEX * 4;
    public static final int X_INDEX = 0;
    public static final int Y_INDEX = 1;
    public static final int Z_INDEX = 2;
    public static final int U_INDEX = 3;
    public static final int V_INDEX = 4;
    public static final int COLOR_INDEX = 5;
    public static final int NORMAL_INDEX = 6;
    public static final int LIGHTMAP_INDEX = 7;

    /**
     * Causes texture to appear with no rotation.
     * Pass in bakeFlags parameter to {@link #spriteBake(IIcon, int)}.
     */
    public static final int BAKE_ROTATE_NONE = 0;

    /**
     * Causes texture to appear rotated 90 deg. clockwise relative to nominal face.
     * Pass in bakeFlags parameter to {@link #spriteBake(IIcon, int)}.
     */
    public static final int BAKE_ROTATE_90 = 1;

    /**
     * Causes texture to appear rotated 180 deg. relative to nominal face.
     * Pass in bakeFlags parameter to {@link #spriteBake(IIcon, int)}.
     */
    public static final int BAKE_ROTATE_180 = 2;

    /**
     * Causes texture to appear rotated 270 deg. clockwise relative to nominal face.
     * Pass in bakeFlags parameter to {@link #spriteBake(IIcon, int)}.
     */
    public static final int BAKE_ROTATE_270 = 3;

    /**
     * When enabled, texture coordinate are assigned based on vertex position.
     * Any existing UV coordinates will be replaced.
     * Pass in bakeFlags parameter to {@link #spriteBake(IIcon, int)}.
     *
     * <p>UV lock always derives texture coordinates based on nominal face, even
     * when the quad is not co-planar with that face, and the result is
     * the same as if the quad were projected onto the nominal face, which
     * is usually the desired result.
     */
    public static final int BAKE_LOCK_UV = 4;

    /**
     * When set, U texture coordinates for the given sprite are
     * flipped as part of baking. Can be useful for some randomization
     * and texture mapping scenarios. Results are different from what
     * can be obtained via rotation and both can be applied.
     * Pass in bakeFlags parameter to {@link #spriteBake(IIcon, int)}.
     */
    public static final int BAKE_FLIP_U = 8;

    /**
     * Same as {@link #BAKE_FLIP_U} but for V coordinate.
     */
    public static final int BAKE_FLIP_V = 16;

    /**
     * UV coordinates by default are assumed to be 0-16 scale for consistency
     * with conventional Minecraft model format. This is scaled to 0-1 during
     * baking before interpolation. Model loaders that already have 0-1 coordinates
     * can avoid wasteful multiplication/division by passing 0-1 coordinates directly.
     * Pass in bakeFlags parameter to {@link #spriteBake(IIcon, int)}.
     */
    public static final int BAKE_NORMALIZED = 32;

    /**
     * Tolerance for determining if the depth parameter to {@link #square(ForgeDirection, float, float, float, float, float)}
     * is effectively zero - meaning the face is a cull face.
     */
    public static float CULL_FACE_EPSILON = 0.00001f;

    private ForgeDirection nominalFace = ForgeDirection.UNKNOWN;
    // It's called a header, but because I'm lazy it's at the end
    private final int[] data = new int[QUAD_STRIDE + HEADER_STRIDE];
    private boolean isGeometryInvalid = true;
    private int tag = 0;
    private int colorIndex = -1;
    final Vector3f faceNormal = new Vector3f();

    /**
     * Dumps to {@param out} and returns it.
     */
    public Quad build(Quad out) {

        /*out.setState(
            data,
            0,
            new BlockRenderer.Flags(true, false, this.colorIndex != -1, false),
            GL11.GL_QUADS,
            0,
            0,
            0
        );*/
        // FRAPI does this late, but we need to do it before baking to Nd quads
        this.computeGeometry();
        out.setRaw(this.data, this.hasShade(), this.cullFace(), this.colorIndex, this.data[QUAD_STRIDE + HEADER_BITS]);
        this.clear();
        return out;
    }

    public void clear() {

        Arrays.fill(this.data, 0);
        this.isGeometryInvalid = true;
        this.nominalFace = ForgeDirection.UNKNOWN;
        this.normalFlags(0);
        this.tag(0);
        this.colorIndex(-1);
        this.cullFace(null);
        this.material(IndigoRenderer.MATERIAL_STANDARD);
    }

    /**
     * Set vertex color in ARGB format (0xAARRGGBB).
     */
    public void color(int vertexIndex, int color) {
        data[vertexIndex * INTS_PER_VERTEX + COLOR_INDEX] = color;
    }

    /**
     * Convenience: set vertex color for all vertices at once.
     */
    public void color(int c0, int c1, int c2, int c3) {
        this.color(0, c0);
        this.color(1, c1);
        this.color(2, c2);
        this.color(3, c3);
    }

    /**
     * Value functions identically to {@link Quad#getColorIndex()} and is
     * used by renderer / model builder in same way. Default value is -1.
     */
    public void colorIndex(int colorIndex) {
        this.colorIndex = colorIndex;
    }

    private void computeGeometry() {
        if (isGeometryInvalid) {
            isGeometryInvalid = false;

            NormalHelper.computeFaceNormal(faceNormal, this);
            this.data[QUAD_STRIDE + HEADER_FACE_NORMAL] = NormalHelper.packNormal(this.faceNormal);

            // depends on face normal
            this.data[QUAD_STRIDE +HEADER_BITS] = EncodingFormat.lightFace(this.data[QUAD_STRIDE +HEADER_BITS], GeometryHelper.lightFace(this));

            // depends on light face
            this.data[QUAD_STRIDE +HEADER_BITS] = EncodingFormat.geometryFlags(this.data[QUAD_STRIDE +HEADER_BITS], GeometryHelper.computeShapeFlags(this));
        }
    }

    /**
     * If non-null, quad should not be rendered in-world if the
     * opposite face of a neighbor block occludes it.
     *
     * @see NdQuadBuilder#cullFace(ForgeDirection)
     */
    @Nullable
    public final ForgeDirection cullFace() {
        return EncodingFormat.cullFace(this.data[QUAD_STRIDE + HEADER_BITS]);
    }

    /**
     * If not {@link ForgeDirection#UNKNOWN}, quad is coplanar with a block face which, if known, simplifies
     * or shortcuts geometric analysis that might otherwise be needed.
     * Set to {@link ForgeDirection#UNKNOWN} if quad is not coplanar or if this is not known.
     * Also controls face culling during block rendering.
     *
     * <p>{@link ForgeDirection#UNKNOWN} by default.
     *
     * <p>When called with a non-{@link ForgeDirection#UNKNOWN} value, also sets {@link #nominalFace(ForgeDirection)}
     * to the same value.
     *
     * <p>This is different from the value reported by {@link Quad#getLightFace()}. That value
     * is computed based on face geometry and must be non-{@link ForgeDirection#UNKNOWN} in vanilla quads.
     * That computed value is returned by {@link #lightFace()}.
     */
    public void cullFace(@Nullable ForgeDirection dir) {

        this.data[QUAD_STRIDE + HEADER_BITS] = EncodingFormat.cullFace(this.data[QUAD_STRIDE + HEADER_BITS], dir);
        this.nominalFace(dir);
    }

    private boolean hasShade() {
        return !this.material().disableDiffuse();
    }

    /**
     * Equivalent to {@link Quad#getLightFace()}. This is the face used for vanilla lighting
     * calculations and will be the block face to which the quad is most closely aligned. Always
     * the same as cull face for quads that are on a block face, but never
     * {@link ForgeDirection#UNKNOWN} or null.
     */
    @NotNull
    ForgeDirection lightFace() {
        this.computeGeometry();
        return EncodingFormat.lightFace(this.data[QUAD_STRIDE + HEADER_BITS]);
    }

    /**
     * Retrieves the material serialized with the quad.
     */
    public final RenderMaterialImpl material() {
        return EncodingFormat.material(this.data[QUAD_STRIDE + HEADER_BITS]);
    }

    /**
     * Assigns a different material to this quad. Useful for transformation of
     * existing meshes because lighting and texture blending are controlled by material.
     */
    public final void material(RenderMaterial material) {
        if (material == null) {
            material = IndigoRenderer.MATERIAL_STANDARD;
        }

        this.data[QUAD_STRIDE + HEADER_BITS] = EncodingFormat.material(this.data[QUAD_STRIDE + HEADER_BITS], (RenderMaterialImpl) material);
    }

    /**
     * Provides a hint to renderer about the facing of this quad. Not required,
     * but if provided can shortcut some geometric analysis if the quad is parallel to a block face.
     * Should be the expected value of {@link #lightFace()}. Value will be confirmed
     * and if invalid the correct light face will be calculated.
     *
     * <p>Null by default, and set automatically by {@link #cullFace(ForgeDirection)}.
     *
     * <p>Models may also find this useful as the face for texture UV locking and rotation semantics.
     *
     * <p>Note: This value is not persisted independently when the quad is encoded.
     * When reading encoded quads, this value will always be the same as {@link #lightFace()}.
     */
    public void nominalFace(@Nullable ForgeDirection face) {
        nominalFace = face;
    }

    /**
     * See {@link #nominalFace(ForgeDirection)}
     */
    public ForgeDirection nominalFace() {
        return nominalFace;
    }

    /**
     * Sets the normal flags.
     */
    private void normalFlags(int flags) {
        this.data[QUAD_STRIDE + HEADER_BITS] =  EncodingFormat.normalFlags(this.data[QUAD_STRIDE + HEADER_BITS], flags);
    }

    /**
     * Sets the geometric vertex position for the given vertex,
     * relative to block origin, (0,0,0). Minecraft rendering is designed
     * for models that fit within a single block space and is recommended
     * that coordinates remain in the 0-1 range, with multi-block meshes
     * split into multiple per-block models.
     */
    public void pos(int vertexIndex, float x, float y, float z) {

        data[vertexIndex * INTS_PER_VERTEX + X_INDEX] = Float.floatToRawIntBits(x);
        data[vertexIndex * INTS_PER_VERTEX + Y_INDEX] = Float.floatToRawIntBits(y);
        data[vertexIndex * INTS_PER_VERTEX + Z_INDEX] = Float.floatToRawIntBits(z);
        isGeometryInvalid = true;
    }

    public Vector3f pos(int vertexIndex) {

        return new Vector3f(
            Float.intBitsToFloat(data[vertexIndex * INTS_PER_VERTEX + X_INDEX]),
            Float.intBitsToFloat(data[vertexIndex * INTS_PER_VERTEX + Y_INDEX]),
            Float.intBitsToFloat(data[vertexIndex * INTS_PER_VERTEX + Z_INDEX])
        );
    }

    /**
     * Convenience: access x, y, z by index 0-2.
     */
    float posByIndex(int vertexIndex, int coordinateIndex) {
        return Float.intBitsToFloat(data[vertexIndex * INTS_PER_VERTEX + X_INDEX + coordinateIndex]);
    }

    /**
     * Convienence, gets the icon from the block atlas. See {@link #spriteBake(IIcon, int)}
     */
    public void spriteBake(String spriteName, int bakeFlags) {

        final IIcon icon = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(spriteName);
        TexHelper.bakeSprite(this, icon, bakeFlags);
    }

    /**
     * Assigns sprite atlas u,v coordinates to this quad for the given sprite.
     * Can handle UV locking, rotation, interpolation, etc. Control this behavior
     * by passing additive combinations of the BAKE_ flags defined in this interface.
     */
    public void spriteBake(IIcon sprite, int bakeFlags) {
        TexHelper.bakeSprite(this, sprite, bakeFlags);
    }

    /**
     * Helper method to assign vertex coordinates for a square aligned with the given face.
     * Ensures that vertex order is consistent with vanilla convention. (Incorrect order can
     * lead to bad AO lighting unless enhanced lighting logic is available/enabled.)
     *
     * <p>Square will be parallel to the given face and coplanar with the face (and culled if the
     * face is occluded) if the depth parameter is approximately zero. See {@link #CULL_FACE_EPSILON}.
     *
     * <p>All coordinates should be normalized (0-1).
     *
     * <p>The directions for the faces assume you're looking at them. For top and bottom, they assume you're looking north.
     */
    public void square(ForgeDirection nominalFace, float left, float bottom, float right, float top, float depth) {
        if (Math.abs(depth) < CULL_FACE_EPSILON) {
            cullFace(nominalFace);
            depth = 0; // avoid any inconsistency for face quads
        } else {
            cullFace(null);
        }

        nominalFace(nominalFace);
        switch (nominalFace) {
            case UP:
                depth = 1 - depth;
                top = 1 - top;
                bottom = 1 - bottom;

            case DOWN:
                pos(0, left, depth, top);
                pos(1, left, depth, bottom);
                pos(2, right, depth, bottom);
                pos(3, right, depth, top);
                break;

            case EAST:
                depth = 1 - depth;
                left = 1 - left;
                right = 1 - right;

            case WEST:
                pos(0, depth, top, left);
                pos(1, depth, bottom, left);
                pos(2, depth, bottom, right);
                pos(3, depth, top, right);
                break;

            case SOUTH:
                depth = 1 - depth;
                left = 1 - left;
                right = 1 - right;

            case NORTH:
                pos(0, 1 - left, top, depth);
                pos(1, 1 - left, bottom, depth);
                pos(2, 1 - right, bottom, depth);
                pos(3, 1 - right, top, depth);
                break;
        }
    }

    /**
     * Encodes an integer tag with this quad that can later be retrieved via
     * {@link NdQuadBuilder#tag()}.  Useful for models that want to perform conditional
     * transformation or filtering on static meshes.
     */
    public void tag(int tag) {
        this.tag = tag;
    }

    /**
     * Retrieve horizontal texture coordinates.
     */
    public float u(int vertexIndex) {
        return Float.intBitsToFloat(data[vertexIndex * INTS_PER_VERTEX + U_INDEX]);
    }

    /**
     * Set texture coordinates.
     */
    public void uv(int vertexIndex, float u, float v) {

        data[vertexIndex * INTS_PER_VERTEX + U_INDEX] = Float.floatToRawIntBits(u);
        data[vertexIndex * INTS_PER_VERTEX + V_INDEX] = Float.floatToRawIntBits(v);
    }

    /**
     * Retrieve vertical texture coordinates.
     */
    public float v(int vertexIndex) {
        return Float.intBitsToFloat(data[vertexIndex * INTS_PER_VERTEX + V_INDEX]);
    }

    /**
     * Retrieve geometric position, x coordinate.
     */
    public float x(int vertexIndex) {
        return Float.intBitsToFloat(data[vertexIndex * INTS_PER_VERTEX + X_INDEX]);
    }

    /**
     * Retrieve geometric position, x coordinate.
     */
    public float y(int vertexIndex) {
        return Float.intBitsToFloat(data[vertexIndex * INTS_PER_VERTEX + Y_INDEX]);
    }

    /**
     * Retrieve geometric position, x coordinate.
     */
    public float z(int vertexIndex) {
        return Float.intBitsToFloat(data[vertexIndex * INTS_PER_VERTEX + Z_INDEX]);
    }

    /**
     * Modern Minecraft uses magic arrays to do this without breaking AO. This is the same thing, but without arrays.
     */
    public static Vector3f mapSideToVertex(Vector3f from, Vector3f to, int index, ForgeDirection side) {

        return switch (side) {
            case DOWN -> switch (index) {
                case 0 -> new Vector3f(from.x, from.y, to.z);
                case 1 -> new Vector3f(from.x, from.y, from.z);
                case 2 -> new Vector3f(to.x, from.y, from.z);
                case 3 -> new Vector3f(to.x, from.y, to.z);
                default -> throw new RuntimeException("Too many indices!");
            };
            case UP -> switch (index) {
                case 0 -> new Vector3f(from.x, to.y, from.z);
                case 1 -> new Vector3f(from.x, to.y, to.z);
                case 2 -> new Vector3f(to.x, to.y, to.z);
                case 3 -> new Vector3f(to.x, to.y, from.z);
                default -> throw new RuntimeException("Too many indices!");
            };
            case NORTH -> switch (index) {
                case 0 -> new Vector3f(to.x, to.y, from.z);
                case 1 -> new Vector3f(to.x, from.y, from.z);
                case 2 -> new Vector3f(from.x, from.y, from.z);
                case 3 -> new Vector3f(from.x, to.y, from.z);
                default -> throw new RuntimeException("Too many indices!");
            };
            case SOUTH -> switch (index) {
                case 0 -> new Vector3f(from.x, to.y, to.z);
                case 1 -> new Vector3f(from.x, from.y, to.z);
                case 2 -> new Vector3f(to.x, from.y, to.z);
                case 3 -> new Vector3f(to.x, to.y, to.z);
                default -> throw new RuntimeException("Too many indices!");
            };
            case WEST -> switch (index) {
                case 0 -> new Vector3f(from.x, to.y, from.z);
                case 1 -> new Vector3f(from.x, from.y, from.z);
                case 2 -> new Vector3f(from.x, from.y, to.z);
                case 3 -> new Vector3f(from.x, to.y, to.z);
                default -> throw new RuntimeException("Too many indices!");
            };
            case EAST -> switch (index) {
                case 0 -> new Vector3f(to.x, to.y, to.z);
                case 1 -> new Vector3f(to.x, from.y, to.z);
                case 2 -> new Vector3f(to.x, from.y, from.z);
                case 3 -> new Vector3f(to.x, to.y, from.z);
                default -> throw new RuntimeException("Too many indices!");
            };
            case UNKNOWN -> null;
        };
    }
}