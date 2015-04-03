package com.js.camera;

import android.app.Activity;
import android.hardware.Camera;
import android.view.Surface;

import static com.js.basic.Tools.*;
import static com.js.android.AndroidTools.*;

/**
 * Encapsulates the camera object, and any additional data; also handles initializing it
 * asynchronously
 */
public class MyCamera {

  public void setActivity(Activity activity) {
    mActivity = activity;
    doNothing();
    doNothingAndroid();
  }

  public void open() {
    trace("open()");

    // Prefer a front-facing camera; if not found, use first (if there is one)
    int preferredCameraId = -1;

    Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
    for (int cameraId = 0; cameraId < Camera.getNumberOfCameras(); cameraId++) {
      Camera.getCameraInfo(cameraId, cameraInfo);
      if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
        preferredCameraId = cameraId;
        break;
      }
      if (preferredCameraId < 0)
        preferredCameraId = cameraId;
    }

    if (preferredCameraId >= 0) {
      try {
        mCameraId = preferredCameraId;
        trace("attempting to Camera.open(" + preferredCameraId + ")");
        mCamera = Camera.open(preferredCameraId);

        // The display is rotated correctly, but the aspect ratio is squashed now
        setCameraDisplayOrientation();

      } catch (RuntimeException e) {
        warning("Failed to open camera #" + preferredCameraId + ":\n" + e);
      }
    }
  }

  public boolean isOpen() {
    return mCamera != null;
  }

  public void startPreview() {
    trace("startPreview(); " + this);
    if (!isOpen())
      return;
    mCamera.startPreview();
  }

  public void stopPreview() {
    trace("stopPreview(); " + this);
    if (!isOpen())
      return;
    mCamera.stopPreview();
  }

  public void close() {
    trace("close()");
    if (!isOpen())
      return;
    mCamera.stopPreview();
    mCamera.release();
    mCamera = null;
  }

  public Activity activity() {
    if (mActivity == null)
      throw new IllegalStateException("No activity specified");
    return mActivity;
  }

  public Camera camera() {
    if (!isOpen())
      throw new IllegalStateException("Camera not open");
    return mCamera;
  }

  private void setCameraDisplayOrientation() {
    trace("setCameraDisplayOrientation()");
    if (!isOpen())
      return;
    Camera.CameraInfo info =
        new Camera.CameraInfo();
    Camera.getCameraInfo(mCameraId, info);
    int rotation = activity().getWindowManager().getDefaultDisplay()
        .getRotation();
    int degrees = 0;
    switch (rotation) {
      case Surface.ROTATION_0:
        degrees = 0;
        break;
      case Surface.ROTATION_90:
        degrees = 90;
        break;
      case Surface.ROTATION_180:
        degrees = 180;
        break;
      case Surface.ROTATION_270:
        degrees = 270;
        break;
    }

    int result;
    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
      result = (info.orientation + degrees) % 360;
      result = (360 - result) % 360;  // compensate the mirror
    } else {  // back-facing
      result = (info.orientation - degrees + 360) % 360;
    }
    trace("setCameraDisplayOrientation to " + result);
    mCamera.setDisplayOrientation(result);
  }

  public void setTrace(boolean state) {
    mTrace = state;
    if (state)
      warning("Turning tracing on");
  }

  private void trace(Object msg) {
    if (mTrace)
      pr("--      MyCamera --: " + msg);
  }

  @Override
  public String toString() {
    return "MyCamera, open=" + d(isOpen()) + " id=" + d(mCameraId);
  }

  private Camera mCamera;
  private int mCameraId;
  private Activity mActivity;
  private boolean mTrace;
}
