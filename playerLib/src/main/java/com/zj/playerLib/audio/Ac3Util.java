package com.zj.playerLib.audio;

import com.zj.playerLib.Format;
import com.zj.playerLib.drm.DrmInitData;
import com.zj.playerLib.util.ParsableBitArray;
import com.zj.playerLib.util.ParsableByteArray;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;

public final class Ac3Util {
    public static final int TRUEHD_RECHUNK_SAMPLE_COUNT = 16;
    public static final int TRUEHD_SYNCFRAME_PREFIX_LENGTH = 10;
    private static final int AUDIO_SAMPLES_PER_AUDIO_BLOCK = 256;
    private static final int AC3_SYNCFRAME_AUDIO_SAMPLE_COUNT = 1536;
    private static final int[] BLOCKS_PER_SYNCFRAME_BY_NUMBLKSCOD = new int[]{1, 2, 3, 6};
    private static final int[] SAMPLE_RATE_BY_FSCOD = new int[]{48000, 44100, 32000};
    private static final int[] SAMPLE_RATE_BY_FSCOD2 = new int[]{24000, 22050, 16000};
    private static final int[] CHANNEL_COUNT_BY_ACMOD = new int[]{2, 1, 2, 3, 3, 4, 4, 5};
    private static final int[] BITRATE_BY_HALF_FRMSIZECOD = new int[]{32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384, 448, 512, 576, 640};
    private static final int[] SYNCFRAME_SIZE_WORDS_BY_HALF_FRMSIZECOD_44_1 = new int[]{69, 87, 104, 121, 139, 174, 208, 243, 278, 348, 417, 487, 557, 696, 835, 975, 1114, 1253, 1393};

    public static Format parseAc3AnnexFFormat(ParsableByteArray data, String trackId, String language, DrmInitData drmInitData) {
        int fscod = (data.readUnsignedByte() & 192) >> 6;
        int sampleRate = SAMPLE_RATE_BY_FSCOD[fscod];
        int nextByte = data.readUnsignedByte();
        int channelCount = CHANNEL_COUNT_BY_ACMOD[(nextByte & 56) >> 3];
        if ((nextByte & 4) != 0) {
            ++channelCount;
        }

        return Format.createAudioSampleFormat(trackId, "audio/ac3", null, -1, -1, channelCount, sampleRate, null, drmInitData, 0, language);
    }

    public static Format parseEAc3AnnexFFormat(ParsableByteArray data, String trackId, String language, DrmInitData drmInitData) {
        data.skipBytes(2);
        int fscod = (data.readUnsignedByte() & 192) >> 6;
        int sampleRate = SAMPLE_RATE_BY_FSCOD[fscod];
        int nextByte = data.readUnsignedByte();
        int channelCount = CHANNEL_COUNT_BY_ACMOD[(nextByte & 14) >> 1];
        if ((nextByte & 1) != 0) {
            ++channelCount;
        }

        nextByte = data.readUnsignedByte();
        int numDepSub = (nextByte & 30) >> 1;
        if (numDepSub > 0) {
            int lowByteChanLoc = data.readUnsignedByte();
            if ((lowByteChanLoc & 2) != 0) {
                channelCount += 2;
            }
        }

        String mimeType = "audio/eac3";
        if (data.bytesLeft() > 0) {
            nextByte = data.readUnsignedByte();
            if ((nextByte & 1) != 0) {
                mimeType = "audio/eac3-joc";
            }
        }
        return Format.createAudioSampleFormat(trackId, mimeType, null, -1, -1, channelCount, sampleRate, null, drmInitData, 0, language);
    }

    public static SyncFrameInfo parseAc3SyncframeInfo(ParsableBitArray data) {
        int initialPosition = data.getPosition();
        data.skipBits(40);
        boolean isEac3 = data.readBits(5) == 16;
        data.setPosition(initialPosition);
        int streamType = -1;
        String mimeType;
        int sampleRate;
        int acmod;
        int frameSize;
        int sampleCount;
        boolean lfeon;
        int channelCount;
        int fscod;
        int audioBlocks;
        if (isEac3) {
            data.skipBits(16);
            switch(data.readBits(2)) {
            case 0:
                streamType = 0;
                break;
            case 1:
                streamType = 1;
                break;
            case 2:
                streamType = 2;
                break;
            default:
                streamType = -1;
            }

            data.skipBits(3);
            frameSize = (data.readBits(11) + 1) * 2;
            fscod = data.readBits(2);
            int numblkscod;
            if (fscod == 3) {
                numblkscod = 3;
                sampleRate = SAMPLE_RATE_BY_FSCOD2[data.readBits(2)];
                audioBlocks = 6;
            } else {
                numblkscod = data.readBits(2);
                audioBlocks = BLOCKS_PER_SYNCFRAME_BY_NUMBLKSCOD[numblkscod];
                sampleRate = SAMPLE_RATE_BY_FSCOD[fscod];
            }

            sampleCount = 256 * audioBlocks;
            acmod = data.readBits(3);
            lfeon = data.readBit();
            channelCount = CHANNEL_COUNT_BY_ACMOD[acmod] + (lfeon ? 1 : 0);
            data.skipBits(10);
            if (data.readBit()) {
                data.skipBits(8);
            }

            if (acmod == 0) {
                data.skipBits(5);
                if (data.readBit()) {
                    data.skipBits(8);
                }
            }

            if (streamType == 1 && data.readBit()) {
                data.skipBits(16);
            }

            int mixdef;
            if (data.readBit()) {
                if (acmod > 2) {
                    data.skipBits(2);
                }

                if ((acmod & 1) != 0 && acmod > 2) {
                    data.skipBits(6);
                }

                if ((acmod & 4) != 0) {
                    data.skipBits(6);
                }

                if (lfeon && data.readBit()) {
                    data.skipBits(5);
                }

                if (streamType == 0) {
                    if (data.readBit()) {
                        data.skipBits(6);
                    }

                    if (acmod == 0 && data.readBit()) {
                        data.skipBits(6);
                    }

                    if (data.readBit()) {
                        data.skipBits(6);
                    }

                    mixdef = data.readBits(2);
                    int blk;
                    if (mixdef == 1) {
                        data.skipBits(5);
                    } else if (mixdef == 2) {
                        data.skipBits(12);
                    } else if (mixdef == 3) {
                        blk = data.readBits(5);
                        if (data.readBit()) {
                            data.skipBits(5);
                            if (data.readBit()) {
                                data.skipBits(4);
                            }

                            if (data.readBit()) {
                                data.skipBits(4);
                            }

                            if (data.readBit()) {
                                data.skipBits(4);
                            }

                            if (data.readBit()) {
                                data.skipBits(4);
                            }

                            if (data.readBit()) {
                                data.skipBits(4);
                            }

                            if (data.readBit()) {
                                data.skipBits(4);
                            }

                            if (data.readBit()) {
                                data.skipBits(4);
                            }

                            if (data.readBit()) {
                                if (data.readBit()) {
                                    data.skipBits(4);
                                }

                                if (data.readBit()) {
                                    data.skipBits(4);
                                }
                            }
                        }

                        if (data.readBit()) {
                            data.skipBits(5);
                            if (data.readBit()) {
                                data.skipBits(7);
                                if (data.readBit()) {
                                    data.skipBits(8);
                                }
                            }
                        }

                        data.skipBits(8 * (blk + 2));
                        data.byteAlign();
                    }

                    if (acmod < 2) {
                        if (data.readBit()) {
                            data.skipBits(14);
                        }

                        if (acmod == 0 && data.readBit()) {
                            data.skipBits(14);
                        }
                    }

                    if (data.readBit()) {
                        if (numblkscod == 0) {
                            data.skipBits(5);
                        } else {
                            for(blk = 0; blk < audioBlocks; ++blk) {
                                if (data.readBit()) {
                                    data.skipBits(5);
                                }
                            }
                        }
                    }
                }
            }

            if (data.readBit()) {
                data.skipBits(5);
                if (acmod == 2) {
                    data.skipBits(4);
                }

                if (acmod >= 6) {
                    data.skipBits(2);
                }

                if (data.readBit()) {
                    data.skipBits(8);
                }

                if (acmod == 0 && data.readBit()) {
                    data.skipBits(8);
                }

                if (fscod < 3) {
                    data.skipBit();
                }
            }

            if (streamType == 0 && numblkscod != 3) {
                data.skipBit();
            }

            if (streamType == 2 && (numblkscod == 3 || data.readBit())) {
                data.skipBits(6);
            }

            mimeType = "audio/eac3";
            if (data.readBit()) {
                mixdef = data.readBits(6);
                if (mixdef == 1 && data.readBits(8) == 1) {
                    mimeType = "audio/eac3-joc";
                }
            }
        } else {
            mimeType = "audio/ac3";
            data.skipBits(32);
            fscod = data.readBits(2);
            audioBlocks = data.readBits(6);
            frameSize = getAc3SyncframeSize(fscod, audioBlocks);
            data.skipBits(8);
            acmod = data.readBits(3);
            if ((acmod & 1) != 0 && acmod != 1) {
                data.skipBits(2);
            }

            if ((acmod & 4) != 0) {
                data.skipBits(2);
            }

            if (acmod == 2) {
                data.skipBits(2);
            }

            sampleRate = SAMPLE_RATE_BY_FSCOD[fscod];
            sampleCount = 1536;
            lfeon = data.readBit();
            channelCount = CHANNEL_COUNT_BY_ACMOD[acmod] + (lfeon ? 1 : 0);
        }

        return new SyncFrameInfo(mimeType, streamType, channelCount, sampleRate, frameSize, sampleCount);
    }

    public static int parseAc3SyncframeSize(byte[] data) {
        if (data.length < 6) {
            return -1;
        } else {
            boolean isEac3 = (data[5] & 255) >> 3 == 16;
            int fscod;
            if (isEac3) {
                fscod = (data[2] & 7) << 8;
                fscod |= data[3] & 255;
                return (fscod + 1) * 2;
            } else {
                fscod = (data[4] & 192) >> 6;
                int frmsizecod = data[4] & 63;
                return getAc3SyncframeSize(fscod, frmsizecod);
            }
        }
    }

    public static int getAc3SyncframeAudioSampleCount() {
        return 1536;
    }

    public static int parseEAc3SyncframeAudioSampleCount(ByteBuffer buffer) {
        int fscod = (buffer.get(buffer.position() + 4) & 192) >> 6;
        return 256 * (fscod == 3 ? 6 : BLOCKS_PER_SYNCFRAME_BY_NUMBLKSCOD[(buffer.get(buffer.position() + 4) & 48) >> 4]);
    }

    public static int findTrueHdSyncframeOffset(ByteBuffer buffer) {
        int startIndex = buffer.position();
        int endIndex = buffer.limit() - 10;

        for(int i = startIndex; i <= endIndex; ++i) {
            if ((buffer.getInt(i + 4) & -16777217) == -1167101192) {
                return i - startIndex;
            }
        }

        return -1;
    }

    public static int parseTrueHdSyncframeAudioSampleCount(byte[] syncframe) {
        if (syncframe[4] == -8 && syncframe[5] == 114 && syncframe[6] == 111 && (syncframe[7] & 254) == 186) {
            boolean isMlp = (syncframe[7] & 255) == 187;
            return 40 << (syncframe[isMlp ? 9 : 8] >> 4 & 7);
        } else {
            return 0;
        }
    }

    public static int parseTrueHdSyncframeAudioSampleCount(ByteBuffer buffer, int offset) {
        boolean isMlp = (buffer.get(buffer.position() + offset + 7) & 255) == 187;
        return 40 << (buffer.get(buffer.position() + offset + (isMlp ? 9 : 8)) >> 4 & 7);
    }

    private static int getAc3SyncframeSize(int fscod, int frmsizecod) {
        int halfFrmsizecod = frmsizecod / 2;
        if (fscod >= 0 && fscod < SAMPLE_RATE_BY_FSCOD.length && frmsizecod >= 0 && halfFrmsizecod < SYNCFRAME_SIZE_WORDS_BY_HALF_FRMSIZECOD_44_1.length) {
            int sampleRate = SAMPLE_RATE_BY_FSCOD[fscod];
            if (sampleRate == 44100) {
                return 2 * (SYNCFRAME_SIZE_WORDS_BY_HALF_FRMSIZECOD_44_1[halfFrmsizecod] + frmsizecod % 2);
            } else {
                int bitrate = BITRATE_BY_HALF_FRMSIZECOD[halfFrmsizecod];
                return sampleRate == 32000 ? 6 * bitrate : 4 * bitrate;
            }
        } else {
            return -1;
        }
    }

    private Ac3Util() {
    }

    public static final class SyncFrameInfo {
        public static final int STREAM_TYPE_UNDEFINED = -1;
        public static final int STREAM_TYPE_TYPE0 = 0;
        public static final int STREAM_TYPE_TYPE1 = 1;
        public static final int STREAM_TYPE_TYPE2 = 2;
        public final String mimeType;
        public final int streamType;
        public final int sampleRate;
        public final int channelCount;
        public final int frameSize;
        public final int sampleCount;

        private SyncFrameInfo(String mimeType, int streamType, int channelCount, int sampleRate, int frameSize, int sampleCount) {
            this.mimeType = mimeType;
            this.streamType = streamType;
            this.channelCount = channelCount;
            this.sampleRate = sampleRate;
            this.frameSize = frameSize;
            this.sampleCount = sampleCount;
        }

        @Documented
        @Retention(RetentionPolicy.SOURCE)
        public @interface StreamType {
        }
    }
}
