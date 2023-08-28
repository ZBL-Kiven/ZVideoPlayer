package com.zj.playerLib.upstream;

import android.net.Uri;
import androidx.annotation.Nullable;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;

public final class FileDataSource extends BaseDataSource {
    @Nullable
    private RandomAccessFile file;
    @Nullable
    private Uri uri;
    private long bytesRemaining;
    private boolean opened;

    public FileDataSource() {
        super(false);
    }

    public long open(DataSpec dataSpec) throws FileDataSourceException {
        try {
            this.uri = dataSpec.uri;
            this.transferInitializing(dataSpec);
            this.file = new RandomAccessFile(dataSpec.uri.getPath(), "r");
            this.file.seek(dataSpec.position);
            this.bytesRemaining = dataSpec.length == -1L ? this.file.length() - dataSpec.position : dataSpec.length;
            if (this.bytesRemaining < 0L) {
                throw new EOFException();
            }
        } catch (IOException var3) {
            throw new FileDataSourceException(var3);
        }

        this.opened = true;
        this.transferStarted(dataSpec);
        return this.bytesRemaining;
    }

    public int read(byte[] buffer, int offset, int readLength) throws FileDataSourceException {
        if (readLength == 0) {
            return 0;
        } else if (this.bytesRemaining == 0L) {
            return -1;
        } else {
            int bytesRead;
            try {
                bytesRead = this.file.read(buffer, offset, (int)Math.min(this.bytesRemaining, readLength));
            } catch (IOException var6) {
                throw new FileDataSourceException(var6);
            }

            if (bytesRead > 0) {
                this.bytesRemaining -= bytesRead;
                this.bytesTransferred(bytesRead);
            }

            return bytesRead;
        }
    }

    @Nullable
    public Uri getUri() {
        return this.uri;
    }

    public void close() throws FileDataSourceException {
        this.uri = null;

        try {
            if (this.file != null) {
                this.file.close();
            }
        } catch (IOException var5) {
            throw new FileDataSourceException(var5);
        } finally {
            this.file = null;
            if (this.opened) {
                this.opened = false;
                this.transferEnded();
            }

        }

    }

    public static class FileDataSourceException extends IOException {
        public FileDataSourceException(IOException cause) {
            super(cause);
        }
    }
}
