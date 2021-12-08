package com.zj.playerLib.text.tx3g;

import android.text.Layout.Alignment;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;

import com.zj.playerLib.text.Cue;
import com.zj.playerLib.text.SimpleSubtitleDecoder;
import com.zj.playerLib.text.Subtitle;
import com.zj.playerLib.text.SubtitleDecoderException;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.Util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class Tx3gDecoder extends SimpleSubtitleDecoder {
    private static final char BOM_UTF16_BE = '\ufeff';
    private static final char BOM_UTF16_LE = '\ufffe';
    private static final int TYPE_STYL = Util.getIntegerCodeForString("styl");
    private static final int TYPE_TBOX = Util.getIntegerCodeForString("tbox");
    private static final String TX3G_SERIF = "Serif";
    private static final int SIZE_ATOM_HEADER = 8;
    private static final int SIZE_SHORT = 2;
    private static final int SIZE_BOM_UTF16 = 2;
    private static final int SIZE_STYLE_RECORD = 12;
    private static final int FONT_FACE_BOLD = 1;
    private static final int FONT_FACE_ITALIC = 2;
    private static final int FONT_FACE_UNDERLINE = 4;
    private static final int SPAN_PRIORITY_LOW = 16711680;
    private static final int SPAN_PRIORITY_HIGH = 0;
    private static final int DEFAULT_FONT_FACE = 0;
    private static final int DEFAULT_COLOR = -1;
    private static final String DEFAULT_FONT_FAMILY = "sans-serif";
    private static final float DEFAULT_VERTICAL_PLACEMENT = 0.85F;
    private final ParsableByteArray parsableByteArray = new ParsableByteArray();
    private boolean customVerticalPlacement;
    private int defaultFontFace;
    private int defaultColorRgba;
    private String defaultFontFamily;
    private float defaultVerticalPlacement;
    private int calculatedVideoTrackHeight;

    public Tx3gDecoder(List<byte[]> initializationData) {
        super("Tx3gDecoder");
        this.decodeInitializationData(initializationData);
    }

    private void decodeInitializationData(List<byte[]> initializationData) {
        if (initializationData == null || initializationData.size() != 1 || initializationData.get(0).length != 48 && initializationData.get(0).length != 53) {
            this.defaultFontFace = 0;
            this.defaultColorRgba = -1;
            this.defaultFontFamily = "sans-serif";
            this.customVerticalPlacement = false;
            this.defaultVerticalPlacement = 0.85F;
        } else {
            byte[] initializationBytes = initializationData.get(0);
            this.defaultFontFace = initializationBytes[24];
            this.defaultColorRgba = (initializationBytes[26] & 255) << 24 | (initializationBytes[27] & 255) << 16 | (initializationBytes[28] & 255) << 8 | initializationBytes[29] & 255;
            String fontFamily = Util.fromUtf8Bytes(initializationBytes, 43, initializationBytes.length - 43);
            this.defaultFontFamily = "Serif".equals(fontFamily) ? "serif" : "sans-serif";
            this.calculatedVideoTrackHeight = 20 * initializationBytes[25];
            this.customVerticalPlacement = (initializationBytes[0] & 32) != 0;
            if (this.customVerticalPlacement) {
                int requestedVerticalPlacement = (initializationBytes[10] & 255) << 8 | initializationBytes[11] & 255;
                this.defaultVerticalPlacement = (float)requestedVerticalPlacement / (float)this.calculatedVideoTrackHeight;
                this.defaultVerticalPlacement = Util.constrainValue(this.defaultVerticalPlacement, 0.0F, 0.95F);
            } else {
                this.defaultVerticalPlacement = 0.85F;
            }
        }

    }

    protected Subtitle decode(byte[] bytes, int length, boolean reset) throws SubtitleDecoderException {
        this.parsableByteArray.reset(bytes, length);
        String cueTextString = readSubtitleText(this.parsableByteArray);
        if (cueTextString.isEmpty()) {
            return Tx3gSubtitle.EMPTY;
        } else {
            SpannableStringBuilder cueText = new SpannableStringBuilder(cueTextString);
            attachFontFace(cueText, this.defaultFontFace, 0, 0, cueText.length(), 16711680);
            attachColor(cueText, this.defaultColorRgba, -1, 0, cueText.length(), 16711680);
            attachFontFamily(cueText, this.defaultFontFamily, "sans-serif", 0, cueText.length(), 16711680);

            float verticalPlacement;
            int position;
            int atomSize;
            for(verticalPlacement = this.defaultVerticalPlacement; this.parsableByteArray.bytesLeft() >= 8; this.parsableByteArray.setPosition(position + atomSize)) {
                position = this.parsableByteArray.getPosition();
                atomSize = this.parsableByteArray.readInt();
                int atomType = this.parsableByteArray.readInt();
                int requestedVerticalPlacement;
                if (atomType == TYPE_STYL) {
                    assertTrue(this.parsableByteArray.bytesLeft() >= 2);
                    requestedVerticalPlacement = this.parsableByteArray.readUnsignedShort();

                    for(int i = 0; i < requestedVerticalPlacement; ++i) {
                        this.applyStyleRecord(this.parsableByteArray, cueText);
                    }
                } else if (atomType == TYPE_TBOX && this.customVerticalPlacement) {
                    assertTrue(this.parsableByteArray.bytesLeft() >= 2);
                    requestedVerticalPlacement = this.parsableByteArray.readUnsignedShort();
                    verticalPlacement = (float)requestedVerticalPlacement / (float)this.calculatedVideoTrackHeight;
                    verticalPlacement = Util.constrainValue(verticalPlacement, 0.0F, 0.95F);
                }
            }

            return new Tx3gSubtitle(new Cue(cueText, null, verticalPlacement, 0, 0, 1.4E-45F, -2147483648, 1.4E-45F));
        }
    }

    private static String readSubtitleText(ParsableByteArray parsableByteArray) throws SubtitleDecoderException {
        assertTrue(parsableByteArray.bytesLeft() >= 2);
        int textLength = parsableByteArray.readUnsignedShort();
        if (textLength == 0) {
            return "";
        } else {
            if (parsableByteArray.bytesLeft() >= 2) {
                char firstChar = parsableByteArray.peekChar();
                if (firstChar == '\ufeff' || firstChar == '\ufffe') {
                    return parsableByteArray.readString(textLength, StandardCharsets.UTF_16);
                }
            }

            return parsableByteArray.readString(textLength, StandardCharsets.UTF_8);
        }
    }

    private void applyStyleRecord(ParsableByteArray parsableByteArray, SpannableStringBuilder cueText) throws SubtitleDecoderException {
        assertTrue(parsableByteArray.bytesLeft() >= 12);
        int start = parsableByteArray.readUnsignedShort();
        int end = parsableByteArray.readUnsignedShort();
        parsableByteArray.skipBytes(2);
        int fontFace = parsableByteArray.readUnsignedByte();
        parsableByteArray.skipBytes(1);
        int colorRgba = parsableByteArray.readInt();
        attachFontFace(cueText, fontFace, this.defaultFontFace, start, end, 0);
        attachColor(cueText, colorRgba, this.defaultColorRgba, start, end, 0);
    }

    private static void attachFontFace(SpannableStringBuilder cueText, int fontFace, int defaultFontFace, int start, int end, int spanPriority) {
        if (fontFace != defaultFontFace) {
            int flags = 33 | spanPriority;
            boolean isBold = (fontFace & 1) != 0;
            boolean isItalic = (fontFace & 2) != 0;
            if (isBold) {
                if (isItalic) {
                    cueText.setSpan(new StyleSpan(3), start, end, flags);
                } else {
                    cueText.setSpan(new StyleSpan(1), start, end, flags);
                }
            } else if (isItalic) {
                cueText.setSpan(new StyleSpan(2), start, end, flags);
            }

            boolean isUnderlined = (fontFace & 4) != 0;
            if (isUnderlined) {
                cueText.setSpan(new UnderlineSpan(), start, end, flags);
            }

            if (!isUnderlined && !isBold && !isItalic) {
                cueText.setSpan(new StyleSpan(0), start, end, flags);
            }
        }

    }

    private static void attachColor(SpannableStringBuilder cueText, int colorRgba, int defaultColorRgba, int start, int end, int spanPriority) {
        if (colorRgba != defaultColorRgba) {
            int colorArgb = (colorRgba & 255) << 24 | colorRgba >>> 8;
            cueText.setSpan(new ForegroundColorSpan(colorArgb), start, end, 33 | spanPriority);
        }

    }

    private static void attachFontFamily(SpannableStringBuilder cueText, String fontFamily, String defaultFontFamily, int start, int end, int spanPriority) {
        if (fontFamily != defaultFontFamily) {
            cueText.setSpan(new TypefaceSpan(fontFamily), start, end, 33 | spanPriority);
        }

    }

    private static void assertTrue(boolean checkValue) throws SubtitleDecoderException {
        if (!checkValue) {
            throw new SubtitleDecoderException("Unexpected subtitle format.");
        }
    }
}
