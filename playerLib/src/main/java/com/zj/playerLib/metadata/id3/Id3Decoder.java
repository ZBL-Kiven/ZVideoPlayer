//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.metadata.id3;

import androidx.annotation.Nullable;
import com.zj.playerLib.metadata.Metadata;
import com.zj.playerLib.metadata.MetadataDecoder;
import com.zj.playerLib.metadata.MetadataInputBuffer;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.ParsableBitArray;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.Util;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class Id3Decoder implements MetadataDecoder {
    public static final FramePredicate NO_FRAMES_PREDICATE = (majorVersion, id0, id1, id2, id3) -> {
        return false;
    };
    private static final String TAG = "Id3Decoder";
    public static final int ID3_TAG = Util.getIntegerCodeForString("ID3");
    public static final int ID3_HEADER_LENGTH = 10;
    private static final int FRAME_FLAG_V3_IS_COMPRESSED = 128;
    private static final int FRAME_FLAG_V3_IS_ENCRYPTED = 64;
    private static final int FRAME_FLAG_V3_HAS_GROUP_IDENTIFIER = 32;
    private static final int FRAME_FLAG_V4_IS_COMPRESSED = 8;
    private static final int FRAME_FLAG_V4_IS_ENCRYPTED = 4;
    private static final int FRAME_FLAG_V4_HAS_GROUP_IDENTIFIER = 64;
    private static final int FRAME_FLAG_V4_IS_UNSYNCHRONIZED = 2;
    private static final int FRAME_FLAG_V4_HAS_DATA_LENGTH = 1;
    private static final int ID3_TEXT_ENCODING_ISO_8859_1 = 0;
    private static final int ID3_TEXT_ENCODING_UTF_16 = 1;
    private static final int ID3_TEXT_ENCODING_UTF_16BE = 2;
    private static final int ID3_TEXT_ENCODING_UTF_8 = 3;
    @Nullable
    private final Id3Decoder.FramePredicate framePredicate;

    public Id3Decoder() {
        this((FramePredicate)null);
    }

    public Id3Decoder(@Nullable Id3Decoder.FramePredicate framePredicate) {
        this.framePredicate = framePredicate;
    }

    @Nullable
    public Metadata decode(MetadataInputBuffer inputBuffer) {
        ByteBuffer buffer = inputBuffer.data;
        return this.decode(buffer.array(), buffer.limit());
    }

    @Nullable
    public Metadata decode(byte[] data, int size) {
        List<Id3Frame> id3Frames = new ArrayList();
        ParsableByteArray id3Data = new ParsableByteArray(data, size);
        Id3Header id3Header = decodeHeader(id3Data);
        if (id3Header == null) {
            return null;
        } else {
            int startPosition = id3Data.getPosition();
            int frameHeaderSize = id3Header.majorVersion == 2 ? 6 : 10;
            int framesSize = id3Header.framesSize;
            if (id3Header.isUnsynchronized) {
                framesSize = removeUnsynchronization(id3Data, id3Header.framesSize);
            }

            id3Data.setLimit(startPosition + framesSize);
            boolean unsignedIntFrameSizeHack = false;
            if (!validateFrames(id3Data, id3Header.majorVersion, frameHeaderSize, false)) {
                if (id3Header.majorVersion != 4 || !validateFrames(id3Data, 4, frameHeaderSize, true)) {
                    Log.w("Id3Decoder", "Failed to validate ID3 tag with majorVersion=" + id3Header.majorVersion);
                    return null;
                }

                unsignedIntFrameSizeHack = true;
            }

            while(id3Data.bytesLeft() >= frameHeaderSize) {
                Id3Frame frame = decodeFrame(id3Header.majorVersion, id3Data, unsignedIntFrameSizeHack, frameHeaderSize, this.framePredicate);
                if (frame != null) {
                    id3Frames.add(frame);
                }
            }

            return new Metadata(id3Frames);
        }
    }

    @Nullable
    private static Id3Decoder.Id3Header decodeHeader(ParsableByteArray data) {
        if (data.bytesLeft() < 10) {
            Log.w("Id3Decoder", "Data too short to be an ID3 tag");
            return null;
        } else {
            int id = data.readUnsignedInt24();
            if (id != ID3_TAG) {
                Log.w("Id3Decoder", "Unexpected first three bytes of ID3 tag header: " + id);
                return null;
            } else {
                int majorVersion = data.readUnsignedByte();
                data.skipBytes(1);
                int flags = data.readUnsignedByte();
                int framesSize = data.readSynchSafeInt();
                boolean hasExtendedHeader;
                if (majorVersion == 2) {
                    hasExtendedHeader = (flags & 64) != 0;
                    if (hasExtendedHeader) {
                        Log.w("Id3Decoder", "Skipped ID3 tag with majorVersion=2 and undefined compression scheme");
                        return null;
                    }
                } else {
                    int extendedHeaderSize;
                    if (majorVersion == 3) {
                        hasExtendedHeader = (flags & 64) != 0;
                        if (hasExtendedHeader) {
                            extendedHeaderSize = data.readInt();
                            data.skipBytes(extendedHeaderSize);
                            framesSize -= extendedHeaderSize + 4;
                        }
                    } else {
                        if (majorVersion != 4) {
                            Log.w("Id3Decoder", "Skipped ID3 tag with unsupported majorVersion=" + majorVersion);
                            return null;
                        }

                        hasExtendedHeader = (flags & 64) != 0;
                        if (hasExtendedHeader) {
                            extendedHeaderSize = data.readSynchSafeInt();
                            data.skipBytes(extendedHeaderSize - 4);
                            framesSize -= extendedHeaderSize;
                        }

                        boolean hasFooter = (flags & 16) != 0;
                        if (hasFooter) {
                            framesSize -= 10;
                        }
                    }
                }

                hasExtendedHeader = majorVersion < 4 && (flags & 128) != 0;
                return new Id3Header(majorVersion, hasExtendedHeader, framesSize);
            }
        }
    }

    private static boolean validateFrames(ParsableByteArray id3Data, int majorVersion, int frameHeaderSize, boolean unsignedIntFrameSizeHack) {
        int startPosition = id3Data.getPosition();

        while(true) {
            boolean var12;
            try {
                if (id3Data.bytesLeft() < frameHeaderSize) {
                    boolean var16 = true;
                    return var16;
                }

                int id;
                long frameSize;
                int flags;
                if (majorVersion >= 3) {
                    id = id3Data.readInt();
                    frameSize = id3Data.readUnsignedInt();
                    flags = id3Data.readUnsignedShort();
                } else {
                    id = id3Data.readUnsignedInt24();
                    frameSize = (long)id3Data.readUnsignedInt24();
                    flags = 0;
                }

                boolean hasGroupIdentifier;
                if (id == 0 && frameSize == 0L && flags == 0) {
                    hasGroupIdentifier = true;
                    return hasGroupIdentifier;
                }

                if (majorVersion == 4 && !unsignedIntFrameSizeHack) {
                    if ((frameSize & 8421504L) != 0L) {
                        hasGroupIdentifier = false;
                        return hasGroupIdentifier;
                    }

                    frameSize = frameSize & 255L | (frameSize >> 8 & 255L) << 7 | (frameSize >> 16 & 255L) << 14 | (frameSize >> 24 & 255L) << 21;
                }

                hasGroupIdentifier = false;
                boolean hasDataLength = false;
                if (majorVersion == 4) {
                    hasGroupIdentifier = (flags & 64) != 0;
                    hasDataLength = (flags & 1) != 0;
                } else if (majorVersion == 3) {
                    hasGroupIdentifier = (flags & 32) != 0;
                    hasDataLength = (flags & 128) != 0;
                }

                int minimumFrameSize = 0;
                if (hasGroupIdentifier) {
                    ++minimumFrameSize;
                }

                if (hasDataLength) {
                    minimumFrameSize += 4;
                }

                if (frameSize >= (long)minimumFrameSize) {
                    if ((long)id3Data.bytesLeft() < frameSize) {
                        var12 = false;
                        return var12;
                    }

                    id3Data.skipBytes((int)frameSize);
                    continue;
                }

                var12 = false;
            } finally {
                id3Data.setPosition(startPosition);
            }

            return var12;
        }
    }

    @Nullable
    private static Id3Frame decodeFrame(int majorVersion, ParsableByteArray id3Data, boolean unsignedIntFrameSizeHack, int frameHeaderSize, @Nullable Id3Decoder.FramePredicate framePredicate) {
        int frameId0 = id3Data.readUnsignedByte();
        int frameId1 = id3Data.readUnsignedByte();
        int frameId2 = id3Data.readUnsignedByte();
        int frameId3 = majorVersion >= 3 ? id3Data.readUnsignedByte() : 0;
        int frameSize;
        if (majorVersion == 4) {
            frameSize = id3Data.readUnsignedIntToInt();
            if (!unsignedIntFrameSizeHack) {
                frameSize = frameSize & 255 | (frameSize >> 8 & 255) << 7 | (frameSize >> 16 & 255) << 14 | (frameSize >> 24 & 255) << 21;
            }
        } else if (majorVersion == 3) {
            frameSize = id3Data.readUnsignedIntToInt();
        } else {
            frameSize = id3Data.readUnsignedInt24();
        }

        int flags = majorVersion >= 3 ? id3Data.readUnsignedShort() : 0;
        if (frameId0 == 0 && frameId1 == 0 && frameId2 == 0 && frameId3 == 0 && frameSize == 0 && flags == 0) {
            id3Data.setPosition(id3Data.limit());
            return null;
        } else {
            int nextFramePosition = id3Data.getPosition() + frameSize;
            if (nextFramePosition > id3Data.limit()) {
                Log.w("Id3Decoder", "Frame size exceeds remaining tag data");
                id3Data.setPosition(id3Data.limit());
                return null;
            } else if (framePredicate != null && !framePredicate.evaluate(majorVersion, frameId0, frameId1, frameId2, frameId3)) {
                id3Data.setPosition(nextFramePosition);
                return null;
            } else {
                boolean isCompressed = false;
                boolean isEncrypted = false;
                boolean isUnsynchronized = false;
                boolean hasDataLength = false;
                boolean hasGroupIdentifier = false;
                if (majorVersion == 3) {
                    isCompressed = (flags & 128) != 0;
                    isEncrypted = (flags & 64) != 0;
                    hasGroupIdentifier = (flags & 32) != 0;
                    hasDataLength = isCompressed;
                } else if (majorVersion == 4) {
                    hasGroupIdentifier = (flags & 64) != 0;
                    isCompressed = (flags & 8) != 0;
                    isEncrypted = (flags & 4) != 0;
                    isUnsynchronized = (flags & 2) != 0;
                    hasDataLength = (flags & 1) != 0;
                }

                if (!isCompressed && !isEncrypted) {
                    if (hasGroupIdentifier) {
                        --frameSize;
                        id3Data.skipBytes(1);
                    }

                    if (hasDataLength) {
                        frameSize -= 4;
                        id3Data.skipBytes(4);
                    }

                    if (isUnsynchronized) {
                        frameSize = removeUnsynchronization(id3Data, frameSize);
                    }

                    String id;
                    try {
                        Object frame;
                        if (frameId0 != 84 || frameId1 != 88 || frameId2 != 88 || majorVersion != 2 && frameId3 != 88) {
                            if (frameId0 == 84) {
                                id = getFrameId(majorVersion, frameId0, frameId1, frameId2, frameId3);
                                frame = decodeTextInformationFrame(id3Data, frameSize, id);
                            } else if (frameId0 == 87 && frameId1 == 88 && frameId2 == 88 && (majorVersion == 2 || frameId3 == 88)) {
                                frame = decodeWxxxFrame(id3Data, frameSize);
                            } else if (frameId0 == 87) {
                                id = getFrameId(majorVersion, frameId0, frameId1, frameId2, frameId3);
                                frame = decodeUrlLinkFrame(id3Data, frameSize, id);
                            } else if (frameId0 == 80 && frameId1 == 82 && frameId2 == 73 && frameId3 == 86) {
                                frame = decodePrivFrame(id3Data, frameSize);
                            } else if (frameId0 == 71 && frameId1 == 69 && frameId2 == 79 && (frameId3 == 66 || majorVersion == 2)) {
                                frame = decodeGeobFrame(id3Data, frameSize);
                            } else {
                                label607: {
                                    label498: {
                                        if (majorVersion == 2) {
                                            if (frameId0 != 80 || frameId1 != 73 || frameId2 != 67) {
                                                break label498;
                                            }
                                        } else if (frameId0 != 65 || frameId1 != 80 || frameId2 != 73 || frameId3 != 67) {
                                            break label498;
                                        }

                                        frame = decodeApicFrame(id3Data, frameSize, majorVersion);
                                        break label607;
                                    }

                                    if (frameId0 == 67 && frameId1 == 79 && frameId2 == 77 && (frameId3 == 77 || majorVersion == 2)) {
                                        frame = decodeCommentFrame(id3Data, frameSize);
                                    } else if (frameId0 == 67 && frameId1 == 72 && frameId2 == 65 && frameId3 == 80) {
                                        frame = decodeChapterFrame(id3Data, frameSize, majorVersion, unsignedIntFrameSizeHack, frameHeaderSize, framePredicate);
                                    } else if (frameId0 == 67 && frameId1 == 84 && frameId2 == 79 && frameId3 == 67) {
                                        frame = decodeChapterTOCFrame(id3Data, frameSize, majorVersion, unsignedIntFrameSizeHack, frameHeaderSize, framePredicate);
                                    } else if (frameId0 == 77 && frameId1 == 76 && frameId2 == 76 && frameId3 == 84) {
                                        frame = decodeMlltFrame(id3Data, frameSize);
                                    } else {
                                        id = getFrameId(majorVersion, frameId0, frameId1, frameId2, frameId3);
                                        frame = decodeBinaryFrame(id3Data, frameSize, id);
                                    }
                                }
                            }
                        } else {
                            frame = decodeTxxxFrame(id3Data, frameSize);
                        }

                        if (frame == null) {
                            Log.w("Id3Decoder", "Failed to decode frame: id=" + getFrameId(majorVersion, frameId0, frameId1, frameId2, frameId3) + ", frameSize=" + frameSize);
                        }
                        Object var24 = frame;
                        return (Id3Frame)var24;
                    } catch (UnsupportedEncodingException var22) {
                        Log.w("Id3Decoder", "Unsupported character encoding");
                    } finally {
                        id3Data.setPosition(nextFramePosition);
                    }
                    return null;
                } else {
                    Log.w("Id3Decoder", "Skipping unsupported compressed or encrypted frame");
                    id3Data.setPosition(nextFramePosition);
                    return null;
                }
            }
        }
    }

    @Nullable
    private static TextInformationFrame decodeTxxxFrame(ParsableByteArray id3Data, int frameSize) throws UnsupportedEncodingException {
        if (frameSize < 1) {
            return null;
        } else {
            int encoding = id3Data.readUnsignedByte();
            String charset = getCharsetName(encoding);
            byte[] data = new byte[frameSize - 1];
            id3Data.readBytes(data, 0, frameSize - 1);
            int descriptionEndIndex = indexOfEos(data, 0, encoding);
            String description = new String(data, 0, descriptionEndIndex, charset);
            int valueStartIndex = descriptionEndIndex + delimiterLength(encoding);
            int valueEndIndex = indexOfEos(data, valueStartIndex, encoding);
            String value = decodeStringIfValid(data, valueStartIndex, valueEndIndex, charset);
            return new TextInformationFrame("TXXX", description, value);
        }
    }

    @Nullable
    private static TextInformationFrame decodeTextInformationFrame(ParsableByteArray id3Data, int frameSize, String id) throws UnsupportedEncodingException {
        if (frameSize < 1) {
            return null;
        } else {
            int encoding = id3Data.readUnsignedByte();
            String charset = getCharsetName(encoding);
            byte[] data = new byte[frameSize - 1];
            id3Data.readBytes(data, 0, frameSize - 1);
            int valueEndIndex = indexOfEos(data, 0, encoding);
            String value = new String(data, 0, valueEndIndex, charset);
            return new TextInformationFrame(id, (String)null, value);
        }
    }

    @Nullable
    private static UrlLinkFrame decodeWxxxFrame(ParsableByteArray id3Data, int frameSize) throws UnsupportedEncodingException {
        if (frameSize < 1) {
            return null;
        } else {
            int encoding = id3Data.readUnsignedByte();
            String charset = getCharsetName(encoding);
            byte[] data = new byte[frameSize - 1];
            id3Data.readBytes(data, 0, frameSize - 1);
            int descriptionEndIndex = indexOfEos(data, 0, encoding);
            String description = new String(data, 0, descriptionEndIndex, charset);
            int urlStartIndex = descriptionEndIndex + delimiterLength(encoding);
            int urlEndIndex = indexOfZeroByte(data, urlStartIndex);
            String url = decodeStringIfValid(data, urlStartIndex, urlEndIndex, "ISO-8859-1");
            return new UrlLinkFrame("WXXX", description, url);
        }
    }

    private static UrlLinkFrame decodeUrlLinkFrame(ParsableByteArray id3Data, int frameSize, String id) throws UnsupportedEncodingException {
        byte[] data = new byte[frameSize];
        id3Data.readBytes(data, 0, frameSize);
        int urlEndIndex = indexOfZeroByte(data, 0);
        String url = new String(data, 0, urlEndIndex, "ISO-8859-1");
        return new UrlLinkFrame(id, (String)null, url);
    }

    private static PrivFrame decodePrivFrame(ParsableByteArray id3Data, int frameSize) throws UnsupportedEncodingException {
        byte[] data = new byte[frameSize];
        id3Data.readBytes(data, 0, frameSize);
        int ownerEndIndex = indexOfZeroByte(data, 0);
        String owner = new String(data, 0, ownerEndIndex, "ISO-8859-1");
        int privateDataStartIndex = ownerEndIndex + 1;
        byte[] privateData = copyOfRangeIfValid(data, privateDataStartIndex, data.length);
        return new PrivFrame(owner, privateData);
    }

    private static GeobFrame decodeGeobFrame(ParsableByteArray id3Data, int frameSize) throws UnsupportedEncodingException {
        int encoding = id3Data.readUnsignedByte();
        String charset = getCharsetName(encoding);
        byte[] data = new byte[frameSize - 1];
        id3Data.readBytes(data, 0, frameSize - 1);
        int mimeTypeEndIndex = indexOfZeroByte(data, 0);
        String mimeType = new String(data, 0, mimeTypeEndIndex, "ISO-8859-1");
        int filenameStartIndex = mimeTypeEndIndex + 1;
        int filenameEndIndex = indexOfEos(data, filenameStartIndex, encoding);
        String filename = decodeStringIfValid(data, filenameStartIndex, filenameEndIndex, charset);
        int descriptionStartIndex = filenameEndIndex + delimiterLength(encoding);
        int descriptionEndIndex = indexOfEos(data, descriptionStartIndex, encoding);
        String description = decodeStringIfValid(data, descriptionStartIndex, descriptionEndIndex, charset);
        int objectDataStartIndex = descriptionEndIndex + delimiterLength(encoding);
        byte[] objectData = copyOfRangeIfValid(data, objectDataStartIndex, data.length);
        return new GeobFrame(mimeType, filename, description, objectData);
    }

    private static ApicFrame decodeApicFrame(ParsableByteArray id3Data, int frameSize, int majorVersion) throws UnsupportedEncodingException {
        int encoding = id3Data.readUnsignedByte();
        String charset = getCharsetName(encoding);
        byte[] data = new byte[frameSize - 1];
        id3Data.readBytes(data, 0, frameSize - 1);
        String mimeType;
        int mimeTypeEndIndex;
        if (majorVersion == 2) {
            mimeTypeEndIndex = 2;
            mimeType = "image/" + Util.toLowerInvariant(new String(data, 0, 3, "ISO-8859-1"));
            if ("image/jpg".equals(mimeType)) {
                mimeType = "image/jpeg";
            }
        } else {
            mimeTypeEndIndex = indexOfZeroByte(data, 0);
            mimeType = Util.toLowerInvariant(new String(data, 0, mimeTypeEndIndex, "ISO-8859-1"));
            if (mimeType.indexOf(47) == -1) {
                mimeType = "image/" + mimeType;
            }
        }

        int pictureType = data[mimeTypeEndIndex + 1] & 255;
        int descriptionStartIndex = mimeTypeEndIndex + 2;
        int descriptionEndIndex = indexOfEos(data, descriptionStartIndex, encoding);
        String description = new String(data, descriptionStartIndex, descriptionEndIndex - descriptionStartIndex, charset);
        int pictureDataStartIndex = descriptionEndIndex + delimiterLength(encoding);
        byte[] pictureData = copyOfRangeIfValid(data, pictureDataStartIndex, data.length);
        return new ApicFrame(mimeType, description, pictureType, pictureData);
    }

    @Nullable
    private static CommentFrame decodeCommentFrame(ParsableByteArray id3Data, int frameSize) throws UnsupportedEncodingException {
        if (frameSize < 4) {
            return null;
        } else {
            int encoding = id3Data.readUnsignedByte();
            String charset = getCharsetName(encoding);
            byte[] data = new byte[3];
            id3Data.readBytes(data, 0, 3);
            String language = new String(data, 0, 3);
            data = new byte[frameSize - 4];
            id3Data.readBytes(data, 0, frameSize - 4);
            int descriptionEndIndex = indexOfEos(data, 0, encoding);
            String description = new String(data, 0, descriptionEndIndex, charset);
            int textStartIndex = descriptionEndIndex + delimiterLength(encoding);
            int textEndIndex = indexOfEos(data, textStartIndex, encoding);
            String text = decodeStringIfValid(data, textStartIndex, textEndIndex, charset);
            return new CommentFrame(language, description, text);
        }
    }

    private static ChapterFrame decodeChapterFrame(ParsableByteArray id3Data, int frameSize, int majorVersion, boolean unsignedIntFrameSizeHack, int frameHeaderSize, @Nullable Id3Decoder.FramePredicate framePredicate) throws UnsupportedEncodingException {
        int framePosition = id3Data.getPosition();
        int chapterIdEndIndex = indexOfZeroByte(id3Data.data, framePosition);
        String chapterId = new String(id3Data.data, framePosition, chapterIdEndIndex - framePosition, "ISO-8859-1");
        id3Data.setPosition(chapterIdEndIndex + 1);
        int startTime = id3Data.readInt();
        int endTime = id3Data.readInt();
        long startOffset = id3Data.readUnsignedInt();
        if (startOffset == 4294967295L) {
            startOffset = -1L;
        }

        long endOffset = id3Data.readUnsignedInt();
        if (endOffset == 4294967295L) {
            endOffset = -1L;
        }

        ArrayList<Id3Frame> subFrames = new ArrayList();
        int limit = framePosition + frameSize;

        while(id3Data.getPosition() < limit) {
            Id3Frame frame = decodeFrame(majorVersion, id3Data, unsignedIntFrameSizeHack, frameHeaderSize, framePredicate);
            if (frame != null) {
                subFrames.add(frame);
            }
        }

        Id3Frame[] subFrameArray = new Id3Frame[subFrames.size()];
        subFrames.toArray(subFrameArray);
        return new ChapterFrame(chapterId, startTime, endTime, startOffset, endOffset, subFrameArray);
    }

    private static ChapterTocFrame decodeChapterTOCFrame(ParsableByteArray id3Data, int frameSize, int majorVersion, boolean unsignedIntFrameSizeHack, int frameHeaderSize, @Nullable Id3Decoder.FramePredicate framePredicate) throws UnsupportedEncodingException {
        int framePosition = id3Data.getPosition();
        int elementIdEndIndex = indexOfZeroByte(id3Data.data, framePosition);
        String elementId = new String(id3Data.data, framePosition, elementIdEndIndex - framePosition, "ISO-8859-1");
        id3Data.setPosition(elementIdEndIndex + 1);
        int ctocFlags = id3Data.readUnsignedByte();
        boolean isRoot = (ctocFlags & 2) != 0;
        boolean isOrdered = (ctocFlags & 1) != 0;
        int childCount = id3Data.readUnsignedByte();
        String[] children = new String[childCount];

        int limit;
        for(int i = 0; i < childCount; ++i) {
            limit = id3Data.getPosition();
            int endIndex = indexOfZeroByte(id3Data.data, limit);
            children[i] = new String(id3Data.data, limit, endIndex - limit, "ISO-8859-1");
            id3Data.setPosition(endIndex + 1);
        }

        ArrayList<Id3Frame> subFrames = new ArrayList<>();
        limit = framePosition + frameSize;

        while(id3Data.getPosition() < limit) {
            Id3Frame frame = decodeFrame(majorVersion, id3Data, unsignedIntFrameSizeHack, frameHeaderSize, framePredicate);
            if (frame != null) {
                subFrames.add(frame);
            }
        }

        Id3Frame[] subFrameArray = new Id3Frame[subFrames.size()];
        subFrames.toArray(subFrameArray);
        return new ChapterTocFrame(elementId, isRoot, isOrdered, children, subFrameArray);
    }

    private static MlltFrame decodeMlltFrame(ParsableByteArray id3Data, int frameSize) {
        int mpegFramesBetweenReference = id3Data.readUnsignedShort();
        int bytesBetweenReference = id3Data.readUnsignedInt24();
        int millisecondsBetweenReference = id3Data.readUnsignedInt24();
        int bitsForBytesDeviation = id3Data.readUnsignedByte();
        int bitsForMillisecondsDeviation = id3Data.readUnsignedByte();
        ParsableBitArray references = new ParsableBitArray();
        references.reset(id3Data);
        int referencesBits = 8 * (frameSize - 10);
        int bitsPerReference = bitsForBytesDeviation + bitsForMillisecondsDeviation;
        int referencesCount = referencesBits / bitsPerReference;
        int[] bytesDeviations = new int[referencesCount];
        int[] millisecondsDeviations = new int[referencesCount];

        for(int i = 0; i < referencesCount; ++i) {
            int bytesDeviation = references.readBits(bitsForBytesDeviation);
            int millisecondsDeviation = references.readBits(bitsForMillisecondsDeviation);
            bytesDeviations[i] = bytesDeviation;
            millisecondsDeviations[i] = millisecondsDeviation;
        }

        return new MlltFrame(mpegFramesBetweenReference, bytesBetweenReference, millisecondsBetweenReference, bytesDeviations, millisecondsDeviations);
    }

    private static BinaryFrame decodeBinaryFrame(ParsableByteArray id3Data, int frameSize, String id) {
        byte[] frame = new byte[frameSize];
        id3Data.readBytes(frame, 0, frameSize);
        return new BinaryFrame(id, frame);
    }

    private static int removeUnsynchronization(ParsableByteArray data, int length) {
        byte[] bytes = data.data;

        for(int i = data.getPosition(); i + 1 < length; ++i) {
            if ((bytes[i] & 255) == 255 && bytes[i + 1] == 0) {
                System.arraycopy(bytes, i + 2, bytes, i + 1, length - i - 2);
                --length;
            }
        }

        return length;
    }

    private static String getCharsetName(int encodingByte) {
        switch(encodingByte) {
        case 0:
        default:
            return "ISO-8859-1";
        case 1:
            return "UTF-16";
        case 2:
            return "UTF-16BE";
        case 3:
            return "UTF-8";
        }
    }

    private static String getFrameId(int majorVersion, int frameId0, int frameId1, int frameId2, int frameId3) {
        return majorVersion == 2 ? String.format(Locale.US, "%c%c%c", frameId0, frameId1, frameId2) : String.format(Locale.US, "%c%c%c%c", frameId0, frameId1, frameId2, frameId3);
    }

    private static int indexOfEos(byte[] data, int fromIndex, int encoding) {
        int terminationPos = indexOfZeroByte(data, fromIndex);
        if (encoding != 0 && encoding != 3) {
            while(terminationPos < data.length - 1) {
                if (terminationPos % 2 == 0 && data[terminationPos + 1] == 0) {
                    return terminationPos;
                }

                terminationPos = indexOfZeroByte(data, terminationPos + 1);
            }

            return data.length;
        } else {
            return terminationPos;
        }
    }

    private static int indexOfZeroByte(byte[] data, int fromIndex) {
        for(int i = fromIndex; i < data.length; ++i) {
            if (data[i] == 0) {
                return i;
            }
        }

        return data.length;
    }

    private static int delimiterLength(int encodingByte) {
        return encodingByte != 0 && encodingByte != 3 ? 2 : 1;
    }

    private static byte[] copyOfRangeIfValid(byte[] data, int from, int to) {
        return to <= from ? Util.EMPTY_BYTE_ARRAY : Arrays.copyOfRange(data, from, to);
    }

    private static String decodeStringIfValid(byte[] data, int from, int to, String charsetName) throws UnsupportedEncodingException {
        return to > from && to <= data.length ? new String(data, from, to - from, charsetName) : "";
    }

    private static final class Id3Header {
        private final int majorVersion;
        private final boolean isUnsynchronized;
        private final int framesSize;

        public Id3Header(int majorVersion, boolean isUnsynchronized, int framesSize) {
            this.majorVersion = majorVersion;
            this.isUnsynchronized = isUnsynchronized;
            this.framesSize = framesSize;
        }
    }

    public interface FramePredicate {
        boolean evaluate(int var1, int var2, int var3, int var4, int var5);
    }
}
