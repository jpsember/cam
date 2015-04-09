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

  public PhotoManipulator(Context context, PhotoInfo photoInfo, Bitmap originalBitmap) {
    mContext = context;
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
        InputStream stream = mContext.getResources().openRawResource(R.raw.vignette);
        sVignette = BitmapFactory.decodeStream(stream);
        stream.close();
      } catch (IOException e) {
        die(e);
      }
    }
    return sVignette;
  }

  private boolean isPortrait() {
    return BitmapTools.getOrientation(mOriginalBitmap) == BitmapTools.ORIENTATION_PORTRAIT;
  }

  private static Bitmap sVignette;

  private Context mContext;
  private PhotoInfo mPhotoInfo;
  private Bitmap mOriginalBitmap;
  private Bitmap mOutputBitmap;
  private Canvas mCanvas;
}
