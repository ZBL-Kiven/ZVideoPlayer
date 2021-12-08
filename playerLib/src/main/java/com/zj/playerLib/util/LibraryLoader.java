package com.zj.playerLib.util;

public final class LibraryLoader {
    private String[] nativeLibraries;
    private boolean loadAttempted;
    private boolean isAvailable;

    public LibraryLoader(String... libraries) {
        this.nativeLibraries = libraries;
    }

    public synchronized void setLibraries(String... libraries) {
        Assertions.checkState(!this.loadAttempted, "Cannot set libraries after loading");
        this.nativeLibraries = libraries;
    }

    public synchronized boolean isAvailable() {
        if (this.loadAttempted) {
            return this.isAvailable;
        } else {
            this.loadAttempted = true;

            try {
                String[] var1 = this.nativeLibraries;
                int var2 = var1.length;

                for(int var3 = 0; var3 < var2; ++var3) {
                    String lib = var1[var3];
                    System.loadLibrary(lib);
                }

                this.isAvailable = true;
            } catch (UnsatisfiedLinkError var5) {
            }

            return this.isAvailable;
        }
    }
}
