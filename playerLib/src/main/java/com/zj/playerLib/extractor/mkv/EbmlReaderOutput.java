package com.zj.playerLib.extractor.mkv;

import com.zj.playerLib.ParserException;
import com.zj.playerLib.extractor.ExtractorInput;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

interface EbmlReaderOutput {
    int TYPE_UNKNOWN = 0;
    int TYPE_MASTER = 1;
    int TYPE_UNSIGNED_INT = 2;
    int TYPE_STRING = 3;
    int TYPE_BINARY = 4;
    int TYPE_FLOAT = 5;

    int getElementType(int var1);

    boolean isLevel1Element(int var1);

    void startMasterElement(int var1, long var2, long var4) throws ParserException;

    void endMasterElement(int var1) throws ParserException;

    void integerElement(int var1, long var2) throws ParserException;

    void floatElement(int var1, double var2) throws ParserException;

    void stringElement(int var1, String var2) throws ParserException;

    void binaryElement(int var1, int var2, ExtractorInput var3) throws IOException, InterruptedException;

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @interface ElementType {
    }
}
