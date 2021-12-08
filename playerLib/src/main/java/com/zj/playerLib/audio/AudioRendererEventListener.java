package com.zj.playerLib.audio;

import android.os.Handler;
import androidx.annotation.Nullable;
import com.zj.playerLib.Format;
import com.zj.playerLib.decoder.DecoderCounters;
import com.zj.playerLib.util.Assertions;

public interface AudioRendererEventListener {
    default void onAudioEnabled(DecoderCounters counters) {
    }

    default void onAudioSessionId(int audioSessionId) {
    }

    default void onAudioDecoderInitialized(String decoderName, long initializedTimestampMs, long initializationDurationMs) {
    }

    default void onAudioInputFormatChanged(Format format) {
    }

    default void onAudioSinkUnderRun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
    }

    default void onAudioDisabled(DecoderCounters counters) {
    }

    final class EventDispatcher {
        @Nullable
        private final Handler handler;
        @Nullable
        private final AudioRendererEventListener listener;

        public EventDispatcher(@Nullable Handler handler, @Nullable AudioRendererEventListener listener) {
            this.handler = listener != null ? Assertions.checkNotNull(handler) : null;
            this.listener = listener;
        }

        public void enabled(DecoderCounters decoderCounters) {
            if (this.listener != null) {
                this.handler.post(() -> {
                    this.listener.onAudioEnabled(decoderCounters);
                });
            }

        }

        public void decoderInitialized(String decoderName, long initializedTimestampMs, long initializationDurationMs) {
            if (this.listener != null) {
                this.handler.post(() -> {
                    this.listener.onAudioDecoderInitialized(decoderName, initializedTimestampMs, initializationDurationMs);
                });
            }

        }

        public void inputFormatChanged(Format format) {
            if (this.listener != null) {
                this.handler.post(() -> {
                    this.listener.onAudioInputFormatChanged(format);
                });
            }

        }

        public void audioTrackUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
            if (this.listener != null) {
                this.handler.post(() -> {
                    this.listener.onAudioSinkUnderRun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
                });
            }

        }

        public void disabled(DecoderCounters counters) {
            if (this.listener != null) {
                this.handler.post(() -> {
                    counters.ensureUpdated();
                    this.listener.onAudioDisabled(counters);
                });
            }

        }

        public void audioSessionId(int audioSessionId) {
            if (this.listener != null) {
                this.handler.post(() -> {
                    this.listener.onAudioSessionId(audioSessionId);
                });
            }

        }
    }
}
