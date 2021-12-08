package com.zj.playerLib.video.spherical;

import com.zj.playerLib.util.Assertions;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class Projection {
    public static final int DRAW_MODE_TRIANGLES = 0;
    public static final int DRAW_MODE_TRIANGLES_STRIP = 1;
    public static final int DRAW_MODE_TRIANGLES_FAN = 2;
    public static final int TEXTURE_COORDS_PER_VERTEX = 2;
    public static final int POSITION_COORDS_PER_VERTEX = 3;
    public final Mesh leftMesh;
    public final Mesh rightMesh;
    public final int stereoMode;
    public final boolean singleMesh;

    public static Projection createEquirectangular(int stereoMode) {
        return createEquirectangular(50.0F, 36, 72, 180.0F, 360.0F, stereoMode);
    }

    public static Projection createEquirectangular(float radius, int latitudes, int longitudes, float verticalFovDegrees, float horizontalFovDegrees, int stereoMode) {
        Assertions.checkArgument(radius > 0.0F);
        Assertions.checkArgument(latitudes >= 1);
        Assertions.checkArgument(longitudes >= 1);
        Assertions.checkArgument(verticalFovDegrees > 0.0F && verticalFovDegrees <= 180.0F);
        Assertions.checkArgument(horizontalFovDegrees > 0.0F && horizontalFovDegrees <= 360.0F);
        float verticalFovRads = (float)Math.toRadians(verticalFovDegrees);
        float horizontalFovRads = (float)Math.toRadians(horizontalFovDegrees);
        float quadHeightRads = verticalFovRads / (float)latitudes;
        float quadWidthRads = horizontalFovRads / (float)longitudes;
        int vertexCount = (2 * (longitudes + 1) + 2) * latitudes;
        float[] vertexData = new float[vertexCount * 3];
        float[] textureData = new float[vertexCount * 2];
        int vOffset = 0;
        int tOffset = 0;

        for(int j = 0; j < latitudes; ++j) {
            float phiLow = quadHeightRads * (float)j - verticalFovRads / 2.0F;
            float phiHigh = quadHeightRads * (float)(j + 1) - verticalFovRads / 2.0F;

            for(int i = 0; i < longitudes + 1; ++i) {
                for(int k = 0; k < 2; ++k) {
                    float phi = k == 0 ? phiLow : phiHigh;
                    float theta = quadWidthRads * (float)i + 3.1415927F - horizontalFovRads / 2.0F;
                    vertexData[vOffset++] = -((float)((double)radius * Math.sin(theta) * Math.cos(phi)));
                    vertexData[vOffset++] = (float)((double)radius * Math.sin(phi));
                    vertexData[vOffset++] = (float)((double)radius * Math.cos(theta) * Math.cos(phi));
                    textureData[tOffset++] = (float)i * quadWidthRads / horizontalFovRads;
                    textureData[tOffset++] = (float)(j + k) * quadHeightRads / verticalFovRads;
                    if (i == 0 && k == 0 || i == longitudes && k == 1) {
                        System.arraycopy(vertexData, vOffset - 3, vertexData, vOffset, 3);
                        vOffset += 3;
                        System.arraycopy(textureData, tOffset - 2, textureData, tOffset, 2);
                        tOffset += 2;
                    }
                }
            }
        }

        SubMesh subMesh = new SubMesh(0, vertexData, textureData, 1);
        return new Projection(new Mesh(subMesh), stereoMode);
    }

    public Projection(Mesh mesh, int stereoMode) {
        this(mesh, mesh, stereoMode);
    }

    public Projection(Mesh leftMesh, Mesh rightMesh, int stereoMode) {
        this.leftMesh = leftMesh;
        this.rightMesh = rightMesh;
        this.stereoMode = stereoMode;
        this.singleMesh = leftMesh == rightMesh;
    }

    public static final class Mesh {
        private final SubMesh[] subMeshes;

        public Mesh(SubMesh... subMeshes) {
            this.subMeshes = subMeshes;
        }

        public int getSubMeshCount() {
            return this.subMeshes.length;
        }

        public SubMesh getSubMesh(int index) {
            return this.subMeshes[index];
        }
    }

    public static final class SubMesh {
        public static final int VIDEO_TEXTURE_ID = 0;
        public final int textureId;
        public final int mode;
        public final float[] vertices;
        public final float[] textureCoords;

        public SubMesh(int textureId, float[] vertices, float[] textureCoords, int mode) {
            this.textureId = textureId;
            Assertions.checkArgument((long)vertices.length * 2L == (long)textureCoords.length * 3L);
            this.vertices = vertices;
            this.textureCoords = textureCoords;
            this.mode = mode;
        }

        public int getVertexCount() {
            return this.vertices.length / 3;
        }
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface DrawMode {
    }
}
