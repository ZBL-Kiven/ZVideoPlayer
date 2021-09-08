//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.source;

import android.os.Handler;
import androidx.annotation.Nullable;
import com.zj.playerLib.InlinePlayer;
import com.zj.playerLib.Timeline;
import com.zj.playerLib.upstream.Allocator;
import com.zj.playerLib.upstream.TransferListener;
import java.io.IOException;

public interface MediaSource {
    void addEventListener(Handler var1, MediaSourceEventListener var2);

    void removeEventListener(MediaSourceEventListener var1);

    @Nullable
    default Object getTag() {
        return null;
    }

    void prepareSource(InlinePlayer var1, boolean var2, SourceInfoRefreshListener var3, @Nullable TransferListener var4);

    void maybeThrowSourceInfoRefreshError() throws IOException;

    MediaPeriod createPeriod(MediaPeriodId var1, Allocator var2);

    void releasePeriod(MediaPeriod var1);

    void releaseSource(SourceInfoRefreshListener var1);

    final class MediaPeriodId {
        public final Object periodUid;
        public final int adGroupIndex;
        public final int adIndexInAdGroup;
        public final long windowSequenceNumber;
        public final long endPositionUs;

        public MediaPeriodId(Object periodUid) {
            this(periodUid, -1L);
        }

        public MediaPeriodId(Object periodUid, long windowSequenceNumber) {
            this(periodUid, -1, -1, windowSequenceNumber, -9223372036854775808L);
        }

        public MediaPeriodId(Object periodUid, long windowSequenceNumber, long endPositionUs) {
            this(periodUid, -1, -1, windowSequenceNumber, endPositionUs);
        }

        public MediaPeriodId(Object periodUid, int adGroupIndex, int adIndexInAdGroup, long windowSequenceNumber) {
            this(periodUid, adGroupIndex, adIndexInAdGroup, windowSequenceNumber, -9223372036854775808L);
        }

        private MediaPeriodId(Object periodUid, int adGroupIndex, int adIndexInAdGroup, long windowSequenceNumber, long endPositionUs) {
            this.periodUid = periodUid;
            this.adGroupIndex = adGroupIndex;
            this.adIndexInAdGroup = adIndexInAdGroup;
            this.windowSequenceNumber = windowSequenceNumber;
            this.endPositionUs = endPositionUs;
        }

        public MediaPeriodId copyWithPeriodUid(Object newPeriodUid) {
            return this.periodUid.equals(newPeriodUid) ? this : new MediaPeriodId(newPeriodUid, this.adGroupIndex, this.adIndexInAdGroup, this.windowSequenceNumber, this.endPositionUs);
        }

        public boolean isAd() {
            return this.adGroupIndex != -1;
        }

        public boolean equals(@Nullable Object obj) {
            if (this == obj) {
                return true;
            } else if (obj != null && this.getClass() == obj.getClass()) {
                MediaPeriodId periodId = (MediaPeriodId)obj;
                return this.periodUid.equals(periodId.periodUid) && this.adGroupIndex == periodId.adGroupIndex && this.adIndexInAdGroup == periodId.adIndexInAdGroup && this.windowSequenceNumber == periodId.windowSequenceNumber && this.endPositionUs == periodId.endPositionUs;
            } else {
                return false;
            }
        }

        public int hashCode() {
            int result = 17;
            result = 31 * result + this.periodUid.hashCode();
            result = 31 * result + this.adGroupIndex;
            result = 31 * result + this.adIndexInAdGroup;
            result = 31 * result + (int)this.windowSequenceNumber;
            result = 31 * result + (int)this.endPositionUs;
            return result;
        }
    }

    interface SourceInfoRefreshListener {
        void onSourceInfoRefreshed(MediaSource var1, Timeline var2, @Nullable Object var3);
    }
}
