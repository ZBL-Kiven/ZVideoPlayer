package com.zj.playerLib.offline;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Util;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class DownloadAction {
    @Nullable
    private static Deserializer[] defaultDeserializers;
    public final String type;
    public final int version;
    public final Uri uri;
    public final boolean isRemoveAction;
    public final byte[] data;

    public static synchronized Deserializer[] getDefaultDeserializers() {
        if (defaultDeserializers == null) {
            Deserializer[] deserializers = new Deserializer[4];
            int count = 1;
            deserializers[count] = ProgressiveDownloadAction.DESERIALIZER;
            Class<?> clazz;
            try {
                clazz = Class.forName("com.zj.playerLib.source.dash.offline.DashDownloadAction");
                deserializers[count++] = getDeserializer(clazz);
            } catch (Exception ignored) {
            }

            try {
                clazz = Class.forName("com.zj.playerLib.source.hls.offline.HlsDownloadAction");
                deserializers[count++] = getDeserializer(clazz);
            } catch (Exception ignored) {
            }

            try {
                clazz = Class.forName("com.zj.playerLib.source.smoothstreaming.offline.SsDownloadAction");
                deserializers[count++] = getDeserializer(clazz);
            } catch (Exception ignored) {
            }

            defaultDeserializers = (Deserializer[]) Arrays.copyOf((Object[]) Assertions.checkNotNull(deserializers), count);
        }
        return defaultDeserializers;
    }

    public static DownloadAction deserializeFromStream(Deserializer[] deserializers, InputStream input) throws IOException {
        DataInputStream dataInputStream = new DataInputStream(input);
        String type = dataInputStream.readUTF();
        int version = dataInputStream.readInt();
        for (Deserializer deserializer : deserializers) {
            if (type.equals(deserializer.type) && deserializer.version >= version) {
                return deserializer.readFromStream(version, dataInputStream);
            }
        }

        throw new DownloadException("No deserializer found for:" + type + ", " + version);
    }

    public static void serializeToStream(DownloadAction action, OutputStream output) throws IOException {
        DataOutputStream dataOutputStream = new DataOutputStream(output);
        dataOutputStream.writeUTF(action.type);
        dataOutputStream.writeInt(action.version);
        action.writeToStream(dataOutputStream);
        dataOutputStream.flush();
    }

    protected DownloadAction(String type, int version, Uri uri, boolean isRemoveAction, @Nullable byte[] data) {
        this.type = type;
        this.version = version;
        this.uri = uri;
        this.isRemoveAction = isRemoveAction;
        this.data = data != null ? data : Util.EMPTY_BYTE_ARRAY;
    }

    public final byte[] toByteArray() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try {
            serializeToStream(this, output);
        } catch (IOException var3) {
            throw new IllegalStateException();
        }

        return output.toByteArray();
    }

    public boolean isSameMedia(DownloadAction other) {
        return this.uri.equals(other.uri);
    }

    public List<StreamKey> getKeys() {
        return Collections.emptyList();
    }

    protected abstract void writeToStream(DataOutputStream var1) throws IOException;

    public abstract Downloader createDownloader(DownloaderConstructorHelper var1);

    public boolean equals(@Nullable Object o) {
        if (o != null && this.getClass() == o.getClass()) {
            DownloadAction that = (DownloadAction) o;
            return this.type.equals(that.type) && this.version == that.version && this.uri.equals(that.uri) && this.isRemoveAction == that.isRemoveAction && Arrays.equals(this.data, that.data);
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = this.uri.hashCode();
        result = 31 * result + (this.isRemoveAction ? 1 : 0);
        result = 31 * result + Arrays.hashCode(this.data);
        return result;
    }

    private static Deserializer getDeserializer(Class<?> clazz) throws NoSuchFieldException, IllegalAccessException {
        Object value = clazz.getDeclaredField("DESERIALIZER").get(null);
        return (Deserializer) Assertions.checkNotNull(value);
    }

    public abstract static class Deserializer {
        public final String type;
        public final int version;

        public Deserializer(String type, int version) {
            this.type = type;
            this.version = version;
        }

        public abstract DownloadAction readFromStream(int var1, DataInputStream var2) throws IOException;
    }
}
