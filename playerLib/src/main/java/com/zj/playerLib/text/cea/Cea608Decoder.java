package com.zj.playerLib.text.cea;

import android.text.Layout.Alignment;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

import com.zj.playerLib.text.Cue;
import com.zj.playerLib.text.Subtitle;
import com.zj.playerLib.text.SubtitleInputBuffer;
import com.zj.playerLib.util.ParsableByteArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Cea608Decoder extends CeaDecoder {
    private static final int CC_VALID_FLAG = 4;
    private static final int CC_TYPE_FLAG = 2;
    private static final int CC_FIELD_FLAG = 1;
    private static final int NTSC_CC_FIELD_1 = 0;
    private static final int NTSC_CC_FIELD_2 = 1;
    private static final int CC_VALID_608_ID = 4;
    private static final int CC_MODE_UNKNOWN = 0;
    private static final int CC_MODE_ROLL_UP = 1;
    private static final int CC_MODE_POP_ON = 2;
    private static final int CC_MODE_PAINT_ON = 3;
    private static final int[] ROW_INDICES = new int[]{11, 1, 3, 12, 14, 5, 7, 9};
    private static final int[] COLUMN_INDICES = new int[]{0, 4, 8, 12, 16, 20, 24, 28};
    private static final int[] STYLE_COLORS = new int[]{-1, -16711936, -16776961, -16711681, -65536, -256, -65281};
    private static final int STYLE_ITALICS = 7;
    private static final int STYLE_UNCHANGED = 8;
    private static final int DEFAULT_CAPTIONS_ROW_COUNT = 4;
    private static final byte CC_IMPLICIT_DATA_HEADER = -4;
    private static final byte CTRL_RESUME_CAPTION_LOADING = 32;
    private static final byte CTRL_ROLL_UP_CAPTIONS_2_ROWS = 37;
    private static final byte CTRL_ROLL_UP_CAPTIONS_3_ROWS = 38;
    private static final byte CTRL_ROLL_UP_CAPTIONS_4_ROWS = 39;
    private static final byte CTRL_RESUME_DIRECT_CAPTIONING = 41;
    private static final byte CTRL_END_OF_CAPTION = 47;
    private static final byte CTRL_ERASE_DISPLAYED_MEMORY = 44;
    private static final byte CTRL_CARRIAGE_RETURN = 45;
    private static final byte CTRL_ERASE_NON_DISPLAYED_MEMORY = 46;
    private static final byte CTRL_DELETE_TO_END_OF_ROW = 36;
    private static final byte CTRL_BACKSPACE = 33;
    private static final int[] BASIC_CHARACTER_SET = new int[]{32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 225, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 233, 93, 237, 243, 250, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 231, 247, 209, 241, 9632};
    private static final int[] SPECIAL_CHARACTER_SET = new int[]{174, 176, 189, 191, 8482, 162, 163, 9834, 224, 32, 232, 226, 234, 238, 244, 251};
    private static final int[] SPECIAL_ES_FR_CHARACTER_SET = new int[]{193, 201, 211, 218, 220, 252, 8216, 161, 42, 39, 8212, 169, 8480, 8226, 8220, 8221, 192, 194, 199, 200, 202, 203, 235, 206, 207, 239, 212, 217, 249, 219, 171, 187};
    private static final int[] SPECIAL_PT_DE_CHARACTER_SET = new int[]{195, 227, 205, 204, 236, 210, 242, 213, 245, 123, 125, 92, 94, 95, 124, 126, 196, 228, 214, 246, 223, 165, 164, 9474, 197, 229, 216, 248, 9484, 9488, 9492, 9496};
    private final ParsableByteArray ccData = new ParsableByteArray();
    private final int packetLength;
    private final int selectedField;
    private final ArrayList<CueBuilder> cueBuilders = new ArrayList();
    private CueBuilder currentCueBuilder = new CueBuilder(0, 4);
    private List<Cue> cues;
    private List<Cue> lastCues;
    private int captionMode;
    private int captionRowCount;
    private boolean repeatableControlSet;
    private byte repeatableControlCc1;
    private byte repeatableControlCc2;

    public Cea608Decoder(String mimeType, int accessibilityChannel) {
        this.packetLength = "application/x-mp4-cea-608".equals(mimeType) ? 2 : 3;
        switch(accessibilityChannel) {
        case -1:
        case 0:
        case 1:
        case 2:
        default:
            this.selectedField = 1;
            break;
        case 3:
        case 4:
            this.selectedField = 2;
        }

        this.setCaptionMode(0);
        this.resetCueBuilders();
    }

    public String getName() {
        return "Cea608Decoder";
    }

    public void flush() {
        super.flush();
        this.cues = null;
        this.lastCues = null;
        this.setCaptionMode(0);
        this.setCaptionRowCount(4);
        this.resetCueBuilders();
        this.repeatableControlSet = false;
        this.repeatableControlCc1 = 0;
        this.repeatableControlCc2 = 0;
    }

    public void release() {
    }

    protected boolean isNewSubtitleDataAvailable() {
        return this.cues != this.lastCues;
    }

    protected Subtitle createSubtitle() {
        this.lastCues = this.cues;
        return new CeaSubtitle(this.cues);
    }

    protected void decode(SubtitleInputBuffer inputBuffer) {
        this.ccData.reset(inputBuffer.data.array(), inputBuffer.data.limit());
        boolean captionDataProcessed = false;
        boolean isRepeatableControl = false;

        while(this.ccData.bytesLeft() >= this.packetLength) {
            byte ccDataHeader = this.packetLength == 2 ? -4 : (byte)this.ccData.readUnsignedByte();
            byte ccData1 = (byte)(this.ccData.readUnsignedByte() & 127);
            byte ccData2 = (byte)(this.ccData.readUnsignedByte() & 127);
            if ((ccDataHeader & 6) == 4 && (this.selectedField != 1 || (ccDataHeader & 1) == 0) && (this.selectedField != 2 || (ccDataHeader & 1) == 1) && (ccData1 != 0 || ccData2 != 0)) {
                captionDataProcessed = true;
                if ((ccData1 & 247) == 17 && (ccData2 & 240) == 48) {
                    this.currentCueBuilder.append(getSpecialChar(ccData2));
                } else if ((ccData1 & 246) == 18 && (ccData2 & 224) == 32) {
                    this.currentCueBuilder.backspace();
                    if ((ccData1 & 1) == 0) {
                        this.currentCueBuilder.append(getExtendedEsFrChar(ccData2));
                    } else {
                        this.currentCueBuilder.append(getExtendedPtDeChar(ccData2));
                    }
                } else if ((ccData1 & 224) == 0) {
                    isRepeatableControl = this.handleCtrl(ccData1, ccData2);
                } else {
                    this.currentCueBuilder.append(getChar(ccData1));
                    if ((ccData2 & 224) != 0) {
                        this.currentCueBuilder.append(getChar(ccData2));
                    }
                }
            }
        }

        if (captionDataProcessed) {
            if (!isRepeatableControl) {
                this.repeatableControlSet = false;
            }

            if (this.captionMode == 1 || this.captionMode == 3) {
                this.cues = this.getDisplayCues();
            }
        }

    }

    private boolean handleCtrl(byte cc1, byte cc2) {
        boolean isRepeatableControl = isRepeatable(cc1);
        if (isRepeatableControl) {
            if (this.repeatableControlSet && this.repeatableControlCc1 == cc1 && this.repeatableControlCc2 == cc2) {
                this.repeatableControlSet = false;
                return true;
            }

            this.repeatableControlSet = true;
            this.repeatableControlCc1 = cc1;
            this.repeatableControlCc2 = cc2;
        }

        if (isMidrowCtrlCode(cc1, cc2)) {
            this.handleMidrowCtrl(cc2);
        } else if (isPreambleAddressCode(cc1, cc2)) {
            this.handlePreambleAddressCode(cc1, cc2);
        } else if (isTabCtrlCode(cc1, cc2)) {
            this.currentCueBuilder.setTab(cc2 - 32);
        } else if (isMiscCode(cc1, cc2)) {
            this.handleMiscCode(cc2);
        }

        return isRepeatableControl;
    }

    private void handleMidrowCtrl(byte cc2) {
        this.currentCueBuilder.append(' ');
        boolean underline = (cc2 & 1) == 1;
        int style = cc2 >> 1 & 7;
        this.currentCueBuilder.setStyle(style, underline);
    }

    private void handlePreambleAddressCode(byte cc1, byte cc2) {
        int row = ROW_INDICES[cc1 & 7];
        boolean nextRowDown = (cc2 & 32) != 0;
        if (nextRowDown) {
            ++row;
        }

        if (row != this.currentCueBuilder.getRow()) {
            if (this.captionMode != 1 && !this.currentCueBuilder.isEmpty()) {
                this.currentCueBuilder = new CueBuilder(this.captionMode, this.captionRowCount);
                this.cueBuilders.add(this.currentCueBuilder);
            }

            this.currentCueBuilder.setRow(row);
        }

        boolean isCursor = (cc2 & 16) == 16;
        boolean underline = (cc2 & 1) == 1;
        int cursorOrStyle = cc2 >> 1 & 7;
        this.currentCueBuilder.setStyle(isCursor ? 8 : cursorOrStyle, underline);
        if (isCursor) {
            this.currentCueBuilder.setIndent(COLUMN_INDICES[cursorOrStyle]);
        }

    }

    private void handleMiscCode(byte cc2) {
        switch(cc2) {
        case 32:
            this.setCaptionMode(2);
            return;
        case 33:
        case 34:
        case 35:
        case 36:
        case 40:
        default:
            if (this.captionMode == 0) {
                return;
            }

            switch(cc2) {
            case 33:
                this.currentCueBuilder.backspace();
            case 34:
            case 35:
            case 36:
            case 37:
            case 38:
            case 39:
            case 40:
            case 41:
            case 42:
            case 43:
            default:
                break;
            case 44:
                this.cues = Collections.emptyList();
                if (this.captionMode == 1 || this.captionMode == 3) {
                    this.resetCueBuilders();
                }
                break;
            case 45:
                if (this.captionMode == 1 && !this.currentCueBuilder.isEmpty()) {
                    this.currentCueBuilder.rollUp();
                }
                break;
            case 46:
                this.resetCueBuilders();
                break;
            case 47:
                this.cues = this.getDisplayCues();
                this.resetCueBuilders();
            }

            return;
        case 37:
            this.setCaptionMode(1);
            this.setCaptionRowCount(2);
            return;
        case 38:
            this.setCaptionMode(1);
            this.setCaptionRowCount(3);
            return;
        case 39:
            this.setCaptionMode(1);
            this.setCaptionRowCount(4);
            return;
        case 41:
            this.setCaptionMode(3);
        }
    }

    private List<Cue> getDisplayCues() {
        List<Cue> displayCues = new ArrayList();

        for(int i = 0; i < this.cueBuilders.size(); ++i) {
            Cue cue = this.cueBuilders.get(i).build();
            if (cue != null) {
                displayCues.add(cue);
            }
        }

        return displayCues;
    }

    private void setCaptionMode(int captionMode) {
        if (this.captionMode != captionMode) {
            int oldCaptionMode = this.captionMode;
            this.captionMode = captionMode;
            this.resetCueBuilders();
            if (oldCaptionMode == 3 || captionMode == 1 || captionMode == 0) {
                this.cues = Collections.emptyList();
            }

        }
    }

    private void setCaptionRowCount(int captionRowCount) {
        this.captionRowCount = captionRowCount;
        this.currentCueBuilder.setCaptionRowCount(captionRowCount);
    }

    private void resetCueBuilders() {
        this.currentCueBuilder.reset(this.captionMode);
        this.cueBuilders.clear();
        this.cueBuilders.add(this.currentCueBuilder);
    }

    private static char getChar(byte ccData) {
        int index = (ccData & 127) - 32;
        return (char)BASIC_CHARACTER_SET[index];
    }

    private static char getSpecialChar(byte ccData) {
        int index = ccData & 15;
        return (char)SPECIAL_CHARACTER_SET[index];
    }

    private static char getExtendedEsFrChar(byte ccData) {
        int index = ccData & 31;
        return (char)SPECIAL_ES_FR_CHARACTER_SET[index];
    }

    private static char getExtendedPtDeChar(byte ccData) {
        int index = ccData & 31;
        return (char)SPECIAL_PT_DE_CHARACTER_SET[index];
    }

    private static boolean isMidrowCtrlCode(byte cc1, byte cc2) {
        return (cc1 & 247) == 17 && (cc2 & 240) == 32;
    }

    private static boolean isPreambleAddressCode(byte cc1, byte cc2) {
        return (cc1 & 240) == 16 && (cc2 & 192) == 64;
    }

    private static boolean isTabCtrlCode(byte cc1, byte cc2) {
        return (cc1 & 247) == 23 && cc2 >= 33 && cc2 <= 35;
    }

    private static boolean isMiscCode(byte cc1, byte cc2) {
        return (cc1 & 247) == 20 && (cc2 & 240) == 32;
    }

    private static boolean isRepeatable(byte cc1) {
        return (cc1 & 240) == 16;
    }

    private static class CueBuilder {
        private static final int SCREEN_CHARWIDTH = 32;
        private static final int BASE_ROW = 15;
        private final List<CueStyle> cueStyles = new ArrayList();
        private final List<SpannableString> rolledUpCaptions = new ArrayList();
        private final StringBuilder captionStringBuilder = new StringBuilder();
        private int row;
        private int indent;
        private int tabOffset;
        private int captionMode;
        private int captionRowCount;

        public CueBuilder(int captionMode, int captionRowCount) {
            this.reset(captionMode);
            this.setCaptionRowCount(captionRowCount);
        }

        public void reset(int captionMode) {
            this.captionMode = captionMode;
            this.cueStyles.clear();
            this.rolledUpCaptions.clear();
            this.captionStringBuilder.setLength(0);
            this.row = 15;
            this.indent = 0;
            this.tabOffset = 0;
        }

        public void setCaptionRowCount(int captionRowCount) {
            this.captionRowCount = captionRowCount;
        }

        public boolean isEmpty() {
            return this.cueStyles.isEmpty() && this.rolledUpCaptions.isEmpty() && this.captionStringBuilder.length() == 0;
        }

        public void backspace() {
            int length = this.captionStringBuilder.length();
            if (length > 0) {
                this.captionStringBuilder.delete(length - 1, length);

                for(int i = this.cueStyles.size() - 1; i >= 0; --i) {
                    CueStyle style = this.cueStyles.get(i);
                    if (style.start != length) {
                        break;
                    }

                    --style.start;
                }
            }

        }

        public int getRow() {
            return this.row;
        }

        public void setRow(int row) {
            this.row = row;
        }

        public void rollUp() {
            this.rolledUpCaptions.add(this.buildSpannableString());
            this.captionStringBuilder.setLength(0);
            this.cueStyles.clear();
            int numRows = Math.min(this.captionRowCount, this.row);

            while(this.rolledUpCaptions.size() >= numRows) {
                this.rolledUpCaptions.remove(0);
            }

        }

        public void setIndent(int indent) {
            this.indent = indent;
        }

        public void setTab(int tabs) {
            this.tabOffset = tabs;
        }

        public void setStyle(int style, boolean underline) {
            this.cueStyles.add(new CueStyle(style, underline, this.captionStringBuilder.length()));
        }

        public void append(char text) {
            this.captionStringBuilder.append(text);
        }

        public SpannableString buildSpannableString() {
            SpannableStringBuilder builder = new SpannableStringBuilder(this.captionStringBuilder);
            int length = builder.length();
            int underlineStartPosition = -1;
            int italicStartPosition = -1;
            int colorStartPosition = 0;
            int color = -1;
            boolean nextItalic = false;
            int nextColor = -1;

            for(int i = 0; i < this.cueStyles.size(); ++i) {
                CueStyle cueStyle = this.cueStyles.get(i);
                boolean underline = cueStyle.underline;
                int style = cueStyle.style;
                if (style != 8) {
                    nextItalic = style == 7;
                    nextColor = style == 7 ? nextColor : Cea608Decoder.STYLE_COLORS[style];
                }

                int position = cueStyle.start;
                int nextPosition = i + 1 < this.cueStyles.size() ? this.cueStyles.get(i + 1).start : length;
                if (position != nextPosition) {
                    if (underlineStartPosition != -1 && !underline) {
                        setUnderlineSpan(builder, underlineStartPosition, position);
                        underlineStartPosition = -1;
                    } else if (underlineStartPosition == -1 && underline) {
                        underlineStartPosition = position;
                    }

                    if (italicStartPosition != -1 && !nextItalic) {
                        setItalicSpan(builder, italicStartPosition, position);
                        italicStartPosition = -1;
                    } else if (italicStartPosition == -1 && nextItalic) {
                        italicStartPosition = position;
                    }

                    if (nextColor != color) {
                        setColorSpan(builder, colorStartPosition, position, color);
                        color = nextColor;
                        colorStartPosition = position;
                    }
                }
            }

            if (underlineStartPosition != -1 && underlineStartPosition != length) {
                setUnderlineSpan(builder, underlineStartPosition, length);
            }

            if (italicStartPosition != -1 && italicStartPosition != length) {
                setItalicSpan(builder, italicStartPosition, length);
            }

            if (colorStartPosition != length) {
                setColorSpan(builder, colorStartPosition, length, color);
            }

            return new SpannableString(builder);
        }

        public Cue build() {
            SpannableStringBuilder cueString = new SpannableStringBuilder();

            for(int i = 0; i < this.rolledUpCaptions.size(); ++i) {
                cueString.append(this.rolledUpCaptions.get(i));
                cueString.append('\n');
            }

            cueString.append(this.buildSpannableString());
            if (cueString.length() == 0) {
                return null;
            } else {
                int startPadding = this.indent + this.tabOffset;
                int endPadding = 32 - startPadding - cueString.length();
                int startEndPaddingDelta = startPadding - endPadding;
                byte positionAnchor;
                float position;
                if (this.captionMode != 2 || Math.abs(startEndPaddingDelta) >= 3 && endPadding >= 0) {
                    if (this.captionMode == 2 && startEndPaddingDelta > 0) {
                        position = (float)(32 - endPadding) / 32.0F;
                        position = position * 0.8F + 0.1F;
                        positionAnchor = 2;
                    } else {
                        position = (float)startPadding / 32.0F;
                        position = position * 0.8F + 0.1F;
                        positionAnchor = 0;
                    }
                } else {
                    position = 0.5F;
                    positionAnchor = 1;
                }

                byte lineAnchor;
                int line;
                if (this.captionMode != 1 && this.row <= 7) {
                    lineAnchor = 0;
                    line = this.row;
                } else {
                    lineAnchor = 2;
                    line = this.row - 15;
                    line -= 2;
                }

                return new Cue(cueString, Alignment.ALIGN_NORMAL, (float)line, 1, lineAnchor, position, positionAnchor, 1.4E-45F);
            }
        }

        public String toString() {
            return this.captionStringBuilder.toString();
        }

        private static void setUnderlineSpan(SpannableStringBuilder builder, int start, int end) {
            builder.setSpan(new UnderlineSpan(), start, end, 33);
        }

        private static void setItalicSpan(SpannableStringBuilder builder, int start, int end) {
            builder.setSpan(new StyleSpan(2), start, end, 33);
        }

        private static void setColorSpan(SpannableStringBuilder builder, int start, int end, int color) {
            if (color != -1) {
                builder.setSpan(new ForegroundColorSpan(color), start, end, 33);
            }
        }

        private static class CueStyle {
            public final int style;
            public final boolean underline;
            public int start;

            public CueStyle(int style, boolean underline, int start) {
                this.style = style;
                this.underline = underline;
                this.start = start;
            }
        }
    }
}
