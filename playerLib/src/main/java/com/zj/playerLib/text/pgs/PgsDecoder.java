package com.zj.playerLib.text.pgs;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;

import com.zj.playerLib.text.Cue;
import com.zj.playerLib.text.SimpleSubtitleDecoder;
import com.zj.playerLib.text.Subtitle;
import com.zj.playerLib.text.SubtitleDecoderException;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.Inflater;

public final class PgsDecoder extends SimpleSubtitleDecoder {
    private static final int SECTION_TYPE_PALETTE = 20;
    private static final int SECTION_TYPE_BITMAP_PICTURE = 21;
    private static final int SECTION_TYPE_IDENTIFIER = 22;
    private static final int SECTION_TYPE_END = 128;
    private static final byte INFLATE_HEADER = 120;
    private final ParsableByteArray buffer = new ParsableByteArray();
    private final ParsableByteArray inflatedBuffer = new ParsableByteArray();
    private final CueBuilder cueBuilder = new CueBuilder();
    private Inflater inflater;

    public PgsDecoder() {
        super("PgsDecoder");
    }

    protected Subtitle decode(byte[] data, int size, boolean reset) throws SubtitleDecoderException {
        this.buffer.reset(data, size);
        this.maybeInflateData(this.buffer);
        this.cueBuilder.reset();
        ArrayList cues = new ArrayList();

        while(this.buffer.bytesLeft() >= 3) {
            Cue cue = readNextSection(this.buffer, this.cueBuilder);
            if (cue != null) {
                cues.add(cue);
            }
        }

        return new PgsSubtitle(Collections.unmodifiableList(cues));
    }

    private void maybeInflateData(ParsableByteArray buffer) {
        if (buffer.bytesLeft() > 0 && buffer.peekUnsignedByte() == 120) {
            if (this.inflater == null) {
                this.inflater = new Inflater();
            }

            if (Util.inflate(buffer, this.inflatedBuffer, this.inflater)) {
                buffer.reset(this.inflatedBuffer.data, this.inflatedBuffer.limit());
            }
        }

    }

    private static Cue readNextSection(ParsableByteArray buffer, CueBuilder cueBuilder) {
        int limit = buffer.limit();
        int sectionType = buffer.readUnsignedByte();
        int sectionLength = buffer.readUnsignedShort();
        int nextSectionPosition = buffer.getPosition() + sectionLength;
        if (nextSectionPosition > limit) {
            buffer.setPosition(limit);
            return null;
        } else {
            Cue cue = null;
            switch(sectionType) {
            case 20:
                cueBuilder.parsePaletteSection(buffer, sectionLength);
                break;
            case 21:
                cueBuilder.parseBitmapSection(buffer, sectionLength);
                break;
            case 22:
                cueBuilder.parseIdentifierSection(buffer, sectionLength);
                break;
            case 128:
                cue = cueBuilder.build();
                cueBuilder.reset();
            }

            buffer.setPosition(nextSectionPosition);
            return cue;
        }
    }

    private static final class CueBuilder {
        private final ParsableByteArray bitmapData = new ParsableByteArray();
        private final int[] colors = new int[256];
        private boolean colorsSet;
        private int planeWidth;
        private int planeHeight;
        private int bitmapX;
        private int bitmapY;
        private int bitmapWidth;
        private int bitmapHeight;

        public CueBuilder() {
        }

        private void parsePaletteSection(ParsableByteArray buffer, int sectionLength) {
            if (sectionLength % 5 == 2) {
                buffer.skipBytes(2);
                Arrays.fill(this.colors, 0);
                int entryCount = sectionLength / 5;

                for(int i = 0; i < entryCount; ++i) {
                    int index = buffer.readUnsignedByte();
                    int y = buffer.readUnsignedByte();
                    int cr = buffer.readUnsignedByte();
                    int cb = buffer.readUnsignedByte();
                    int a = buffer.readUnsignedByte();
                    int r = (int)((double)y + 1.402D * (double)(cr - 128));
                    int g = (int)((double)y - 0.34414D * (double)(cb - 128) - 0.71414D * (double)(cr - 128));
                    int b = (int)((double)y + 1.772D * (double)(cb - 128));
                    this.colors[index] = a << 24 | Util.constrainValue(r, 0, 255) << 16 | Util.constrainValue(g, 0, 255) << 8 | Util.constrainValue(b, 0, 255);
                }

                this.colorsSet = true;
            }
        }

        private void parseBitmapSection(ParsableByteArray buffer, int sectionLength) {
            if (sectionLength >= 4) {
                buffer.skipBytes(3);
                boolean isBaseSection = (128 & buffer.readUnsignedByte()) != 0;
                sectionLength -= 4;
                int totalLength;
                if (isBaseSection) {
                    if (sectionLength < 7) {
                        return;
                    }

                    totalLength = buffer.readUnsignedInt24();
                    if (totalLength < 4) {
                        return;
                    }

                    this.bitmapWidth = buffer.readUnsignedShort();
                    this.bitmapHeight = buffer.readUnsignedShort();
                    this.bitmapData.reset(totalLength - 4);
                    sectionLength -= 7;
                }

                totalLength = this.bitmapData.getPosition();
                int limit = this.bitmapData.limit();
                if (totalLength < limit && sectionLength > 0) {
                    int bytesToRead = Math.min(sectionLength, limit - totalLength);
                    buffer.readBytes(this.bitmapData.data, totalLength, bytesToRead);
                    this.bitmapData.setPosition(totalLength + bytesToRead);
                }

            }
        }

        private void parseIdentifierSection(ParsableByteArray buffer, int sectionLength) {
            if (sectionLength >= 19) {
                this.planeWidth = buffer.readUnsignedShort();
                this.planeHeight = buffer.readUnsignedShort();
                buffer.skipBytes(11);
                this.bitmapX = buffer.readUnsignedShort();
                this.bitmapY = buffer.readUnsignedShort();
            }
        }

        public Cue build() {
            if (this.planeWidth != 0 && this.planeHeight != 0 && this.bitmapWidth != 0 && this.bitmapHeight != 0 && this.bitmapData.limit() != 0 && this.bitmapData.getPosition() == this.bitmapData.limit() && this.colorsSet) {
                this.bitmapData.setPosition(0);
                int[] argbBitmapData = new int[this.bitmapWidth * this.bitmapHeight];
                int argbBitmapDataIndex = 0;

                while(argbBitmapDataIndex < argbBitmapData.length) {
                    int colorIndex = this.bitmapData.readUnsignedByte();
                    if (colorIndex != 0) {
                        argbBitmapData[argbBitmapDataIndex++] = this.colors[colorIndex];
                    } else {
                        int switchBits = this.bitmapData.readUnsignedByte();
                        if (switchBits != 0) {
                            int runLength = (switchBits & 64) == 0 ? switchBits & 63 : (switchBits & 63) << 8 | this.bitmapData.readUnsignedByte();
                            int color = (switchBits & 128) == 0 ? 0 : this.colors[this.bitmapData.readUnsignedByte()];
                            Arrays.fill(argbBitmapData, argbBitmapDataIndex, argbBitmapDataIndex + runLength, color);
                            argbBitmapDataIndex += runLength;
                        }
                    }
                }

                Bitmap bitmap = Bitmap.createBitmap(argbBitmapData, this.bitmapWidth, this.bitmapHeight, Config.ARGB_8888);
                return new Cue(bitmap, (float)this.bitmapX / (float)this.planeWidth, 0, (float)this.bitmapY / (float)this.planeHeight, 0, (float)this.bitmapWidth / (float)this.planeWidth, (float)this.bitmapHeight / (float)this.planeHeight);
            } else {
                return null;
            }
        }

        public void reset() {
            this.planeWidth = 0;
            this.planeHeight = 0;
            this.bitmapX = 0;
            this.bitmapY = 0;
            this.bitmapWidth = 0;
            this.bitmapHeight = 0;
            this.bitmapData.reset(0);
            this.colorsSet = false;
        }
    }
}
