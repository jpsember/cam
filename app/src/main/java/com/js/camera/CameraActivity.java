package com.js.camera;

import java.util.Observable;
import java.util.Observer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
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

  public static Intent buildIntent(Context context) {
    Intent intent = new Intent(context, CameraActivity.class);
    return intent;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    doNothingAndroid();
    AppState.prepare(this);
    mPhotoFile = AppState.photoFile(this);

    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

    setContentView(buildContentView());
  }

  private final static int BGND_COLOR = Color.DKGRAY;

  private View buildContentView() {

    // Construct a linear layout that contains the Camera view, and some other views as well
    final LinearLayout container = UITools.linearLayout(this, false);

    mCameraViewContainer = new FrameLayout(this);
    mCameraViewContainer.setBackgroundColor(BGND_COLOR);

    float weight = 0.8f;
    container.addView(buildImageView(), UITools.layoutParams(container, weight));
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
        IPoint pictureSize = mCamera.getProperties().getPictureSize();
        mPhotoFile.createPhoto(jpeg, pictureSize, rotationToApply);
        // When photo file has created new entry, it will call our observer
        // and we can then add it to the image view
      }
    });

    buildCameraView();
    mCameraViewContainer.addView(mPreview);
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
    mCamera.takePicture();
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
    return mImageView;
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
      }
      break;
      case PhotoCreated:
        // Request a load of the photo's bitmap to display in the image view
        mBitmapLoadingPhotoInfo = (PhotoInfo) args[1];
        mPhotoFile.loadBitmapIntoView(this, mBitmapLoadingPhotoInfo, 0, mImageView);
        break;
    }
  }

  private MyCamera mCamera;
  private CameraPreview mPreview;
  private FrameLayout mCameraViewContainer;
  private ImageView mImageView;
  private PhotoFile mPhotoFile;
  private PhotoInfo mBitmapLoadingPhotoInfo;
  private boolean mResumed;
}
