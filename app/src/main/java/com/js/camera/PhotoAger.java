package com.js.camera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.js.basic.IPoint;

import java.io.ByteArrayOutputStream;

import static com.js.basic.Tools.*;

public class PhotoAger {

  // Force scaled dimensions to be multiple of 2 pixels?
  // Necessary for RGB -> YUV conversions
  private static final boolean QUANTIZED_DIMENSIONS = true;

  /**
   * Construct an ager for a photo
   */
  public PhotoAger(PhotoInfo photoInfo, byte[] currentJPEG) {
    photoInfo.assertFrozen();
    mPhotoInfo = photoInfo;
    mCurrentJPEG = currentJPEG;
  }

  public byte[] getAgedJPEG() {
    if (mAgedPhotoInfo == null)
      constructAgedPhoto();
    return mCurrentJPEG;
  }

  public PhotoInfo getAgedInfo() {
    getAgedJPEG();
    return mAgedPhotoInfo;
  }

  private IPoint calcSizeForAge(int ageState) {
    float scale = (1.0f * (PhotoInfo.AGE_STATE_MAX - 1 - ageState) + (.3f * ageState)) / (PhotoInfo.AGE_STATE_MAX - 1);
    IPoint size = PhotoInfo.getLogicalMaximumSize(mIsPortrait);
    size.x = (int) (size.x * scale);
    size.y = (int) (size.y * scale);
    if (QUANTIZED_DIMENSIONS) {
      size.x &= ~1;
      size.y &= ~1;
    }
    return size;
  }

  private float calcColorScaleForAge(int ageState) {
    float scale = (1.0f * (PhotoInfo.AGE_STATE_MAX - 1 - ageState) + (0f * ageState)) / (PhotoInfo.AGE_STATE_MAX - 1);
    return scale;
  }

  private void constructAgedPhoto() {
    mAgedPhotoInfo = mPhotoInfo;
    pr("constructAgedPhoto: " + mAgedPhotoInfo);

    while (mAgedPhotoInfo.getTargetAgeState() > mAgedPhotoInfo.getCurrentAgeState()) {
      Bitmap bitmap = BitmapFactory.decodeByteArray(mCurrentJPEG, 0, mCurrentJPEG.length);
      if (bitmap == null)
        die("Failed to decode jpeg");
      mIsPortrait = BitmapTools.getOrientation(bitmap) == BitmapTools.ORIENTATION_PORTRAIT;

      int newAge = mAgedPhotoInfo.getCurrentAgeState() + 1;
      PhotoInfo newInfo = mutableCopyOf(mAgedPhotoInfo);
      newInfo.setCurrentAgeState(newAge);
      pr(" newInfo: " + newInfo);

      // Scale bitmap to new size
      IPoint newSize = calcSizeForAge(newAge);
      bitmap = BitmapTools.scaleBitmapToFit(bitmap, newSize, false);

      // Bleach out the colors a bit
      {
        float origScale = calcColorScaleForAge(mAgedPhotoInfo.getCurrentAgeState());
        float newScale = calcColorScaleForAge(newAge);
        if (origScale > 0) {
          float colorScale = newScale / origScale;
          byte[] yuv = BitmapTools.getYUV420SP(bitmap, null);
          BitmapTools.scaleYUV420SP(yuv, newSize, 1.0f, colorScale, colorScale);
          int[] argb = BitmapTools.decodeYUV420SP(null, yuv, newSize);
          bitmap = Bitmap.createBitmap(argb, newSize.x, newSize.y, Bitmap.Config.ARGB_8888);
        }
      }

      // Convert back to JPEG array of bytes

      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
      unimp("JPEG quality settings");

      mCurrentJPEG = stream.toByteArray();
      mAgedPhotoInfo = frozen(newInfo);
    }
  }

  private PhotoInfo mPhotoInfo;
  private boolean mIsPortrait;
  private byte[] mCurrentJPEG;
  private PhotoInfo mAgedPhotoInfo;

}
