//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.source;

import android.net.Uri;

import com.zj.playerLib.ParserException;

public class UnrecognizedInputFormatException extends ParserException {
    public final Uri uri;

    public UnrecognizedInputFormatException(String message, Uri uri) {
        super(message);
        this.uri = uri;
    }
}
