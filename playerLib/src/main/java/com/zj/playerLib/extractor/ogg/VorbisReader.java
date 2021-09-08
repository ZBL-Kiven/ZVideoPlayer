//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.extractor.ogg;

import com.zj.playerLib.Format;
import com.zj.playerLib.ParserException;
import com.zj.playerLib.drm.DrmInitData;
import com.zj.playerLib.extractor.ogg.VorbisUtil.CommentHeader;
import com.zj.playerLib.extractor.ogg.VorbisUtil.Mode;
import com.zj.playerLib.extractor.ogg.VorbisUtil.VorbisIdHeader;
import com.zj.playerLib.util.ParsableByteArray;

import java.io.IOException;
import java.util.ArrayList;

final class VorbisReader extends StreamReader {
    private VorbisSetup vorbisSetup;
    private int previousPacketBlockSize;
    private boolean seenFirstAudioPacket;
    private VorbisIdHeader vorbisIdHeader;
    private CommentHeader commentHeader;

    VorbisReader() {
    }

    public static boolean verifyBitstreamType(ParsableByteArray data) {
        try {
            return VorbisUtil.verifyVorbisHeaderCapturePattern(1, data, true);
        } catch (ParserException var2) {
            return false;
        }
    }

    protected void reset(boolean headerData) {
        super.reset(headerData);
        if (headerData) {
            this.vorbisSetup = null;
            this.vorbisIdHeader = null;
            this.commentHeader = null;
        }

        this.previousPacketBlockSize = 0;
        this.seenFirstAudioPacket = false;
    }

    protected void onSeekEnd(long currentGranule) {
        super.onSeekEnd(currentGranule);
        this.seenFirstAudioPacket = currentGranule != 0L;
        this.previousPacketBlockSize = this.vorbisIdHeader != null ? this.vorbisIdHeader.blockSize0 : 0;
    }

    protected long preparePayload(ParsableByteArray packet) {
        if ((packet.data[0] & 1) == 1) {
            return -1L;
        } else {
            int packetBlockSize = decodeBlockSize(packet.data[0], this.vorbisSetup);
            int samplesInPacket = this.seenFirstAudioPacket ? (packetBlockSize + this.previousPacketBlockSize) / 4 : 0;
            appendNumberOfSamples(packet, (long)samplesInPacket);
            this.seenFirstAudioPacket = true;
            this.previousPacketBlockSize = packetBlockSize;
            return (long)samplesInPacket;
        }
    }

    protected boolean readHeaders(ParsableByteArray packet, long position, SetupData setupData) throws IOException, InterruptedException {
        if (this.vorbisSetup != null) {
            return false;
        } else {
            this.vorbisSetup = this.readSetupHeaders(packet);
            if (this.vorbisSetup == null) {
                return true;
            } else {
                ArrayList<byte[]> codecInitialisationData = new ArrayList();
                codecInitialisationData.add(this.vorbisSetup.idHeader.data);
                codecInitialisationData.add(this.vorbisSetup.setupHeaderData);
                setupData.format = Format.createAudioSampleFormat((String)null, "audio/vorbis", (String)null, this.vorbisSetup.idHeader.bitrateNominal, -1, this.vorbisSetup.idHeader.channels, (int)this.vorbisSetup.idHeader.sampleRate, codecInitialisationData, (DrmInitData)null, 0, (String)null);
                return true;
            }
        }
    }

    VorbisSetup readSetupHeaders(ParsableByteArray scratch) throws IOException {
        if (this.vorbisIdHeader == null) {
            this.vorbisIdHeader = VorbisUtil.readVorbisIdentificationHeader(scratch);
            return null;
        } else if (this.commentHeader == null) {
            this.commentHeader = VorbisUtil.readVorbisCommentHeader(scratch);
            return null;
        } else {
            byte[] setupHeaderData = new byte[scratch.limit()];
            System.arraycopy(scratch.data, 0, setupHeaderData, 0, scratch.limit());
            Mode[] modes = VorbisUtil.readVorbisModes(scratch, this.vorbisIdHeader.channels);
            int iLogModes = VorbisUtil.iLog(modes.length - 1);
            return new VorbisSetup(this.vorbisIdHeader, this.commentHeader, setupHeaderData, modes, iLogModes);
        }
    }

    static int readBits(byte src, int length, int leastSignificantBitIndex) {
        return src >> leastSignificantBitIndex & 255 >>> 8 - length;
    }

    static void appendNumberOfSamples(ParsableByteArray buffer, long packetSampleCount) {
        buffer.setLimit(buffer.limit() + 4);
        buffer.data[buffer.limit() - 4] = (byte)((int)(packetSampleCount & 255L));
        buffer.data[buffer.limit() - 3] = (byte)((int)(packetSampleCount >>> 8 & 255L));
        buffer.data[buffer.limit() - 2] = (byte)((int)(packetSampleCount >>> 16 & 255L));
        buffer.data[buffer.limit() - 1] = (byte)((int)(packetSampleCount >>> 24 & 255L));
    }

    private static int decodeBlockSize(byte firstByteOfAudioPacket, VorbisSetup vorbisSetup) {
        int modeNumber = readBits(firstByteOfAudioPacket, vorbisSetup.iLogModes, 1);
        int currentBlockSize;
        if (!vorbisSetup.modes[modeNumber].blockFlag) {
            currentBlockSize = vorbisSetup.idHeader.blockSize0;
        } else {
            currentBlockSize = vorbisSetup.idHeader.blockSize1;
        }

        return currentBlockSize;
    }

    static final class VorbisSetup {
        public final VorbisIdHeader idHeader;
        public final CommentHeader commentHeader;
        public final byte[] setupHeaderData;
        public final Mode[] modes;
        public final int iLogModes;

        public VorbisSetup(VorbisIdHeader idHeader, CommentHeader commentHeader, byte[] setupHeaderData, Mode[] modes, int iLogModes) {
            this.idHeader = idHeader;
            this.commentHeader = commentHeader;
            this.setupHeaderData = setupHeaderData;
            this.modes = modes;
            this.iLogModes = iLogModes;
        }
    }
}
