//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.source.chunk;

import com.zj.playerLib.extractor.DummyTrackOutput;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.source.SampleQueue;
import com.zj.playerLib.source.chunk.ChunkExtractorWrapper.TrackOutputProvider;
import com.zj.playerLib.util.Log;

public final class BaseMediaChunkOutput implements TrackOutputProvider {
    private static final String TAG = "BaseMediaChunkOutput";
    private final int[] trackTypes;
    private final SampleQueue[] sampleQueues;

    public BaseMediaChunkOutput(int[] trackTypes, SampleQueue[] sampleQueues) {
        this.trackTypes = trackTypes;
        this.sampleQueues = sampleQueues;
    }

    public TrackOutput track(int id, int type) {
        for(int i = 0; i < this.trackTypes.length; ++i) {
            if (type == this.trackTypes[i]) {
                return this.sampleQueues[i];
            }
        }

        Log.e("BaseMediaChunkOutput", "Unmatched track of type: " + type);
        return new DummyTrackOutput();
    }

    public int[] getWriteIndices() {
        int[] writeIndices = new int[this.sampleQueues.length];

        for(int i = 0; i < this.sampleQueues.length; ++i) {
            if (this.sampleQueues[i] != null) {
                writeIndices[i] = this.sampleQueues[i].getWriteIndex();
            }
        }

        return writeIndices;
    }

    public void setSampleOffsetUs(long sampleOffsetUs) {
        SampleQueue[] var3 = this.sampleQueues;
        int var4 = var3.length;

        for(int var5 = 0; var5 < var4; ++var5) {
            SampleQueue sampleQueue = var3[var5];
            if (sampleQueue != null) {
                sampleQueue.setSampleOffsetUs(sampleOffsetUs);
            }
        }

    }
}
