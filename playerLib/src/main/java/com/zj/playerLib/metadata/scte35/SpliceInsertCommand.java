package com.zj.playerLib.metadata.scte35;

import android.os.Parcel;

import com.zj.playerLib.util.ParsableByteArray;
import com.zj.playerLib.util.TimestampAdjuster;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SpliceInsertCommand extends SpliceCommand {
    public final long spliceEventId;
    public final boolean spliceEventCancelIndicator;
    public final boolean outOfNetworkIndicator;
    public final boolean programSpliceFlag;
    public final boolean spliceImmediateFlag;
    public final long programSplicePts;
    public final long programSplicePlaybackPositionUs;
    public final List<ComponentSplice> componentSpliceList;
    public final boolean autoReturn;
    public final long breakDurationUs;
    public final int uniqueProgramId;
    public final int availNum;
    public final int availsExpected;
    public static final Creator<SpliceInsertCommand> CREATOR = new Creator<SpliceInsertCommand>() {
        public SpliceInsertCommand createFromParcel(Parcel in) {
            return new SpliceInsertCommand(in);
        }

        public SpliceInsertCommand[] newArray(int size) {
            return new SpliceInsertCommand[size];
        }
    };

    private SpliceInsertCommand(long spliceEventId, boolean spliceEventCancelIndicator, boolean outOfNetworkIndicator, boolean programSpliceFlag, boolean spliceImmediateFlag, long programSplicePts, long programSplicePlaybackPositionUs, List<ComponentSplice> componentSpliceList, boolean autoReturn, long breakDurationUs, int uniqueProgramId, int availNum, int availsExpected) {
        this.spliceEventId = spliceEventId;
        this.spliceEventCancelIndicator = spliceEventCancelIndicator;
        this.outOfNetworkIndicator = outOfNetworkIndicator;
        this.programSpliceFlag = programSpliceFlag;
        this.spliceImmediateFlag = spliceImmediateFlag;
        this.programSplicePts = programSplicePts;
        this.programSplicePlaybackPositionUs = programSplicePlaybackPositionUs;
        this.componentSpliceList = Collections.unmodifiableList(componentSpliceList);
        this.autoReturn = autoReturn;
        this.breakDurationUs = breakDurationUs;
        this.uniqueProgramId = uniqueProgramId;
        this.availNum = availNum;
        this.availsExpected = availsExpected;
    }

    private SpliceInsertCommand(Parcel in) {
        this.spliceEventId = in.readLong();
        this.spliceEventCancelIndicator = in.readByte() == 1;
        this.outOfNetworkIndicator = in.readByte() == 1;
        this.programSpliceFlag = in.readByte() == 1;
        this.spliceImmediateFlag = in.readByte() == 1;
        this.programSplicePts = in.readLong();
        this.programSplicePlaybackPositionUs = in.readLong();
        int componentSpliceListSize = in.readInt();
        List<ComponentSplice> componentSpliceList = new ArrayList(componentSpliceListSize);

        for(int i = 0; i < componentSpliceListSize; ++i) {
            componentSpliceList.add(ComponentSplice.createFromParcel(in));
        }

        this.componentSpliceList = Collections.unmodifiableList(componentSpliceList);
        this.autoReturn = in.readByte() == 1;
        this.breakDurationUs = in.readLong();
        this.uniqueProgramId = in.readInt();
        this.availNum = in.readInt();
        this.availsExpected = in.readInt();
    }

    static SpliceInsertCommand parseFromSection(ParsableByteArray sectionData, long ptsAdjustment, TimestampAdjuster timestampAdjuster) {
        long spliceEventId = sectionData.readUnsignedInt();
        boolean spliceEventCancelIndicator = (sectionData.readUnsignedByte() & 128) != 0;
        boolean outOfNetworkIndicator = false;
        boolean programSpliceFlag = false;
        boolean spliceImmediateFlag = false;
        long programSplicePts = -Long.MAX_VALUE;
        List<ComponentSplice> componentSplices = Collections.emptyList();
        int uniqueProgramId = 0;
        int availNum = 0;
        int availsExpected = 0;
        boolean autoReturn = false;
        long breakDurationUs = -Long.MAX_VALUE;
        if (!spliceEventCancelIndicator) {
            int headerByte = sectionData.readUnsignedByte();
            outOfNetworkIndicator = (headerByte & 128) != 0;
            programSpliceFlag = (headerByte & 64) != 0;
            boolean durationFlag = (headerByte & 32) != 0;
            spliceImmediateFlag = (headerByte & 16) != 0;
            if (programSpliceFlag && !spliceImmediateFlag) {
                programSplicePts = TimeSignalCommand.parseSpliceTime(sectionData, ptsAdjustment);
            }

            if (!programSpliceFlag) {
                int componentCount = sectionData.readUnsignedByte();
                componentSplices = new ArrayList(componentCount);

                for(int i = 0; i < componentCount; ++i) {
                    int componentTag = sectionData.readUnsignedByte();
                    long componentSplicePts = -Long.MAX_VALUE;
                    if (!spliceImmediateFlag) {
                        componentSplicePts = TimeSignalCommand.parseSpliceTime(sectionData, ptsAdjustment);
                    }

                    componentSplices.add(new ComponentSplice(componentTag, componentSplicePts, timestampAdjuster.adjustTsTimestamp(componentSplicePts)));
                }
            }

            if (durationFlag) {
                long firstByte = sectionData.readUnsignedByte();
                autoReturn = (firstByte & 128L) != 0L;
                long breakDuration90khz = (firstByte & 1L) << 32 | sectionData.readUnsignedInt();
                breakDurationUs = breakDuration90khz * 1000L / 90L;
            }

            uniqueProgramId = sectionData.readUnsignedShort();
            availNum = sectionData.readUnsignedByte();
            availsExpected = sectionData.readUnsignedByte();
        }

        return new SpliceInsertCommand(spliceEventId, spliceEventCancelIndicator, outOfNetworkIndicator, programSpliceFlag, spliceImmediateFlag, programSplicePts, timestampAdjuster.adjustTsTimestamp(programSplicePts), componentSplices, autoReturn, breakDurationUs, uniqueProgramId, availNum, availsExpected);
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(this.spliceEventId);
        dest.writeByte((byte)(this.spliceEventCancelIndicator ? 1 : 0));
        dest.writeByte((byte)(this.outOfNetworkIndicator ? 1 : 0));
        dest.writeByte((byte)(this.programSpliceFlag ? 1 : 0));
        dest.writeByte((byte)(this.spliceImmediateFlag ? 1 : 0));
        dest.writeLong(this.programSplicePts);
        dest.writeLong(this.programSplicePlaybackPositionUs);
        int componentSpliceListSize = this.componentSpliceList.size();
        dest.writeInt(componentSpliceListSize);

        for(int i = 0; i < componentSpliceListSize; ++i) {
            this.componentSpliceList.get(i).writeToParcel(dest);
        }

        dest.writeByte((byte)(this.autoReturn ? 1 : 0));
        dest.writeLong(this.breakDurationUs);
        dest.writeInt(this.uniqueProgramId);
        dest.writeInt(this.availNum);
        dest.writeInt(this.availsExpected);
    }

    public static final class ComponentSplice {
        public final int componentTag;
        public final long componentSplicePts;
        public final long componentSplicePlaybackPositionUs;

        private ComponentSplice(int componentTag, long componentSplicePts, long componentSplicePlaybackPositionUs) {
            this.componentTag = componentTag;
            this.componentSplicePts = componentSplicePts;
            this.componentSplicePlaybackPositionUs = componentSplicePlaybackPositionUs;
        }

        public void writeToParcel(Parcel dest) {
            dest.writeInt(this.componentTag);
            dest.writeLong(this.componentSplicePts);
            dest.writeLong(this.componentSplicePlaybackPositionUs);
        }

        public static ComponentSplice createFromParcel(Parcel in) {
            return new ComponentSplice(in.readInt(), in.readLong(), in.readLong());
        }
    }
}
