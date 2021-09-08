package com.zj.playerLib.audio;

import androidx.annotation.Nullable;

public final class AuxEffectInfo {
    public final int effectId;
    public final float sendLevel;

    public AuxEffectInfo(int effectId, float sendLevel) {
        this.effectId = effectId;
        this.sendLevel = sendLevel;
    }

    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            AuxEffectInfo auxEffectInfo = (AuxEffectInfo)o;
            return this.effectId == auxEffectInfo.effectId && Float.compare(auxEffectInfo.sendLevel, this.sendLevel) == 0;
        } else {
            return false;
        }
    }

    public int hashCode() {
        int result = 17;
        result = 31 * result + this.effectId;
        result = 31 * result + Float.floatToIntBits(this.sendLevel);
        return result;
    }
}
