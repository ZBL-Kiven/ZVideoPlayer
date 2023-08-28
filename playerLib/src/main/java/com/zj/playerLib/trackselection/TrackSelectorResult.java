package com.zj.playerLib.trackselection;

import com.zj.playerLib.RendererConfiguration;
import com.zj.playerLib.util.Util;

public final class TrackSelectorResult {
    public final int length;
    public final RendererConfiguration[] rendererConfigurations;
    public final TrackSelectionArray selections;
    public final Object info;

    public TrackSelectorResult(RendererConfiguration[] rendererConfigurations, TrackSelection[] selections, Object info) {
        this.rendererConfigurations = rendererConfigurations;
        this.selections = new TrackSelectionArray(selections);
        this.info = info;
        this.length = rendererConfigurations.length;
    }

    public boolean isRendererEnabled(int index) {
        return this.rendererConfigurations[index] != null;
    }

    public boolean isEquivalent(TrackSelectorResult other) {
        if (other != null && other.selections.length == this.selections.length) {
            for(int i = 0; i < this.selections.length; ++i) {
                if (!this.isEquivalent(other, i)) {
                    return false;
                }
            }

            return true;
        } else {
            return false;
        }
    }

    public boolean isEquivalent(TrackSelectorResult other, int index) {
        if (other == null) {
            return false;
        } else {
            return Util.areEqual(this.rendererConfigurations[index], other.rendererConfigurations[index]) && Util.areEqual(this.selections.get(index), other.selections.get(index));
        }
    }
}
