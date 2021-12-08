package com.zj.playerLib.extractor.mp4;

import com.zj.playerLib.util.Util;

final class FixedSampleSizeRechunker {
    private static final int MAX_SAMPLE_SIZE = 8192;

    public static Results rechunk(int fixedSampleSize, long[] chunkOffsets, int[] chunkSampleCounts, long timestampDeltaInTimeUnits) {
        int maxSampleCount = 8192 / fixedSampleSize;
        int rechunkedSampleCount = 0;
        int[] var7 = chunkSampleCounts;
        int var8 = chunkSampleCounts.length;

        int maximumSize;
        for(maximumSize = 0; maximumSize < var8; ++maximumSize) {
            int chunkSampleCount = var7[maximumSize];
            rechunkedSampleCount += Util.ceilDivide(chunkSampleCount, maxSampleCount);
        }

        long[] offsets = new long[rechunkedSampleCount];
        int[] sizes = new int[rechunkedSampleCount];
        maximumSize = 0;
        long[] timestamps = new long[rechunkedSampleCount];
        int[] flags = new int[rechunkedSampleCount];
        int originalSampleIndex = 0;
        int newSampleIndex = 0;

        for(int chunkIndex = 0; chunkIndex < chunkSampleCounts.length; ++chunkIndex) {
            int chunkSamplesRemaining = chunkSampleCounts[chunkIndex];

            for(long sampleOffset = chunkOffsets[chunkIndex]; chunkSamplesRemaining > 0; ++newSampleIndex) {
                int bufferSampleCount = Math.min(maxSampleCount, chunkSamplesRemaining);
                offsets[newSampleIndex] = sampleOffset;
                sizes[newSampleIndex] = fixedSampleSize * bufferSampleCount;
                maximumSize = Math.max(maximumSize, sizes[newSampleIndex]);
                timestamps[newSampleIndex] = timestampDeltaInTimeUnits * (long)originalSampleIndex;
                flags[newSampleIndex] = 1;
                sampleOffset += sizes[newSampleIndex];
                originalSampleIndex += bufferSampleCount;
                chunkSamplesRemaining -= bufferSampleCount;
            }
        }

        long duration = timestampDeltaInTimeUnits * (long)originalSampleIndex;
        return new Results(offsets, sizes, maximumSize, timestamps, flags, duration);
    }

    private FixedSampleSizeRechunker() {
    }

    public static final class Results {
        public final long[] offsets;
        public final int[] sizes;
        public final int maximumSize;
        public final long[] timestamps;
        public final int[] flags;
        public final long duration;

        private Results(long[] offsets, int[] sizes, int maximumSize, long[] timestamps, int[] flags, long duration) {
            this.offsets = offsets;
            this.sizes = sizes;
            this.maximumSize = maximumSize;
            this.timestamps = timestamps;
            this.flags = flags;
            this.duration = duration;
        }
    }
}
