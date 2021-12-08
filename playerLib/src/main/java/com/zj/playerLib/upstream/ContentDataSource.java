package com.zj.playerLib.upstream;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;

import androidx.annotation.Nullable;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel;

public final class ContentDataSource extends BaseDataSource {
    private final ContentResolver resolver;
    @Nullable
    private Uri uri;
    @Nullable
    private AssetFileDescriptor assetFileDescriptor;
    @Nullable
    private FileInputStream inputStream;
    private long bytesRemaining;
    private boolean opened;

    public ContentDataSource(Context context) {
        super(false);
        this.resolver = context.getContentResolver();
    }

    /**
     * @deprecated
     */
    @Deprecated
    public ContentDataSource(Context context, @Nullable TransferListener listener) {
        this(context);
        if (listener != null) {
            this.addTransferListener(listener);
        }

    }

    public long open(DataSpec dataSpec) throws ContentDataSourceException {
        try {
            this.uri = dataSpec.uri;
            this.transferInitializing(dataSpec);
            this.assetFileDescriptor = this.resolver.openAssetFileDescriptor(this.uri, "r");
            if (this.assetFileDescriptor == null) {
                throw new FileNotFoundException("Could not open file descriptor for: " + this.uri);
            }

            this.inputStream = new FileInputStream(this.assetFileDescriptor.getFileDescriptor());
            long assetStartOffset = this.assetFileDescriptor.getStartOffset();
            long skipped = this.inputStream.skip(assetStartOffset + dataSpec.position) - assetStartOffset;
            if (skipped != dataSpec.position) {
                throw new EOFException();
            }

            if (dataSpec.length != -1L) {
                this.bytesRemaining = dataSpec.length;
            } else {
                long assetFileDescriptorLength = this.assetFileDescriptor.getLength();
                if (assetFileDescriptorLength == -1L) {
                    FileChannel channel = this.inputStream.getChannel();
                    long channelSize = channel.size();
                    this.bytesRemaining = channelSize == 0L ? -1L : channelSize - channel.position();
                } else {
                    this.bytesRemaining = assetFileDescriptorLength - skipped;
                }
            }
        } catch (IOException var11) {
            throw new ContentDataSourceException(var11);
        }

        this.opened = true;
        this.transferStarted(dataSpec);
        return this.bytesRemaining;
    }

    public int read(byte[] buffer, int offset, int readLength) throws ContentDataSourceException {
        if (readLength == 0) {
            return 0;
        } else if (this.bytesRemaining == 0L) {
            return -1;
        } else {
            int bytesRead;
            try {
                int bytesToRead = this.bytesRemaining == -1L ? readLength : (int) Math.min(this.bytesRemaining, readLength);
                assert this.inputStream != null;
                bytesRead = this.inputStream.read(buffer, offset, bytesToRead);
            } catch (IOException var6) {
                throw new ContentDataSourceException(var6);
            }

            if (bytesRead == -1) {
                if (this.bytesRemaining != -1L) {
                    throw new ContentDataSourceException(new EOFException());
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

    public void close() throws ContentDataSourceException {
        this.uri = null;

        try {
            if (this.inputStream != null) {
                this.inputStream.close();
            }
        } catch (IOException var26) {
            throw new ContentDataSourceException(var26);
        } finally {
            finallyClose();
        }
    }

    private void finallyClose() throws ContentDataSourceException {
        this.inputStream = null;
        try {
            if (this.assetFileDescriptor != null) {
                this.assetFileDescriptor.close();
            }
        } catch (IOException var24) {
            throw new ContentDataSourceException(var24);
        } finally {
            this.assetFileDescriptor = null;
            if (this.opened) {
                this.opened = false;
                this.transferEnded();
            }
        }
    }

    public static class ContentDataSourceException extends IOException {
        public ContentDataSourceException(IOException cause) {
            super(cause);
        }
    }
}
