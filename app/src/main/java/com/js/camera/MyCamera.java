package com.js.camera;

import android.app.Activity;
import android.hardware.Camera;
import android.view.Surface;

import com.js.basic.IPoint;

import java.util.ArrayList;
import java.util.List;

import static com.js.basic.Tools.*;
import static com.js.android.AndroidTools.*;

import android.hardware.Camera.Size;

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
        mCamera.setDisplayOrientation(determineDisplayOrientation());
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

  public void assertOpen() {
    if (!isOpen())
      throw new IllegalStateException("Camera not open");
  }

  public Camera camera() {
    assertOpen();
    return mCamera;
  }

  private int determineDisplayOrientation() {
    trace("determineCameraDisplayOrientation()");
    assertOpen();
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
    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
      result = -(info.orientation + degrees);
    else
      result = info.orientation - degrees;
    result = myMod(result, 360);
    return result;
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

  /**
   * Get list of preview sizes supported for camera, as IPoint objects
   */
  public List<IPoint> getPreviewSizes() {
    assertOpen();
    Camera.Parameters parameters = mCamera.getParameters();
    List<Size> sizes = parameters.getSupportedPreviewSizes();
    List<IPoint> output = new ArrayList();
    for (Size size : sizes) {
      output.add(new IPoint(size.width, size.height));
    }
    return output;
  }

  public void setPreviewSize(IPoint mPreviewSize) {
    assertOpen();
    Camera.Parameters parameters = mCamera.getParameters();
    parameters.setPreviewSize(mPreviewSize.x, mPreviewSize.y);
    mCamera.setParameters(parameters);
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
