package com.zj.playerLib.extractor.mp4;

import androidx.annotation.Nullable;
import com.zj.playerLib.Format;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class Track {
    public static final int TRANSFORMATION_NONE = 0;
    public static final int TRANSFORMATION_CEA608_CDAT = 1;
    public final int id;
    public final int type;
    public final long timescale;
    public final long movieTimescale;
    public final long durationUs;
    public final Format format;
    public final int sampleTransformation;
    @Nullable
    public final long[] editListDurations;
    @Nullable
    public final long[] editListMediaTimes;
    public final int nalUnitLengthFieldLength;
    @Nullable
    private final TrackEncryptionBox[] sampleDescriptionEncryptionBoxes;

    public Track(int id, int type, long timescale, long movieTimescale, long durationUs, Format format, int sampleTransformation, @Nullable TrackEncryptionBox[] sampleDescriptionEncryptionBoxes, int nalUnitLengthFieldLength, @Nullable long[] editListDurations, @Nullable long[] editListMediaTimes) {
        this.id = id;
        this.type = type;
        this.timescale = timescale;
        this.movieTimescale = movieTimescale;
        this.durationUs = durationUs;
        this.format = format;
        this.sampleTransformation = sampleTransformation;
        this.sampleDescriptionEncryptionBoxes = sampleDescriptionEncryptionBoxes;
        this.nalUnitLengthFieldLength = nalUnitLengthFieldLength;
        this.editListDurations = editListDurations;
        this.editListMediaTimes = editListMediaTimes;
    }

    public TrackEncryptionBox getSampleDescriptionEncryptionBox(int sampleDescriptionIndex) {
        return this.sampleDescriptionEncryptionBoxes == null ? null : this.sampleDescriptionEncryptionBoxes[sampleDescriptionIndex];
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    public @interface Transformation {
    }
}
