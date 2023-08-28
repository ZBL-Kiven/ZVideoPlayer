package com.zj.playerLib.extractor.mp4;

import androidx.annotation.Nullable;
import com.zj.playerLib.metadata.Metadata.Entry;
import com.zj.playerLib.metadata.id3.ApicFrame;
import com.zj.playerLib.metadata.id3.CommentFrame;
import com.zj.playerLib.metadata.id3.Id3Frame;
import com.zj.playerLib.metadata.id3.InternalFrame;
import com.zj.playerLib.metadata.id3.TextInformationFrame;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.Util;

final class MetadataUtil {
    private static final String TAG = "MetadataUtil";
    private static final int SHORT_TYPE_NAME_1 = Util.getIntegerCodeForString("nam");
    private static final int SHORT_TYPE_NAME_2 = Util.getIntegerCodeForString("trk");
    private static final int SHORT_TYPE_COMMENT = Util.getIntegerCodeForString("cmt");
    private static final int SHORT_TYPE_YEAR = Util.getIntegerCodeForString("day");
    private static final int SHORT_TYPE_ARTIST = Util.getIntegerCodeForString("ART");
    private static final int SHORT_TYPE_ENCODER = Util.getIntegerCodeForString("too");
    private static final int SHORT_TYPE_ALBUM = Util.getIntegerCodeForString("alb");
    private static final int SHORT_TYPE_COMPOSER_1 = Util.getIntegerCodeForString("com");
    private static final int SHORT_TYPE_COMPOSER_2 = Util.getIntegerCodeForString("wrt");
    private static final int SHORT_TYPE_LYRICS = Util.getIntegerCodeForString("lyr");
    private static final int SHORT_TYPE_GENRE = Util.getIntegerCodeForString("gen");
    private static final int TYPE_COVER_ART = Util.getIntegerCodeForString("covr");
    private static final int TYPE_GENRE = Util.getIntegerCodeForString("gnre");
    private static final int TYPE_GROUPING = Util.getIntegerCodeForString("grp");
    private static final int TYPE_DISK_NUMBER = Util.getIntegerCodeForString("disk");
    private static final int TYPE_TRACK_NUMBER = Util.getIntegerCodeForString("trkn");
    private static final int TYPE_TEMPO = Util.getIntegerCodeForString("tmpo");
    private static final int TYPE_COMPILATION = Util.getIntegerCodeForString("cpil");
    private static final int TYPE_ALBUM_ARTIST = Util.getIntegerCodeForString("aART");
    private static final int TYPE_SORT_TRACK_NAME = Util.getIntegerCodeForString("sonm");
    private static final int TYPE_SORT_ALBUM = Util.getIntegerCodeForString("soal");
    private static final int TYPE_SORT_ARTIST = Util.getIntegerCodeForString("soar");
    private static final int TYPE_SORT_ALBUM_ARTIST = Util.getIntegerCodeForString("soaa");
    private static final int TYPE_SORT_COMPOSER = Util.getIntegerCodeForString("soco");
    private static final int TYPE_RATING = Util.getIntegerCodeForString("rtng");
    private static final int TYPE_GAPLESS_ALBUM = Util.getIntegerCodeForString("pgap");
    private static final int TYPE_TV_SORT_SHOW = Util.getIntegerCodeForString("sosn");
    private static final int TYPE_TV_SHOW = Util.getIntegerCodeForString("tvsh");
    private static final int TYPE_INTERNAL = Util.getIntegerCodeForString("----");
    private static final int PICTURE_TYPE_FRONT_COVER = 3;
    private static final String[] STANDARD_GENRES = new String[]{"Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop", "Jazz", "Metal", "New Age", "Oldies", "Other", "Pop", "R&B", "Rap", "Reggae", "Rock", "Techno", "Industrial", "Alternative", "Ska", "Death Metal", "Pranks", "Soundtrack", "Euro-Techno", "Ambient", "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion", "Trance", "Classical", "Instrumental", "Acid", "House", "Game", "Sound Clip", "Gospel", "Noise", "AlternRock", "Bass", "Soul", "Punk", "Space", "Meditative", "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic", "Darkwave", "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance", "Dream", "Southern Rock", "Comedy", "Cult", "Gangsta", "Top 40", "Christian Rap", "Pop/Funk", "Jungle", "Native American", "Cabaret", "New Wave", "Psychadelic", "Rave", "Showtunes", "Trailer", "Lo-Fi", "Tribal", "Acid Punk", "Acid Jazz", "Polka", "Retro", "Musical", "Rock & Roll", "Hard Rock", "Folk", "Folk-Rock", "National Folk", "Swing", "Fast Fusion", "Bebob", "Latin", "Revival", "Celtic", "Bluegrass", "Avantgarde", "Gothic Rock", "Progressive Rock", "Psychedelic Rock", "Symphonic Rock", "Slow Rock", "Big Band", "Chorus", "Easy Listening", "Acoustic", "Humour", "Speech", "Chanson", "Opera", "Chamber Music", "Sonata", "Symphony", "Booty Bass", "Primus", "Porn Groove", "Satire", "Slow Jam", "Club", "Tango", "Samba", "Folklore", "Ballad", "Power Ballad", "Rhythmic Soul", "Freestyle", "Duet", "Punk Rock", "Drum Solo", "A capella", "Euro-House", "Dance Hall", "Goa", "Drum & Bass", "Club-House", "Hardcore", "Terror", "Indie", "BritPop", "Negerpunk", "Polsk Punk", "Beat", "Christian Gangsta Rap", "Heavy Metal", "Black Metal", "Crossover", "Contemporary Christian", "Christian Rock", "Merengue", "Salsa", "Thrash Metal", "Anime", "Jpop", "Synthpop"};
    private static final String LANGUAGE_UNDEFINED = "und";

    private MetadataUtil() {
    }

    @Nullable
    public static Entry parseIlstElement(ParsableByteArray ilst) {
        int position = ilst.getPosition();
        int endPosition = position + ilst.readInt();
        int type = ilst.readInt();
        int typeTopByte = type >> 24 & 255;

        try {
            Id3Frame var10;
            if (typeTopByte != 169 && typeTopByte != 65533) {
                TextInformationFrame var11;
                if (type == TYPE_GENRE) {
                    var11 = parseStandardGenreAttribute(ilst);
                    return var11;
                }

                if (type == TYPE_DISK_NUMBER) {
                    var11 = parseIndexAndCountAttribute(type, "TPOS", ilst);
                    return var11;
                }

                if (type == TYPE_TRACK_NUMBER) {
                    var11 = parseIndexAndCountAttribute(type, "TRCK", ilst);
                    return var11;
                }

                if (type == TYPE_TEMPO) {
                    var10 = parseUint8Attribute(type, "TBPM", ilst, true, false);
                    return var10;
                }

                if (type == TYPE_COMPILATION) {
                    var10 = parseUint8Attribute(type, "TCMP", ilst, true, true);
                    return var10;
                }

                if (type == TYPE_COVER_ART) {
                    ApicFrame var13 = parseCoverArt(ilst);
                    return var13;
                }

                if (type == TYPE_ALBUM_ARTIST) {
                    var11 = parseTextAttribute(type, "TPE2", ilst);
                    return var11;
                }

                if (type == TYPE_SORT_TRACK_NAME) {
                    var11 = parseTextAttribute(type, "TSOT", ilst);
                    return var11;
                }

                if (type == TYPE_SORT_ALBUM) {
                    var11 = parseTextAttribute(type, "TSO2", ilst);
                    return var11;
                }

                if (type == TYPE_SORT_ARTIST) {
                    var11 = parseTextAttribute(type, "TSOA", ilst);
                    return var11;
                }

                if (type == TYPE_SORT_ALBUM_ARTIST) {
                    var11 = parseTextAttribute(type, "TSOP", ilst);
                    return var11;
                }

                if (type == TYPE_SORT_COMPOSER) {
                    var11 = parseTextAttribute(type, "TSOC", ilst);
                    return var11;
                }

                if (type == TYPE_RATING) {
                    var10 = parseUint8Attribute(type, "ITUNESADVISORY", ilst, false, false);
                    return var10;
                }

                if (type == TYPE_GAPLESS_ALBUM) {
                    var10 = parseUint8Attribute(type, "ITUNESGAPLESS", ilst, false, true);
                    return var10;
                }

                if (type == TYPE_TV_SORT_SHOW) {
                    var11 = parseTextAttribute(type, "TVSHOWSORT", ilst);
                    return var11;
                }

                if (type == TYPE_TV_SHOW) {
                    var11 = parseTextAttribute(type, "TVSHOW", ilst);
                    return var11;
                }

                if (type == TYPE_INTERNAL) {
                    var10 = parseInternalAttribute(ilst, endPosition);
                    return var10;
                }
            } else {
                int shortType = type & 16777215;
                if (shortType == SHORT_TYPE_COMMENT) {
                    CommentFrame var12 = parseCommentAttribute(type, ilst);
                    return var12;
                }

                TextInformationFrame var6;
                if (shortType == SHORT_TYPE_NAME_1 || shortType == SHORT_TYPE_NAME_2) {
                    var6 = parseTextAttribute(type, "TIT2", ilst);
                    return var6;
                }

                if (shortType == SHORT_TYPE_COMPOSER_1 || shortType == SHORT_TYPE_COMPOSER_2) {
                    var6 = parseTextAttribute(type, "TCOM", ilst);
                    return var6;
                }

                if (shortType == SHORT_TYPE_YEAR) {
                    var6 = parseTextAttribute(type, "TDRC", ilst);
                    return var6;
                }

                if (shortType == SHORT_TYPE_ARTIST) {
                    var6 = parseTextAttribute(type, "TPE1", ilst);
                    return var6;
                }

                if (shortType == SHORT_TYPE_ENCODER) {
                    var6 = parseTextAttribute(type, "TSSE", ilst);
                    return var6;
                }

                if (shortType == SHORT_TYPE_ALBUM) {
                    var6 = parseTextAttribute(type, "TALB", ilst);
                    return var6;
                }

                if (shortType == SHORT_TYPE_LYRICS) {
                    var6 = parseTextAttribute(type, "USLT", ilst);
                    return var6;
                }

                if (shortType == SHORT_TYPE_GENRE) {
                    var6 = parseTextAttribute(type, "TCON", ilst);
                    return var6;
                }

                if (shortType == TYPE_GROUPING) {
                    var6 = parseTextAttribute(type, "TIT1", ilst);
                    return var6;
                }
            }

            Log.d("MetadataUtil", "Skipped unknown metadata entry: " + Atom.getAtomTypeString(type));
            var10 = null;
            return var10;
        } finally {
            ilst.setPosition(endPosition);
        }
    }

    @Nullable
    private static TextInformationFrame parseTextAttribute(int type, String id, ParsableByteArray data) {
        int atomSize = data.readInt();
        int atomType = data.readInt();
        if (atomType == Atom.TYPE_data) {
            data.skipBytes(8);
            String value = data.readNullTerminatedString(atomSize - 16);
            return new TextInformationFrame(id, null, value);
        } else {
            Log.w("MetadataUtil", "Failed to parse text attribute: " + Atom.getAtomTypeString(type));
            return null;
        }
    }

    @Nullable
    private static CommentFrame parseCommentAttribute(int type, ParsableByteArray data) {
        int atomSize = data.readInt();
        int atomType = data.readInt();
        if (atomType == Atom.TYPE_data) {
            data.skipBytes(8);
            String value = data.readNullTerminatedString(atomSize - 16);
            return new CommentFrame("und", value, value);
        } else {
            Log.w("MetadataUtil", "Failed to parse comment attribute: " + Atom.getAtomTypeString(type));
            return null;
        }
    }

    @Nullable
    private static Id3Frame parseUint8Attribute(int type, String id, ParsableByteArray data, boolean isTextInformationFrame, boolean isBoolean) {
        int value = parseUint8AttributeValue(data);
        if (isBoolean) {
            value = Math.min(1, value);
        }

        if (value >= 0) {
            return isTextInformationFrame ? new TextInformationFrame(id, null, Integer.toString(value)) : new CommentFrame("und", id, Integer.toString(value));
        } else {
            Log.w("MetadataUtil", "Failed to parse uint8 attribute: " + Atom.getAtomTypeString(type));
            return null;
        }
    }

    @Nullable
    private static TextInformationFrame parseIndexAndCountAttribute(int type, String attributeName, ParsableByteArray data) {
        int atomSize = data.readInt();
        int atomType = data.readInt();
        if (atomType == Atom.TYPE_data && atomSize >= 22) {
            data.skipBytes(10);
            int index = data.readUnsignedShort();
            if (index > 0) {
                String value = "" + index;
                int count = data.readUnsignedShort();
                if (count > 0) {
                    value = value + "/" + count;
                }

                return new TextInformationFrame(attributeName, null, value);
            }
        }

        Log.w("MetadataUtil", "Failed to parse index/count attribute: " + Atom.getAtomTypeString(type));
        return null;
    }

    @Nullable
    private static TextInformationFrame parseStandardGenreAttribute(ParsableByteArray data) {
        int genreCode = parseUint8AttributeValue(data);
        String genreString = 0 < genreCode && genreCode <= STANDARD_GENRES.length ? STANDARD_GENRES[genreCode - 1] : null;
        if (genreString != null) {
            return new TextInformationFrame("TCON", null, genreString);
        } else {
            Log.w("MetadataUtil", "Failed to parse standard genre code");
            return null;
        }
    }

    @Nullable
    private static ApicFrame parseCoverArt(ParsableByteArray data) {
        int atomSize = data.readInt();
        int atomType = data.readInt();
        if (atomType == Atom.TYPE_data) {
            int fullVersionInt = data.readInt();
            int flags = Atom.parseFullAtomFlags(fullVersionInt);
            String mimeType = flags == 13 ? "image/jpeg" : (flags == 14 ? "image/png" : null);
            if (mimeType == null) {
                Log.w("MetadataUtil", "Unrecognized cover art flags: " + flags);
                return null;
            } else {
                data.skipBytes(4);
                byte[] pictureData = new byte[atomSize - 16];
                data.readBytes(pictureData, 0, pictureData.length);
                return new ApicFrame(mimeType, null, 3, pictureData);
            }
        } else {
            Log.w("MetadataUtil", "Failed to parse cover art attribute");
            return null;
        }
    }

    @Nullable
    private static Id3Frame parseInternalAttribute(ParsableByteArray data, int endPosition) {
        String domain = null;
        String name = null;
        int dataAtomPosition = -1;
        int dataAtomSize = -1;

        while(data.getPosition() < endPosition) {
            int atomPosition = data.getPosition();
            int atomSize = data.readInt();
            int atomType = data.readInt();
            data.skipBytes(4);
            if (atomType == Atom.TYPE_mean) {
                domain = data.readNullTerminatedString(atomSize - 12);
            } else if (atomType == Atom.TYPE_name) {
                name = data.readNullTerminatedString(atomSize - 12);
            } else {
                if (atomType == Atom.TYPE_data) {
                    dataAtomPosition = atomPosition;
                    dataAtomSize = atomSize;
                }

                data.skipBytes(atomSize - 12);
            }
        }

        if (domain != null && name != null && dataAtomPosition != -1) {
            data.setPosition(dataAtomPosition);
            data.skipBytes(16);
            String value = data.readNullTerminatedString(dataAtomSize - 16);
            return new InternalFrame(domain, name, value);
        } else {
            return null;
        }
    }

    private static int parseUint8AttributeValue(ParsableByteArray data) {
        data.skipBytes(4);
        int atomType = data.readInt();
        if (atomType == Atom.TYPE_data) {
            data.skipBytes(8);
            return data.readUnsignedByte();
        } else {
            Log.w("MetadataUtil", "Failed to parse uint8 attribute value");
            return -1;
        }
    }
}
