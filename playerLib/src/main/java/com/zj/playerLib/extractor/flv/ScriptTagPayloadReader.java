//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.extractor.flv;

import com.zj.playerLib.ParserException;
import com.zj.playerLib.extractor.TrackOutput;
import com.zj.playerLib.util.ParsableByteArray;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

final class ScriptTagPayloadReader extends TagPayloadReader {
    private static final String NAME_METADATA = "onMetaData";
    private static final String KEY_DURATION = "duration";
    private static final int AMF_TYPE_NUMBER = 0;
    private static final int AMF_TYPE_BOOLEAN = 1;
    private static final int AMF_TYPE_STRING = 2;
    private static final int AMF_TYPE_OBJECT = 3;
    private static final int AMF_TYPE_ECMA_ARRAY = 8;
    private static final int AMF_TYPE_END_MARKER = 9;
    private static final int AMF_TYPE_STRICT_ARRAY = 10;
    private static final int AMF_TYPE_DATE = 11;
    private long durationUs = -9223372036854775807L;

    public ScriptTagPayloadReader() {
        super((TrackOutput) null);
    }

    public long getDurationUs() {
        return this.durationUs;
    }

    public void seek() {
    }

    protected boolean parseHeader(ParsableByteArray data) {
        return true;
    }

    protected void parsePayload(ParsableByteArray data, long timeUs) throws ParserException {
        int nameType = readAmfType(data);
        if (nameType != 2) {
            throw new ParserException();
        } else {
            String name = readAmfString(data);
            if ("onMetaData".equals(name)) {
                int type = readAmfType(data);
                if (type == 8) {
                    Map<String, Object> metadata = readAmfEcmaArray(data);
                    if (metadata.containsKey("duration")) {
                        Object duration = metadata.get("duration");
                        if (duration == null) duration = 0;
                        double durationSeconds = (Double) duration;
                        if (durationSeconds > 0.0D) {
                            this.durationUs = (long) (durationSeconds * 1000000.0D);
                        }
                    }
                }
            }
        }
    }

    private static int readAmfType(ParsableByteArray data) {
        return data.readUnsignedByte();
    }

    private static Boolean readAmfBoolean(ParsableByteArray data) {
        return data.readUnsignedByte() == 1;
    }

    private static Double readAmfDouble(ParsableByteArray data) {
        return Double.longBitsToDouble(data.readLong());
    }

    private static String readAmfString(ParsableByteArray data) {
        int size = data.readUnsignedShort();
        int position = data.getPosition();
        data.skipBytes(size);
        return new String(data.data, position, size);
    }

    private static ArrayList<Object> readAmfStrictArray(ParsableByteArray data) {
        int count = data.readUnsignedIntToInt();
        ArrayList<Object> list = new ArrayList<>(count);

        for (int i = 0; i < count; ++i) {
            int type = readAmfType(data);
            list.add(readAmfData(data, type));
        }

        return list;
    }

    private static HashMap<String, Object> readAmfObject(ParsableByteArray data) {
        HashMap<String, Object> array = new HashMap<>();

        while (true) {
            String key = readAmfString(data);
            int type = readAmfType(data);
            if (type == 9) {
                return array;
            }

            array.put(key, readAmfData(data, type));
        }
    }

    private static HashMap<String, Object> readAmfEcmaArray(ParsableByteArray data) {
        int count = data.readUnsignedIntToInt();
        HashMap<String, Object> array = new HashMap<>(count);

        for (int i = 0; i < count; ++i) {
            String key = readAmfString(data);
            int type = readAmfType(data);
            array.put(key, readAmfData(data, type));
        }

        return array;
    }

    private static Date readAmfDate(ParsableByteArray data) {
        Date date = new Date(data.readLong());
        data.skipBytes(2);
        return date;
    }

    private static Object readAmfData(ParsableByteArray data, int type) {
        switch (type) {
            case 0:
                return readAmfDouble(data);
            case 1:
                return readAmfBoolean(data);
            case 2:
                return readAmfString(data);
            case 3:
                return readAmfObject(data);
            case 4:
            case 5:
            case 6:
            case 7:
            case 9:
            default:
                return null;
            case 8:
                return readAmfEcmaArray(data);
            case 10:
                return readAmfStrictArray(data);
            case 11:
                return readAmfDate(data);
        }
    }
}
