package com.zj.playerLib.upstream;

import android.net.Uri;
import androidx.annotation.Nullable;
import com.zj.playerLib.util.Assertions;
import java.io.IOException;

public final class ByteArrayDataSource extends BaseDataSource {
    private final byte[] data;
    @Nullable
    private Uri uri;
    private int readPosition;
    private int bytesRemaining;
    private boolean opened;

    public ByteArrayDataSource(byte[] data) {
        super(false);
        Assertions.checkNotNull(data);
        Assertions.checkArgument(data.length > 0);
        this.data = data;
    }

    public long open(DataSpec dataSpec) throws IOException {
        this.uri = dataSpec.uri;
        this.transferInitializing(dataSpec);
        this.readPosition = (int)dataSpec.position;
        this.bytesRemaining = (int)(dataSpec.length == -1L ? (long)this.data.length - dataSpec.position : dataSpec.length);
        if (this.bytesRemaining > 0 && this.readPosition + this.bytesRemaining <= this.data.length) {
            this.opened = true;
            this.transferStarted(dataSpec);
            return this.bytesRemaining;
        } else {
            throw new IOException("Unsatisfiable range: [" + this.readPosition + ", " + dataSpec.length + "], length: " + this.data.length);
        }
    }

    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        if (readLength == 0) {
            return 0;
        } else if (this.bytesRemaining == 0) {
            return -1;
        } else {
            readLength = Math.min(readLength, this.bytesRemaining);
            System.arraycopy(this.data, this.readPosition, buffer, offset, readLength);
            this.readPosition += readLength;
            this.bytesRemaining -= readLength;
            this.bytesTransferred(readLength);
            return readLength;
        }
    }

    @Nullable
    public Uri getUri() {
        return this.uri;
    }

    public void close() throws IOException {
        if (this.opened) {
            this.opened = false;
            this.transferEnded();
        }

        this.uri = null;
    }
}
