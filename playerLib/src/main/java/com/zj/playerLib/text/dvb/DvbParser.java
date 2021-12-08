package com.zj.playerLib.text.dvb;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PathEffect;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Region.Op;
import android.util.SparseArray;

import com.zj.playerLib.text.Cue;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.ParsableBitArray;
import com.zj.playerLib.util.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class DvbParser {
    private static final String TAG = "DvbParser";
    private static final int SEGMENT_TYPE_PAGE_COMPOSITION = 16;
    private static final int SEGMENT_TYPE_REGION_COMPOSITION = 17;
    private static final int SEGMENT_TYPE_CLUT_DEFINITION = 18;
    private static final int SEGMENT_TYPE_OBJECT_DATA = 19;
    private static final int SEGMENT_TYPE_DISPLAY_DEFINITION = 20;
    private static final int PAGE_STATE_NORMAL = 0;
    private static final int REGION_DEPTH_4_BIT = 2;
    private static final int REGION_DEPTH_8_BIT = 3;
    private static final int OBJECT_CODING_PIXELS = 0;
    private static final int OBJECT_CODING_STRING = 1;
    private static final int DATA_TYPE_2BP_CODE_STRING = 16;
    private static final int DATA_TYPE_4BP_CODE_STRING = 17;
    private static final int DATA_TYPE_8BP_CODE_STRING = 18;
    private static final int DATA_TYPE_24_TABLE_DATA = 32;
    private static final int DATA_TYPE_28_TABLE_DATA = 33;
    private static final int DATA_TYPE_48_TABLE_DATA = 34;
    private static final int DATA_TYPE_END_LINE = 240;
    private static final byte[] defaultMap2To4 = new byte[]{0, 7, 8, 15};
    private static final byte[] defaultMap2To8 = new byte[]{0, 119, -120, -1};
    private static final byte[] defaultMap4To8 = new byte[]{0, 17, 34, 51, 68, 85, 102, 119, -120, -103, -86, -69, -52, -35, -18, -1};
    private final Paint defaultPaint = new Paint();
    private final Paint fillRegionPaint;
    private final Canvas canvas;
    private final DisplayDefinition defaultDisplayDefinition;
    private final ClutDefinition defaultClutDefinition;
    private final SubtitleService subtitleService;
    private Bitmap bitmap;

    public DvbParser(int subtitlePageId, int ancillaryPageId) {
        this.defaultPaint.setStyle(Style.FILL_AND_STROKE);
        this.defaultPaint.setXfermode(new PorterDuffXfermode(Mode.SRC));
        this.defaultPaint.setPathEffect(null);
        this.fillRegionPaint = new Paint();
        this.fillRegionPaint.setStyle(Style.FILL);
        this.fillRegionPaint.setXfermode(new PorterDuffXfermode(Mode.DST_OVER));
        this.fillRegionPaint.setPathEffect(null);
        this.canvas = new Canvas();
        this.defaultDisplayDefinition = new DisplayDefinition(719, 575, 0, 719, 0, 575);
        this.defaultClutDefinition = new ClutDefinition(0, generateDefault2BitClutEntries(), generateDefault4BitClutEntries(), generateDefault8BitClutEntries());
        this.subtitleService = new SubtitleService(subtitlePageId, ancillaryPageId);
    }

    public void reset() {
        this.subtitleService.reset();
    }

    public List<Cue> decode(byte[] data, int limit) {
        ParsableBitArray dataBitArray = new ParsableBitArray(data, limit);

        while(dataBitArray.bitsLeft() >= 48 && dataBitArray.readBits(8) == 15) {
            parseSubtitlingSegment(dataBitArray, this.subtitleService);
        }

        if (this.subtitleService.pageComposition == null) {
            return Collections.emptyList();
        } else {
            DisplayDefinition displayDefinition = this.subtitleService.displayDefinition != null ? this.subtitleService.displayDefinition : this.defaultDisplayDefinition;
            if (this.bitmap == null || displayDefinition.width + 1 != this.bitmap.getWidth() || displayDefinition.height + 1 != this.bitmap.getHeight()) {
                this.bitmap = Bitmap.createBitmap(displayDefinition.width + 1, displayDefinition.height + 1, Config.ARGB_8888);
                this.canvas.setBitmap(this.bitmap);
            }

            List<Cue> cues = new ArrayList();
            SparseArray<PageRegion> pageRegions = this.subtitleService.pageComposition.regions;

            for(int i = 0; i < pageRegions.size(); ++i) {
                PageRegion pageRegion = pageRegions.valueAt(i);
                int regionId = pageRegions.keyAt(i);
                RegionComposition regionComposition = this.subtitleService.regions.get(regionId);
                int baseHorizontalAddress = pageRegion.horizontalAddress + displayDefinition.horizontalPositionMinimum;
                int baseVerticalAddress = pageRegion.verticalAddress + displayDefinition.verticalPositionMinimum;
                int clipRight = Math.min(baseHorizontalAddress + regionComposition.width, displayDefinition.horizontalPositionMaximum);
                int clipBottom = Math.min(baseVerticalAddress + regionComposition.height, displayDefinition.verticalPositionMaximum);
                this.canvas.clipRect((float)baseHorizontalAddress, (float)baseVerticalAddress, (float)clipRight, (float)clipBottom, Op.REPLACE);
                ClutDefinition clutDefinition = this.subtitleService.cluts.get(regionComposition.clutId);
                if (clutDefinition == null) {
                    clutDefinition = this.subtitleService.ancillaryCluts.get(regionComposition.clutId);
                    if (clutDefinition == null) {
                        clutDefinition = this.defaultClutDefinition;
                    }
                }

                SparseArray<RegionObject> regionObjects = regionComposition.regionObjects;

                int color;
                for(color = 0; color < regionObjects.size(); ++color) {
                    int objectId = regionObjects.keyAt(color);
                    RegionObject regionObject = regionObjects.valueAt(color);
                    ObjectData objectData = this.subtitleService.objects.get(objectId);
                    if (objectData == null) {
                        objectData = this.subtitleService.ancillaryObjects.get(objectId);
                    }

                    if (objectData != null) {
                        Paint paint = objectData.nonModifyingColorFlag ? null : this.defaultPaint;
                        paintPixelDataSubBlocks(objectData, clutDefinition, regionComposition.depth, baseHorizontalAddress + regionObject.horizontalPosition, baseVerticalAddress + regionObject.verticalPosition, paint, this.canvas);
                    }
                }

                if (regionComposition.fillFlag) {
                    if (regionComposition.depth == 3) {
                        color = clutDefinition.clutEntries8Bit[regionComposition.pixelCode8Bit];
                    } else if (regionComposition.depth == 2) {
                        color = clutDefinition.clutEntries4Bit[regionComposition.pixelCode4Bit];
                    } else {
                        color = clutDefinition.clutEntries2Bit[regionComposition.pixelCode2Bit];
                    }

                    this.fillRegionPaint.setColor(color);
                    this.canvas.drawRect((float)baseHorizontalAddress, (float)baseVerticalAddress, (float)(baseHorizontalAddress + regionComposition.width), (float)(baseVerticalAddress + regionComposition.height), this.fillRegionPaint);
                }

                Bitmap cueBitmap = Bitmap.createBitmap(this.bitmap, baseHorizontalAddress, baseVerticalAddress, regionComposition.width, regionComposition.height);
                cues.add(new Cue(cueBitmap, (float)baseHorizontalAddress / (float)displayDefinition.width, 0, (float)baseVerticalAddress / (float)displayDefinition.height, 0, (float)regionComposition.width / (float)displayDefinition.width, (float)regionComposition.height / (float)displayDefinition.height));
                this.canvas.drawColor(0, Mode.CLEAR);
            }

            return cues;
        }
    }

    private static void parseSubtitlingSegment(ParsableBitArray data, SubtitleService service) {
        int segmentType = data.readBits(8);
        int pageId = data.readBits(16);
        int dataFieldLength = data.readBits(16);
        int dataFieldLimit = data.getBytePosition() + dataFieldLength;
        if (dataFieldLength * 8 > data.bitsLeft()) {
            Log.w("DvbParser", "Data field length exceeds limit");
            data.skipBits(data.bitsLeft());
        } else {
            PageComposition current;
            switch(segmentType) {
            case 16:
                if (pageId == service.subtitlePageId) {
                    current = service.pageComposition;
                    PageComposition pageComposition = parsePageComposition(data, dataFieldLength);
                    if (pageComposition.state != 0) {
                        service.pageComposition = pageComposition;
                        service.regions.clear();
                        service.cluts.clear();
                        service.objects.clear();
                    } else if (current != null && current.version != pageComposition.version) {
                        service.pageComposition = pageComposition;
                    }
                }
                break;
            case 17:
                current = service.pageComposition;
                if (pageId == service.subtitlePageId && current != null) {
                    RegionComposition regionComposition = parseRegionComposition(data, dataFieldLength);
                    if (current.state == 0) {
                        regionComposition.mergeFrom(service.regions.get(regionComposition.id));
                    }

                    service.regions.put(regionComposition.id, regionComposition);
                }
                break;
            case 18:
                ClutDefinition clutDefinition;
                if (pageId == service.subtitlePageId) {
                    clutDefinition = parseClutDefinition(data, dataFieldLength);
                    service.cluts.put(clutDefinition.id, clutDefinition);
                } else if (pageId == service.ancillaryPageId) {
                    clutDefinition = parseClutDefinition(data, dataFieldLength);
                    service.ancillaryCluts.put(clutDefinition.id, clutDefinition);
                }
                break;
            case 19:
                ObjectData objectData;
                if (pageId == service.subtitlePageId) {
                    objectData = parseObjectData(data);
                    service.objects.put(objectData.id, objectData);
                } else if (pageId == service.ancillaryPageId) {
                    objectData = parseObjectData(data);
                    service.ancillaryObjects.put(objectData.id, objectData);
                }
                break;
            case 20:
                if (pageId == service.subtitlePageId) {
                    service.displayDefinition = parseDisplayDefinition(data);
                }
            }

            data.skipBytes(dataFieldLimit - data.getBytePosition());
        }
    }

    private static DisplayDefinition parseDisplayDefinition(ParsableBitArray data) {
        data.skipBits(4);
        boolean displayWindowFlag = data.readBit();
        data.skipBits(3);
        int width = data.readBits(16);
        int height = data.readBits(16);
        int horizontalPositionMinimum;
        int horizontalPositionMaximum;
        int verticalPositionMinimum;
        int verticalPositionMaximum;
        if (displayWindowFlag) {
            horizontalPositionMinimum = data.readBits(16);
            horizontalPositionMaximum = data.readBits(16);
            verticalPositionMinimum = data.readBits(16);
            verticalPositionMaximum = data.readBits(16);
        } else {
            horizontalPositionMinimum = 0;
            horizontalPositionMaximum = width;
            verticalPositionMinimum = 0;
            verticalPositionMaximum = height;
        }

        return new DisplayDefinition(width, height, horizontalPositionMinimum, horizontalPositionMaximum, verticalPositionMinimum, verticalPositionMaximum);
    }

    private static PageComposition parsePageComposition(ParsableBitArray data, int length) {
        int timeoutSecs = data.readBits(8);
        int version = data.readBits(4);
        int state = data.readBits(2);
        data.skipBits(2);
        int remainingLength = length - 2;
        SparseArray regions = new SparseArray();

        while(remainingLength > 0) {
            int regionId = data.readBits(8);
            data.skipBits(8);
            int regionHorizontalAddress = data.readBits(16);
            int regionVerticalAddress = data.readBits(16);
            remainingLength -= 6;
            regions.put(regionId, new PageRegion(regionHorizontalAddress, regionVerticalAddress));
        }

        return new PageComposition(timeoutSecs, version, state, regions);
    }

    private static RegionComposition parseRegionComposition(ParsableBitArray data, int length) {
        int id = data.readBits(8);
        data.skipBits(4);
        boolean fillFlag = data.readBit();
        data.skipBits(3);
        int width = data.readBits(16);
        int height = data.readBits(16);
        int levelOfCompatibility = data.readBits(3);
        int depth = data.readBits(3);
        data.skipBits(2);
        int clutId = data.readBits(8);
        int pixelCode8Bit = data.readBits(8);
        int pixelCode4Bit = data.readBits(4);
        int pixelCode2Bit = data.readBits(2);
        data.skipBits(2);
        int remainingLength = length - 10;

        SparseArray regionObjects;
        int objectId;
        int objectType;
        int objectProvider;
        int objectHorizontalPosition;
        int objectVerticalPosition;
        int foregroundPixelCode;
        int backgroundPixelCode;
        for(regionObjects = new SparseArray(); remainingLength > 0; regionObjects.put(objectId, new RegionObject(objectType, objectProvider, objectHorizontalPosition, objectVerticalPosition, foregroundPixelCode, backgroundPixelCode))) {
            objectId = data.readBits(16);
            objectType = data.readBits(2);
            objectProvider = data.readBits(2);
            objectHorizontalPosition = data.readBits(12);
            data.skipBits(4);
            objectVerticalPosition = data.readBits(12);
            remainingLength -= 6;
            foregroundPixelCode = 0;
            backgroundPixelCode = 0;
            if (objectType == 1 || objectType == 2) {
                foregroundPixelCode = data.readBits(8);
                backgroundPixelCode = data.readBits(8);
                remainingLength -= 2;
            }
        }

        return new RegionComposition(id, fillFlag, width, height, levelOfCompatibility, depth, clutId, pixelCode8Bit, pixelCode4Bit, pixelCode2Bit, regionObjects);
    }

    private static ClutDefinition parseClutDefinition(ParsableBitArray data, int length) {
        int clutId = data.readBits(8);
        data.skipBits(8);
        int remainingLength = length - 2;
        int[] clutEntries2Bit = generateDefault2BitClutEntries();
        int[] clutEntries4Bit = generateDefault4BitClutEntries();

        int[] clutEntries8Bit;
        int entryId;
        int[] clutEntries;
        byte a;
        int r;
        int g;
        int b;
        for(clutEntries8Bit = generateDefault8BitClutEntries(); remainingLength > 0; clutEntries[entryId] = getColor(a, Util.constrainValue(r, 0, 255), Util.constrainValue(g, 0, 255), Util.constrainValue(b, 0, 255))) {
            entryId = data.readBits(8);
            int entryFlags = data.readBits(8);
            remainingLength -= 2;
            if ((entryFlags & 128) != 0) {
                clutEntries = clutEntries2Bit;
            } else if ((entryFlags & 64) != 0) {
                clutEntries = clutEntries4Bit;
            } else {
                clutEntries = clutEntries8Bit;
            }

            int y;
            int cr;
            int cb;
            int t;
            if ((entryFlags & 1) != 0) {
                y = data.readBits(8);
                cr = data.readBits(8);
                cb = data.readBits(8);
                t = data.readBits(8);
                remainingLength -= 4;
            } else {
                y = data.readBits(6) << 2;
                cr = data.readBits(4) << 4;
                cb = data.readBits(4) << 4;
                t = data.readBits(2) << 6;
                remainingLength -= 2;
            }

            if (y == 0) {
                cr = 0;
                cb = 0;
                t = 255;
            }

            a = (byte)(255 - (t & 255));
            r = (int)((double)y + 1.402D * (double)(cr - 128));
            g = (int)((double)y - 0.34414D * (double)(cb - 128) - 0.71414D * (double)(cr - 128));
            b = (int)((double)y + 1.772D * (double)(cb - 128));
        }

        return new ClutDefinition(clutId, clutEntries2Bit, clutEntries4Bit, clutEntries8Bit);
    }

    private static ObjectData parseObjectData(ParsableBitArray data) {
        int objectId = data.readBits(16);
        data.skipBits(4);
        int objectCodingMethod = data.readBits(2);
        boolean nonModifyingColorFlag = data.readBit();
        data.skipBits(1);
        byte[] topFieldData = null;
        byte[] bottomFieldData = null;
        int topFieldDataLength;
        if (objectCodingMethod == 1) {
            topFieldDataLength = data.readBits(8);
            data.skipBits(topFieldDataLength * 16);
        } else if (objectCodingMethod == 0) {
            topFieldDataLength = data.readBits(16);
            int bottomFieldDataLength = data.readBits(16);
            if (topFieldDataLength > 0) {
                topFieldData = new byte[topFieldDataLength];
                data.readBytes(topFieldData, 0, topFieldDataLength);
            }

            if (bottomFieldDataLength > 0) {
                bottomFieldData = new byte[bottomFieldDataLength];
                data.readBytes(bottomFieldData, 0, bottomFieldDataLength);
            } else {
                bottomFieldData = topFieldData;
            }
        }

        return new ObjectData(objectId, nonModifyingColorFlag, topFieldData, bottomFieldData);
    }

    private static int[] generateDefault2BitClutEntries() {
        int[] entries = new int[]{0, -1, -16777216, -8421505};
        return entries;
    }

    private static int[] generateDefault4BitClutEntries() {
        int[] entries = new int[16];
        entries[0] = 0;

        for(int i = 1; i < entries.length; ++i) {
            if (i < 8) {
                entries[i] = getColor(255, (i & 1) != 0 ? 255 : 0, (i & 2) != 0 ? 255 : 0, (i & 4) != 0 ? 255 : 0);
            } else {
                entries[i] = getColor(255, (i & 1) != 0 ? 127 : 0, (i & 2) != 0 ? 127 : 0, (i & 4) != 0 ? 127 : 0);
            }
        }

        return entries;
    }

    private static int[] generateDefault8BitClutEntries() {
        int[] entries = new int[256];
        entries[0] = 0;

        for(int i = 0; i < entries.length; ++i) {
            if (i < 8) {
                entries[i] = getColor(63, (i & 1) != 0 ? 255 : 0, (i & 2) != 0 ? 255 : 0, (i & 4) != 0 ? 255 : 0);
            } else {
                switch(i & 136) {
                case 0:
                    entries[i] = getColor(255, ((i & 1) != 0 ? 85 : 0) + ((i & 16) != 0 ? 170 : 0), ((i & 2) != 0 ? 85 : 0) + ((i & 32) != 0 ? 170 : 0), ((i & 4) != 0 ? 85 : 0) + ((i & 64) != 0 ? 170 : 0));
                    break;
                case 8:
                    entries[i] = getColor(127, ((i & 1) != 0 ? 85 : 0) + ((i & 16) != 0 ? 170 : 0), ((i & 2) != 0 ? 85 : 0) + ((i & 32) != 0 ? 170 : 0), ((i & 4) != 0 ? 85 : 0) + ((i & 64) != 0 ? 170 : 0));
                    break;
                case 128:
                    entries[i] = getColor(255, 127 + ((i & 1) != 0 ? 43 : 0) + ((i & 16) != 0 ? 85 : 0), 127 + ((i & 2) != 0 ? 43 : 0) + ((i & 32) != 0 ? 85 : 0), 127 + ((i & 4) != 0 ? 43 : 0) + ((i & 64) != 0 ? 85 : 0));
                    break;
                case 136:
                    entries[i] = getColor(255, ((i & 1) != 0 ? 43 : 0) + ((i & 16) != 0 ? 85 : 0), ((i & 2) != 0 ? 43 : 0) + ((i & 32) != 0 ? 85 : 0), ((i & 4) != 0 ? 43 : 0) + ((i & 64) != 0 ? 85 : 0));
                }
            }
        }

        return entries;
    }

    private static int getColor(int a, int r, int g, int b) {
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static void paintPixelDataSubBlocks(ObjectData objectData, ClutDefinition clutDefinition, int regionDepth, int horizontalAddress, int verticalAddress, Paint paint, Canvas canvas) {
        int[] clutEntries;
        if (regionDepth == 3) {
            clutEntries = clutDefinition.clutEntries8Bit;
        } else if (regionDepth == 2) {
            clutEntries = clutDefinition.clutEntries4Bit;
        } else {
            clutEntries = clutDefinition.clutEntries2Bit;
        }

        paintPixelDataSubBlock(objectData.topFieldData, clutEntries, regionDepth, horizontalAddress, verticalAddress, paint, canvas);
        paintPixelDataSubBlock(objectData.bottomFieldData, clutEntries, regionDepth, horizontalAddress, verticalAddress + 1, paint, canvas);
    }

    private static void paintPixelDataSubBlock(byte[] pixelData, int[] clutEntries, int regionDepth, int horizontalAddress, int verticalAddress, Paint paint, Canvas canvas) {
        ParsableBitArray data = new ParsableBitArray(pixelData);
        int column = horizontalAddress;
        int line = verticalAddress;
        byte[] clutMapTable2To4 = null;
        byte[] clutMapTable2To8 = null;
        Object clutMapTable4To8 = null;

        while(data.bitsLeft() != 0) {
            int dataType = data.readBits(8);
            switch(dataType) {
            case 16:
                byte[] clutMapTable2ToX;
                if (regionDepth == 3) {
                    clutMapTable2ToX = clutMapTable2To8 == null ? defaultMap2To8 : clutMapTable2To8;
                } else if (regionDepth == 2) {
                    clutMapTable2ToX = clutMapTable2To4 == null ? defaultMap2To4 : clutMapTable2To4;
                } else {
                    clutMapTable2ToX = null;
                }

                column = paint2BitPixelCodeString(data, clutEntries, clutMapTable2ToX, column, line, paint, canvas);
                data.byteAlign();
                break;
            case 17:
                byte[] clutMapTable4ToX;
                if (regionDepth == 3) {
                    clutMapTable4ToX = (byte[])(clutMapTable4To8 == null ? defaultMap4To8 : clutMapTable4To8);
                } else {
                    clutMapTable4ToX = null;
                }

                column = paint4BitPixelCodeString(data, clutEntries, clutMapTable4ToX, column, line, paint, canvas);
                data.byteAlign();
                break;
            case 18:
                column = paint8BitPixelCodeString(data, clutEntries, null, column, line, paint, canvas);
                break;
            case 32:
                clutMapTable2To4 = buildClutMapTable(4, 4, data);
                break;
            case 33:
                clutMapTable2To8 = buildClutMapTable(4, 8, data);
                break;
            case 34:
                clutMapTable2To8 = buildClutMapTable(16, 8, data);
                break;
            case 240:
                column = horizontalAddress;
                line += 2;
            }
        }

    }

    private static int paint2BitPixelCodeString(ParsableBitArray data, int[] clutEntries, byte[] clutMapTable, int column, int line, Paint paint, Canvas canvas) {
        boolean endOfPixelCodeString = false;

        do {
            int runLength = 0;
            int clutIndex = 0;
            int peek = data.readBits(2);
            if (peek != 0) {
                runLength = 1;
                clutIndex = peek;
            } else if (data.readBit()) {
                runLength = 3 + data.readBits(3);
                clutIndex = data.readBits(2);
            } else if (data.readBit()) {
                runLength = 1;
            } else {
                switch(data.readBits(2)) {
                case 0:
                    endOfPixelCodeString = true;
                    break;
                case 1:
                    runLength = 2;
                    break;
                case 2:
                    runLength = 12 + data.readBits(4);
                    clutIndex = data.readBits(2);
                    break;
                case 3:
                    runLength = 29 + data.readBits(8);
                    clutIndex = data.readBits(2);
                }
            }

            if (runLength != 0 && paint != null) {
                paint.setColor(clutEntries[clutMapTable != null ? clutMapTable[clutIndex] : clutIndex]);
                canvas.drawRect((float)column, (float)line, (float)(column + runLength), (float)(line + 1), paint);
            }

            column += runLength;
        } while(!endOfPixelCodeString);

        return column;
    }

    private static int paint4BitPixelCodeString(ParsableBitArray data, int[] clutEntries, byte[] clutMapTable, int column, int line, Paint paint, Canvas canvas) {
        boolean endOfPixelCodeString = false;

        do {
            int runLength = 0;
            int clutIndex = 0;
            int peek = data.readBits(4);
            if (peek != 0) {
                runLength = 1;
                clutIndex = peek;
            } else if (!data.readBit()) {
                peek = data.readBits(3);
                if (peek != 0) {
                    runLength = 2 + peek;
                    clutIndex = 0;
                } else {
                    endOfPixelCodeString = true;
                }
            } else if (!data.readBit()) {
                runLength = 4 + data.readBits(2);
                clutIndex = data.readBits(4);
            } else {
                switch(data.readBits(2)) {
                case 0:
                    runLength = 1;
                    break;
                case 1:
                    runLength = 2;
                    break;
                case 2:
                    runLength = 9 + data.readBits(4);
                    clutIndex = data.readBits(4);
                    break;
                case 3:
                    runLength = 25 + data.readBits(8);
                    clutIndex = data.readBits(4);
                }
            }

            if (runLength != 0 && paint != null) {
                paint.setColor(clutEntries[clutMapTable != null ? clutMapTable[clutIndex] : clutIndex]);
                canvas.drawRect((float)column, (float)line, (float)(column + runLength), (float)(line + 1), paint);
            }

            column += runLength;
        } while(!endOfPixelCodeString);

        return column;
    }

    private static int paint8BitPixelCodeString(ParsableBitArray data, int[] clutEntries, byte[] clutMapTable, int column, int line, Paint paint, Canvas canvas) {
        boolean endOfPixelCodeString = false;

        do {
            int runLength = 0;
            int clutIndex = 0;
            int peek = data.readBits(8);
            if (peek != 0) {
                runLength = 1;
                clutIndex = peek;
            } else if (!data.readBit()) {
                peek = data.readBits(7);
                if (peek != 0) {
                    runLength = peek;
                    clutIndex = 0;
                } else {
                    endOfPixelCodeString = true;
                }
            } else {
                runLength = data.readBits(7);
                clutIndex = data.readBits(8);
            }

            if (runLength != 0 && paint != null) {
                paint.setColor(clutEntries[clutMapTable != null ? clutMapTable[clutIndex] : clutIndex]);
                canvas.drawRect((float)column, (float)line, (float)(column + runLength), (float)(line + 1), paint);
            }

            column += runLength;
        } while(!endOfPixelCodeString);

        return column;
    }

    private static byte[] buildClutMapTable(int length, int bitsPerEntry, ParsableBitArray data) {
        byte[] clutMapTable = new byte[length];

        for(int i = 0; i < length; ++i) {
            clutMapTable[i] = (byte)data.readBits(bitsPerEntry);
        }

        return clutMapTable;
    }

    private static final class ObjectData {
        public final int id;
        public final boolean nonModifyingColorFlag;
        public final byte[] topFieldData;
        public final byte[] bottomFieldData;

        public ObjectData(int id, boolean nonModifyingColorFlag, byte[] topFieldData, byte[] bottomFieldData) {
            this.id = id;
            this.nonModifyingColorFlag = nonModifyingColorFlag;
            this.topFieldData = topFieldData;
            this.bottomFieldData = bottomFieldData;
        }
    }

    private static final class ClutDefinition {
        public final int id;
        public final int[] clutEntries2Bit;
        public final int[] clutEntries4Bit;
        public final int[] clutEntries8Bit;

        public ClutDefinition(int id, int[] clutEntries2Bit, int[] clutEntries4Bit, int[] clutEntries8bit) {
            this.id = id;
            this.clutEntries2Bit = clutEntries2Bit;
            this.clutEntries4Bit = clutEntries4Bit;
            this.clutEntries8Bit = clutEntries8bit;
        }
    }

    private static final class RegionObject {
        public final int type;
        public final int provider;
        public final int horizontalPosition;
        public final int verticalPosition;
        public final int foregroundPixelCode;
        public final int backgroundPixelCode;

        public RegionObject(int type, int provider, int horizontalPosition, int verticalPosition, int foregroundPixelCode, int backgroundPixelCode) {
            this.type = type;
            this.provider = provider;
            this.horizontalPosition = horizontalPosition;
            this.verticalPosition = verticalPosition;
            this.foregroundPixelCode = foregroundPixelCode;
            this.backgroundPixelCode = backgroundPixelCode;
        }
    }

    private static final class RegionComposition {
        public final int id;
        public final boolean fillFlag;
        public final int width;
        public final int height;
        public final int levelOfCompatibility;
        public final int depth;
        public final int clutId;
        public final int pixelCode8Bit;
        public final int pixelCode4Bit;
        public final int pixelCode2Bit;
        public final SparseArray<RegionObject> regionObjects;

        public RegionComposition(int id, boolean fillFlag, int width, int height, int levelOfCompatibility, int depth, int clutId, int pixelCode8Bit, int pixelCode4Bit, int pixelCode2Bit, SparseArray<RegionObject> regionObjects) {
            this.id = id;
            this.fillFlag = fillFlag;
            this.width = width;
            this.height = height;
            this.levelOfCompatibility = levelOfCompatibility;
            this.depth = depth;
            this.clutId = clutId;
            this.pixelCode8Bit = pixelCode8Bit;
            this.pixelCode4Bit = pixelCode4Bit;
            this.pixelCode2Bit = pixelCode2Bit;
            this.regionObjects = regionObjects;
        }

        public void mergeFrom(RegionComposition otherRegionComposition) {
            if (otherRegionComposition != null) {
                SparseArray<RegionObject> otherRegionObjects = otherRegionComposition.regionObjects;

                for(int i = 0; i < otherRegionObjects.size(); ++i) {
                    this.regionObjects.put(otherRegionObjects.keyAt(i), otherRegionObjects.valueAt(i));
                }

            }
        }
    }

    private static final class PageRegion {
        public final int horizontalAddress;
        public final int verticalAddress;

        public PageRegion(int horizontalAddress, int verticalAddress) {
            this.horizontalAddress = horizontalAddress;
            this.verticalAddress = verticalAddress;
        }
    }

    private static final class PageComposition {
        public final int timeOutSecs;
        public final int version;
        public final int state;
        public final SparseArray<PageRegion> regions;

        public PageComposition(int timeoutSecs, int version, int state, SparseArray<PageRegion> regions) {
            this.timeOutSecs = timeoutSecs;
            this.version = version;
            this.state = state;
            this.regions = regions;
        }
    }

    private static final class DisplayDefinition {
        public final int width;
        public final int height;
        public final int horizontalPositionMinimum;
        public final int horizontalPositionMaximum;
        public final int verticalPositionMinimum;
        public final int verticalPositionMaximum;

        public DisplayDefinition(int width, int height, int horizontalPositionMinimum, int horizontalPositionMaximum, int verticalPositionMinimum, int verticalPositionMaximum) {
            this.width = width;
            this.height = height;
            this.horizontalPositionMinimum = horizontalPositionMinimum;
            this.horizontalPositionMaximum = horizontalPositionMaximum;
            this.verticalPositionMinimum = verticalPositionMinimum;
            this.verticalPositionMaximum = verticalPositionMaximum;
        }
    }

    private static final class SubtitleService {
        public final int subtitlePageId;
        public final int ancillaryPageId;
        public final SparseArray<RegionComposition> regions = new SparseArray();
        public final SparseArray<ClutDefinition> cluts = new SparseArray();
        public final SparseArray<ObjectData> objects = new SparseArray();
        public final SparseArray<ClutDefinition> ancillaryCluts = new SparseArray();
        public final SparseArray<ObjectData> ancillaryObjects = new SparseArray();
        public DisplayDefinition displayDefinition;
        public PageComposition pageComposition;

        public SubtitleService(int subtitlePageId, int ancillaryPageId) {
            this.subtitlePageId = subtitlePageId;
            this.ancillaryPageId = ancillaryPageId;
        }

        public void reset() {
            this.regions.clear();
            this.cluts.clear();
            this.objects.clear();
            this.ancillaryCluts.clear();
            this.ancillaryObjects.clear();
            this.displayDefinition = null;
            this.pageComposition = null;
        }
    }
}
