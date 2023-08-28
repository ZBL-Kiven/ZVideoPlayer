package com.zj.playerLib.offline;

import java.util.List;

public interface FilterableManifest<T> {
    T copy(List<StreamKey> var1);
}
