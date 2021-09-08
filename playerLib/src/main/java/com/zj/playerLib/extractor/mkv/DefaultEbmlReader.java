//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.extractor.mkv;

import com.zj.playerLib.ParserException;
import com.zj.playerLib.extractor.ExtractorInput;
import com.zj.playerLib.util.Assertions;

import java.io.IOException;
import java.util.ArrayDeque;

final class DefaultEbmlReader implements EbmlReader {
    private static final int ELEMENT_STATE_READ_ID = 0;
    private static final int ELEMENT_STATE_READ_CONTENT_SIZE = 1;
    private static final int ELEMENT_STATE_READ_CONTENT = 2;
    private static final int MAX_ID_BYTES = 4;
    private static final int MAX_LENGTH_BYTES = 8;
    private static final int MAX_INTEGER_ELEMENT_SIZE_BYTES = 8;
    private static final int VALID_FLOAT32_ELEMENT_SIZE_BYTES = 4;
    private static final int VALID_FLOAT64_ELEMENT_SIZE_BYTES = 8;
    private final byte[] scratch = new byte[8];
    private final ArrayDeque<MasterElement> masterElementsStack = new ArrayDeque();
    private final VarintReader varintReader = new VarintReader();
    private EbmlReaderOutput output;
    private int elementState;
    private int elementId;
    private long elementContentSize;

    public DefaultEbmlReader() {
    }

    public void init(EbmlReaderOutput eventHandler) {
        this.output = eventHandler;
    }

    public void reset() {
        this.elementState = 0;
        this.masterElementsStack.clear();
        this.varintReader.reset();
    }

    public boolean read(ExtractorInput input) throws IOException, InterruptedException {
        Assertions.checkState(this.output != null);

        while(this.masterElementsStack.isEmpty() || input.getPosition() < ((MasterElement)this.masterElementsStack.peek()).elementEndPosition) {
            if (this.elementState == 0) {
                long result = this.varintReader.readUnsignedVarint(input, true, false, 4);
                if (result == -2L) {
                    result = this.maybeResyncToNextLevel1Element(input);
                }

                if (result == -1L) {
                    return false;
                }

                this.elementId = (int)result;
                this.elementState = 1;
            }

            if (this.elementState == 1) {
                this.elementContentSize = this.varintReader.readUnsignedVarint(input, false, true, 8);
                this.elementState = 2;
            }

            int type = this.output.getElementType(this.elementId);
            switch(type) {
            case 0:
                input.skipFully((int)this.elementContentSize);
                this.elementState = 0;
                break;
            case 1:
                long elementContentPosition = input.getPosition();
                long elementEndPosition = elementContentPosition + this.elementContentSize;
                this.masterElementsStack.push(new MasterElement(this.elementId, elementEndPosition));
                this.output.startMasterElement(this.elementId, elementContentPosition, this.elementContentSize);
                this.elementState = 0;
                return true;
            case 2:
                if (this.elementContentSize > 8L) {
                    throw new ParserException("Invalid integer size: " + this.elementContentSize);
                }

                this.output.integerElement(this.elementId, this.readInteger(input, (int)this.elementContentSize));
                this.elementState = 0;
                return true;
            case 3:
                if (this.elementContentSize > 2147483647L) {
                    throw new ParserException("String element size: " + this.elementContentSize);
                }

                this.output.stringElement(this.elementId, this.readString(input, (int)this.elementContentSize));
                this.elementState = 0;
                return true;
            case 4:
                this.output.binaryElement(this.elementId, (int)this.elementContentSize, input);
                this.elementState = 0;
                return true;
            case 5:
                if (this.elementContentSize != 4L && this.elementContentSize != 8L) {
                    throw new ParserException("Invalid float size: " + this.elementContentSize);
                }

                this.output.floatElement(this.elementId, this.readFloat(input, (int)this.elementContentSize));
                this.elementState = 0;
                return true;
            default:
                throw new ParserException("Invalid element type " + type);
            }
        }

        this.output.endMasterElement(((MasterElement)this.masterElementsStack.pop()).elementId);
        return true;
    }

    private long maybeResyncToNextLevel1Element(ExtractorInput input) throws IOException, InterruptedException {
        input.resetPeekPosition();

        while(true) {
            input.peekFully(this.scratch, 0, 4);
            int varintLength = VarintReader.parseUnsignedVarintLength(this.scratch[0]);
            if (varintLength != -1 && varintLength <= 4) {
                int potentialId = (int)VarintReader.assembleVarint(this.scratch, varintLength, false);
                if (this.output.isLevel1Element(potentialId)) {
                    input.skipFully(varintLength);
                    return (long)potentialId;
                }
            }

            input.skipFully(1);
        }
    }

    private long readInteger(ExtractorInput input, int byteLength) throws IOException, InterruptedException {
        input.readFully(this.scratch, 0, byteLength);
        long value = 0L;

        for(int i = 0; i < byteLength; ++i) {
            value = value << 8 | (long)(this.scratch[i] & 255);
        }

        return value;
    }

    private double readFloat(ExtractorInput input, int byteLength) throws IOException, InterruptedException {
        long integerValue = this.readInteger(input, byteLength);
        double floatValue;
        if (byteLength == 4) {
            floatValue = (double)Float.intBitsToFloat((int)integerValue);
        } else {
            floatValue = Double.longBitsToDouble(integerValue);
        }

        return floatValue;
    }

    private String readString(ExtractorInput input, int byteLength) throws IOException, InterruptedException {
        if (byteLength == 0) {
            return "";
        } else {
            byte[] stringBytes = new byte[byteLength];
            input.readFully(stringBytes, 0, byteLength);

            int trimmedLength;
            for(trimmedLength = byteLength; trimmedLength > 0 && stringBytes[trimmedLength - 1] == 0; --trimmedLength) {
            }

            return new String(stringBytes, 0, trimmedLength);
        }
    }

    private static final class MasterElement {
        private final int elementId;
        private final long elementEndPosition;

        private MasterElement(int elementId, long elementEndPosition) {
            this.elementId = elementId;
            this.elementEndPosition = elementEndPosition;
        }
    }
}
