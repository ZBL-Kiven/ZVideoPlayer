//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.extractor.mp4;

import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.Util;
import java.io.IOException;

final class Sniffer {
    private static final int SEARCH_LENGTH = 4096;
    private static final int[] COMPATIBLE_BRANDS = new int[]{Util.getIntegerCodeForString("isom"), Util.getIntegerCodeForString("iso2"), Util.getIntegerCodeForString("iso3"), Util.getIntegerCodeForString("iso4"), Util.getIntegerCodeForString("iso5"), Util.getIntegerCodeForString("iso6"), Util.getIntegerCodeForString("avc1"), Util.getIntegerCodeForString("hvc1"), Util.getIntegerCodeForString("hev1"), Util.getIntegerCodeForString("mp41"), Util.getIntegerCodeForString("mp42"), Util.getIntegerCodeForString("3g2a"), Util.getIntegerCodeForString("3g2b"), Util.getIntegerCodeForString("3gr6"), Util.getIntegerCodeForString("3gs6"), Util.getIntegerCodeForString("3ge6"), Util.getIntegerCodeForString("3gg6"), Util.getIntegerCodeForString("M4V "), Util.getIntegerCodeForString("M4A "), Util.getIntegerCodeForString("f4v "), Util.getIntegerCodeForString("kddi"), Util.getIntegerCodeForString("M4VP"), Util.getIntegerCodeForString("qt  "), Util.getIntegerCodeForString("MSNV")};

    public static boolean sniffFragmented(ExtractorInput input) throws IOException, InterruptedException {
        return sniffInternal(input, true);
    }

    public static boolean sniffUnfragmented(ExtractorInput input) throws IOException, InterruptedException {
        return sniffInternal(input, false);
    }

    private static boolean sniffInternal(ExtractorInput input, boolean fragmented) throws IOException, InterruptedException {
        long inputLength = input.getLength();
        int bytesToSearch = (int)(inputLength != -1L && inputLength <= 4096L ? inputLength : 4096L);
        ParsableByteArray buffer = new ParsableByteArray(64);
        int bytesSearched = 0;
        boolean foundGoodFileType = false;
        boolean isFragmented = false;

        while(bytesSearched < bytesToSearch) {
            int headerSize = 8;
            buffer.reset(headerSize);
            input.peekFully(buffer.data, 0, headerSize);
            long atomSize = buffer.readUnsignedInt();
            int atomType = buffer.readInt();
            if (atomSize == 1L) {
                headerSize = 16;
                input.peekFully(buffer.data, 8, 8);
                buffer.setLimit(16);
                atomSize = buffer.readUnsignedLongToLong();
            } else if (atomSize == 0L) {
                long endPosition = input.getLength();
                if (endPosition != -1L) {
                    atomSize = endPosition - input.getPosition() + (long)headerSize;
                }
            }

            if (atomSize < (long)headerSize) {
                return false;
            }

            bytesSearched += headerSize;
            if (atomType != Atom.TYPE_moov) {
                if (atomType == Atom.TYPE_moof || atomType == Atom.TYPE_mvex) {
                    isFragmented = true;
                    break;
                }

                if ((long)bytesSearched + atomSize - (long)headerSize >= (long)bytesToSearch) {
                    break;
                }

                int atomDataSize = (int)(atomSize - (long)headerSize);
                bytesSearched += atomDataSize;
                if (atomType != Atom.TYPE_ftyp) {
                    if (atomDataSize != 0) {
                        input.advancePeekPosition(atomDataSize);
                    }
                } else {
                    if (atomDataSize < 8) {
                        return false;
                    }

                    buffer.reset(atomDataSize);
                    input.peekFully(buffer.data, 0, atomDataSize);
                    int brandsCount = atomDataSize / 4;

                    for(int i = 0; i < brandsCount; ++i) {
                        if (i == 1) {
                            buffer.skipBytes(4);
                        } else if (isCompatibleBrand(buffer.readInt())) {
                            foundGoodFileType = true;
                            break;
                        }
                    }

                    if (!foundGoodFileType) {
                        return false;
                    }
                }
            }
        }

        return foundGoodFileType && fragmented == isFragmented;
    }

    private static boolean isCompatibleBrand(int brand) {
        if (brand >>> 8 == Util.getIntegerCodeForString("3gp")) {
            return true;
        } else {
            int[] var1 = COMPATIBLE_BRANDS;
            int var2 = var1.length;

            for(int var3 = 0; var3 < var2; ++var3) {
                int compatibleBrand = var1[var3];
                if (compatibleBrand == brand) {
                    return true;
                }
            }

            return false;
        }
    }

    private Sniffer() {
    }
}
