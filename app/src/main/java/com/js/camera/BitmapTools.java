package com.js.camera;

import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Matrix;

import com.js.basic.IPoint;
import com.js.basic.MyMath;
import com.js.basic.Rect;

import java.io.ByteArrayOutputStream;

import static com.js.basic.Tools.*;
import static com.js.android.AndroidTools.*;

public class BitmapTools {

  public static final int ORIENTATION_LANDSCAPE = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
  public static final int ORIENTATION_PORTRAIT = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;

  /**
   * Decode YUV 4:2:0 bitmap to ARGB_8888 format.
   * Note that a (fully opaque) alpha channel is added;
   * if we omit this, I can't then see how to create a Bitmap from the result
   *
   * @param argb where to store data; if null, creates it
   * @return argb data
   */
  public static int[] decodeYUV420SP(int[] argb, byte[] yuv, IPoint size) {
    doNothing();
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
        int y = (0xff & ((int) yuv[yp])) - 16;
        if (y < 0) y = 0;
        if ((i & 1) == 0) {
          v = (0xff & yuv[uvp++]) - 128;
          u = (0xff & yuv[uvp++]) - 128;
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
      showFreeMemory(null);
      bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
      showFreeMemory(null);
    }
    return bitmap;
  }

  /**
   * Scale bitmap, if necessary, to fit a target rectangle, without changing aspect ratio
   */
  public static Bitmap scaleBitmapToFit(Bitmap bitmap, IPoint size, boolean preserveAspectRatio) {
    if (!preserveAspectRatio) {
      return Bitmap.createScaledBitmap(bitmap, size.x, size.y, true);
    } else {
      Rect targetRect = new Rect(0, 0, size.x, size.y);
      Rect originalRect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
      Matrix matrix = MyMath.calcRectFitRectTransform(originalRect, targetRect);
      return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }
  }

  public static int getOrientation(Bitmap bitmap) {
    return (bitmap.getWidth() > bitmap.getHeight()) ? ORIENTATION_LANDSCAPE : ORIENTATION_PORTRAIT;
  }

  /**
   * Get bitmap's pixels as YUV (NV21);
   * see http://en.wikipedia.org/wiki/YUV#Y.27UV420sp_.28NV21.29_to_RGB_conversion_.28Android.29
   *
   * @param yuv where to store data; if null, creates it
   * @return yuv
   */
  public static byte[] getYUV420SP(Bitmap bitmap, byte[] yuv) {

    int width = bitmap.getWidth();
    int height = bitmap.getHeight();
    if ((width % 2) != 0 || (height % 2) != 0)
      throw new IllegalArgumentException("Dimensions must be multiple of 2");

    int[] argb = new int[width * height];

    bitmap.getPixels(argb, 0, width, 0, 0, width, height);

    int yuvLength = (width * height * 3) / 2;
    if (yuv == null)
      yuv = new byte[yuvLength];
    else if (yuv.length != yuvLength)
      throw new IllegalArgumentException("yuv length problem");
    encodeYUV420SP(yuv, argb, width, height);

    return yuv;
  }

  private static void encodeYUV420SP(byte[] yuv420sp, int[] argb, int width, int height) {
    final int frameSize = width * height;

    int yIndex = 0;
    int uvIndex = frameSize;

    int R, G, B, Y, U, V;
    int index = 0;
    for (int j = 0; j < height; j++) {
      for (int i = 0; i < width; i++) {

        R = (argb[index] & 0xff0000) >> 16;
        G = (argb[index] & 0xff00) >> 8;
        B = (argb[index] & 0xff);

        Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
        U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
        V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;

        // NV21 has a plane of Y and interleaved planes of VU each sampled by a factor of 2
        //    meaning for every 4 Y pixels there are 1 V and 1 U.  Note the sampling is every other
        //    pixel AND every other scanline.
        yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
        if (j % 2 == 0 && index % 2 == 0) {
          yuv420sp[uvIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
          yuv420sp[uvIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
        }
        index++;
      }
    }
  }

  public static void scaleYUV420SP(byte[] yuv, IPoint size, float yScale, float uScale, float vScale) {

    int yScaleI = (int) (yScale * 256.0f);
    int uScaleI = (int) (uScale * 256.0f);
    int vScaleI = (int) (vScale * 256.0f);

    int height = size.y;
    int width = size.x;
    final int frameSize = width * height;
    int yIndex = 0;
    int uvIndex = frameSize;
    int index = 0;
    for (int j = 0; j < height; j++) {
      for (int i = 0; i < width; i++) {
        int y = (((int) yuv[yIndex]) & 0xff) - 128;
        if (yScaleI != 256) {
          y = (y * yScaleI) >> 8;
          y = (y < -128) ? -128 : ((y > 127) ? 127 : y);
          yuv[yIndex] = (byte) (y + 128);
        }
        yIndex++;
        if (j % 2 == 0 && index % 2 == 0) {
          int u = (((int) yuv[uvIndex]) & 0xff) - 128;
          u = (u * uScaleI) >> 8;
          u = (u < -128) ? -128 : ((u > 127) ? 127 : u);
          yuv[uvIndex] = (byte) (u + 128);

          int v = (((int) yuv[uvIndex + 1]) & 0xff) - 128;
          v = (v * vScaleI) >> 8;
          v = (v < -128) ? -128 : ((v > 127) ? 127 : v);
          yuv[uvIndex + 1] = (byte) (v + 128);

          uvIndex += 2;
        }
        index++;
      }
    }
  }

  public static IPoint size(Bitmap bitmap) {
    return new IPoint(bitmap.getWidth(), bitmap.getHeight());
  }

  public static byte[] encodeJPEG(Bitmap bitmap, int quality) {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream);
    byte[] bytes = stream.toByteArray();
//    pr("encoded JPEG of size "+size(bitmap)+", quality "+quality+", length "+bytes.length);
    return bytes;
  }

}
