package com.zj.playerLib.offline;

import android.net.Uri;
import androidx.annotation.Nullable;

import com.zj.playerLib.util.Assertions;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class SegmentDownloadAction extends DownloadAction {
    public final List<StreamKey> keys;

    protected SegmentDownloadAction(String type, int version, Uri uri, boolean isRemoveAction, @Nullable byte[] data, List<StreamKey> keys) {
        super(type, version, uri, isRemoveAction, data);
        if (isRemoveAction) {
            Assertions.checkArgument(keys.isEmpty());
            this.keys = Collections.emptyList();
        } else {
            ArrayList<StreamKey> mutableKeys = new ArrayList(keys);
            Collections.sort(mutableKeys);
            this.keys = Collections.unmodifiableList(mutableKeys);
        }

    }

    public List<StreamKey> getKeys() {
        return this.keys;
    }

    public final void writeToStream(DataOutputStream output) throws IOException {
        output.writeUTF(this.uri.toString());
        output.writeBoolean(this.isRemoveAction);
        output.writeInt(this.data.length);
        output.write(this.data);
        output.writeInt(this.keys.size());

        for(int i = 0; i < this.keys.size(); ++i) {
            this.writeKey(output, this.keys.get(i));
        }

    }

    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        } else if (!super.equals(o)) {
            return false;
        } else {
            SegmentDownloadAction that = (SegmentDownloadAction)o;
            return this.keys.equals(that.keys);
        }
    }

    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + this.keys.hashCode();
        return result;
    }

    private void writeKey(DataOutputStream output, StreamKey key) throws IOException {
        output.writeInt(key.periodIndex);
        output.writeInt(key.groupIndex);
        output.writeInt(key.trackIndex);
    }

    protected abstract static class SegmentDownloadActionDeserializer extends Deserializer {
        public SegmentDownloadActionDeserializer(String type, int version) {
            super(type, version);
        }

        public final DownloadAction readFromStream(int version, DataInputStream input) throws IOException {
            Uri uri = Uri.parse(input.readUTF());
            boolean isRemoveAction = input.readBoolean();
            int dataLength = input.readInt();
            byte[] data = new byte[dataLength];
            input.readFully(data);
            int keyCount = input.readInt();
            List<StreamKey> keys = new ArrayList();

            for(int i = 0; i < keyCount; ++i) {
                keys.add(this.readKey(version, input));
            }

            return this.createDownloadAction(uri, isRemoveAction, data, keys);
        }

        protected StreamKey readKey(int version, DataInputStream input) throws IOException {
            int periodIndex = input.readInt();
            int groupIndex = input.readInt();
            int trackIndex = input.readInt();
            return new StreamKey(periodIndex, groupIndex, trackIndex);
        }

        protected abstract DownloadAction createDownloadAction(Uri var1, boolean var2, byte[] var3, List<StreamKey> var4);
    }
}
