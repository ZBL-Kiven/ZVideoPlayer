//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.extractor.ogg;

import com.zj.playerLib.Format;
import com.zj.playerLib.drm.DrmInitData;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.Util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class OpusReader extends StreamReader {
    private static final int DEFAULT_SEEK_PRE_ROLL_SAMPLES = 3840;
    private static final int SAMPLE_RATE = 48000;
    private static final int OPUS_CODE = Util.getIntegerCodeForString("Opus");
    private static final byte[] OPUS_SIGNATURE = new byte[]{79, 112, 117, 115, 72, 101, 97, 100};
    private boolean headerRead;

    OpusReader() {
    }

    public static boolean verifyBitstreamType(ParsableByteArray data) {
        if (data.bytesLeft() < OPUS_SIGNATURE.length) {
            return false;
        } else {
            byte[] header = new byte[OPUS_SIGNATURE.length];
            data.readBytes(header, 0, OPUS_SIGNATURE.length);
            return Arrays.equals(header, OPUS_SIGNATURE);
        }
    }

    protected void reset(boolean headerData) {
        super.reset(headerData);
        if (headerData) {
            this.headerRead = false;
        }

    }

    protected long preparePayload(ParsableByteArray packet) {
        return this.convertTimeToGranule(this.getPacketDurationUs(packet.data));
    }

    protected boolean readHeaders(ParsableByteArray packet, long position, SetupData setupData) {
        if (!this.headerRead) {
            byte[] metadata = Arrays.copyOf(packet.data, packet.limit());
            int channelCount = metadata[9] & 255;
            int preskip = (metadata[11] & 255) << 8 | metadata[10] & 255;
            List<byte[]> initializationData = new ArrayList(3);
            initializationData.add(metadata);
            this.putNativeOrderLong(initializationData, preskip);
            this.putNativeOrderLong(initializationData, 3840);
            setupData.format = Format.createAudioSampleFormat((String)null, "audio/opus", (String)null, -1, -1, channelCount, 48000, initializationData, (DrmInitData)null, 0, (String)null);
            this.headerRead = true;
            return true;
        } else {
            boolean headerPacket = packet.readInt() == OPUS_CODE;
            packet.setPosition(0);
            return headerPacket;
        }
    }

    private void putNativeOrderLong(List<byte[]> initializationData, int samples) {
        long ns = (long)samples * 1000000000L / 48000L;
        byte[] array = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putLong(ns).array();
        initializationData.add(array);
    }

    private long getPacketDurationUs(byte[] packet) {
        int toc = packet[0] & 255;
        int frames;
        switch(toc & 3) {
        case 0:
            frames = 1;
            break;
        case 1:
        case 2:
            frames = 2;
            break;
        default:
            frames = packet[1] & 63;
        }

        int config = toc >> 3;
        int length = config & 3;
        if (config >= 16) {
            length = 2500 << length;
        } else if (config >= 12) {
            length = 10000 << (length & 1);
        } else if (length == 3) {
            length = 60000;
        } else {
            length = 10000 << length;
        }

        return (long)frames * (long)length;
    }
}
