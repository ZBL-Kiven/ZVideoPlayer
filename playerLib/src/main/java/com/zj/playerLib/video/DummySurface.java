//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.video;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLDisplay;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Handler.Callback;
import android.view.Surface;
import androidx.annotation.Nullable;
import com.zj.playerLib.util.Assertions;
import com.zj.playerLib.util.EGLSurfaceTexture;
import com.zj.playerLib.util.Log;
import com.zj.playerLib.util.Util;

@TargetApi(17)
public final class DummySurface extends Surface {
    private static final String TAG = "DummySurface";
    private static final String EXTENSION_PROTECTED_CONTENT = "EGL_EXT_protected_content";
    private static final String EXTENSION_SURFACELESS_CONTEXT = "EGL_KHR_surfaceless_context";
    public final boolean secure;
    private static int secureMode;
    private static boolean secureModeInitialized;
    private final DummySurfaceThread thread;
    private boolean threadReleased;

    public static synchronized boolean isSecureSupported(Context context) {
        if (!secureModeInitialized) {
            secureMode = Util.SDK_INT < 24 ? 0 : getSecureModeV24(context);
            secureModeInitialized = true;
        }

        return secureMode != 0;
    }

    public static DummySurface newInstanceV17(Context context, boolean secure) {
        assertApiLevel17OrHigher();
        Assertions.checkState(!secure || isSecureSupported(context));
        DummySurfaceThread thread = new DummySurfaceThread();
        return thread.init(secure ? secureMode : 0);
    }

    private DummySurface(DummySurfaceThread thread, SurfaceTexture surfaceTexture, boolean secure) {
        super(surfaceTexture);
        this.thread = thread;
        this.secure = secure;
    }

    public void release() {
        super.release();
        synchronized(this.thread) {
            if (!this.threadReleased) {
                this.thread.release();
                this.threadReleased = true;
            }

        }
    }

    private static void assertApiLevel17OrHigher() {
        if (Util.SDK_INT < 17) {
            throw new UnsupportedOperationException("Unsupported prior to API level 17");
        }
    }

    @TargetApi(24)
    private static int getSecureModeV24(Context context) {
        if (Util.SDK_INT >= 26 || !"samsung".equals(Util.MANUFACTURER) && !"XT1650".equals(Util.MODEL)) {
            if (Util.SDK_INT < 26 && !context.getPackageManager().hasSystemFeature("android.hardware.vr.high_performance")) {
                return 0;
            } else {
                EGLDisplay display = EGL14.eglGetDisplay(0);
                String eglExtensions = EGL14.eglQueryString(display, 12373);
                if (eglExtensions == null) {
                    return 0;
                } else if (!eglExtensions.contains("EGL_EXT_protected_content")) {
                    return 0;
                } else {
                    return eglExtensions.contains("EGL_KHR_surfaceless_context") ? 1 : 2;
                }
            }
        } else {
            return 0;
        }
    }

    private static class DummySurfaceThread extends HandlerThread implements Callback {
        private static final int MSG_INIT = 1;
        private static final int MSG_RELEASE = 2;
        private EGLSurfaceTexture eglSurfaceTexture;
        private Handler handler;
        @Nullable
        private Error initError;
        @Nullable
        private RuntimeException initException;
        @Nullable
        private DummySurface surface;

        public DummySurfaceThread() {
            super("dummySurface");
        }

        public DummySurface init(int secureMode) {
            this.start();
            this.handler = new Handler(this.getLooper(), this);
            this.eglSurfaceTexture = new EGLSurfaceTexture(this.handler);
            boolean wasInterrupted = false;
            synchronized(this) {
                this.handler.obtainMessage(1, secureMode, 0).sendToTarget();

                while(true) {
                    if (this.surface != null || this.initException != null || this.initError != null) {
                        break;
                    }

                    try {
                        this.wait();
                    } catch (InterruptedException var6) {
                        wasInterrupted = true;
                    }
                }
            }

            if (wasInterrupted) {
                Thread.currentThread().interrupt();
            }

            if (this.initException != null) {
                throw this.initException;
            } else if (this.initError != null) {
                throw this.initError;
            } else {
                return (DummySurface)Assertions.checkNotNull(this.surface);
            }
        }

        public void release() {
            Assertions.checkNotNull(this.handler);
            this.handler.sendEmptyMessage(2);
        }

        public boolean handleMessage(Message msg) {
            switch(msg.what) {
            case 1:
                boolean var28 = false;

                label199: {
                    label200: {
                        try {
                            var28 = true;
                            this.initInternal(msg.arg1);
                            var28 = false;
                            break label199;
                        } catch (RuntimeException var35) {
                            Log.e("DummySurface", "Failed to initialize dummy surface", var35);
                            this.initException = var35;
                            var28 = false;
                        } catch (Error var36) {
                            Log.e("DummySurface", "Failed to initialize dummy surface", var36);
                            this.initError = var36;
                            var28 = false;
                            break label200;
                        } finally {
                            if (var28) {
                                synchronized(this) {
                                    this.notify();
                                }
                            }
                        }

                        synchronized(this) {
                            this.notify();
                            return true;
                        }
                    }

                    synchronized(this) {
                        this.notify();
                        return true;
                    }
                }

                synchronized(this) {
                    this.notify();
                }

                return true;
            case 2:
                try {
                    this.releaseInternal();
                } catch (Throwable var30) {
                    Log.e("DummySurface", "Failed to release dummy surface", var30);
                } finally {
                    this.quit();
                }

                return true;
            default:
                return true;
            }
        }

        private void initInternal(int secureMode) {
            Assertions.checkNotNull(this.eglSurfaceTexture);
            this.eglSurfaceTexture.init(secureMode);
            this.surface = new DummySurface(this, this.eglSurfaceTexture.getSurfaceTexture(), secureMode != 0);
        }

        private void releaseInternal() {
            Assertions.checkNotNull(this.eglSurfaceTexture);
            this.eglSurfaceTexture.release();
        }
    }
}
