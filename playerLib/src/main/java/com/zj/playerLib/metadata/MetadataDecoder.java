package com.zj.playerLib.metadata;

import androidx.annotation.Nullable;

public interface MetadataDecoder {
    @Nullable
    Metadata decode(MetadataInputBuffer var1);
}
