package com.js.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;

import com.js.basic.IPoint;
import com.js.camera.camera.R;

import java.io.IOException;
import java.io.InputStream;

import static com.js.basic.Tools.*;

public class PhotoManipulator {

  private static final int LOGICAL_PORTRAIT_WIDTH = 480;
  private static final int LOGICAL_PORTRAIT_HEIGHT = 640;

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
  }

  public Bitmap getManipulatedBitmap() {
    if (mOutputBitmap == null)
      constructManipulatedBitmap();
    return mOutputBitmap;
  }

  private void constructManipulatedBitmap() {
    constructCanvas();

    Paint paint = new Paint();
    paint.setAlpha(128);
    // Vignettes are in landscape mode; if necessary, rotate to portrait
    Matrix matrix = new Matrix();
    if (isPortrait()) {
      float sFlipBetweenLandscapeAndPortrait[] = {0, 1, 0, 1, 0, 0, 0, 0, 1};
      matrix.setValues(sFlipBetweenLandscapeAndPortrait);
    }
    mCanvas.drawBitmap(getVignette(), matrix, paint);

    // Throw out unneeded resources
    mOriginalBitmap = null;
    mCanvas = null;
    mPhotoFile = null;
  }

  private void constructCanvas() {
    IPoint targetSize;
    if (isPortrait()) {
      targetSize = new IPoint(LOGICAL_PORTRAIT_WIDTH, LOGICAL_PORTRAIT_HEIGHT);
    } else {
      targetSize = new IPoint(LOGICAL_PORTRAIT_HEIGHT, LOGICAL_PORTRAIT_WIDTH);
    }
    Bitmap bitmap = BitmapTools.scaleBitmapToFit(mOriginalBitmap, targetSize);
    // Construct a copy of the scaled bitmap, so we don't modify the original
    bitmap = bitmap.copy(bitmap.getConfig(), true);
    if (bitmap == null)
      die("failed to make copy of bitmap");

    mOutputBitmap = bitmap;
    mCanvas = new Canvas();
    mCanvas.setBitmap(mOutputBitmap);
  }

  private Bitmap getVignette() {
    if (sVignette == null) {
      try {
        InputStream stream = mPhotoFile.getContext().getResources().openRawResource(R.raw.vignette3);
        sVignette = BitmapFactory.decodeStream(stream);
        stream.close();
      } catch (IOException e) {
        die(e);
      }
      assertCorrectConfig(sVignette);
    }
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

  private static Bitmap sVignette;

  private PhotoFile mPhotoFile;
  private PhotoInfo mPhotoInfo;
  private Bitmap mOriginalBitmap;
  private Bitmap mOutputBitmap;
  private Canvas mCanvas;
}
