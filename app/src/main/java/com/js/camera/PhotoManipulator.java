package com.js.camera;

import android.graphics.Bitmap;
import android.graphics.Matrix;

import static com.js.basic.Tools.*;

public class PhotoManipulator {

  public PhotoManipulator(PhotoInfo photoInfo, Bitmap originalBitmap) {
    mPhotoInfo = photoInfo;
    mOriginalBitmap = originalBitmap;
  }

  public Bitmap getManipulatedBitmap() {
    if (mOutputBitmap == null)
      constructManipulatedBitmap();
    return mOutputBitmap;
  }

  private void constructManipulatedBitmap() {
    warning("just rotating");
    Matrix matrix = new Matrix();
    matrix.postRotate(45);
    Bitmap bitmap = Bitmap.createBitmap(mOriginalBitmap, 0, 0, mOriginalBitmap.getWidth(), mOriginalBitmap.getHeight(), matrix, true);
    mOutputBitmap = bitmap;
  }

  private PhotoInfo mPhotoInfo;
  private Bitmap mOriginalBitmap;
  private Bitmap mOutputBitmap;
}
