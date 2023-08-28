package com.zj.playerLib.extractor.mp4;

import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.util.ParsableByteArray;

import java.io.IOException;

final class TrackFragment {
    public DefaultSampleValues header;
    public long atomPosition;
    public long dataPosition;
    public long auxiliaryDataPosition;
    public int trunCount;
    public int sampleCount;
    public long[] trunDataPosition;
    public int[] trunLength;
    public int[] sampleSizeTable;
    public int[] sampleCompositionTimeOffsetTable;
    public long[] sampleDecodingTimeTable;
    public boolean[] sampleIsSyncFrameTable;
    public boolean definesEncryptionData;
    public boolean[] sampleHasSubsampleEncryptionTable;
    public TrackEncryptionBox trackEncryptionBox;
    public int sampleEncryptionDataLength;
    public ParsableByteArray sampleEncryptionData;
    public boolean sampleEncryptionDataNeedsFill;
    public long nextFragmentDecodeTime;

    TrackFragment() {
    }

    public void reset() {
        this.trunCount = 0;
        this.nextFragmentDecodeTime = 0L;
        this.definesEncryptionData = false;
        this.sampleEncryptionDataNeedsFill = false;
        this.trackEncryptionBox = null;
    }

    public void initTables(int trunCount, int sampleCount) {
        this.trunCount = trunCount;
        this.sampleCount = sampleCount;
        if (this.trunLength == null || this.trunLength.length < trunCount) {
            this.trunDataPosition = new long[trunCount];
            this.trunLength = new int[trunCount];
        }

        if (this.sampleSizeTable == null || this.sampleSizeTable.length < sampleCount) {
            int tableSize = sampleCount * 125 / 100;
            this.sampleSizeTable = new int[tableSize];
            this.sampleCompositionTimeOffsetTable = new int[tableSize];
            this.sampleDecodingTimeTable = new long[tableSize];
            this.sampleIsSyncFrameTable = new boolean[tableSize];
            this.sampleHasSubsampleEncryptionTable = new boolean[tableSize];
        }

    }

    public void initEncryptionData(int length) {
        if (this.sampleEncryptionData == null || this.sampleEncryptionData.limit() < length) {
            this.sampleEncryptionData = new ParsableByteArray(length);
        }

        this.sampleEncryptionDataLength = length;
        this.definesEncryptionData = true;
        this.sampleEncryptionDataNeedsFill = true;
    }

    public void fillEncryptionData(ExtractorInput input) throws IOException, InterruptedException {
        input.readFully(this.sampleEncryptionData.data, 0, this.sampleEncryptionDataLength);
        this.sampleEncryptionData.setPosition(0);
        this.sampleEncryptionDataNeedsFill = false;
    }

    public void fillEncryptionData(ParsableByteArray source) {
        source.readBytes(this.sampleEncryptionData.data, 0, this.sampleEncryptionDataLength);
        this.sampleEncryptionData.setPosition(0);
        this.sampleEncryptionDataNeedsFill = false;
    }

    public long getSamplePresentationTime(int index) {
        return this.sampleDecodingTimeTable[index] + (long)this.sampleCompositionTimeOffsetTable[index];
    }

    public boolean sampleHasSubsampleEncryptionTable(int index) {
        return this.definesEncryptionData && this.sampleHasSubsampleEncryptionTable[index];
    }
}
