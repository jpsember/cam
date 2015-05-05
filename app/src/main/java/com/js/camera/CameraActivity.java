package com.js.camera;

import java.util.Observable;
import java.util.Observer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.js.android.UITools;
import com.js.basic.IPoint;

import static com.js.basic.Tools.*;
import static com.js.android.AndroidTools.*;

public class CameraActivity extends Activity implements OnClickListener, Observer {

  public static Intent buildIntent(Context context) {
    Intent intent = new Intent(context, CameraActivity.class);
    return intent;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    doNothing();
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
    super.onResume();
    resumeCamera();
    resumePhotoFile();
  }

  @Override
  protected void onPause() {
    pausePhotoFile();
    pauseCamera();
    super.onPause();
  }

  // Observer interface (for PhotoFile)
  @Override
  public void update(Observable observable, Object data) {
    Object[] args = (Object[]) data;
    PhotoFile.Event event = (PhotoFile.Event) args[0];

    switch (event) {
      case PhotoCreated:
        // Leave activity now that photo was taken (and saved)
        this.finish();
        break;
    }
  }

  private MyCamera mCamera;
  private CameraPreview mPreview;
  private FrameLayout mCameraViewContainer;
  private PhotoFile mPhotoFile;

}
