package com.zj.playerLib.extractor.mp4;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.ParsableByteArray;

import java.nio.ByteBuffer;
import java.util.UUID;

public final class PsshAtomUtil {
    private static final String TAG = "PsshAtomUtil";

    private PsshAtomUtil() {
    }

    public static byte[] buildPsshAtom(UUID systemId, @Nullable byte[] data) {
        return buildPsshAtom(systemId, null, data);
    }

    public static byte[] buildPsshAtom(UUID systemId, @Nullable UUID[] keyIds, @Nullable byte[] data) {
        int dataLength = data != null ? data.length : 0;
        int psshBoxLength = 32 + dataLength;
        if (keyIds != null) {
            psshBoxLength += 4 + keyIds.length * 16;
        }

        ByteBuffer psshBox = ByteBuffer.allocate(psshBoxLength);
        psshBox.putInt(psshBoxLength);
        psshBox.putInt(Atom.TYPE_pssh);
        psshBox.putInt(keyIds != null ? 16777216 : 0);
        psshBox.putLong(systemId.getMostSignificantBits());
        psshBox.putLong(systemId.getLeastSignificantBits());
        if (keyIds != null) {
            psshBox.putInt(keyIds.length);
            UUID[] var6 = keyIds;
            int var7 = keyIds.length;

            for(int var8 = 0; var8 < var7; ++var8) {
                UUID keyId = var6[var8];
                psshBox.putLong(keyId.getMostSignificantBits());
                psshBox.putLong(keyId.getLeastSignificantBits());
            }
        }

        if (data != null && data.length != 0) {
            psshBox.putInt(data.length);
            psshBox.put(data);
        }

        return psshBox.array();
    }

    public static boolean isPsshAtom(byte[] data) {
        return parsePsshAtom(data) != null;
    }

    @Nullable
    public static UUID parseUuid(byte[] atom) {
        PsshAtom parsedAtom = parsePsshAtom(atom);
        return parsedAtom == null ? null : parsedAtom.uuid;
    }

    public static int parseVersion(byte[] atom) {
        PsshAtom parsedAtom = parsePsshAtom(atom);
        return parsedAtom == null ? -1 : parsedAtom.version;
    }

    @Nullable
    public static byte[] parseSchemeSpecificData(byte[] atom, UUID uuid) {
        PsshAtom parsedAtom = parsePsshAtom(atom);
        if (parsedAtom == null) {
            return null;
        } else if (uuid != null && !uuid.equals(parsedAtom.uuid)) {
            Log.w("PsshAtomUtil", "UUID mismatch. Expected: " + uuid + ", got: " + parsedAtom.uuid + ".");
            return null;
        } else {
            return parsedAtom.schemeData;
        }
    }

    @Nullable
    private static PsshAtomUtil.PsshAtom parsePsshAtom(byte[] atom) {
        ParsableByteArray atomData = new ParsableByteArray(atom);
        if (atomData.limit() < 32) {
            return null;
        } else {
            atomData.setPosition(0);
            int atomSize = atomData.readInt();
            if (atomSize != atomData.bytesLeft() + 4) {
                return null;
            } else {
                int atomType = atomData.readInt();
                if (atomType != Atom.TYPE_pssh) {
                    return null;
                } else {
                    int atomVersion = Atom.parseFullAtomVersion(atomData.readInt());
                    if (atomVersion > 1) {
                        Log.w("PsshAtomUtil", "Unsupported pssh version: " + atomVersion);
                        return null;
                    } else {
                        UUID uuid = new UUID(atomData.readLong(), atomData.readLong());
                        int dataSize;
                        if (atomVersion == 1) {
                            dataSize = atomData.readUnsignedIntToInt();
                            atomData.skipBytes(16 * dataSize);
                        }

                        dataSize = atomData.readUnsignedIntToInt();
                        if (dataSize != atomData.bytesLeft()) {
                            return null;
                        } else {
                            byte[] data = new byte[dataSize];
                            atomData.readBytes(data, 0, dataSize);
                            return new PsshAtom(uuid, atomVersion, data);
                        }
                    }
                }
            }
        }
    }

    private static class PsshAtom {
        private final UUID uuid;
        private final int version;
        private final byte[] schemeData;

        public PsshAtom(UUID uuid, int version, byte[] schemeData) {
            this.uuid = uuid;
            this.version = version;
            this.schemeData = schemeData;
        }
    }
}
