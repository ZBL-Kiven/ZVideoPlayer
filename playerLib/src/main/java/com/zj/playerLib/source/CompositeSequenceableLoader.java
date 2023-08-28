package com.zj.playerLib.source;

public class CompositeSequenceableLoader implements SequenceableLoader {
    protected final SequenceableLoader[] loaders;

    public CompositeSequenceableLoader(SequenceableLoader[] loaders) {
        this.loaders = loaders;
    }

    public final long getBufferedPositionUs() {
        long bufferedPositionUs = Long.MAX_VALUE;
        SequenceableLoader[] var3 = this.loaders;
        int var4 = var3.length;

        for(int var5 = 0; var5 < var4; ++var5) {
            SequenceableLoader loader = var3[var5];
            long loaderBufferedPositionUs = loader.getBufferedPositionUs();
            if (loaderBufferedPositionUs != -9223372036854775808L) {
                bufferedPositionUs = Math.min(bufferedPositionUs, loaderBufferedPositionUs);
            }
        }

        return bufferedPositionUs == Long.MAX_VALUE ? -9223372036854775808L : bufferedPositionUs;
    }

    public final long getNextLoadPositionUs() {
        long nextLoadPositionUs = Long.MAX_VALUE;
        SequenceableLoader[] var3 = this.loaders;
        int var4 = var3.length;

        for(int var5 = 0; var5 < var4; ++var5) {
            SequenceableLoader loader = var3[var5];
            long loaderNextLoadPositionUs = loader.getNextLoadPositionUs();
            if (loaderNextLoadPositionUs != -9223372036854775808L) {
                nextLoadPositionUs = Math.min(nextLoadPositionUs, loaderNextLoadPositionUs);
            }
        }

        return nextLoadPositionUs == Long.MAX_VALUE ? -9223372036854775808L : nextLoadPositionUs;
    }

    public final void reevaluateBuffer(long positionUs) {
        SequenceableLoader[] var3 = this.loaders;
        int var4 = var3.length;

        for(int var5 = 0; var5 < var4; ++var5) {
            SequenceableLoader loader = var3[var5];
            loader.reevaluateBuffer(positionUs);
        }

    }

    public boolean continueLoading(long positionUs) {
        boolean madeProgress = false;

        boolean madeProgressThisIteration;
        do {
            madeProgressThisIteration = false;
            long nextLoadPositionUs = this.getNextLoadPositionUs();
            if (nextLoadPositionUs == -9223372036854775808L) {
                break;
            }

            SequenceableLoader[] var7 = this.loaders;
            int var8 = var7.length;

            for(int var9 = 0; var9 < var8; ++var9) {
                SequenceableLoader loader = var7[var9];
                long loaderNextLoadPositionUs = loader.getNextLoadPositionUs();
                boolean isLoaderBehind = loaderNextLoadPositionUs != -9223372036854775808L && loaderNextLoadPositionUs <= positionUs;
                if (loaderNextLoadPositionUs == nextLoadPositionUs || isLoaderBehind) {
                    madeProgressThisIteration |= loader.continueLoading(positionUs);
                }
            }

            madeProgress |= madeProgressThisIteration;
        } while(madeProgressThisIteration);

        return madeProgress;
    }
}
