package com.zj.playerLib.metadata.emsg;

import com.zj.playerLib.metadata.Metadata;
import com.zj.playerLib.metadata.MetadataDecoder;
import com.zj.playerLib.metadata.MetadataInputBuffer;
import com.zj.playerLib.metadata.Metadata.Entry;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.Util;
import java.nio.ByteBuffer;
import java.util.Arrays;

public final class EventMessageDecoder implements MetadataDecoder {
    public EventMessageDecoder() {
    }

    public Metadata decode(MetadataInputBuffer inputBuffer) {
        ByteBuffer buffer = inputBuffer.data;
        byte[] data = buffer.array();
        int size = buffer.limit();
        ParsableByteArray emsgData = new ParsableByteArray(data, size);
        String schemeIdUri = Assertions.checkNotNull(emsgData.readNullTerminatedString());
        String value = Assertions.checkNotNull(emsgData.readNullTerminatedString());
        long timescale = emsgData.readUnsignedInt();
        long presentationTimeUs = Util.scaleLargeTimestamp(emsgData.readUnsignedInt(), 1000000L, timescale);
        long durationMs = Util.scaleLargeTimestamp(emsgData.readUnsignedInt(), 1000L, timescale);
        long id = emsgData.readUnsignedInt();
        byte[] messageData = Arrays.copyOfRange(data, emsgData.getPosition(), size);
        return new Metadata(new EventMessage(schemeIdUri, value, durationMs, id, messageData, presentationTimeUs));
    }
}
