package com.js.camera;

import java.util.List;
import java.util.Observable;
import java.util.Observer;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.js.android.UITools;
import com.js.basic.IPoint;
import com.js.camera.camera.R;

import static com.js.basic.Tools.*;
import static com.js.android.AndroidTools.*;

public class CameraActivity extends Activity implements OnClickListener, Observer {

  private enum Demo {
    Preview, TakePhotos, PhotoManip, PhotoAger
  }

  private static final Demo DEMO = Demo.TakePhotos;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    doNothingAndroid();
    AppState.prepare(this);
    mPhotoFile = AppState.photoFile();

    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

    setContentView(buildContentView());
  }

  /**
   * Install a PreviewCallback for test purposes
   */
  private void installPreviewCallback() {
    final MyCamera previewCamera = mCamera;
    previewCamera.setPreviewCallback(new Camera.PreviewCallback() {
      @Override
      public void onPreviewFrame(byte[] data, Camera camera) {
        // This is running in a different thread!  We must
        // verify that the camera is still open, and avoid using
        // getParameters() or other methods that are not threadsafe
        MyCamera.Properties properties = previewCamera.getProperties();

        IPoint previewSize = properties.previewSize();
        if (previewSize == null)
          return;

        mCounter++;
        if (mCounter % 40 != 0)
          return;

        if (properties.format() != ImageFormat.NV21)
          throw new UnsupportedOperationException("Unsupported preview image format: " + properties.format());

        int argb[] = BitmapTools.decodeYUV420SP(null, data, previewSize);
        Bitmap bitmap = Bitmap.createBitmap(argb, previewSize.x, previewSize.y, Bitmap.Config.ARGB_8888);

        int rotation = properties.rotation();
        if (rotation != 0) {
          Matrix matrix = new Matrix();
          matrix.postRotate(rotation);
          bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }

        final Bitmap finalBitmap = bitmap;
        AppState.postUIEvent(new Runnable() {
          public void run() {
            if (!mResumed) return;
            mImageView.setImageBitmap(finalBitmap);
          }
        });
      }

      private int mCounter;
    });
  }

  private final static int BGND_COLOR = Color.DKGRAY;

  private View buildContentView() {

    // Construct a linear layout that contains the Camera view, and some other views as well
    final LinearLayout container = UITools.linearLayout(this, false);

    mCameraViewContainer = new FrameLayout(this);
    {
      mCameraViewContainer.setBackgroundColor(BGND_COLOR);
      if (DEMO == Demo.Preview)
        mCameraViewContainer.setPadding(10, 10, 10, 10);
    }

    float weight = (DEMO == Demo.PhotoManip || DEMO == Demo.PhotoAger) ? 4.0f : 0.8f;
    container.addView(buildImageView(), UITools.layoutParams(container, weight));
    if (DEMO == Demo.Preview)
      ShrinkingView.build(container, 1.0f);
    container.addView(mCameraViewContainer, UITools.layoutParams(container, 1.0f));

    return container;
  }

  private void resumePhotoFile() {
    mPhotoFile.addObserver(this);
  }

  private void resumeCamera() {
    mCamera = new MyCamera(this, new MyCamera.Listener() {
      @Override
      public void stateChanged() {
        if (mCamera.isOpen())
          mPreview.cameraOpen();
      }

      @Override
      public void pictureTaken(byte[] jpeg, int rotationToApply) {
        if (!mPhotoFile.isOpen())
          return;
        showFreeMemory(null, "PhotoFile.createPhoto");
        IPoint pictureSize = mCamera.getProperties().getPictureSize();
        mPhotoFile.createPhoto(jpeg, pictureSize, rotationToApply);
        showFreeMemory(null, "After createPhoto");
        // When photo file has created new entry, it will call our observer
        // and we can then add it to the image view
      }
    });

    buildCameraView();
    mCameraViewContainer.addView(mPreview);
    if (DEMO == Demo.Preview)
      installPreviewCallback();

    mCamera.open();
  }

  private void pauseCamera() {
    mCamera.close();
    mCamera = null;
    mCameraViewContainer.removeView(mPreview);
  }

  private void pausePhotoFile() {
    mPhotoFile.deleteObserver(this);
  }

  private PhotoInfo getNextPhotoFromFile() {
    PhotoInfo photoInfo = null;
    List<PhotoInfo> photos = mPhotoFile.getPhotos(0, 100);
    if (photos.size() < 4)
      photos.clear();
    int bestDiff = Integer.MAX_VALUE;
    int previousPhotoId = -1;
    if (mBitmapLoadingPhotoInfo != null)
      previousPhotoId = mBitmapLoadingPhotoInfo.getId();
    for (PhotoInfo p : photos) {
      int diff = p.getId() - previousPhotoId;
      if (diff > 0 && diff < bestDiff) {
        bestDiff = diff;
        photoInfo = p;
      }
    }
    if (photoInfo == null && !photos.isEmpty())
      photoInfo = photos.get(0);
    return photoInfo;
  }

  private void buildCameraView() {
    mPreview = new CameraPreview(this, mCamera);
    mPreview.setBackgroundColor(BGND_COLOR);

    int style = 0;
    if (style >= 2) {
      mPreview.setGlassColor(0xc0600000);
      mPreview.setFrameRadius(50);
    }
    if (style % 2 != 0)
      mPreview.setFrameStyle(CameraPreview.FRAMESTYLE_NONE);

    mPreview.setKeepScreenOn(true);
    mPreview.setOnClickListener(this);
  }

  @Override
  public void onClick(View arg0) {
    if (!mCamera.isOpen())
      return;

    switch (DEMO) {
      case PhotoAger: {
        if (!mPhotoFile.isOpen())
          return;
        PhotoInfo photoInfo = getNextPhotoFromFile();
        ASSERT(photoInfo != null);
        mAgePhotoInfo = photoInfo;
        mBitmapLoadingPhotoInfo = photoInfo;
        mPhotoFile.getBitmap(mBitmapLoadingPhotoInfo);
      }
      break;
      case PhotoManip: {
        if (!mPhotoFile.isOpen())
          return;
        PhotoInfo photoInfo = getNextPhotoFromFile();
        if (photoInfo != null) {
          mBitmapLoadingPhotoInfo = photoInfo;
          mPhotoFile.getBitmap(mBitmapLoadingPhotoInfo);
        }
      }
      break;
      case Preview:
        mCamera.setPreviewStarted(!mCamera.isPreviewStarted());
        break;
      case TakePhotos:
        mCamera.takePicture();
        break;
    }
  }

  @Override
  protected void onResume() {
    mResumed = true;
    super.onResume();
    resumeCamera();
    resumePhotoFile();
  }

  @Override
  protected void onPause() {
    pausePhotoFile();
    pauseCamera();
    super.onPause();
    mResumed = false;
  }

  private View buildImageView() {
    mImageView = new ImageView(this);
    mImageView.setBackgroundColor(UITools.debugColor());
    mImageView.setImageResource(R.drawable.ic_launcher);
    mImageView.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        processImageViewClick();
      }
    });
    return mImageView;
  }

  private void processImageViewClick() {
    if (!mPhotoFile.isOpen())
      return;

    switch (DEMO) {
      case PhotoAger:
        if (mAgeBitmap == null)
          return;
        int targetAge = mAgePhotoInfo.getCurrentAgeState() + 1;
        if (targetAge == PhotoInfo.AGE_STATE_MAX) {
          mAgePhotoInfo = null;
          mAgeBitmap = null;
          return;
        }
        // Age the photo
        mAgePhotoInfo = mutableCopyOf(mAgePhotoInfo);
        mAgePhotoInfo.setTargetAgeState(targetAge);
        mAgePhotoInfo.freeze();

        PhotoAger ager = new PhotoAger(mAgePhotoInfo, mAgeBitmap);
        mAgeBitmap = ager.getAgedJPEG();
        mAgePhotoInfo = ager.getAgedInfo();

        Bitmap bitmap = BitmapFactory.decodeByteArray(mAgeBitmap, 0, mAgeBitmap.length);
        // This should perhaps be done in the photo file thread...
        PhotoManipulator m = new PhotoManipulator(mPhotoFile, mAgePhotoInfo, bitmap);
        mImageView.setImageBitmap(m.getManipulatedBitmap());
        break;
    }
  }

  private Bitmap constructBitmapFromJPEG(byte[] jpeg, int rotationToApply) {
    Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
    showFreeMemory(null, "constructBitmapFromJPEG");
    bitmap = BitmapTools.rotateBitmap(bitmap, rotationToApply);
    return bitmap;
  }

  // Observer interface (for PhotoFile)
  @Override
  public void update(Observable observable, Object data) {
    Object[] args = (Object[]) data;
    PhotoFile.Event event = (PhotoFile.Event) args[0];

    switch (event) {
      case BitmapConstructed: {
        PhotoInfo photoInfo = (PhotoInfo) args[1];
        Bitmap bitmap = (Bitmap) args[2];
        if (bitmap == null) {
          warning("no bitmap for " + photoInfo);
          return;
        }
        if (mBitmapLoadingPhotoInfo == null || photoInfo.getId() != mBitmapLoadingPhotoInfo.getId()) {
          warning("bitmap is stale:" + photoInfo);
          return;
        }
        mImageView.setImageBitmap(bitmap);
        if (DEMO == Demo.PhotoAger) {
          mAgeBitmap = BitmapTools.encodeJPEG(bitmap, 80);
        }
      }
      break;
      case PhotoCreated:
        if (DEMO == Demo.TakePhotos) {
          // Request a load of the photo's bitmap to display in the image view
          mBitmapLoadingPhotoInfo = (PhotoInfo) args[1];
          mPhotoFile.getBitmap(mBitmapLoadingPhotoInfo);
          break;
        }
        break;
    }
  }

  private MyCamera mCamera;
  private CameraPreview mPreview;
  private FrameLayout mCameraViewContainer;
  private ImageView mImageView;
  private PhotoFile mPhotoFile;
  private PhotoInfo mBitmapLoadingPhotoInfo;
  private PhotoInfo mAgePhotoInfo;
  private byte[] mAgeBitmap;
  private boolean mResumed;
}
