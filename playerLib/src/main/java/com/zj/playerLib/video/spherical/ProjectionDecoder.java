package com.zj.playerLib.video.spherical;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.ParsableBitArray;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.Util;
import com.zj.playerLib.video.spherical.Projection.Mesh;
import com.zj.playerLib.video.spherical.Projection.SubMesh;

import java.util.ArrayList;
import java.util.zip.Inflater;

public final class ProjectionDecoder {
    private static final int TYPE_YTMP = Util.getIntegerCodeForString("ytmp");
    private static final int TYPE_MSHP = Util.getIntegerCodeForString("mshp");
    private static final int TYPE_RAW = Util.getIntegerCodeForString("raw ");
    private static final int TYPE_DFL8 = Util.getIntegerCodeForString("dfl8");
    private static final int TYPE_MESH = Util.getIntegerCodeForString("mesh");
    private static final int TYPE_PROJ = Util.getIntegerCodeForString("proj");
    private static final int MAX_COORDINATE_COUNT = 10000;
    private static final int MAX_VERTEX_COUNT = 32000;
    private static final int MAX_TRIANGLE_INDICES = 128000;

    private ProjectionDecoder() {
    }

    @Nullable
    public static Projection decode(byte[] projectionData, int stereoMode) {
        ParsableByteArray input = new ParsableByteArray(projectionData);
        ArrayList<Mesh> meshes = null;

        try {
            meshes = isProj(input) ? parseProj(input) : parseMshp(input);
        } catch (ArrayIndexOutOfBoundsException ignored) {
        }

        if (meshes == null) {
            return null;
        } else {
            switch (meshes.size()) {
                case 0:
                default:
                    return null;
                case 1:
                    return new Projection(meshes.get(0), stereoMode);
                case 2:
                    return new Projection(meshes.get(0), meshes.get(1), stereoMode);
            }
        }
    }

    private static boolean isProj(ParsableByteArray input) {
        input.skipBytes(4);
        int type = input.readInt();
        input.setPosition(0);
        return type == TYPE_PROJ;
    }

    @Nullable
    private static ArrayList<Mesh> parseProj(ParsableByteArray input) {
        input.skipBytes(8);
        int position = input.getPosition();
        int limit = input.limit();

        while (position < limit) {
            int childEnd = position + input.readInt();
            if (childEnd > position && childEnd <= limit) {
                int childAtomType = input.readInt();
                if (childAtomType != TYPE_YTMP && childAtomType != TYPE_MSHP) {
                    position = childEnd;
                    input.setPosition(childEnd);
                    continue;
                }

                input.setLimit(childEnd);
                return parseMshp(input);
            }

            return null;
        }

        return null;
    }

    @Nullable
    private static ArrayList<Mesh> parseMshp(ParsableByteArray input) {
        int version = input.readUnsignedByte();
        if (version != 0) {
            return null;
        } else {
            input.skipBytes(7);
            int encoding = input.readInt();
            if (encoding == TYPE_DFL8) {
                ParsableByteArray output = new ParsableByteArray();
                Inflater inflater = new Inflater(true);

                try {
                    if (!Util.inflate(input, output, inflater)) {
                        return null;
                    }
                } finally {
                    inflater.end();
                }

                input = output;
            } else if (encoding != TYPE_RAW) {
                return null;
            }

            return parseRawMshpData(input);
        }
    }

    @Nullable
    private static ArrayList<Mesh> parseRawMshpData(ParsableByteArray input) {
        ArrayList<Mesh> meshes = new ArrayList<>();
        int position = input.getPosition();
        int limit = input.limit();

        while (position < limit) {
            int childEnd = position + input.readInt();
            if (childEnd <= position || childEnd > limit) {
                return null;
            }

            int childAtomType = input.readInt();
            if (childAtomType == TYPE_MESH) {
                Mesh mesh = parseMesh(input);
                if (mesh == null) {
                    return null;
                }

                meshes.add(mesh);
            }

            position = childEnd;
            input.setPosition(childEnd);
        }

        return meshes;
    }

    @Nullable
    private static Mesh parseMesh(ParsableByteArray input) {
        int coordinateCount = input.readInt();
        if (coordinateCount > 10000) {
            return null;
        } else {
            float[] coordinates = new float[coordinateCount];

            int vertexCount;
            for (vertexCount = 0; vertexCount < coordinateCount; ++vertexCount) {
                coordinates[vertexCount] = input.readFloat();
            }

            vertexCount = input.readInt();
            if (vertexCount > 32000) {
                return null;
            } else {
                double log2 = Math.log(2.0D);
                int coordinateCountSizeBits = (int) Math.ceil(Math.log(2.0D * (double) coordinateCount) / log2);
                ParsableBitArray bitInput = new ParsableBitArray(input.data);
                bitInput.setPosition(input.getPosition() * 8);
                float[] vertices = new float[vertexCount * 5];
                int[] coordinateIndices = new int[5];
                int vertexIndex = 0;

                int subMeshCount;
                for (subMeshCount = 0; subMeshCount < vertexCount; ++subMeshCount) {
                    for (int i = 0; i < 5; ++i) {
                        i = coordinateIndices[i] + decodeZigZag(bitInput.readBits(coordinateCountSizeBits));
                        if (i >= coordinateCount || i < 0) {
                            return null;
                        }

                        vertices[vertexIndex++] = coordinates[i];
                        coordinateIndices[i] = i;
                    }
                }

                bitInput.setPosition(bitInput.getPosition() + 7 & -8);
                subMeshCount = bitInput.readBits(32);
                SubMesh[] subMeshes = new SubMesh[subMeshCount];
                for (int i = 0; i < subMeshCount; ++i) {
                    int textureId = bitInput.readBits(8);
                    int drawMode = bitInput.readBits(8);
                    int triangleIndexCount = bitInput.readBits(32);
                    if (triangleIndexCount > 128000) {
                        return null;
                    }

                    int vertexCountSizeBits = (int) Math.ceil(Math.log(2.0D * (double) vertexCount) / log2);
                    int index = 0;
                    float[] triangleVertices = new float[triangleIndexCount * 3];
                    float[] textureCoords = new float[triangleIndexCount * 2];

                    for (int counter = 0; counter < triangleIndexCount; ++counter) {
                        index += decodeZigZag(bitInput.readBits(vertexCountSizeBits));
                        if (index < 0 || index >= vertexCount) {
                            return null;
                        }

                        triangleVertices[counter * 3] = vertices[index * 5];
                        triangleVertices[counter * 3 + 1] = vertices[index * 5 + 1];
                        triangleVertices[counter * 3 + 2] = vertices[index * 5 + 2];
                        textureCoords[counter * 2] = vertices[index * 5 + 3];
                        textureCoords[counter * 2 + 1] = vertices[index * 5 + 4];
                    }

                    subMeshes[i] = new SubMesh(textureId, triangleVertices, textureCoords, drawMode);
                }

                return new Mesh(subMeshes);
            }
        }
    }

    private static int decodeZigZag(int n) {
        return n >> 1 ^ -(n & 1);
    }
}
