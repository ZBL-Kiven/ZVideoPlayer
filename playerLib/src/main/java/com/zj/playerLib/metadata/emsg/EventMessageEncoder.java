package com.zj.playerLib.metadata.emsg;

import androidx.annotation.Nullable;

import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Util;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class EventMessageEncoder {
    private final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(512);
    private final DataOutputStream dataOutputStream;

    public EventMessageEncoder() {
        this.dataOutputStream = new DataOutputStream(this.byteArrayOutputStream);
    }

    @Nullable
    public byte[] encode(EventMessage eventMessage, long timescale) {
        Assertions.checkArgument(timescale >= 0L);
        this.byteArrayOutputStream.reset();

        try {
            writeNullTerminatedString(this.dataOutputStream, eventMessage.schemeIdUri);
            String nonNullValue = eventMessage.value != null ? eventMessage.value : "";
            writeNullTerminatedString(this.dataOutputStream, nonNullValue);
            writeUnsignedInt(this.dataOutputStream, timescale);
            long presentationTime = Util.scaleLargeTimestamp(eventMessage.presentationTimeUs, timescale, 1000000L);
            writeUnsignedInt(this.dataOutputStream, presentationTime);
            long duration = Util.scaleLargeTimestamp(eventMessage.durationMs, timescale, 1000L);
            writeUnsignedInt(this.dataOutputStream, duration);
            writeUnsignedInt(this.dataOutputStream, eventMessage.id);
            this.dataOutputStream.write(eventMessage.messageData);
            this.dataOutputStream.flush();
            return this.byteArrayOutputStream.toByteArray();
        } catch (IOException var9) {
            throw new RuntimeException(var9);
        }
    }

    private static void writeNullTerminatedString(DataOutputStream dataOutputStream, String value) throws IOException {
        dataOutputStream.writeBytes(value);
        dataOutputStream.writeByte(0);
    }

    private static void writeUnsignedInt(DataOutputStream outputStream, long value) throws IOException {
        outputStream.writeByte((int)(value >>> 24) & 255);
        outputStream.writeByte((int)(value >>> 16) & 255);
        outputStream.writeByte((int)(value >>> 8) & 255);
        outputStream.writeByte((int)value & 255);
    }
}
