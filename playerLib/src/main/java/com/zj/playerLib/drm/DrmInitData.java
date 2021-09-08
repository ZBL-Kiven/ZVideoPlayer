//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.drm;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;
import com.zj.playerLib.C;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.Util;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class DrmInitData implements Comparator<DrmInitData.SchemeData>, Parcelable {
    private final SchemeData[] schemeDatas;
    private int hashCode;
    @Nullable
    public final String schemeType;
    public final int schemeDataCount;
    public static final Creator<DrmInitData> CREATOR = new Creator<DrmInitData>() {
        public DrmInitData createFromParcel(Parcel in) {
            return new DrmInitData(in);
        }

        public DrmInitData[] newArray(int size) {
            return new DrmInitData[size];
        }
    };

    @Nullable
    public static DrmInitData createSessionCreationData(@Nullable DrmInitData manifestData, @Nullable DrmInitData mediaData) {
        ArrayList<SchemeData> result = new ArrayList();
        String schemeType = null;
        int var6;
        if (manifestData != null) {
            schemeType = manifestData.schemeType;
            SchemeData[] var4 = manifestData.schemeDatas;
            int var5 = var4.length;

            for(var6 = 0; var6 < var5; ++var6) {
                SchemeData data = var4[var6];
                if (data.hasData()) {
                    result.add(data);
                }
            }
        }

        if (mediaData != null) {
            if (schemeType == null) {
                schemeType = mediaData.schemeType;
            }

            int manifestDatasCount = result.size();
            SchemeData[] var10 = mediaData.schemeDatas;
            var6 = var10.length;

            for(int var11 = 0; var11 < var6; ++var11) {
                SchemeData data = var10[var11];
                if (data.hasData() && !containsSchemeDataWithUuid(result, manifestDatasCount, data.uuid)) {
                    result.add(data);
                }
            }
        }

        return result.isEmpty() ? null : new DrmInitData(schemeType, result);
    }

    public DrmInitData(List<SchemeData> schemeDatas) {
        this((String)null, false, (SchemeData[])schemeDatas.toArray(new SchemeData[schemeDatas.size()]));
    }

    public DrmInitData(String schemeType, List<SchemeData> schemeDatas) {
        this(schemeType, false, (SchemeData[])schemeDatas.toArray(new SchemeData[schemeDatas.size()]));
    }

    public DrmInitData(SchemeData... schemeDatas) {
        this((String)null, (SchemeData[])schemeDatas);
    }

    public DrmInitData(@Nullable String schemeType, SchemeData... schemeDatas) {
        this(schemeType, true, schemeDatas);
    }

    private DrmInitData(@Nullable String schemeType, boolean cloneSchemeDatas, SchemeData... schemeDatas) {
        this.schemeType = schemeType;
        if (cloneSchemeDatas) {
            schemeDatas = (SchemeData[])schemeDatas.clone();
        }

        Arrays.sort(schemeDatas, this);
        this.schemeDatas = schemeDatas;
        this.schemeDataCount = schemeDatas.length;
    }

    DrmInitData(Parcel in) {
        this.schemeType = in.readString();
        this.schemeDatas = (SchemeData[])in.createTypedArray(SchemeData.CREATOR);
        this.schemeDataCount = this.schemeDatas.length;
    }

    /** @deprecated */
    @Deprecated
    public SchemeData get(UUID uuid) {
        SchemeData[] var2 = this.schemeDatas;
        int var3 = var2.length;

        for(int var4 = 0; var4 < var3; ++var4) {
            SchemeData schemeData = var2[var4];
            if (schemeData.matches(uuid)) {
                return schemeData;
            }
        }

        return null;
    }

    public SchemeData get(int index) {
        return this.schemeDatas[index];
    }

    public DrmInitData copyWithSchemeType(@Nullable String schemeType) {
        return Util.areEqual(this.schemeType, schemeType) ? this : new DrmInitData(schemeType, false, this.schemeDatas);
    }

    public int hashCode() {
        if (this.hashCode == 0) {
            int result = this.schemeType == null ? 0 : this.schemeType.hashCode();
            result = 31 * result + Arrays.hashCode(this.schemeDatas);
            this.hashCode = result;
        }

        return this.hashCode;
    }

    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            DrmInitData other = (DrmInitData)obj;
            return Util.areEqual(this.schemeType, other.schemeType) && Arrays.equals(this.schemeDatas, other.schemeDatas);
        } else {
            return false;
        }
    }

    public int compare(SchemeData first, SchemeData second) {
        return C.UUID_NIL.equals(first.uuid) ? (C.UUID_NIL.equals(second.uuid) ? 0 : 1) : first.uuid.compareTo(second.uuid);
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.schemeType);
        dest.writeTypedArray(this.schemeDatas, 0);
    }

    private static boolean containsSchemeDataWithUuid(ArrayList<SchemeData> datas, int limit, UUID uuid) {
        for(int i = 0; i < limit; ++i) {
            if (((SchemeData)datas.get(i)).uuid.equals(uuid)) {
                return true;
            }
        }

        return false;
    }

    public static final class SchemeData implements Parcelable {
        private int hashCode;
        private final UUID uuid;
        @Nullable
        public final String licenseServerUrl;
        public final String mimeType;
        public final byte[] data;
        public final boolean requiresSecureDecryption;
        public static final Creator<SchemeData> CREATOR = new Creator<SchemeData>() {
            public SchemeData createFromParcel(Parcel in) {
                return new SchemeData(in);
            }

            public SchemeData[] newArray(int size) {
                return new SchemeData[size];
            }
        };

        public SchemeData(UUID uuid, String mimeType, byte[] data) {
            this(uuid, mimeType, data, false);
        }

        public SchemeData(UUID uuid, String mimeType, byte[] data, boolean requiresSecureDecryption) {
            this(uuid, (String)null, mimeType, data, requiresSecureDecryption);
        }

        public SchemeData(UUID uuid, @Nullable String licenseServerUrl, String mimeType, byte[] data, boolean requiresSecureDecryption) {
            this.uuid = (UUID)Assertions.checkNotNull(uuid);
            this.licenseServerUrl = licenseServerUrl;
            this.mimeType = (String)Assertions.checkNotNull(mimeType);
            this.data = data;
            this.requiresSecureDecryption = requiresSecureDecryption;
        }

        SchemeData(Parcel in) {
            this.uuid = new UUID(in.readLong(), in.readLong());
            this.licenseServerUrl = in.readString();
            this.mimeType = in.readString();
            this.data = in.createByteArray();
            this.requiresSecureDecryption = in.readByte() != 0;
        }

        public boolean matches(UUID schemeUuid) {
            return C.UUID_NIL.equals(this.uuid) || schemeUuid.equals(this.uuid);
        }

        public boolean canReplace(SchemeData other) {
            return this.hasData() && !other.hasData() && this.matches(other.uuid);
        }

        public boolean hasData() {
            return this.data != null;
        }

        public SchemeData copyWithData(@Nullable byte[] data) {
            return new SchemeData(this.uuid, this.licenseServerUrl, this.mimeType, data, this.requiresSecureDecryption);
        }

        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof SchemeData)) {
                return false;
            } else if (obj == this) {
                return true;
            } else {
                SchemeData other = (SchemeData)obj;
                return Util.areEqual(this.licenseServerUrl, other.licenseServerUrl) && Util.areEqual(this.mimeType, other.mimeType) && Util.areEqual(this.uuid, other.uuid) && Arrays.equals(this.data, other.data);
            }
        }

        public int hashCode() {
            if (this.hashCode == 0) {
                int result = this.uuid.hashCode();
                result = 31 * result + (this.licenseServerUrl == null ? 0 : this.licenseServerUrl.hashCode());
                result = 31 * result + this.mimeType.hashCode();
                result = 31 * result + Arrays.hashCode(this.data);
                this.hashCode = result;
            }

            return this.hashCode;
        }

        public int describeContents() {
            return 0;
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(this.uuid.getMostSignificantBits());
            dest.writeLong(this.uuid.getLeastSignificantBits());
            dest.writeString(this.licenseServerUrl);
            dest.writeString(this.mimeType);
            dest.writeByteArray(this.data);
            dest.writeByte((byte)(this.requiresSecureDecryption ? 1 : 0));
        }
    }
}
