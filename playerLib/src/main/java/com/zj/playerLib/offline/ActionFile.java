package com.zj.playerLib.offline;

import com.zj.playerLib.offline.DownloadAction.Deserializer;
import com.zj.playerLib.util.AtomicFile;
import com.zj.playerLib.util.Util;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public final class ActionFile {
    static final int VERSION = 0;
    private final AtomicFile atomicFile;
    private final File actionFile;

    public ActionFile(File actionFile) {
        this.actionFile = actionFile;
        this.atomicFile = new AtomicFile(actionFile);
    }

    public DownloadAction[] load(Deserializer... deserializers) throws IOException {
        if (!this.actionFile.exists()) {
            return new DownloadAction[0];
        } else {
            InputStream inputStream = null;

            try {
                inputStream = this.atomicFile.openRead();
                DataInputStream dataInputStream = new DataInputStream(inputStream);
                int version = dataInputStream.readInt();
                if (version > 0) {
                    throw new IOException("Unsupported action file version: " + version);
                } else {
                    int actionCount = dataInputStream.readInt();
                    DownloadAction[] actions = new DownloadAction[actionCount];

                    for(int i = 0; i < actionCount; ++i) {
                        actions[i] = DownloadAction.deserializeFromStream(deserializers, dataInputStream);
                    }

                    DownloadAction[] var11 = actions;
                    return var11;
                }
            } finally {
                Util.closeQuietly(inputStream);
            }
        }
    }

    public void store(DownloadAction... downloadActions) throws IOException {
        DataOutputStream output = null;

        try {
            output = new DataOutputStream(this.atomicFile.startWrite());
            output.writeInt(0);
            output.writeInt(downloadActions.length);
            DownloadAction[] var3 = downloadActions;
            int var4 = downloadActions.length;

            for(int var5 = 0; var5 < var4; ++var5) {
                DownloadAction action = var3[var5];
                DownloadAction.serializeToStream(action, output);
            }

            this.atomicFile.endWrite(output);
            output = null;
        } finally {
            Util.closeQuietly(output);
        }
    }
}
