package com.zj.playerLib.upstream;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class RawResourceDataSource extends BaseDataSource {
    public static final String RAW_RESOURCE_SCHEME = "rawresource";
    private final Resources resources;
    @Nullable
    private Uri uri;
    @Nullable
    private AssetFileDescriptor assetFileDescriptor;
    @Nullable
    private InputStream inputStream;
    private long bytesRemaining;
    private boolean opened;

    public static Uri buildRawResourceUri(int rawResourceId) {
        return Uri.parse("rawresource:///" + rawResourceId);
    }

    public RawResourceDataSource(Context context) {
        super(false);
        this.resources = context.getResources();
    }

    /** @deprecated */
    @Deprecated
    public RawResourceDataSource(Context context, @Nullable TransferListener listener) {
        this(context);
        if (listener != null) {
            this.addTransferListener(listener);
        }

    }

    public long open(DataSpec dataSpec) throws RawResourceDataSourceException {
        try {
            this.uri = dataSpec.uri;
            if (!TextUtils.equals("rawresource", this.uri.getScheme())) {
                throw new RawResourceDataSourceException("URI must use scheme rawresource");
            }

            int resourceId;
            try {
                resourceId = Integer.parseInt(this.uri.getLastPathSegment());
            } catch (NumberFormatException var7) {
                throw new RawResourceDataSourceException("Resource identifier must be an integer.");
            }

            this.transferInitializing(dataSpec);
            this.assetFileDescriptor = this.resources.openRawResourceFd(resourceId);
            this.inputStream = new FileInputStream(this.assetFileDescriptor.getFileDescriptor());
            this.inputStream.skip(this.assetFileDescriptor.getStartOffset());
            long skipped = this.inputStream.skip(dataSpec.position);
            if (skipped < dataSpec.position) {
                throw new EOFException();
            }

            if (dataSpec.length != -1L) {
                this.bytesRemaining = dataSpec.length;
            } else {
                long assetFileDescriptorLength = this.assetFileDescriptor.getLength();
                this.bytesRemaining = assetFileDescriptorLength == -1L ? -1L : assetFileDescriptorLength - dataSpec.position;
            }
        } catch (IOException var8) {
            throw new RawResourceDataSourceException(var8);
        }

        this.opened = true;
        this.transferStarted(dataSpec);
        return this.bytesRemaining;
    }

    public int read(byte[] buffer, int offset, int readLength) throws RawResourceDataSourceException {
        if (readLength == 0) {
            return 0;
        } else if (this.bytesRemaining == 0L) {
            return -1;
        } else {
            int bytesRead;
            try {
                int bytesToRead = this.bytesRemaining == -1L ? readLength : (int)Math.min(this.bytesRemaining, readLength);
                bytesRead = this.inputStream.read(buffer, offset, bytesToRead);
            } catch (IOException var6) {
                throw new RawResourceDataSourceException(var6);
            }

            if (bytesRead == -1) {
                if (this.bytesRemaining != -1L) {
                    throw new RawResourceDataSourceException(new EOFException());
                } else {
                    return -1;
                }
            } else {
                if (this.bytesRemaining != -1L) {
                    this.bytesRemaining -= bytesRead;
                }

                this.bytesTransferred(bytesRead);
                return bytesRead;
            }
        }
    }

    @Nullable
    public Uri getUri() {
        return this.uri;
    }

    public void close() throws RawResourceDataSourceException {
        this.uri = null;

        try {
            if (this.inputStream != null) {
                this.inputStream.close();
            }
        } catch (IOException var26) {
            throw new RawResourceDataSourceException(var26);
        } finally {
            this.inputStream = null;

            try {
                if (this.assetFileDescriptor != null) {
                    this.assetFileDescriptor.close();
                }
            } catch (IOException var24) {
                throw new RawResourceDataSourceException(var24);
            } finally {
                this.assetFileDescriptor = null;
                if (this.opened) {
                    this.opened = false;
                    this.transferEnded();
                }

            }

        }

    }

    public static class RawResourceDataSourceException extends IOException {
        public RawResourceDataSourceException(String message) {
            super(message);
        }

        public RawResourceDataSourceException(IOException e) {
            super(e);
        }
    }
}
