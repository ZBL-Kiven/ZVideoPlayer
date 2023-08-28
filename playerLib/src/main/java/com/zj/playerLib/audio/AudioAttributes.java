package com.zj.playerLib.audio;

import android.annotation.TargetApi;

import androidx.annotation.Nullable;

public final class AudioAttributes {
    public static final AudioAttributes DEFAULT = (new Builder()).build();
    public final int contentType;
    public final int flags;
    public final int usage;
    @Nullable
    private android.media.AudioAttributes audioAttributesV21;

    private AudioAttributes(int contentType, int flags, int usage) {
        this.contentType = contentType;
        this.flags = flags;
        this.usage = usage;
    }

    @TargetApi(21)
    public android.media.AudioAttributes getAudioAttributesV21() {
        if (this.audioAttributesV21 == null) {
            this.audioAttributesV21 = (new android.media.AudioAttributes.Builder()).setContentType(this.contentType).setFlags(this.flags).setUsage(this.usage).build();
        }

        return this.audioAttributesV21;
    }

    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            AudioAttributes other = (AudioAttributes)obj;
            return this.contentType == other.contentType && this.flags == other.flags && this.usage == other.usage;
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = 17;
        result = 31 * result + this.contentType;
        result = 31 * result + this.flags;
        result = 31 * result + this.usage;
        return result;
    }

    public static final class Builder {
        private int contentType = 0;
        private int flags = 0;
        private int usage = 1;

        public Builder() {
        }

        public Builder setContentType(int contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder setFlags(int flags) {
            this.flags = flags;
            return this;
        }

        public Builder setUsage(int usage) {
            this.usage = usage;
            return this;
        }

        public AudioAttributes build() {
            return new AudioAttributes(this.contentType, this.flags, this.usage);
        }
    }
}
