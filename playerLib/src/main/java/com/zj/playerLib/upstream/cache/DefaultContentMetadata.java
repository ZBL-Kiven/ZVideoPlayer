package com.zj.playerLib.upstream.cache;

import androidx.annotation.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public final class DefaultContentMetadata implements ContentMetadata {
    public static final DefaultContentMetadata EMPTY = new DefaultContentMetadata(Collections.emptyMap());
    private static final int MAX_VALUE_LENGTH = 10485760;
    private int hashCode;
    private final Map<String, byte[]> metadata;

    public static DefaultContentMetadata readFromStream(DataInputStream input) throws IOException {
        int size = input.readInt();
        HashMap<String, byte[]> metadata = new HashMap();

        for(int i = 0; i < size; ++i) {
            String name = input.readUTF();
            int valueSize = input.readInt();
            if (valueSize < 0 || valueSize > 10485760) {
                throw new IOException("Invalid value size: " + valueSize);
            }

            byte[] value = new byte[valueSize];
            input.readFully(value);
            metadata.put(name, value);
        }

        return new DefaultContentMetadata(metadata);
    }

    private DefaultContentMetadata(Map<String, byte[]> metadata) {
        this.metadata = Collections.unmodifiableMap(metadata);
    }

    public DefaultContentMetadata copyWithMutationsApplied(ContentMetadataMutations mutations) {
        Map<String, byte[]> mutatedMetadata = applyMutations(this.metadata, mutations);
        return this.isMetadataEqual(mutatedMetadata) ? this : new DefaultContentMetadata(mutatedMetadata);
    }

    public void writeToStream(DataOutputStream output) throws IOException {
        output.writeInt(this.metadata.size());
        Iterator var2 = this.metadata.entrySet().iterator();

        while(var2.hasNext()) {
            Entry<String, byte[]> entry = (Entry)var2.next();
            output.writeUTF(entry.getKey());
            byte[] value = entry.getValue();
            output.writeInt(value.length);
            output.write(value);
        }

    }

    public final byte[] get(String name, byte[] defaultValue) {
        if (this.metadata.containsKey(name)) {
            byte[] bytes = this.metadata.get(name);
            return Arrays.copyOf(bytes, bytes.length);
        } else {
            return defaultValue;
        }
    }

    public final String get(String name, String defaultValue) {
        if (this.metadata.containsKey(name)) {
            byte[] bytes = this.metadata.get(name);
            return new String(bytes, StandardCharsets.UTF_8);
        } else {
            return defaultValue;
        }
    }

    public final long get(String name, long defaultValue) {
        if (this.metadata.containsKey(name)) {
            byte[] bytes = this.metadata.get(name);
            return ByteBuffer.wrap(bytes).getLong();
        } else {
            return defaultValue;
        }
    }

    public final boolean contains(String name) {
        return this.metadata.containsKey(name);
    }

    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        } else {
            return o != null && this.getClass() == o.getClass() && this.isMetadataEqual(((DefaultContentMetadata) o).metadata);
        }
    }

    private boolean isMetadataEqual(Map<String, byte[]> otherMetadata) {
        if (this.metadata.size() != otherMetadata.size()) {
            return false;
        } else {
            Iterator var2 = this.metadata.entrySet().iterator();

            byte[] value;
            byte[] otherValue;
            do {
                if (!var2.hasNext()) {
                    return true;
                }

                Entry<String, byte[]> entry = (Entry)var2.next();
                value = entry.getValue();
                otherValue = otherMetadata.get(entry.getKey());
            } while(Arrays.equals(value, otherValue));

            return false;
        }
    }

    public int hashCode() {
        if (this.hashCode == 0) {
            int result = 0;

            Entry entry;
            for(Iterator var2 = this.metadata.entrySet().iterator(); var2.hasNext(); result += entry.getKey().hashCode() ^ Arrays.hashCode((byte[])entry.getValue())) {
                entry = (Entry)var2.next();
            }

            this.hashCode = result;
        }

        return this.hashCode;
    }

    private static Map<String, byte[]> applyMutations(Map<String, byte[]> otherMetadata, ContentMetadataMutations mutations) {
        HashMap<String, byte[]> metadata = new HashMap(otherMetadata);
        removeValues(metadata, mutations.getRemovedValues());
        addValues(metadata, mutations.getEditedValues());
        return metadata;
    }

    private static void removeValues(HashMap<String, byte[]> metadata, List<String> names) {
        for(int i = 0; i < names.size(); ++i) {
            metadata.remove(names.get(i));
        }

    }

    private static void addValues(HashMap<String, byte[]> metadata, Map<String, Object> values) {
        Iterator var2 = values.keySet().iterator();

        while(var2.hasNext()) {
            String name = (String)var2.next();
            Object value = values.get(name);
            byte[] bytes = getBytes(value);
            if (bytes.length > 10485760) {
                throw new IllegalArgumentException("The size of " + name + " (" + bytes.length + ") is greater than maximum allowed: " + 10485760);
            }

            metadata.put(name, bytes);
        }

    }

    private static byte[] getBytes(Object value) {
        if (value instanceof Long) {
            return ByteBuffer.allocate(8).putLong((Long)value).array();
        } else if (value instanceof String) {
            return ((String)value).getBytes(StandardCharsets.UTF_8);
        } else if (value instanceof byte[]) {
            return (byte[]) value;
        } else {
            throw new IllegalArgumentException();
        }
    }
}
