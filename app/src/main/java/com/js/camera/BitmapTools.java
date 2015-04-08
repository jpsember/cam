package com.js.camera;

import android.graphics.Bitmap;
import android.graphics.Matrix;

import com.js.basic.IPoint;

public class BitmapTools {

  /**
   * Decode YUV 4:2:0 bitmap to ARGB_8888 format.
   * Note that a (fully opaque) alpha channel is added;
   * if we omit this, I can't then see how to create a Bitmap from the result
   *
   * @param argb where to store data; if null, creates it
   * @return argb data
   */
  public static int[] decodeYUV420SP(int[] argb, byte[] yuv420sp, IPoint size) {

    int width = size.x;
    int height = size.y;
    int frameSize = width * height;
    if (argb == null)
      argb = new int[frameSize * 2];

    int uvpBase = frameSize;
    for (int j = 0, yp = 0; j < height; j++) {
      int u = 0;
      int v = 0;
      int uvp = uvpBase;
      for (int i = 0; i < width; i++, yp++) {
        int y = (0xff & ((int) yuv420sp[yp])) - 16;
        if (y < 0) y = 0;
        if ((i & 1) == 0) {
          v = (0xff & yuv420sp[uvp++]) - 128;
          u = (0xff & yuv420sp[uvp++]) - 128;
        }

        int y1192 = 1192 * y;
        int r = (y1192 + 1634 * v);
        int g = (y1192 - 833 * v - 400 * u);
        int b = (y1192 + 2066 * u);

        if (r < 0) r = 0;
        else if (r > 262143) r = 262143;
        if (g < 0) g = 0;
        else if (g > 262143) g = 262143;
        if (b < 0) b = 0;
        else if (b > 262143) b = 262143;

        argb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
      }
      if ((j & 1) != 0)
        uvpBase += width;
    }
    return argb;
  }

  /**
   * Rotate a bitmap
   *
   * @param rotationToApply rotation, in degrees
   * @return rotated bitmap, or original if no rotation was necessary
   */
  public static Bitmap rotateBitmap(Bitmap bitmap, int rotationToApply) {
    if (rotationToApply != 0) {
      Matrix matrix = new Matrix();
      matrix.postRotate(rotationToApply);
      bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }
    return bitmap;
  }

}
