package com.js.camera;

import android.app.Activity;
import android.hardware.Camera;
import android.os.AsyncTask;
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

  private static final boolean SIMULATED_DELAYS = true;

  public interface Listener {
    /**
     * Called when the Camera object has been assigned a new value (null if it's been closed,
     * non-null if it's been opened)
     */
    void cameraChanged(Camera camera);
  }

  public MyCamera() {
    mState = State.Start;
//    setTrace(true);
  }

  public void setListener(Listener listener) {
    mListener = listener;
  }

  private enum State {
    Start, Opening, Open, Closed, Failed
  }

  public void setActivity(Activity activity) {
    mActivity = activity;
    doNothing();
    doNothingAndroid();
  }

  public void open() {
    if (mState != State.Start && mState != State.Closed)
      throw new IllegalStateException();

    trace("open()");

    // Find preferred facing; if not found, use first camera
    int PREFERRED_FACING = Camera.CameraInfo.CAMERA_FACING_BACK;

    int preferredCameraId = -1;

    Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
    for (int cameraId = 0; cameraId < Camera.getNumberOfCameras(); cameraId++) {
      Camera.getCameraInfo(cameraId, cameraInfo);
      if (cameraInfo.facing == PREFERRED_FACING) {
        preferredCameraId = cameraId;
        break;
      }
      if (preferredCameraId < 0)
        preferredCameraId = cameraId;
    }
    if (preferredCameraId < 0) {
      setFailed("No cameras found");
      return;
    }

    mCameraId = preferredCameraId;
    setState(State.Opening);
    new OpenCameraTask().execute();
  }


  private void setState(State state) {
    if (mState != state) {
      trace("Changing state from " + mState + " to " + state);
      mState = state;
    }
  }

  public boolean isOpen() {
    return mState == State.Open;
  }

  /**
   * Set the preview started state; ignored if camera isn't open
   */
  public void setPreviewStarted(boolean state) {
    trace("setPreviewStarted(" + state + "); " + this + "\n" + stackTrace(0, 10));
    if (!state) {
      stopPreview();
    } else {
      startPreview();
    }
  }

  /**
   * Start the preview, if it is not already; ignored if camera isn't open
   */
  public void startPreview() {
    trace("setPreviewStarted(); " + this + " started=" + d(mPreviewStarted));
    if (mPreviewStarted)
      return;
    if (!isOpen())
      return;
    mCamera.startPreview();
    mPreviewStarted = true;
  }

  public boolean stopPreview() {
    trace("stopPreview(); " + this);
    boolean wasStarted = mPreviewStarted;
    if (mPreviewStarted) {
      mCamera.stopPreview();
      mPreviewStarted = false;
    }
    return wasStarted;
  }

  public void close() {
    trace("close()");
    if (!isOpen())
      return;
    stopPreview();
    mCamera.release();
    setState(State.Closed);
    setCamera(null);
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

  /**
   * Get the underlying Camera object; must be open
   */
  public Camera camera() {
    assertOpen();
    return mCamera;
  }

  private int determineDisplayOrientation() {
    trace("determineCameraDisplayOrientation()");
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

  public void setPreviewSize(IPoint size) {
    assertOpen();
    Camera.Parameters parameters = mCamera.getParameters();
    parameters.setPreviewSize(size.x, size.y);
    setParameters(parameters);
  }

  private void setParameters(Camera.Parameters parameters) {
    trace("setParameters");
    // Issue #9: avoid changing parameters while preview is active
    boolean previewState = stopPreview();
    try {
      mCamera.setParameters(parameters);
    } catch (RuntimeException e) {
      warning("Failed setting parameters: " + d(e));
    }
    setPreviewStarted(previewState);
  }

  @Override
  public String toString() {
    String s = "MyCamera";
    s += " state " + mState;
    if (mState == State.Failed)
      s += " (cause:" + mFailureMessage + ")";
    return s;
  }

  private void setFailed(String message) {
    if (mState == State.Failed)
      return;
    setState(State.Failed);
    mFailureMessage = message;
    trace("Failed with message " + message);
  }

  /**
   * AsyncTask to open the camera, which according to the documentation
   * is potentially time consuming
   */
  private class OpenCameraTask extends AsyncTask<Void, Void, Camera> {

    protected Camera doInBackground(Void... params) {
      if (SIMULATED_DELAYS)
        sleepFor(1200);

      int preferredCameraId = mCameraId;
      Camera camera = null;
      try {
        camera = Camera.open(preferredCameraId);
      } catch (RuntimeException e) {
        warning("Failed to open camera: " + d(e));
      }

      if (SIMULATED_DELAYS)
        sleepFor(1200);

      return camera;
    }

    protected void onPostExecute(Camera camera) {
      if (camera == null) {
        setFailed("Opening camera");
        return;
      }

      setState(State.Open);
      camera.setDisplayOrientation(determineDisplayOrientation());

      // Set the camera, and give listener an opportunity to e.g. start preview
      setCamera(camera);
    }
  }

  private void setCamera(Camera camera) {
    if (mCamera != camera) {
      mCamera = camera;
      if (mListener != null)
        mListener.cameraChanged(mCamera);
    }
  }

  private Camera mCamera;
  private int mCameraId;
  private Activity mActivity;
  private boolean mTrace;
  private State mState;
  private String mFailureMessage;
  private Listener mListener;
  private boolean mPreviewStarted;
}
