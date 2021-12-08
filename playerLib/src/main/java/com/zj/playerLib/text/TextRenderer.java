package com.zj.playerLib.text;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Handler.Callback;
import androidx.annotation.Nullable;
import com.zj.playerLib.BaseRenderer;
import com.zj.playerLib.PlaybackException;
import com.zj.playerLib.Format;
import com.zj.playerLib.FormatHolder;
import com.zj.playerLib.drm.DrmSessionManager;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.MimeTypes;
import com.zj.playerLib.util.Util;
import java.util.Collections;
import java.util.List;

public final class TextRenderer extends BaseRenderer implements Callback {
    private static final int REPLACEMENT_STATE_NONE = 0;
    private static final int REPLACEMENT_STATE_SIGNAL_END_OF_STREAM = 1;
    private static final int REPLACEMENT_STATE_WAIT_END_OF_STREAM = 2;
    private static final int MSG_UPDATE_OUTPUT = 0;
    @Nullable
    private final Handler outputHandler;
    private final TextOutput output;
    private final SubtitleDecoderFactory decoderFactory;
    private final FormatHolder formatHolder;
    private boolean inputStreamEnded;
    private boolean outputStreamEnded;
    private int decoderReplacementState;
    private Format streamFormat;
    private SubtitleDecoder decoder;
    private SubtitleInputBuffer nextInputBuffer;
    private SubtitleOutputBuffer subtitle;
    private SubtitleOutputBuffer nextSubtitle;
    private int nextSubtitleEventIndex;

    public TextRenderer(TextOutput output, @Nullable Looper outputLooper) {
        this(output, outputLooper, SubtitleDecoderFactory.DEFAULT);
    }

    public TextRenderer(TextOutput output, @Nullable Looper outputLooper, SubtitleDecoderFactory decoderFactory) {
        super(3);
        this.output = Assertions.checkNotNull(output);
        this.outputHandler = outputLooper == null ? null : Util.createHandler(outputLooper, this);
        this.decoderFactory = decoderFactory;
        this.formatHolder = new FormatHolder();
    }

    public int supportsFormat(Format format) {
        if (this.decoderFactory.supportsFormat(format)) {
            return supportsFormatDrm(null, format.drmInitData) ? 4 : 2;
        } else {
            return MimeTypes.isText(format.sampleMimeType) ? 1 : 0;
        }
    }

    protected void onStreamChanged(Format[] formats, long offsetUs) throws PlaybackException {
        this.streamFormat = formats[0];
        if (this.decoder != null) {
            this.decoderReplacementState = 1;
        } else {
            this.decoder = this.decoderFactory.createDecoder(this.streamFormat);
        }

    }

    protected void onPositionReset(long positionUs, boolean joining) {
        this.clearOutput();
        this.inputStreamEnded = false;
        this.outputStreamEnded = false;
        if (this.decoderReplacementState != 0) {
            this.replaceDecoder();
        } else {
            this.releaseBuffers();
            this.decoder.flush();
        }

    }

    public void render(long positionUs, long elapsedRealtimeUs) throws PlaybackException {
        if (!this.outputStreamEnded) {
            if (this.nextSubtitle == null) {
                this.decoder.setPositionUs(positionUs);

                try {
                    this.nextSubtitle = this.decoder.dequeueOutputBuffer();
                } catch (SubtitleDecoderException var9) {
                    throw PlaybackException.createForRenderer(var9, this.getIndex());
                }
            }

            if (this.getState() == 2) {
                boolean textRendererNeedsUpdate = false;
                if (this.subtitle != null) {
                    for(long subtitleNextEventTimeUs = this.getNextEventTime(); subtitleNextEventTimeUs <= positionUs; textRendererNeedsUpdate = true) {
                        ++this.nextSubtitleEventIndex;
                        subtitleNextEventTimeUs = this.getNextEventTime();
                    }
                }

                if (this.nextSubtitle != null) {
                    if (this.nextSubtitle.isEndOfStream()) {
                        if (!textRendererNeedsUpdate && this.getNextEventTime() == Long.MAX_VALUE) {
                            if (this.decoderReplacementState == 2) {
                                this.replaceDecoder();
                            } else {
                                this.releaseBuffers();
                                this.outputStreamEnded = true;
                            }
                        }
                    } else if (this.nextSubtitle.timeUs <= positionUs) {
                        if (this.subtitle != null) {
                            this.subtitle.release();
                        }

                        this.subtitle = this.nextSubtitle;
                        this.nextSubtitle = null;
                        this.nextSubtitleEventIndex = this.subtitle.getNextEventTimeIndex(positionUs);
                        textRendererNeedsUpdate = true;
                    }
                }

                if (textRendererNeedsUpdate) {
                    this.updateOutput(this.subtitle.getCues(positionUs));
                }

                if (this.decoderReplacementState != 2) {
                    try {
                        while(!this.inputStreamEnded) {
                            if (this.nextInputBuffer == null) {
                                this.nextInputBuffer = this.decoder.dequeueInputBuffer();
                                if (this.nextInputBuffer == null) {
                                    return;
                                }
                            }

                            if (this.decoderReplacementState == 1) {
                                this.nextInputBuffer.setFlags(4);
                                this.decoder.queueInputBuffer(this.nextInputBuffer);
                                this.nextInputBuffer = null;
                                this.decoderReplacementState = 2;
                                return;
                            }

                            int result = this.readSource(this.formatHolder, this.nextInputBuffer, false);
                            if (result == -4) {
                                if (this.nextInputBuffer.isEndOfStream()) {
                                    this.inputStreamEnded = true;
                                } else {
                                    this.nextInputBuffer.subSampleOffsetUs = this.formatHolder.format.subSampleOffsetUs;
                                    this.nextInputBuffer.flip();
                                }

                                this.decoder.queueInputBuffer(this.nextInputBuffer);
                                this.nextInputBuffer = null;
                            } else if (result == -3) {
                                return;
                            }
                        }

                    } catch (SubtitleDecoderException var8) {
                        throw PlaybackException.createForRenderer(var8, this.getIndex());
                    }
                }
            }
        }
    }

    protected void onDisabled() {
        this.streamFormat = null;
        this.clearOutput();
        this.releaseDecoder();
    }

    public boolean isEnded() {
        return this.outputStreamEnded;
    }

    public boolean isReady() {
        return true;
    }

    private void releaseBuffers() {
        this.nextInputBuffer = null;
        this.nextSubtitleEventIndex = -1;
        if (this.subtitle != null) {
            this.subtitle.release();
            this.subtitle = null;
        }

        if (this.nextSubtitle != null) {
            this.nextSubtitle.release();
            this.nextSubtitle = null;
        }

    }

    private void releaseDecoder() {
        this.releaseBuffers();
        this.decoder.release();
        this.decoder = null;
        this.decoderReplacementState = 0;
    }

    private void replaceDecoder() {
        this.releaseDecoder();
        this.decoder = this.decoderFactory.createDecoder(this.streamFormat);
    }

    private long getNextEventTime() {
        return this.nextSubtitleEventIndex != -1 && this.nextSubtitleEventIndex < this.subtitle.getEventTimeCount() ? this.subtitle.getEventTime(this.nextSubtitleEventIndex) : Long.MAX_VALUE;
    }

    private void updateOutput(List<Cue> cues) {
        if (this.outputHandler != null) {
            this.outputHandler.obtainMessage(0, cues).sendToTarget();
        } else {
            this.invokeUpdateOutputInternal(cues);
        }

    }

    private void clearOutput() {
        this.updateOutput(Collections.emptyList());
    }

    public boolean handleMessage(Message msg) {
        switch(msg.what) {
        case 0:
            this.invokeUpdateOutputInternal((List)msg.obj);
            return true;
        default:
            throw new IllegalStateException();
        }
    }

    private void invokeUpdateOutputInternal(List<Cue> cues) {
        this.output.onCues(cues);
    }

    /** @deprecated */
    @Deprecated
    public interface Output extends TextOutput {
    }
}
