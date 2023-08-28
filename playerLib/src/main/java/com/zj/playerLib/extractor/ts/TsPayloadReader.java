package com.zj.playerLib.extractor.ts;

import android.util.SparseArray;

import com.zj.playerLib.ParserException;
import com.zj.playerLib.extractor.ExtractorOutput;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.TimestampAdjuster;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.List;

public interface TsPayloadReader {
    int FLAG_PAYLOAD_UNIT_START_INDICATOR = 1;
    int FLAG_RANDOM_ACCESS_INDICATOR = 2;
    int FLAG_DATA_ALIGNMENT_INDICATOR = 4;

    void init(TimestampAdjuster var1, ExtractorOutput var2, TrackIdGenerator var3);

    void seek();

    void consume(ParsableByteArray var1, int var2) throws ParserException;

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @interface Flags {
    }

    final class TrackIdGenerator {
        private static final int ID_UNSET = -2147483648;
        private final String formatIdPrefix;
        private final int firstTrackId;
        private final int trackIdIncrement;
        private int trackId;
        private String formatId;

        public TrackIdGenerator(int firstTrackId, int trackIdIncrement) {
            this(-2147483648, firstTrackId, trackIdIncrement);
        }

        public TrackIdGenerator(int programNumber, int firstTrackId, int trackIdIncrement) {
            this.formatIdPrefix = programNumber != -2147483648 ? programNumber + "/" : "";
            this.firstTrackId = firstTrackId;
            this.trackIdIncrement = trackIdIncrement;
            this.trackId = -2147483648;
        }

        public void generateNewId() {
            this.trackId = this.trackId == -2147483648 ? this.firstTrackId : this.trackId + this.trackIdIncrement;
            this.formatId = this.formatIdPrefix + this.trackId;
        }

        public int getTrackId() {
            this.maybeThrowUninitializedError();
            return this.trackId;
        }

        public String getFormatId() {
            this.maybeThrowUninitializedError();
            return this.formatId;
        }

        private void maybeThrowUninitializedError() {
            if (this.trackId == -2147483648) {
                throw new IllegalStateException("generateNewId() must be called before retrieving ids.");
            }
        }
    }

    final class DvbSubtitleInfo {
        public final String language;
        public final int type;
        public final byte[] initializationData;

        public DvbSubtitleInfo(String language, int type, byte[] initializationData) {
            this.language = language;
            this.type = type;
            this.initializationData = initializationData;
        }
    }

    final class EsInfo {
        public final int streamType;
        public final String language;
        public final List<DvbSubtitleInfo> dvbSubtitleInfos;
        public final byte[] descriptorBytes;

        public EsInfo(int streamType, String language, List<DvbSubtitleInfo> dvbSubtitleInfos, byte[] descriptorBytes) {
            this.streamType = streamType;
            this.language = language;
            this.dvbSubtitleInfos = dvbSubtitleInfos == null ? Collections.emptyList() : Collections.unmodifiableList(dvbSubtitleInfos);
            this.descriptorBytes = descriptorBytes;
        }
    }

    interface Factory {
        SparseArray<TsPayloadReader> createInitialPayloadReaders();

        TsPayloadReader createPayloadReader(int var1, EsInfo var2);
    }
}
