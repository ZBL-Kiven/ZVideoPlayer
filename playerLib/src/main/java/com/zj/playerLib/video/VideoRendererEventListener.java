//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.video;

import android.os.Handler;
import android.view.Surface;

import androidx.annotation.Nullable;

import com.zj.playerLib.Format;
import com.zj.playerLib.decoder.DecoderCounters;
import com.zj.playerLib.util.Assertions;

public interface VideoRendererEventListener {
    default void onVideoEnabled(DecoderCounters counters) {
    }

    default void onVideoDecoderInitialized(String decoderName, long initializedTimestampMs, long initializationDurationMs) {
    }

    default void onVideoInputFormatChanged(Format format) {
    }

    default void onDroppedFrames(int count, long elapsedMs) {
    }

    default void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
    }

    default void onRenderedFirstFrame(@Nullable Surface surface) {
    }

    default void onVideoDisabled(DecoderCounters counters) {
    }

    public static final class EventDispatcher {
        @Nullable
        private final Handler handler;
        @Nullable
        private final VideoRendererEventListener listener;

        public EventDispatcher(@Nullable Handler handler, @Nullable VideoRendererEventListener listener) {
            this.handler = listener != null ? (Handler)Assertions.checkNotNull(handler) : null;
            this.listener = listener;
        }

        public void enabled(DecoderCounters decoderCounters) {
            if (this.listener != null) {
                this.handler.post(() -> {
                    this.listener.onVideoEnabled(decoderCounters);
                });
            }

        }

        public void decoderInitialized(String decoderName, long initializedTimestampMs, long initializationDurationMs) {
            if (this.listener != null) {
                this.handler.post(() -> {
                    this.listener.onVideoDecoderInitialized(decoderName, initializedTimestampMs, initializationDurationMs);
                });
            }

        }

        public void inputFormatChanged(Format format) {
            if (this.listener != null) {
                this.handler.post(() -> {
                    this.listener.onVideoInputFormatChanged(format);
                });
            }

        }

        public void droppedFrames(int droppedFrameCount, long elapsedMs) {
            if (this.listener != null) {
                this.handler.post(() -> {
                    this.listener.onDroppedFrames(droppedFrameCount, elapsedMs);
                });
            }

        }

        public void videoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
            if (this.listener != null) {
                this.handler.post(() -> {
                    this.listener.onVideoSizeChanged(width, height, unappliedRotationDegrees, pixelWidthHeightRatio);
                });
            }

        }

        public void renderedFirstFrame(@Nullable Surface surface) {
            if (this.listener != null) {
                this.handler.post(() -> {
                    this.listener.onRenderedFirstFrame(surface);
                });
            }

        }

        public void disabled(DecoderCounters counters) {
            if (this.listener != null) {
                this.handler.post(() -> {
                    counters.ensureUpdated();
                    this.listener.onVideoDisabled(counters);
                });
            }

        }
    }
}
