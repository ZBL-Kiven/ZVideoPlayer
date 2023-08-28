package com.zj.playerLib.offline;

import java.io.IOException;

public final class DownloadException extends IOException {
    public DownloadException(String message) {
        super(message);
    }

    public DownloadException(Throwable cause) {
        super(cause);
    }
}
