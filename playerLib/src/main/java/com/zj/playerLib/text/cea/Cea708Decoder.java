package com.zj.playerLib.text.cea;

import android.graphics.Color;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Layout.Alignment;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import com.zj.playerLib.text.Cue;
import com.zj.playerLib.text.Subtitle;
import com.zj.playerLib.text.SubtitleInputBuffer;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.ParsableBitArray;
import com.zj.playerLib.util.ParsableByteArray;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Cea708Decoder extends CeaDecoder {
    private static final String TAG = "Cea708Decoder";
    private static final int NUM_WINDOWS = 8;
    private static final int DTVCC_PACKET_DATA = 2;
    private static final int DTVCC_PACKET_START = 3;
    private static final int CC_VALID_FLAG = 4;
    private static final int GROUP_C0_END = 31;
    private static final int GROUP_G0_END = 127;
    private static final int GROUP_C1_END = 159;
    private static final int GROUP_G1_END = 255;
    private static final int GROUP_C2_END = 31;
    private static final int GROUP_G2_END = 127;
    private static final int GROUP_C3_END = 159;
    private static final int GROUP_G3_END = 255;
    private static final int COMMAND_NUL = 0;
    private static final int COMMAND_ETX = 3;
    private static final int COMMAND_BS = 8;
    private static final int COMMAND_FF = 12;
    private static final int COMMAND_CR = 13;
    private static final int COMMAND_HCR = 14;
    private static final int COMMAND_EXT1 = 16;
    private static final int COMMAND_EXT1_START = 17;
    private static final int COMMAND_EXT1_END = 23;
    private static final int COMMAND_P16_START = 24;
    private static final int COMMAND_P16_END = 31;
    private static final int COMMAND_CW0 = 128;
    private static final int COMMAND_CW1 = 129;
    private static final int COMMAND_CW2 = 130;
    private static final int COMMAND_CW3 = 131;
    private static final int COMMAND_CW4 = 132;
    private static final int COMMAND_CW5 = 133;
    private static final int COMMAND_CW6 = 134;
    private static final int COMMAND_CW7 = 135;
    private static final int COMMAND_CLW = 136;
    private static final int COMMAND_DSW = 137;
    private static final int COMMAND_HDW = 138;
    private static final int COMMAND_TGW = 139;
    private static final int COMMAND_DLW = 140;
    private static final int COMMAND_DLY = 141;
    private static final int COMMAND_DLC = 142;
    private static final int COMMAND_RST = 143;
    private static final int COMMAND_SPA = 144;
    private static final int COMMAND_SPC = 145;
    private static final int COMMAND_SPL = 146;
    private static final int COMMAND_SWA = 151;
    private static final int COMMAND_DF0 = 152;
    private static final int COMMAND_DF1 = 153;
    private static final int COMMAND_DF2 = 154;
    private static final int COMMAND_DF3 = 155;
    private static final int COMMAND_DF4 = 156;
    private static final int COMMAND_DF5 = 157;
    private static final int COMMAND_DF6 = 158;
    private static final int COMMAND_DF7 = 159;
    private static final int CHARACTER_MN = 127;
    private static final int CHARACTER_TSP = 32;
    private static final int CHARACTER_NBTSP = 33;
    private static final int CHARACTER_ELLIPSIS = 37;
    private static final int CHARACTER_BIG_CARONS = 42;
    private static final int CHARACTER_BIG_OE = 44;
    private static final int CHARACTER_SOLID_BLOCK = 48;
    private static final int CHARACTER_OPEN_SINGLE_QUOTE = 49;
    private static final int CHARACTER_CLOSE_SINGLE_QUOTE = 50;
    private static final int CHARACTER_OPEN_DOUBLE_QUOTE = 51;
    private static final int CHARACTER_CLOSE_DOUBLE_QUOTE = 52;
    private static final int CHARACTER_BOLD_BULLET = 53;
    private static final int CHARACTER_TM = 57;
    private static final int CHARACTER_SMALL_CARONS = 58;
    private static final int CHARACTER_SMALL_OE = 60;
    private static final int CHARACTER_SM = 61;
    private static final int CHARACTER_DIAERESIS_Y = 63;
    private static final int CHARACTER_ONE_EIGHTH = 118;
    private static final int CHARACTER_THREE_EIGHTHS = 119;
    private static final int CHARACTER_FIVE_EIGHTHS = 120;
    private static final int CHARACTER_SEVEN_EIGHTHS = 121;
    private static final int CHARACTER_VERTICAL_BORDER = 122;
    private static final int CHARACTER_UPPER_RIGHT_BORDER = 123;
    private static final int CHARACTER_LOWER_LEFT_BORDER = 124;
    private static final int CHARACTER_HORIZONTAL_BORDER = 125;
    private static final int CHARACTER_LOWER_RIGHT_BORDER = 126;
    private static final int CHARACTER_UPPER_LEFT_BORDER = 127;
    private final ParsableByteArray ccData = new ParsableByteArray();
    private final ParsableBitArray serviceBlockPacket = new ParsableBitArray();
    private final int selectedServiceNumber;
    private final CueBuilder[] cueBuilders;
    private CueBuilder currentCueBuilder;
    private List<Cue> cues;
    private List<Cue> lastCues;
    private DtvCcPacket currentDtvCcPacket;
    private int currentWindow;

    public Cea708Decoder(int accessibilityChannel, List<byte[]> initializationData) {
        this.selectedServiceNumber = accessibilityChannel == -1 ? 1 : accessibilityChannel;
        this.cueBuilders = new CueBuilder[8];

        for(int i = 0; i < 8; ++i) {
            this.cueBuilders[i] = new CueBuilder();
        }

        this.currentCueBuilder = this.cueBuilders[0];
        this.resetCueBuilders();
    }

    public String getName() {
        return "Cea708Decoder";
    }

    public void flush() {
        super.flush();
        this.cues = null;
        this.lastCues = null;
        this.currentWindow = 0;
        this.currentCueBuilder = this.cueBuilders[this.currentWindow];
        this.resetCueBuilders();
        this.currentDtvCcPacket = null;
    }

    protected boolean isNewSubtitleDataAvailable() {
        return this.cues != this.lastCues;
    }

    protected Subtitle createSubtitle() {
        this.lastCues = this.cues;
        return new CeaSubtitle(this.cues);
    }

    protected void decode(SubtitleInputBuffer inputBuffer) {
        byte[] inputBufferData = inputBuffer.data.array();
        this.ccData.reset(inputBufferData, inputBuffer.data.limit());

        while(true) {
            while(true) {
                int ccType;
                boolean ccValid;
                byte ccData1;
                byte ccData2;
                do {
                    do {
                        if (this.ccData.bytesLeft() < 3) {
                            return;
                        }

                        int ccTypeAndValid = this.ccData.readUnsignedByte() & 7;
                        ccType = ccTypeAndValid & 3;
                        ccValid = (ccTypeAndValid & 4) == 4;
                        ccData1 = (byte)this.ccData.readUnsignedByte();
                        ccData2 = (byte)this.ccData.readUnsignedByte();
                    } while(ccType != 2 && ccType != 3);
                } while(!ccValid);

                if (ccType == 3) {
                    this.finalizeCurrentPacket();
                    int sequenceNumber = (ccData1 & 192) >> 6;
                    int packetSize = ccData1 & 63;
                    if (packetSize == 0) {
                        packetSize = 64;
                    }

                    this.currentDtvCcPacket = new DtvCcPacket(sequenceNumber, packetSize);
                    this.currentDtvCcPacket.packetData[this.currentDtvCcPacket.currentIndex++] = ccData2;
                } else {
                    Assertions.checkArgument(ccType == 2);
                    if (this.currentDtvCcPacket == null) {
                        Log.e("Cea708Decoder", "Encountered DTVCC_PACKET_DATA before DTVCC_PACKET_START");
                        continue;
                    }

                    this.currentDtvCcPacket.packetData[this.currentDtvCcPacket.currentIndex++] = ccData1;
                    this.currentDtvCcPacket.packetData[this.currentDtvCcPacket.currentIndex++] = ccData2;
                }

                if (this.currentDtvCcPacket.currentIndex == this.currentDtvCcPacket.packetSize * 2 - 1) {
                    this.finalizeCurrentPacket();
                }
            }
        }
    }

    private void finalizeCurrentPacket() {
        if (this.currentDtvCcPacket != null) {
            this.processCurrentPacket();
            this.currentDtvCcPacket = null;
        }
    }

    private void processCurrentPacket() {
        if (this.currentDtvCcPacket.currentIndex != this.currentDtvCcPacket.packetSize * 2 - 1) {
            Log.w("Cea708Decoder", "DtvCcPacket ended prematurely; size is " + (this.currentDtvCcPacket.packetSize * 2 - 1) + ", but current index is " + this.currentDtvCcPacket.currentIndex + " (sequence number " + this.currentDtvCcPacket.sequenceNumber + "); ignoring packet");
        } else {
            this.serviceBlockPacket.reset(this.currentDtvCcPacket.packetData, this.currentDtvCcPacket.currentIndex);
            int serviceNumber = this.serviceBlockPacket.readBits(3);
            int blockSize = this.serviceBlockPacket.readBits(5);
            if (serviceNumber == 7) {
                this.serviceBlockPacket.skipBits(2);
                serviceNumber = this.serviceBlockPacket.readBits(6);
                if (serviceNumber < 7) {
                    Log.w("Cea708Decoder", "Invalid extended service number: " + serviceNumber);
                }
            }

            if (blockSize == 0) {
                if (serviceNumber != 0) {
                    Log.w("Cea708Decoder", "serviceNumber is non-zero (" + serviceNumber + ") when blockSize is 0");
                }

            } else if (serviceNumber == this.selectedServiceNumber) {
                boolean cuesNeedUpdate = false;

                while(this.serviceBlockPacket.bitsLeft() > 0) {
                    int command = this.serviceBlockPacket.readBits(8);
                    if (command != 16) {
                        if (command <= 31) {
                            this.handleC0Command(command);
                        } else if (command <= 127) {
                            this.handleG0Character(command);
                            cuesNeedUpdate = true;
                        } else if (command <= 159) {
                            this.handleC1Command(command);
                            cuesNeedUpdate = true;
                        } else if (command <= 255) {
                            this.handleG1Character(command);
                            cuesNeedUpdate = true;
                        } else {
                            Log.w("Cea708Decoder", "Invalid base command: " + command);
                        }
                    } else {
                        command = this.serviceBlockPacket.readBits(8);
                        if (command <= 31) {
                            this.handleC2Command(command);
                        } else if (command <= 127) {
                            this.handleG2Character(command);
                            cuesNeedUpdate = true;
                        } else if (command <= 159) {
                            this.handleC3Command(command);
                        } else if (command <= 255) {
                            this.handleG3Character(command);
                            cuesNeedUpdate = true;
                        } else {
                            Log.w("Cea708Decoder", "Invalid extended command: " + command);
                        }
                    }
                }

                if (cuesNeedUpdate) {
                    this.cues = this.getDisplayCues();
                }

            }
        }
    }

    private void handleC0Command(int command) {
        switch(command) {
        case 0:
        case 14:
            break;
        case 1:
        case 2:
        case 4:
        case 5:
        case 6:
        case 7:
        case 9:
        case 10:
        case 11:
        default:
            if (command >= 17 && command <= 23) {
                Log.w("Cea708Decoder", "Currently unsupported COMMAND_EXT1 Command: " + command);
                this.serviceBlockPacket.skipBits(8);
            } else if (command >= 24 && command <= 31) {
                Log.w("Cea708Decoder", "Currently unsupported COMMAND_P16 Command: " + command);
                this.serviceBlockPacket.skipBits(16);
            } else {
                Log.w("Cea708Decoder", "Invalid C0 command: " + command);
            }
            break;
        case 3:
            this.cues = this.getDisplayCues();
            break;
        case 8:
            this.currentCueBuilder.backspace();
            break;
        case 12:
            this.resetCueBuilders();
            break;
        case 13:
            this.currentCueBuilder.append('\n');
        }

    }

    private void handleC1Command(int command) {
        int window;
        int i;
        switch(command) {
        case 128:
        case 129:
        case 130:
        case 131:
        case 132:
        case 133:
        case 134:
        case 135:
            window = command - 128;
            if (this.currentWindow != window) {
                this.currentWindow = window;
                this.currentCueBuilder = this.cueBuilders[window];
            }
            break;
        case 136:
            for(i = 1; i <= 8; ++i) {
                if (this.serviceBlockPacket.readBit()) {
                    this.cueBuilders[8 - i].clear();
                }
            }

            return;
        case 137:
            for(i = 1; i <= 8; ++i) {
                if (this.serviceBlockPacket.readBit()) {
                    this.cueBuilders[8 - i].setVisibility(true);
                }
            }

            return;
        case 138:
            for(i = 1; i <= 8; ++i) {
                if (this.serviceBlockPacket.readBit()) {
                    this.cueBuilders[8 - i].setVisibility(false);
                }
            }

            return;
        case 139:
            for(i = 1; i <= 8; ++i) {
                if (this.serviceBlockPacket.readBit()) {
                    CueBuilder cueBuilder = this.cueBuilders[8 - i];
                    cueBuilder.setVisibility(!cueBuilder.isVisible());
                }
            }

            return;
        case 140:
            for(i = 1; i <= 8; ++i) {
                if (this.serviceBlockPacket.readBit()) {
                    this.cueBuilders[8 - i].reset();
                }
            }

            return;
        case 141:
            this.serviceBlockPacket.skipBits(8);
        case 142:
            break;
        case 143:
            this.resetCueBuilders();
            break;
        case 144:
            if (!this.currentCueBuilder.isDefined()) {
                this.serviceBlockPacket.skipBits(16);
            } else {
                this.handleSetPenAttributes();
            }
            break;
        case 145:
            if (!this.currentCueBuilder.isDefined()) {
                this.serviceBlockPacket.skipBits(24);
            } else {
                this.handleSetPenColor();
            }
            break;
        case 146:
            if (!this.currentCueBuilder.isDefined()) {
                this.serviceBlockPacket.skipBits(16);
            } else {
                this.handleSetPenLocation();
            }
            break;
        case 147:
        case 148:
        case 149:
        case 150:
        default:
            Log.w("Cea708Decoder", "Invalid C1 command: " + command);
            break;
        case 151:
            if (!this.currentCueBuilder.isDefined()) {
                this.serviceBlockPacket.skipBits(32);
            } else {
                this.handleSetWindowAttributes();
            }
            break;
        case 152:
        case 153:
        case 154:
        case 155:
        case 156:
        case 157:
        case 158:
        case 159:
            window = command - 152;
            this.handleDefineWindow(window);
            if (this.currentWindow != window) {
                this.currentWindow = window;
                this.currentCueBuilder = this.cueBuilders[window];
            }
        }

    }

    private void handleC2Command(int command) {
        if (command > 7) {
            if (command <= 15) {
                this.serviceBlockPacket.skipBits(8);
            } else if (command <= 23) {
                this.serviceBlockPacket.skipBits(16);
            } else if (command <= 31) {
                this.serviceBlockPacket.skipBits(24);
            }
        }

    }

    private void handleC3Command(int command) {
        if (command <= 135) {
            this.serviceBlockPacket.skipBits(32);
        } else if (command <= 143) {
            this.serviceBlockPacket.skipBits(40);
        } else if (command <= 159) {
            this.serviceBlockPacket.skipBits(2);
            int length = this.serviceBlockPacket.readBits(6);
            this.serviceBlockPacket.skipBits(8 * length);
        }

    }

    private void handleG0Character(int characterCode) {
        if (characterCode == 127) {
            this.currentCueBuilder.append('♫');
        } else {
            this.currentCueBuilder.append((char)(characterCode & 255));
        }

    }

    private void handleG1Character(int characterCode) {
        this.currentCueBuilder.append((char)(characterCode & 255));
    }

    private void handleG2Character(int characterCode) {
        switch(characterCode) {
        case 32:
            this.currentCueBuilder.append(' ');
            break;
        case 33:
            this.currentCueBuilder.append(' ');
            break;
        case 34:
        case 35:
        case 36:
        case 38:
        case 39:
        case 40:
        case 41:
        case 43:
        case 45:
        case 46:
        case 47:
        case 54:
        case 55:
        case 56:
        case 59:
        case 62:
        case 64:
        case 65:
        case 66:
        case 67:
        case 68:
        case 69:
        case 70:
        case 71:
        case 72:
        case 73:
        case 74:
        case 75:
        case 76:
        case 77:
        case 78:
        case 79:
        case 80:
        case 81:
        case 82:
        case 83:
        case 84:
        case 85:
        case 86:
        case 87:
        case 88:
        case 89:
        case 90:
        case 91:
        case 92:
        case 93:
        case 94:
        case 95:
        case 96:
        case 97:
        case 98:
        case 99:
        case 100:
        case 101:
        case 102:
        case 103:
        case 104:
        case 105:
        case 106:
        case 107:
        case 108:
        case 109:
        case 110:
        case 111:
        case 112:
        case 113:
        case 114:
        case 115:
        case 116:
        case 117:
        default:
            Log.w("Cea708Decoder", "Invalid G2 character: " + characterCode);
            break;
        case 37:
            this.currentCueBuilder.append('…');
            break;
        case 42:
            this.currentCueBuilder.append('Š');
            break;
        case 44:
            this.currentCueBuilder.append('Œ');
            break;
        case 48:
            this.currentCueBuilder.append('█');
            break;
        case 49:
            this.currentCueBuilder.append('‘');
            break;
        case 50:
            this.currentCueBuilder.append('’');
            break;
        case 51:
            this.currentCueBuilder.append('“');
            break;
        case 52:
            this.currentCueBuilder.append('”');
            break;
        case 53:
            this.currentCueBuilder.append('•');
            break;
        case 57:
            this.currentCueBuilder.append('™');
            break;
        case 58:
            this.currentCueBuilder.append('š');
            break;
        case 60:
            this.currentCueBuilder.append('œ');
            break;
        case 61:
            this.currentCueBuilder.append('℠');
            break;
        case 63:
            this.currentCueBuilder.append('Ÿ');
            break;
        case 118:
            this.currentCueBuilder.append('⅛');
            break;
        case 119:
            this.currentCueBuilder.append('⅜');
            break;
        case 120:
            this.currentCueBuilder.append('⅝');
            break;
        case 121:
            this.currentCueBuilder.append('⅞');
            break;
        case 122:
            this.currentCueBuilder.append('│');
            break;
        case 123:
            this.currentCueBuilder.append('┐');
            break;
        case 124:
            this.currentCueBuilder.append('└');
            break;
        case 125:
            this.currentCueBuilder.append('─');
            break;
        case 126:
            this.currentCueBuilder.append('┘');
            break;
        case 127:
            this.currentCueBuilder.append('┌');
        }

    }

    private void handleG3Character(int characterCode) {
        if (characterCode == 160) {
            this.currentCueBuilder.append('㏄');
        } else {
            Log.w("Cea708Decoder", "Invalid G3 character: " + characterCode);
            this.currentCueBuilder.append('_');
        }

    }

    private void handleSetPenAttributes() {
        int textTag = this.serviceBlockPacket.readBits(4);
        int offset = this.serviceBlockPacket.readBits(2);
        int penSize = this.serviceBlockPacket.readBits(2);
        boolean italicsToggle = this.serviceBlockPacket.readBit();
        boolean underlineToggle = this.serviceBlockPacket.readBit();
        int edgeType = this.serviceBlockPacket.readBits(3);
        int fontStyle = this.serviceBlockPacket.readBits(3);
        this.currentCueBuilder.setPenAttributes(textTag, offset, penSize, italicsToggle, underlineToggle, edgeType, fontStyle);
    }

    private void handleSetPenColor() {
        int foregroundO = this.serviceBlockPacket.readBits(2);
        int foregroundR = this.serviceBlockPacket.readBits(2);
        int foregroundG = this.serviceBlockPacket.readBits(2);
        int foregroundB = this.serviceBlockPacket.readBits(2);
        int foregroundColor = CueBuilder.getArgbColorFromCeaColor(foregroundR, foregroundG, foregroundB, foregroundO);
        int backgroundO = this.serviceBlockPacket.readBits(2);
        int backgroundR = this.serviceBlockPacket.readBits(2);
        int backgroundG = this.serviceBlockPacket.readBits(2);
        int backgroundB = this.serviceBlockPacket.readBits(2);
        int backgroundColor = CueBuilder.getArgbColorFromCeaColor(backgroundR, backgroundG, backgroundB, backgroundO);
        this.serviceBlockPacket.skipBits(2);
        int edgeR = this.serviceBlockPacket.readBits(2);
        int edgeG = this.serviceBlockPacket.readBits(2);
        int edgeB = this.serviceBlockPacket.readBits(2);
        int edgeColor = CueBuilder.getArgbColorFromCeaColor(edgeR, edgeG, edgeB);
        this.currentCueBuilder.setPenColor(foregroundColor, backgroundColor, edgeColor);
    }

    private void handleSetPenLocation() {
        this.serviceBlockPacket.skipBits(4);
        int row = this.serviceBlockPacket.readBits(4);
        this.serviceBlockPacket.skipBits(2);
        int column = this.serviceBlockPacket.readBits(6);
        this.currentCueBuilder.setPenLocation(row, column);
    }

    private void handleSetWindowAttributes() {
        int fillO = this.serviceBlockPacket.readBits(2);
        int fillR = this.serviceBlockPacket.readBits(2);
        int fillG = this.serviceBlockPacket.readBits(2);
        int fillB = this.serviceBlockPacket.readBits(2);
        int fillColor = CueBuilder.getArgbColorFromCeaColor(fillR, fillG, fillB, fillO);
        int borderType = this.serviceBlockPacket.readBits(2);
        int borderR = this.serviceBlockPacket.readBits(2);
        int borderG = this.serviceBlockPacket.readBits(2);
        int borderB = this.serviceBlockPacket.readBits(2);
        int borderColor = CueBuilder.getArgbColorFromCeaColor(borderR, borderG, borderB);
        if (this.serviceBlockPacket.readBit()) {
            borderType |= 4;
        }

        boolean wordWrapToggle = this.serviceBlockPacket.readBit();
        int printDirection = this.serviceBlockPacket.readBits(2);
        int scrollDirection = this.serviceBlockPacket.readBits(2);
        int justification = this.serviceBlockPacket.readBits(2);
        this.serviceBlockPacket.skipBits(8);
        this.currentCueBuilder.setWindowAttributes(fillColor, borderColor, wordWrapToggle, borderType, printDirection, scrollDirection, justification);
    }

    private void handleDefineWindow(int window) {
        CueBuilder cueBuilder = this.cueBuilders[window];
        this.serviceBlockPacket.skipBits(2);
        boolean visible = this.serviceBlockPacket.readBit();
        boolean rowLock = this.serviceBlockPacket.readBit();
        boolean columnLock = this.serviceBlockPacket.readBit();
        int priority = this.serviceBlockPacket.readBits(3);
        boolean relativePositioning = this.serviceBlockPacket.readBit();
        int verticalAnchor = this.serviceBlockPacket.readBits(7);
        int horizontalAnchor = this.serviceBlockPacket.readBits(8);
        int anchorId = this.serviceBlockPacket.readBits(4);
        int rowCount = this.serviceBlockPacket.readBits(4);
        this.serviceBlockPacket.skipBits(2);
        int columnCount = this.serviceBlockPacket.readBits(6);
        this.serviceBlockPacket.skipBits(2);
        int windowStyle = this.serviceBlockPacket.readBits(3);
        int penStyle = this.serviceBlockPacket.readBits(3);
        cueBuilder.defineWindow(visible, rowLock, columnLock, priority, relativePositioning, verticalAnchor, horizontalAnchor, rowCount, columnCount, anchorId, windowStyle, penStyle);
    }

    private List<Cue> getDisplayCues() {
        List<Cea708Cue> displayCues = new ArrayList();

        for(int i = 0; i < 8; ++i) {
            if (!this.cueBuilders[i].isEmpty() && this.cueBuilders[i].isVisible()) {
                displayCues.add(this.cueBuilders[i].build());
            }
        }

        Collections.sort(displayCues);
        return Collections.unmodifiableList(displayCues);
    }

    private void resetCueBuilders() {
        for(int i = 0; i < 8; ++i) {
            this.cueBuilders[i].reset();
        }

    }

    private static final class CueBuilder {
        private static final int RELATIVE_CUE_SIZE = 99;
        private static final int VERTICAL_SIZE = 74;
        private static final int HORIZONTAL_SIZE = 209;
        private static final int DEFAULT_PRIORITY = 4;
        private static final int MAXIMUM_ROW_COUNT = 15;
        private static final int JUSTIFICATION_LEFT = 0;
        private static final int JUSTIFICATION_RIGHT = 1;
        private static final int JUSTIFICATION_CENTER = 2;
        private static final int JUSTIFICATION_FULL = 3;
        private static final int DIRECTION_LEFT_TO_RIGHT = 0;
        private static final int DIRECTION_RIGHT_TO_LEFT = 1;
        private static final int DIRECTION_TOP_TO_BOTTOM = 2;
        private static final int DIRECTION_BOTTOM_TO_TOP = 3;
        private static final int BORDER_AND_EDGE_TYPE_NONE = 0;
        private static final int BORDER_AND_EDGE_TYPE_UNIFORM = 3;
        public static final int COLOR_SOLID_WHITE = getArgbColorFromCeaColor(2, 2, 2, 0);
        public static final int COLOR_SOLID_BLACK = getArgbColorFromCeaColor(0, 0, 0, 0);
        public static final int COLOR_TRANSPARENT = getArgbColorFromCeaColor(0, 0, 0, 3);
        private static final int PEN_SIZE_STANDARD = 1;
        private static final int PEN_FONT_STYLE_DEFAULT = 0;
        private static final int PEN_FONT_STYLE_MONOSPACED_WITH_SERIFS = 1;
        private static final int PEN_FONT_STYLE_PROPORTIONALLY_SPACED_WITH_SERIFS = 2;
        private static final int PEN_FONT_STYLE_MONOSPACED_WITHOUT_SERIFS = 3;
        private static final int PEN_FONT_STYLE_PROPORTIONALLY_SPACED_WITHOUT_SERIFS = 4;
        private static final int PEN_OFFSET_NORMAL = 1;
        private static final int[] WINDOW_STYLE_JUSTIFICATION = new int[]{0, 0, 0, 0, 0, 2, 0};
        private static final int[] WINDOW_STYLE_PRINT_DIRECTION = new int[]{0, 0, 0, 0, 0, 0, 2};
        private static final int[] WINDOW_STYLE_SCROLL_DIRECTION = new int[]{3, 3, 3, 3, 3, 3, 1};
        private static final boolean[] WINDOW_STYLE_WORD_WRAP = new boolean[]{false, false, false, true, true, true, false};
        private static final int[] WINDOW_STYLE_FILL;
        private static final int[] PEN_STYLE_FONT_STYLE;
        private static final int[] PEN_STYLE_EDGE_TYPE;
        private static final int[] PEN_STYLE_BACKGROUND;
        private final List<SpannableString> rolledUpCaptions = new ArrayList();
        private final SpannableStringBuilder captionStringBuilder = new SpannableStringBuilder();
        private boolean defined;
        private boolean visible;
        private int priority;
        private boolean relativePositioning;
        private int verticalAnchor;
        private int horizontalAnchor;
        private int anchorId;
        private int rowCount;
        private boolean rowLock;
        private int justification;
        private int windowStyleId;
        private int penStyleId;
        private int windowFillColor;
        private int italicsStartPosition;
        private int underlineStartPosition;
        private int foregroundColorStartPosition;
        private int foregroundColor;
        private int backgroundColorStartPosition;
        private int backgroundColor;
        private int row;

        public CueBuilder() {
            this.reset();
        }

        public boolean isEmpty() {
            return !this.isDefined() || this.rolledUpCaptions.isEmpty() && this.captionStringBuilder.length() == 0;
        }

        public void reset() {
            this.clear();
            this.defined = false;
            this.visible = false;
            this.priority = 4;
            this.relativePositioning = false;
            this.verticalAnchor = 0;
            this.horizontalAnchor = 0;
            this.anchorId = 0;
            this.rowCount = 15;
            this.rowLock = true;
            this.justification = 0;
            this.windowStyleId = 0;
            this.penStyleId = 0;
            this.windowFillColor = COLOR_SOLID_BLACK;
            this.foregroundColor = COLOR_SOLID_WHITE;
            this.backgroundColor = COLOR_SOLID_BLACK;
        }

        public void clear() {
            this.rolledUpCaptions.clear();
            this.captionStringBuilder.clear();
            this.italicsStartPosition = -1;
            this.underlineStartPosition = -1;
            this.foregroundColorStartPosition = -1;
            this.backgroundColorStartPosition = -1;
            this.row = 0;
        }

        public boolean isDefined() {
            return this.defined;
        }

        public void setVisibility(boolean visible) {
            this.visible = visible;
        }

        public boolean isVisible() {
            return this.visible;
        }

        public void defineWindow(boolean visible, boolean rowLock, boolean columnLock, int priority, boolean relativePositioning, int verticalAnchor, int horizontalAnchor, int rowCount, int columnCount, int anchorId, int windowStyleId, int penStyleId) {
            this.defined = true;
            this.visible = visible;
            this.rowLock = rowLock;
            this.priority = priority;
            this.relativePositioning = relativePositioning;
            this.verticalAnchor = verticalAnchor;
            this.horizontalAnchor = horizontalAnchor;
            this.anchorId = anchorId;
            if (this.rowCount != rowCount + 1) {
                this.rowCount = rowCount + 1;

                while(rowLock && this.rolledUpCaptions.size() >= this.rowCount || this.rolledUpCaptions.size() >= 15) {
                    this.rolledUpCaptions.remove(0);
                }
            }

            int penStyleIdIndex;
            if (windowStyleId != 0 && this.windowStyleId != windowStyleId) {
                this.windowStyleId = windowStyleId;
                penStyleIdIndex = windowStyleId - 1;
                this.setWindowAttributes(WINDOW_STYLE_FILL[penStyleIdIndex], COLOR_TRANSPARENT, WINDOW_STYLE_WORD_WRAP[penStyleIdIndex], 0, WINDOW_STYLE_PRINT_DIRECTION[penStyleIdIndex], WINDOW_STYLE_SCROLL_DIRECTION[penStyleIdIndex], WINDOW_STYLE_JUSTIFICATION[penStyleIdIndex]);
            }

            if (penStyleId != 0 && this.penStyleId != penStyleId) {
                this.penStyleId = penStyleId;
                penStyleIdIndex = penStyleId - 1;
                this.setPenAttributes(0, 1, 1, false, false, PEN_STYLE_EDGE_TYPE[penStyleIdIndex], PEN_STYLE_FONT_STYLE[penStyleIdIndex]);
                this.setPenColor(COLOR_SOLID_WHITE, PEN_STYLE_BACKGROUND[penStyleIdIndex], COLOR_SOLID_BLACK);
            }

        }

        public void setWindowAttributes(int fillColor, int borderColor, boolean wordWrapToggle, int borderType, int printDirection, int scrollDirection, int justification) {
            this.windowFillColor = fillColor;
            this.justification = justification;
        }

        public void setPenAttributes(int textTag, int offset, int penSize, boolean italicsToggle, boolean underlineToggle, int edgeType, int fontStyle) {
            if (this.italicsStartPosition != -1) {
                if (!italicsToggle) {
                    this.captionStringBuilder.setSpan(new StyleSpan(2), this.italicsStartPosition, this.captionStringBuilder.length(), 33);
                    this.italicsStartPosition = -1;
                }
            } else if (italicsToggle) {
                this.italicsStartPosition = this.captionStringBuilder.length();
            }

            if (this.underlineStartPosition != -1) {
                if (!underlineToggle) {
                    this.captionStringBuilder.setSpan(new UnderlineSpan(), this.underlineStartPosition, this.captionStringBuilder.length(), 33);
                    this.underlineStartPosition = -1;
                }
            } else if (underlineToggle) {
                this.underlineStartPosition = this.captionStringBuilder.length();
            }

        }

        public void setPenColor(int foregroundColor, int backgroundColor, int edgeColor) {
            if (this.foregroundColorStartPosition != -1 && this.foregroundColor != foregroundColor) {
                this.captionStringBuilder.setSpan(new ForegroundColorSpan(this.foregroundColor), this.foregroundColorStartPosition, this.captionStringBuilder.length(), 33);
            }

            if (foregroundColor != COLOR_SOLID_WHITE) {
                this.foregroundColorStartPosition = this.captionStringBuilder.length();
                this.foregroundColor = foregroundColor;
            }

            if (this.backgroundColorStartPosition != -1 && this.backgroundColor != backgroundColor) {
                this.captionStringBuilder.setSpan(new BackgroundColorSpan(this.backgroundColor), this.backgroundColorStartPosition, this.captionStringBuilder.length(), 33);
            }

            if (backgroundColor != COLOR_SOLID_BLACK) {
                this.backgroundColorStartPosition = this.captionStringBuilder.length();
                this.backgroundColor = backgroundColor;
            }

        }

        public void setPenLocation(int row, int column) {
            if (this.row != row) {
                this.append('\n');
            }

            this.row = row;
        }

        public void backspace() {
            int length = this.captionStringBuilder.length();
            if (length > 0) {
                this.captionStringBuilder.delete(length - 1, length);
            }

        }

        public void append(char text) {
            if (text == '\n') {
                this.rolledUpCaptions.add(this.buildSpannableString());
                this.captionStringBuilder.clear();
                if (this.italicsStartPosition != -1) {
                    this.italicsStartPosition = 0;
                }

                if (this.underlineStartPosition != -1) {
                    this.underlineStartPosition = 0;
                }

                if (this.foregroundColorStartPosition != -1) {
                    this.foregroundColorStartPosition = 0;
                }

                if (this.backgroundColorStartPosition != -1) {
                    this.backgroundColorStartPosition = 0;
                }

                while(this.rowLock && this.rolledUpCaptions.size() >= this.rowCount || this.rolledUpCaptions.size() >= 15) {
                    this.rolledUpCaptions.remove(0);
                }
            } else {
                this.captionStringBuilder.append(text);
            }

        }

        public SpannableString buildSpannableString() {
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(this.captionStringBuilder);
            int length = spannableStringBuilder.length();
            if (length > 0) {
                if (this.italicsStartPosition != -1) {
                    spannableStringBuilder.setSpan(new StyleSpan(2), this.italicsStartPosition, length, 33);
                }

                if (this.underlineStartPosition != -1) {
                    spannableStringBuilder.setSpan(new UnderlineSpan(), this.underlineStartPosition, length, 33);
                }

                if (this.foregroundColorStartPosition != -1) {
                    spannableStringBuilder.setSpan(new ForegroundColorSpan(this.foregroundColor), this.foregroundColorStartPosition, length, 33);
                }

                if (this.backgroundColorStartPosition != -1) {
                    spannableStringBuilder.setSpan(new BackgroundColorSpan(this.backgroundColor), this.backgroundColorStartPosition, length, 33);
                }
            }

            return new SpannableString(spannableStringBuilder);
        }

        public Cea708Cue build() {
            if (this.isEmpty()) {
                return null;
            } else {
                SpannableStringBuilder cueString = new SpannableStringBuilder();

                for(int i = 0; i < this.rolledUpCaptions.size(); ++i) {
                    cueString.append(this.rolledUpCaptions.get(i));
                    cueString.append('\n');
                }

                cueString.append(this.buildSpannableString());
                Alignment alignment;
                switch(this.justification) {
                case 0:
                case 3:
                    alignment = Alignment.ALIGN_NORMAL;
                    break;
                case 1:
                    alignment = Alignment.ALIGN_OPPOSITE;
                    break;
                case 2:
                    alignment = Alignment.ALIGN_CENTER;
                    break;
                default:
                    throw new IllegalArgumentException("Unexpected justification value: " + this.justification);
                }

                float position;
                float line;
                if (this.relativePositioning) {
                    position = (float)this.horizontalAnchor / 99.0F;
                    line = (float)this.verticalAnchor / 99.0F;
                } else {
                    position = (float)this.horizontalAnchor / 209.0F;
                    line = (float)this.verticalAnchor / 74.0F;
                }

                position = position * 0.9F + 0.05F;
                line = line * 0.9F + 0.05F;
                byte verticalAnchorType;
                if (this.anchorId % 3 == 0) {
                    verticalAnchorType = 0;
                } else if (this.anchorId % 3 == 1) {
                    verticalAnchorType = 1;
                } else {
                    verticalAnchorType = 2;
                }

                byte horizontalAnchorType;
                if (this.anchorId / 3 == 0) {
                    horizontalAnchorType = 0;
                } else if (this.anchorId / 3 == 1) {
                    horizontalAnchorType = 1;
                } else {
                    horizontalAnchorType = 2;
                }

                boolean windowColorSet = this.windowFillColor != COLOR_SOLID_BLACK;
                return new Cea708Cue(cueString, alignment, line, 0, verticalAnchorType, position, horizontalAnchorType, 1.4E-45F, windowColorSet, this.windowFillColor, this.priority);
            }
        }

        public static int getArgbColorFromCeaColor(int red, int green, int blue) {
            return getArgbColorFromCeaColor(red, green, blue, 0);
        }

        public static int getArgbColorFromCeaColor(int red, int green, int blue, int opacity) {
            Assertions.checkIndex(red, 0, 4);
            Assertions.checkIndex(green, 0, 4);
            Assertions.checkIndex(blue, 0, 4);
            Assertions.checkIndex(opacity, 0, 4);
            short alpha;
            switch(opacity) {
            case 0:
            case 1:
                alpha = 255;
                break;
            case 2:
                alpha = 127;
                break;
            case 3:
                alpha = 0;
                break;
            default:
                alpha = 255;
            }

            return Color.argb(alpha, red > 1 ? 255 : 0, green > 1 ? 255 : 0, blue > 1 ? 255 : 0);
        }

        static {
            WINDOW_STYLE_FILL = new int[]{COLOR_SOLID_BLACK, COLOR_TRANSPARENT, COLOR_SOLID_BLACK, COLOR_SOLID_BLACK, COLOR_TRANSPARENT, COLOR_SOLID_BLACK, COLOR_SOLID_BLACK};
            PEN_STYLE_FONT_STYLE = new int[]{0, 1, 2, 3, 4, 3, 4};
            PEN_STYLE_EDGE_TYPE = new int[]{0, 0, 0, 0, 0, 3, 3};
            PEN_STYLE_BACKGROUND = new int[]{COLOR_SOLID_BLACK, COLOR_SOLID_BLACK, COLOR_SOLID_BLACK, COLOR_SOLID_BLACK, COLOR_SOLID_BLACK, COLOR_TRANSPARENT, COLOR_TRANSPARENT};
        }
    }

    private static final class DtvCcPacket {
        public final int sequenceNumber;
        public final int packetSize;
        public final byte[] packetData;
        int currentIndex;

        public DtvCcPacket(int sequenceNumber, int packetSize) {
            this.sequenceNumber = sequenceNumber;
            this.packetSize = packetSize;
            this.packetData = new byte[2 * packetSize - 1];
            this.currentIndex = 0;
        }
    }
}
