package com.zj.playerLib.upstream.cache;

import com.zj.playerLib.upstream.DataSink;
import com.zj.playerLib.upstream.DataSpec;
import com.zj.playerLib.upstream.cache.Cache.CacheException;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.ReusableBufferedOutputStream;
import com.zj.playerLib.util.Util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public final class CacheDataSink implements DataSink {
    public static final int DEFAULT_BUFFER_SIZE = 20480;
    private final Cache cache;
    private final long maxCacheFileSize;
    private final int bufferSize;
    private final boolean syncFileDescriptor;
    private DataSpec dataSpec;
    private File file;
    private OutputStream outputStream;
    private FileOutputStream underlyingFileOutputStream;
    private long outputStreamBytesWritten;
    private long dataSpecBytesWritten;
    private ReusableBufferedOutputStream bufferedOutputStream;

    public CacheDataSink(Cache cache, long maxCacheFileSize) {
        this(cache, maxCacheFileSize, 20480, true);
    }

    public CacheDataSink(Cache cache, long maxCacheFileSize, boolean syncFileDescriptor) {
        this(cache, maxCacheFileSize, 20480, syncFileDescriptor);
    }

    public CacheDataSink(Cache cache, long maxCacheFileSize, int bufferSize) {
        this(cache, maxCacheFileSize, bufferSize, true);
    }

    public CacheDataSink(Cache cache, long maxCacheFileSize, int bufferSize, boolean syncFileDescriptor) {
        this.cache = Assertions.checkNotNull(cache);
        this.maxCacheFileSize = maxCacheFileSize;
        this.bufferSize = bufferSize;
        this.syncFileDescriptor = syncFileDescriptor;
    }

    public void open(DataSpec dataSpec) throws CacheDataSinkException {
        if (dataSpec.length == -1L && !dataSpec.isFlagSet(2)) {
            this.dataSpec = null;
        } else {
            this.dataSpec = dataSpec;
            this.dataSpecBytesWritten = 0L;

            try {
                this.openNextOutputStream();
            } catch (IOException var3) {
                throw new CacheDataSinkException(var3);
            }
        }
    }

    public void write(byte[] buffer, int offset, int length) throws CacheDataSinkException {
        if (this.dataSpec != null) {
            try {
                int bytesToWrite;
                for(int bytesWritten = 0; bytesWritten < length; this.dataSpecBytesWritten += bytesToWrite) {
                    if (this.outputStreamBytesWritten == this.maxCacheFileSize) {
                        this.closeCurrentOutputStream();
                        this.openNextOutputStream();
                    }

                    bytesToWrite = (int)Math.min(length - bytesWritten, this.maxCacheFileSize - this.outputStreamBytesWritten);
                    this.outputStream.write(buffer, offset + bytesWritten, bytesToWrite);
                    bytesWritten += bytesToWrite;
                    this.outputStreamBytesWritten += bytesToWrite;
                }

            } catch (IOException var6) {
                throw new CacheDataSinkException(var6);
            }
        }
    }

    public void close() throws CacheDataSinkException {
        if (this.dataSpec != null) {
            try {
                this.closeCurrentOutputStream();
            } catch (IOException var2) {
                throw new CacheDataSinkException(var2);
            }
        }
    }

    private void openNextOutputStream() throws IOException {
        long maxLength = this.dataSpec.length == -1L ? this.maxCacheFileSize : Math.min(this.dataSpec.length - this.dataSpecBytesWritten, this.maxCacheFileSize);
        this.file = this.cache.startFile(this.dataSpec.key, this.dataSpec.absoluteStreamPosition + this.dataSpecBytesWritten, maxLength);
        this.underlyingFileOutputStream = new FileOutputStream(this.file);
        if (this.bufferSize > 0) {
            if (this.bufferedOutputStream == null) {
                this.bufferedOutputStream = new ReusableBufferedOutputStream(this.underlyingFileOutputStream, this.bufferSize);
            } else {
                this.bufferedOutputStream.reset(this.underlyingFileOutputStream);
            }

            this.outputStream = this.bufferedOutputStream;
        } else {
            this.outputStream = this.underlyingFileOutputStream;
        }

        this.outputStreamBytesWritten = 0L;
    }

    private void closeCurrentOutputStream() throws IOException {
        if (this.outputStream != null) {
            boolean success = false;
            boolean var6 = false;

            try {
                var6 = true;
                this.outputStream.flush();
                if (this.syncFileDescriptor) {
                    this.underlyingFileOutputStream.getFD().sync();
                }

                success = true;
                var6 = false;
            } finally {
                if (var6) {
                    Util.closeQuietly(this.outputStream);
                    this.outputStream = null;
                    File fileToCommit = this.file;
                    this.file = null;
                    if (success) {
                        this.cache.commitFile(fileToCommit);
                    } else {
                        fileToCommit.delete();
                    }

                }
            }

            Util.closeQuietly(this.outputStream);
            this.outputStream = null;
            File fileToCommit = this.file;
            this.file = null;
            if (success) {
                this.cache.commitFile(fileToCommit);
            } else {
                fileToCommit.delete();
            }

        }
    }

    public static class CacheDataSinkException extends CacheException {
        public CacheDataSinkException(IOException cause) {
            super(cause);
        }
    }
}
