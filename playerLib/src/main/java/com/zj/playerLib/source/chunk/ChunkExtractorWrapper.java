package com.zj.playerLib.source.chunk;

import android.util.SparseArray;
import androidx.annotation.Nullable;
import com.zj.playerLib.Format;
import com.zj.playerLib.extractor.DummyTrackOutput;
import com.zj.playerLib.extractor.Extractor;
import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.extractor.SeekMap;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.ParsableByteArray;
import java.io.IOException;

public final class ChunkExtractorWrapper implements ExtractorOutput {
    public final Extractor extractor;
    private final int primaryTrackType;
    private final Format primaryTrackManifestFormat;
    private final SparseArray<BindingTrackOutput> bindingTrackOutputs;
    private boolean extractorInitialized;
    private TrackOutputProvider trackOutputProvider;
    private long endTimeUs;
    private SeekMap seekMap;
    private Format[] sampleFormats;

    public ChunkExtractorWrapper(Extractor extractor, int primaryTrackType, Format primaryTrackManifestFormat) {
        this.extractor = extractor;
        this.primaryTrackType = primaryTrackType;
        this.primaryTrackManifestFormat = primaryTrackManifestFormat;
        this.bindingTrackOutputs = new SparseArray();
    }

    public SeekMap getSeekMap() {
        return this.seekMap;
    }

    public Format[] getSampleFormats() {
        return this.sampleFormats;
    }

    public void init(@Nullable ChunkExtractorWrapper.TrackOutputProvider trackOutputProvider, long startTimeUs, long endTimeUs) {
        this.trackOutputProvider = trackOutputProvider;
        this.endTimeUs = endTimeUs;
        if (!this.extractorInitialized) {
            this.extractor.init(this);
            if (startTimeUs != -Long.MAX_VALUE) {
                this.extractor.seek(0L, startTimeUs);
            }

            this.extractorInitialized = true;
        } else {
            this.extractor.seek(0L, startTimeUs == -Long.MAX_VALUE ? 0L : startTimeUs);

            for(int i = 0; i < this.bindingTrackOutputs.size(); ++i) {
                this.bindingTrackOutputs.valueAt(i).bind(trackOutputProvider, endTimeUs);
            }
        }

    }

    public TrackOutput track(int id, int type) {
        BindingTrackOutput bindingTrackOutput = this.bindingTrackOutputs.get(id);
        if (bindingTrackOutput == null) {
            Assertions.checkState(this.sampleFormats == null);
            bindingTrackOutput = new BindingTrackOutput(id, type, type == this.primaryTrackType ? this.primaryTrackManifestFormat : null);
            bindingTrackOutput.bind(this.trackOutputProvider, this.endTimeUs);
            this.bindingTrackOutputs.put(id, bindingTrackOutput);
        }

        return bindingTrackOutput;
    }

    public void endTracks() {
        Format[] sampleFormats = new Format[this.bindingTrackOutputs.size()];

        for(int i = 0; i < this.bindingTrackOutputs.size(); ++i) {
            sampleFormats[i] = this.bindingTrackOutputs.valueAt(i).sampleFormat;
        }

        this.sampleFormats = sampleFormats;
    }

    public void seekMap(SeekMap seekMap) {
        this.seekMap = seekMap;
    }

    private static final class BindingTrackOutput implements TrackOutput {
        private final int id;
        private final int type;
        private final Format manifestFormat;
        private final DummyTrackOutput dummyTrackOutput;
        public Format sampleFormat;
        private TrackOutput trackOutput;
        private long endTimeUs;

        public BindingTrackOutput(int id, int type, Format manifestFormat) {
            this.id = id;
            this.type = type;
            this.manifestFormat = manifestFormat;
            this.dummyTrackOutput = new DummyTrackOutput();
        }

        public void bind(TrackOutputProvider trackOutputProvider, long endTimeUs) {
            if (trackOutputProvider == null) {
                this.trackOutput = this.dummyTrackOutput;
            } else {
                this.endTimeUs = endTimeUs;
                this.trackOutput = trackOutputProvider.track(this.id, this.type);
                if (this.sampleFormat != null) {
                    this.trackOutput.format(this.sampleFormat);
                }

            }
        }

        public void format(Format format) {
            this.sampleFormat = this.manifestFormat != null ? format.copyWithManifestFormatInfo(this.manifestFormat) : format;
            this.trackOutput.format(this.sampleFormat);
        }

        public int sampleData(ExtractorInput input, int length, boolean allowEndOfInput) throws IOException, InterruptedException {
            return this.trackOutput.sampleData(input, length, allowEndOfInput);
        }

        public void sampleData(ParsableByteArray data, int length) {
            this.trackOutput.sampleData(data, length);
        }

        public void sampleMetadata(long timeUs, int flags, int size, int offset, CryptoData cryptoData) {
            if (this.endTimeUs != -Long.MAX_VALUE && timeUs >= this.endTimeUs) {
                this.trackOutput = this.dummyTrackOutput;
            }

            this.trackOutput.sampleMetadata(timeUs, flags, size, offset, cryptoData);
        }
    }

    public interface TrackOutputProvider {
        TrackOutput track(int var1, int var2);
    }
}
