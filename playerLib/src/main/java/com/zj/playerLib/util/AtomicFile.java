package com.zj.playerLib.util;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class AtomicFile {
    private final File baseName;
    private final File backupName;

    public AtomicFile(File baseName) {
        this.baseName = baseName;
        this.backupName = new File(baseName.getPath() + ".bak");
    }

    public boolean delete() {
        return this.baseName.delete() || this.backupName.delete();
    }

    public OutputStream startWrite() throws IOException {
        if (this.baseName.exists()) {
            if (!this.backupName.exists()) {
                if (!this.baseName.renameTo(this.backupName)) {
                    Log.w("AtomicFile", "Couldn't rename file " + this.baseName + " to backup file " + this.backupName);
                }
            } else {
                this.baseName.delete();
            }
        }

        AtomicFileOutputStream str;
        try {
            str = new AtomicFileOutputStream(this.baseName);
        } catch (FileNotFoundException var6) {
            File parent = this.baseName.getParentFile();
            if (parent == null || !parent.mkdirs()) {
                throw new IOException("Couldn't create directory " + this.baseName, var6);
            }

            try {
                str = new AtomicFileOutputStream(this.baseName);
            } catch (FileNotFoundException var5) {
                throw new IOException("Couldn't create " + this.baseName, var5);
            }
        }

        return str;
    }

    public void endWrite(OutputStream str) throws IOException {
        str.close();
        this.backupName.delete();
    }

    public InputStream openRead() throws FileNotFoundException {
        this.restoreBackup();
        return new FileInputStream(this.baseName);
    }

    private void restoreBackup() {
        if (this.backupName.exists()) {
            this.baseName.delete();
            this.backupName.renameTo(this.baseName);
        }

    }

    private static final class AtomicFileOutputStream extends OutputStream {
        private final FileOutputStream fileOutputStream;
        private boolean closed = false;

        public AtomicFileOutputStream(File file) throws FileNotFoundException {
            this.fileOutputStream = new FileOutputStream(file);
        }

        public void close() throws IOException {
            if (!this.closed) {
                this.closed = true;
                this.flush();

                try {
                    this.fileOutputStream.getFD().sync();
                } catch (IOException var2) {
                    Log.w("AtomicFile", "Failed to sync file descriptor:", var2);
                }

                this.fileOutputStream.close();
            }
        }

        public void flush() throws IOException {
            this.fileOutputStream.flush();
        }

        public void write(int b) throws IOException {
            this.fileOutputStream.write(b);
        }

        public void write(@NonNull byte[] b) throws IOException {
            this.fileOutputStream.write(b);
        }

        public void write(@NonNull byte[] b, int off, int len) throws IOException {
            this.fileOutputStream.write(b, off, len);
        }
    }
}
