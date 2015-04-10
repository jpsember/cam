package com.js.camera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;

import com.js.basic.IPoint;
import com.js.camera.camera.R;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import static com.js.basic.Tools.*;

public class PhotoManipulator {

  /**
   * Construct a manipulator for a photo
   *
   * @param originalBitmap the bitmap to be manipulated; may be changed by the manipulation
   */
  public PhotoManipulator(PhotoFile photoFile, PhotoInfo photoInfo, Bitmap originalBitmap) {
    assertCorrectConfig(originalBitmap);
    mPhotoFile = photoFile;
    mPhotoInfo = photoInfo;
    mOriginalBitmap = originalBitmap;
    calcHashValue();
  }

  public Bitmap getManipulatedBitmap() {
    if (mOutputBitmap == null)
      constructManipulatedBitmap();
    return mOutputBitmap;
  }

  private static final float sFlipBetweenLandscapeAndPortrait[] = {0, 1, 0, 1, 0, 0, 0, 0, 1};

  private void constructManipulatedBitmap() {
    constructCanvas();

    applyVignette();

    // Throw out unneeded resources
    mOriginalBitmap = null;
    mCanvas = null;
    mPhotoFile = null;
  }

  private int calcVignetteAlphaForAge() {
    int ageState = mPhotoInfo.getCurrentAgeState();
    float scale = (0 * (PhotoInfo.AGE_STATE_MAX - 1 - ageState)
        + (80 * ageState)) / (PhotoInfo.AGE_STATE_MAX - 1);
    return (int) scale;
  }

  private void applyVignette() {
    Paint paint = new Paint();
    paint.setAlpha(calcVignetteAlphaForAge());
    // Vignettes are in landscape mode; if necessary, rotate to portrait
    Matrix matrix = new Matrix();
    if (isPortrait()) {
      matrix.setValues(sFlipBetweenLandscapeAndPortrait);
    }

    int jpegIndex = (mHashValue >> HASH_SHIFT_VIGNETTE);
    Bitmap vignetteBitmap = getVignette(jpegIndex);

    // Flip vertically and/or horizontally, based on hash value
    int flipIndex = myMod(mHashValue >> HASH_SHIFT_VIGNETTE_FLIP, 4);
    if (0 != (flipIndex & 1)) {
      Matrix matrix2 = new Matrix();
      matrix2.postScale(-1, 1);
      matrix2.postTranslate(vignetteBitmap.getWidth(), 0);
      matrix.postConcat(matrix2);
    }
    if (0 != (flipIndex & 2)) {
      Matrix matrix2 = new Matrix();
      matrix2.postScale(1, -1);
      matrix2.postTranslate(0, vignetteBitmap.getHeight());
      matrix.postConcat(matrix2);
    }
    mCanvas.drawBitmap(vignetteBitmap, matrix, paint);

  }

  private void constructCanvas() {
    IPoint targetSize = PhotoInfo.getLogicalMaximumSize(isPortrait());
    Bitmap bitmap = BitmapTools.scaleBitmapToFit(mOriginalBitmap, targetSize, true);
    // Construct a copy of the scaled bitmap, so we don't modify the original
    bitmap = bitmap.copy(bitmap.getConfig(), true);
    if (bitmap == null)
      die("failed to make copy of bitmap");

    mOutputBitmap = bitmap;
    mCanvas = new Canvas();
    mCanvas.setBitmap(mOutputBitmap);
  }

  private static final int[] sVignetteIds = {
      R.raw.vignette, R.raw.vignette2, R.raw.vignette3};

  private static final int HASH_BITS_VIGNETTE = 3;
  private static final int HASH_BITS_VIGNETTE_FLIP = 2;

  private static final int HASH_SHIFT_VIGNETTE = 0;
  private static final int HASH_SHIFT_VIGNETTE_FLIP = HASH_SHIFT_VIGNETTE + HASH_BITS_VIGNETTE;
  private static final int HASH_SHIFT_next = HASH_SHIFT_VIGNETTE_FLIP + HASH_BITS_VIGNETTE_FLIP;

  private Bitmap getVignette(int vignetteIndex) {
    Bitmap sVignette = null;
    try {
      int vignetteId = sVignetteIds[myMod(vignetteIndex, sVignetteIds.length)];
      InputStream stream = mPhotoFile.getContext().getResources().openRawResource(vignetteId);
      sVignette = BitmapFactory.decodeStream(stream);
      stream.close();
    } catch (IOException e) {
      die(e);
    }
    assertCorrectConfig(sVignette);
    return sVignette;
  }

  private boolean isPortrait() {
    return BitmapTools.getOrientation(mOriginalBitmap) == BitmapTools.ORIENTATION_PORTRAIT;
  }

  private static void assertCorrectConfig(Bitmap bitmap) {
    if (bitmap.getConfig() != Bitmap.Config.ARGB_8888)
      throw new
          IllegalArgumentException("Unexpected Bitmap.Config: " + bitmap);
  }

  /**
   * Calculate hash value for photo, derived from id and photo file's random seed
   */
  private void calcHashValue() {
    Checksum c = new CRC32();
    c.update(mPhotoInfo.getId());
    c.update(mPhotoFile.getRandomSeed());
    mHashValue = (int) c.getValue();
  }

  private int mHashValue;
  private PhotoFile mPhotoFile;
  private PhotoInfo mPhotoInfo;
  private Bitmap mOriginalBitmap;
  private Bitmap mOutputBitmap;
  private Canvas mCanvas;
}
