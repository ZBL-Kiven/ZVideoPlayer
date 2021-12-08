package com.zj.playerLib.text;

public class SubtitleDecoderException extends Exception {
    public SubtitleDecoderException(String message) {
        super(message);
    }

    public SubtitleDecoderException(Exception cause) {
        super(cause);
    }

    public SubtitleDecoderException(String message, Throwable cause) {
        super(message, cause);
    }
}
