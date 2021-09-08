package com.zj.playerLib.upstream.cache;

public interface ContentMetadata {

    byte[] get(String var1, byte[] var2);

    String get(String var1, String var2);

    long get(String var1, long var2);

    boolean contains(String var1);
}
