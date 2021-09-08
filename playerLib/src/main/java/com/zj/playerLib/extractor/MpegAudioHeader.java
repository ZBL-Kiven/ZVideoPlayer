//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.extractor;

public final class MpegAudioHeader {
    public static final int MAX_FRAME_SIZE_BYTES = 4096;
    private static final String[] MIME_TYPE_BY_LAYER = new String[]{"audio/mpeg-L1", "audio/mpeg-L2", "audio/mpeg"};
    private static final int[] SAMPLING_RATE_V1 = new int[]{44100, 48000, 32000};
    private static final int[] BITRATE_V1_L1 = new int[]{32000, 64000, 96000, 128000, 160000, 192000, 224000, 256000, 288000, 320000, 352000, 384000, 416000, 448000};
    private static final int[] BITRATE_V2_L1 = new int[]{32000, 48000, 56000, 64000, 80000, 96000, 112000, 128000, 144000, 160000, 176000, 192000, 224000, 256000};
    private static final int[] BITRATE_V1_L2 = new int[]{32000, 48000, 56000, 64000, 80000, 96000, 112000, 128000, 160000, 192000, 224000, 256000, 320000, 384000};
    private static final int[] BITRATE_V1_L3 = new int[]{32000, 40000, 48000, 56000, 64000, 80000, 96000, 112000, 128000, 160000, 192000, 224000, 256000, 320000};
    private static final int[] BITRATE_V2 = new int[]{8000, 16000, 24000, 32000, 40000, 48000, 56000, 64000, 80000, 96000, 112000, 128000, 144000, 160000};
    public int version;
    public String mimeType;
    public int frameSize;
    public int sampleRate;
    public int channels;
    public int bitrate;
    public int samplesPerFrame;

    public MpegAudioHeader() {
    }

    public static int getFrameSize(int header) {
        if ((header & -2097152) != -2097152) {
            return -1;
        } else {
            int version = header >>> 19 & 3;
            if (version == 1) {
                return -1;
            } else {
                int layer = header >>> 17 & 3;
                if (layer == 0) {
                    return -1;
                } else {
                    int bitrateIndex = header >>> 12 & 15;
                    if (bitrateIndex != 0 && bitrateIndex != 15) {
                        int samplingRateIndex = header >>> 10 & 3;
                        if (samplingRateIndex == 3) {
                            return -1;
                        } else {
                            int samplingRate = SAMPLING_RATE_V1[samplingRateIndex];
                            if (version == 2) {
                                samplingRate /= 2;
                            } else if (version == 0) {
                                samplingRate /= 4;
                            }

                            int padding = header >>> 9 & 1;
                            int bitrate;
                            if (layer == 3) {
                                bitrate = version == 3 ? BITRATE_V1_L1[bitrateIndex - 1] : BITRATE_V2_L1[bitrateIndex - 1];
                                return (12 * bitrate / samplingRate + padding) * 4;
                            } else {
                                if (version == 3) {
                                    bitrate = layer == 2 ? BITRATE_V1_L2[bitrateIndex - 1] : BITRATE_V1_L3[bitrateIndex - 1];
                                } else {
                                    bitrate = BITRATE_V2[bitrateIndex - 1];
                                }

                                return version == 3 ? 144 * bitrate / samplingRate + padding : (layer == 1 ? 72 : 144) * bitrate / samplingRate + padding;
                            }
                        }
                    } else {
                        return -1;
                    }
                }
            }
        }
    }

    public static boolean populateHeader(int headerData, MpegAudioHeader header) {
        if ((headerData & -2097152) != -2097152) {
            return false;
        } else {
            int version = headerData >>> 19 & 3;
            if (version == 1) {
                return false;
            } else {
                int layer = headerData >>> 17 & 3;
                if (layer == 0) {
                    return false;
                } else {
                    int bitrateIndex = headerData >>> 12 & 15;
                    if (bitrateIndex != 0 && bitrateIndex != 15) {
                        int samplingRateIndex = headerData >>> 10 & 3;
                        if (samplingRateIndex == 3) {
                            return false;
                        } else {
                            int sampleRate = SAMPLING_RATE_V1[samplingRateIndex];
                            if (version == 2) {
                                sampleRate /= 2;
                            } else if (version == 0) {
                                sampleRate /= 4;
                            }

                            int padding = headerData >>> 9 & 1;
                            int bitrate;
                            int frameSize;
                            int samplesPerFrame;
                            if (layer == 3) {
                                bitrate = version == 3 ? BITRATE_V1_L1[bitrateIndex - 1] : BITRATE_V2_L1[bitrateIndex - 1];
                                frameSize = (12 * bitrate / sampleRate + padding) * 4;
                                samplesPerFrame = 384;
                            } else if (version == 3) {
                                bitrate = layer == 2 ? BITRATE_V1_L2[bitrateIndex - 1] : BITRATE_V1_L3[bitrateIndex - 1];
                                samplesPerFrame = 1152;
                                frameSize = 144 * bitrate / sampleRate + padding;
                            } else {
                                bitrate = BITRATE_V2[bitrateIndex - 1];
                                samplesPerFrame = layer == 1 ? 576 : 1152;
                                frameSize = (layer == 1 ? 72 : 144) * bitrate / sampleRate + padding;
                            }

                            bitrate = 8 * frameSize * sampleRate / samplesPerFrame;
                            String mimeType = MIME_TYPE_BY_LAYER[3 - layer];
                            int channels = (headerData >> 6 & 3) == 3 ? 1 : 2;
                            header.setValues(version, mimeType, frameSize, sampleRate, channels, bitrate, samplesPerFrame);
                            return true;
                        }
                    } else {
                        return false;
                    }
                }
            }
        }
    }

    private void setValues(int version, String mimeType, int frameSize, int sampleRate, int channels, int bitrate, int samplesPerFrame) {
        this.version = version;
        this.mimeType = mimeType;
        this.frameSize = frameSize;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.bitrate = bitrate;
        this.samplesPerFrame = samplesPerFrame;
    }
}
