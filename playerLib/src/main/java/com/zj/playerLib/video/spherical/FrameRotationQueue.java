//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.zj.playerLib.video.spherical;

import android.opengl.Matrix;

import com.zj.playerLib.util.TimedValueQueue;

public final class FrameRotationQueue {
    private final float[] recenterMatrix = new float[16];
    private final float[] rotationMatrix = new float[16];
    private final TimedValueQueue rotations = new TimedValueQueue();
    private boolean recenterMatrixComputed;

    public FrameRotationQueue() {
    }

    public void setRotation(long timestampUs, float[] angleAxis) {
        this.rotations.add(timestampUs, angleAxis);
    }

    public void reset() {
        this.rotations.clear();
        this.recenterMatrixComputed = false;
    }

    public boolean pollRotationMatrix(float[] matrix, long timestampUs) {
        float[] rotation = (float[])this.rotations.pollFloor(timestampUs);
        if (rotation == null) {
            return false;
        } else {
            getRotationMatrixFromAngleAxis(this.rotationMatrix, rotation);
            if (!this.recenterMatrixComputed) {
                computeRecenterMatrix(this.recenterMatrix, this.rotationMatrix);
                this.recenterMatrixComputed = true;
            }

            Matrix.multiplyMM(matrix, 0, this.recenterMatrix, 0, this.rotationMatrix, 0);
            return true;
        }
    }

    private static void computeRecenterMatrix(float[] recenterMatrix, float[] rotationMatrix) {
        Matrix.setIdentityM(recenterMatrix, 0);
        float normRowSqr = rotationMatrix[10] * rotationMatrix[10] + rotationMatrix[8] * rotationMatrix[8];
        float normRow = (float)Math.sqrt((double)normRowSqr);
        recenterMatrix[0] = rotationMatrix[10] / normRow;
        recenterMatrix[2] = rotationMatrix[8] / normRow;
        recenterMatrix[8] = -rotationMatrix[8] / normRow;
        recenterMatrix[10] = rotationMatrix[10] / normRow;
    }

    private static void getRotationMatrixFromAngleAxis(float[] matrix, float[] angleAxis) {
        float x = angleAxis[0];
        float y = -angleAxis[1];
        float z = -angleAxis[2];
        float angleRad = Matrix.length(x, y, z);
        if (angleRad != 0.0F) {
            float angleDeg = (float)Math.toDegrees((double)angleRad);
            Matrix.setRotateM(matrix, 0, angleDeg, x / angleRad, y / angleRad, z / angleRad);
        } else {
            Matrix.setIdentityM(matrix, 0);
        }

    }
}
