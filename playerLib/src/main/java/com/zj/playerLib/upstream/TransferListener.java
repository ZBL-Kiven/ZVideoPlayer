package com.zj.playerLib.upstream;

public interface TransferListener {
    void onTransferInitializing(DataSource var1, DataSpec var2, boolean var3);

    void onTransferStart(DataSource var1, DataSpec var2, boolean var3);

    void onBytesTransferred(DataSource var1, DataSpec var2, boolean var3, int var4);

    void onTransferEnd(DataSource var1, DataSpec var2, boolean var3);
}
