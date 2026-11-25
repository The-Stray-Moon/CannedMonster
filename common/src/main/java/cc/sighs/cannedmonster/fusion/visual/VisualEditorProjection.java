package cc.sighs.cannedmonster.fusion.visual;

/**
 * Orthographic projection used by the preview overlay. It follows the same center/scale/camera
 * convention as {@code CanPreview}: model angle is the preview yaw, pitch tilts around X at the
 * entity origin, and half the body height is lifted afterwards in screen space, so guides land on
 * the same pixels as the rendered members at any pitch.
 *
 * <p>Preview cameras are fitted into a {@link Frame} that excludes the editor hint line and the
 * bottom toolbar, from either the body box (tooltips, legacy fitting) or the real composition
 * {@link Box}. Both the renderer and the overlay use the returned {@link Camera}, so the guides and
 * the model always share one origin and one scale.
 */
public final class VisualEditorProjection {
    public record Point(float x, float y) {}

    /** Integer GUI rectangle. */
    public record Frame(int left, int top, int right, int bottom) {
        public int width() { return Math.max(1, right - left); }

        public int height() { return Math.max(1, bottom - top); }

        public float centerX() { return (left + right) / 2.0F; }

        public float centerY() { return (top + bottom) / 2.0F; }

        public boolean contains(float x, float y) {
            return x >= left && x <= right && y >= top && y <= bottom;
        }
    }

    /** Axis aligned box in host body space: feet at {@code y = 0}, positive y up, x/z from the layout. */
    public record Box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        public Box {
            float x0 = Math.min(minX, maxX);
            float y0 = Math.min(minY, maxY);
            float z0 = Math.min(minZ, maxZ);
            minX = x0;
            minY = y0;
            minZ = z0;
            maxX = Math.max(minX, maxX);
            maxY = Math.max(minY, maxY);
            maxZ = Math.max(minZ, maxZ);
        }

        public static Box of(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            return new Box((float) minX, (float) minY, (float) minZ, (float) maxX, (float) maxY, (float) maxZ);
        }

        public Box union(Box other) {
            if (other == null) return this;
            return new Box(Math.min(minX, other.minX), Math.min(minY, other.minY), Math.min(minZ, other.minZ),
                    Math.max(maxX, other.maxX), Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
        }

        public float height() { return maxY - minY; }

        public float width() { return Math.max(maxX - minX, maxZ - minZ); }

        public float centerX() { return (minX + maxX) * 0.5F; }

        public float centerY() { return (minY + maxY) * 0.5F; }

        public float centerZ() { return (minZ + maxZ) * 0.5F; }
    }

    public record Camera(float centerX, float centerY, float scale, float bodyWidth, float bodyHeight,
                         float yaw, float pitch) {
        public Point project(float x, float y, float z) {
            float yawRad = (float) Math.toRadians(-yaw);
            float cosYaw = (float) Math.cos(yawRad);
            float sinYaw = (float) Math.sin(yawRad);
            float x1 = x * cosYaw - z * sinYaw;
            float z1 = x * sinYaw + z * cosYaw;

            float pitchRad = (float) Math.toRadians(pitch);
            float cosPitch = (float) Math.cos(pitchRad);
            float sinPitch = (float) Math.sin(pitchRad);
            float tiltedY = y * cosPitch - z1 * sinPitch;

            return new Point(centerX - scale * x1, centerY - scale * (tiltedY - bodyHeight * 0.5F));
        }
    }

    /** Fraction of the fitted frame kept free on every side by {@link #camera(int, int, int, int, float, float, float, float, float)}. */
    public static final float LEGACY_MARGIN = 0.075F;

    private VisualEditorProjection() {}

    /** Body-box fit used by tooltips; {@link #LEGACY_MARGIN} reproduces the former 0.85 factor. */
    public static Camera camera(int left, int top, int right, int bottom, float bodyWidth, float bodyHeight,
                                float yaw, float pitch, float zoom) {
        return bodyCamera(new Frame(left, top, right, bottom), bodyWidth, bodyHeight, yaw, pitch, zoom,
                LEGACY_MARGIN);
    }

    /**
     * Fits the host body box into {@code content}. The box is centred on the frame, matching the
     * entity-in-inventory convention used before the editor existed.
     */
    public static Camera bodyCamera(Frame content, float bodyWidth, float bodyHeight,
                                    float yaw, float pitch, float zoom, float margin) {
        float width = Math.max(0.01F, bodyWidth);
        float height = Math.max(0.01F, bodyHeight);
        float usableWidth = content.width() * usableFraction(margin);
        float usableHeight = content.height() * usableFraction(margin);
        float scale = Math.min(usableWidth / width, usableHeight / height) * zoomFactor(zoom);
        return new Camera(content.centerX(), content.centerY(), scale, width, height, yaw, pitch);
    }

    /**
     * Fits every corner of a body-space composition box into {@code content} at the current yaw and
     * pitch, so the projected model (including overhanging heads, beaks and feet) keeps the requested
     * margin. The returned camera is the same one the guides project with.
     */
    public static Camera boxCamera(Frame content, Box box, float bodyWidth, float bodyHeight,
                                   float yaw, float pitch, float zoom, float margin) {
        if (content == null || box == null || !(box.maxY() > box.minY())) return null;
        float width = Math.max(0.01F, bodyWidth);
        float height = Math.max(0.01F, bodyHeight);
        float yawRad = (float) Math.toRadians(-yaw);
        float cosYaw = (float) Math.cos(yawRad);
        float sinYaw = (float) Math.sin(yawRad);
        float pitchRad = (float) Math.toRadians(pitch);
        float cosPitch = (float) Math.cos(pitchRad);
        float sinPitch = (float) Math.sin(pitchRad);
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int corner = 0; corner < 8; corner++) {
            float x = (corner & 1) == 0 ? box.minX() : box.maxX();
            float y = (corner & 2) == 0 ? box.minY() : box.maxY();
            float z = (corner & 4) == 0 ? box.minZ() : box.maxZ();
            float planarX = x * cosYaw - z * sinYaw;
            float planarZ = x * sinYaw + z * cosYaw;
            float tiltedY = y * cosPitch - planarZ * sinPitch;
            minX = Math.min(minX, planarX);
            maxX = Math.max(maxX, planarX);
            minY = Math.min(minY, tiltedY);
            maxY = Math.max(maxY, tiltedY);
        }
        float usableWidth = content.width() * usableFraction(margin);
        float usableHeight = content.height() * usableFraction(margin);
        float scale = Math.min(usableWidth / Math.max(1.0E-3F, maxX - minX),
                usableHeight / Math.max(1.0E-3F, maxY - minY)) * zoomFactor(zoom);
        float planarCenter = (minX + maxX) * 0.5F;
        float tiltedCenter = (minY + maxY) * 0.5F;
        float centerX = content.centerX() + scale * planarCenter;
        float centerY = content.centerY() - scale * (height * 0.5F - tiltedCenter);
        if (!Float.isFinite(scale) || !(scale > 0.0F) || !Float.isFinite(centerX) || !Float.isFinite(centerY)) {
            return null;
        }
        return new Camera(centerX, centerY, scale, width, height, yaw, pitch);
    }

    /** Applies the same Z-then-Y-then-X order as {@code Quaternionf.rotateX().rotateY().rotateZ()}. */
    public static float[] rotate(VisualLayout.V rotation, float x, float y, float z) {
        float rx = (float) Math.toRadians(rotation.x());
        float ry = (float) Math.toRadians(rotation.y());
        float rz = (float) Math.toRadians(rotation.z());

        float cz = (float) Math.cos(rz);
        float sz = (float) Math.sin(rz);
        float x1 = x * cz - y * sz;
        float y1 = x * sz + y * cz;
        float z1 = z;

        float cy = (float) Math.cos(ry);
        float sy = (float) Math.sin(ry);
        float x2 = x1 * cy + z1 * sy;
        float y2 = y1;
        float z2 = -x1 * sy + z1 * cy;

        float cx = (float) Math.cos(rx);
        float sx = (float) Math.sin(rx);
        return new float[]{x2, y2 * cx - z2 * sx, y2 * sx + z2 * cx};
    }

    public static Point endpoint(Camera camera, float x, float y, float z, float dx, float dy, float dz,
                                 float length) {
        return camera.project(x + dx * length, y + dy * length, z + dz * length);
    }

    private static float usableFraction(float margin) {
        return Math.max(0.05F, 1.0F - 2.0F * Math.max(0.0F, margin));
    }

    private static float zoomFactor(float zoom) {
        return Math.max(0.01F, zoom);
    }
}
