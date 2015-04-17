package com.js.camera;

import android.app.Activity;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Surface;

import com.js.basic.Freezable;
import com.js.basic.IPoint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.js.basic.Tools.*;
import static com.js.android.AndroidTools.*;

import android.hardware.Camera.Size;
import android.view.SurfaceHolder;

/**
 * Encapsulates the camera object, and any additional data; also handles initializing it
 * asynchronously
 */
public class MyCamera {

  private static final boolean SIMULATED_DELAYS = true;

  public interface Listener {
    /**
     * Called when the MyCamera state has changed
     */
    void stateChanged();

    /**
     * Called when a picture taken via takePicture() is available
     */
    void pictureTaken(byte[] jpeg, int rotationToApply);
  }

  public MyCamera(Activity activity, Listener listener) {
    mState = State.Start;
    mListener = listener;
//    setTrace(true);
    mDeviceRotation = activity.getWindowManager().getDefaultDisplay()
        .getRotation();
    doNothing();
    doNothingAndroid();
  }

  private enum State {
    Start, Opening, Open, Closed, Failed
  }

  private void openBackgroundHandler() {
    mUIThreadHandler = new Handler(Looper.getMainLooper());
    HandlerThread backgroundThreadHandler = new HandlerThread("MyCamera background thread");
    backgroundThreadHandler.start();
    mBackgroundThreadHandler = new Handler(backgroundThreadHandler.getLooper());
  }

  public void open() {
    assertUIThread();
    if (mState != State.Start)
      throw new IllegalStateException();

    trace("open()");

    openBackgroundHandler();

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

    mBackgroundThreadHandler.post(new Runnable() {
      public void run() {
        mCamera = backgroundThreadOpenCamera();
        mUIThreadHandler.post(new Runnable() {
          public void run() {
            processCameraReceivedFromBackgroundThread();
          }
        });
      }
    });
  }

  private void setState(State state) {
    if (mState != state) {
      trace("Changing state from " + mState + " to " + state);
      mState = state;
      mListener.stateChanged();
    }
  }

  public boolean isOpen() {
    return mState == State.Open;
  }

  /**
   * Set the preview started state; ignored if camera isn't open
   */
  public void setPreviewStarted(boolean state) {
    assertUIThread();
    trace("setPreviewStarted(" + state + "); " + this);
    if (!state) {
      stopPreview();
    } else {
      startPreview();
    }
  }

  public boolean isPreviewStarted() {
    assertOpen();
    return mPreviewStarted;
  }

  /**
   * Start the preview, if it is not already; ignored if camera isn't open
   */
  public void startPreview() {
    assertUIThread();
    trace("setPreviewStarted(); " + this + " started=" + d(mPreviewStarted));
    if (mPreviewStarted)
      return;
    if (!isOpen())
      return;
    mCamera.startPreview();
    mPreviewStarted = true;
    if (mPreviewCallback != null)
      mCamera.setPreviewCallback(mPreviewCallback);
  }

  public boolean stopPreview() {
    assertUIThread();
    trace("stopPreview(); " + this);
    boolean wasStarted = mPreviewStarted;
    if (mPreviewStarted) {
      mCamera.stopPreview();
      mPreviewStarted = false;
    }
    return wasStarted;
  }

  public void close() {
    assertUIThread();
    trace("close()");

    // State may be Opening, not just Open
    if (isOpen())
      stopPreview();

    if (mCamera != null) {
      mCamera.release();
    }
    setState(State.Closed);
  }

  public void assertOpen() {
    if (!isOpen())
      throw new IllegalStateException("Camera not open");
  }

  /**
   * Get the underlying Camera object; must be open, and only UI thread
   */
  private Camera camera() {
    assertUIThread();
    assertOpen();
    return mCamera;
  }

  public void setPreviewDisplay(SurfaceHolder holder) {
    try {
      camera().setPreviewDisplay(holder);
    } catch (IOException e) {
      die(e);
    }
  }

  /**
   * Install a PreviewCallback
   */
  public void setPreviewCallback(Camera.PreviewCallback callback) {
    if (callback == null)
      throw new IllegalArgumentException();
    mPreviewCallback = callback;
    if (isOpen())
      mCamera.setPreviewCallback(mPreviewCallback);
  }

  public void takePicture() {
    camera().takePicture(null, null, mTakePictureJPEGCallback);
  }

  private int determineDisplayOrientation(Camera.CameraInfo info) {
    int degrees = 0;
    switch (mDeviceRotation) {
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

  private Camera.PictureCallback mTakePictureJPEGCallback = new Camera.PictureCallback() {

    public void onPictureTaken(final byte[] data, Camera camera) {
      mUIThreadHandler.post(new Runnable() {
        public void run() {
          try {
            if (data != null)
              mListener.pictureTaken(data, mCorrectingRotation);
          } finally {
            // The Camera class stopped the preview to take the picture; so
            // restart it
            mPreviewStarted = false;
            startPreview();
          }
        }
      });
    }
  };

  public void setTrace(boolean state) {
    mTrace = state;
    if (state)
      warning("Turning tracing on");
  }

  private static boolean isUIThread() {
    return Thread.currentThread() == Looper.getMainLooper().getThread();
  }

  private void trace(Object msg) {
    if (mTrace) {
      String threadMessage = "";
      if (!isUIThread()) {
        threadMessage = "(" + nameOf(Thread.currentThread()) + ") ";
      }
      pr("--      MyCamera " + threadMessage + "--: " + msg);
    }
  }

  public void setPreviewSizeIndex(int sizeIndex) {
    assertOpen();
    Properties m = mutable(mProperties);
    IPoint size = m.setPreviewSizeIndex(sizeIndex);
    setProperties(m);
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

  private Camera backgroundThreadOpenCamera() {
    trace("backgroundThreadOpenCamera");
    if (SIMULATED_DELAYS)
      sleepFor(1200);

    int preferredCameraId = mCameraId;
    Camera camera = null;
    try {
      camera = Camera.open(preferredCameraId);
      Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
      Camera.getCameraInfo(preferredCameraId, cameraInfo);
      mCorrectingRotation = determineDisplayOrientation(cameraInfo);
    } catch (RuntimeException e) {
      warning("Failed to open camera: " + d(e));
    }

    if (SIMULATED_DELAYS)
      sleepFor(1200);
    return camera;
  }

  private void processCameraReceivedFromBackgroundThread() {
    trace("processCameraReceived " + nameOf(mCamera));
    if (mCamera == null) {
      setFailed("Opening camera");
      return;
    }
    // If state is unexpected, app may have shut down or something
    if (mState != State.Opening) {
      warning("Stale state: " + this);
      mCamera.release();
      return;
    }

    constructProperties(mCamera);
    mCamera.setDisplayOrientation(mProperties.rotation());
    setState(State.Open);
  }

  private void constructProperties(Camera camera) {
    Properties p = new Properties();
    p.mRotation = mCorrectingRotation;
    p.setPreviewSizes(camera.getParameters());
    p.mFormat = camera.getParameters().getPreviewFormat();
    setProperties(p);
  }

  private void assertUIThread() {
    if (isUIThread())
      return;
    throw new IllegalStateException("Attempt to call from non-UI thread " + nameOf(Thread.currentThread()));
  }

  private void setProperties(Properties p) {
    mProperties = frozen(p);
  }

  public Properties getProperties() {
    if (mProperties == null)
      throw new IllegalStateException();
    return mProperties;
  }

  // The camera is constructed by the background thread, and once
  // assigned, is never changed
  private Camera mCamera;

  private int mCameraId;
  // Rotation to be applied to captured images to agree with device rotation
  private int mCorrectingRotation;
  // Rotation of device
  private int mDeviceRotation;
  private boolean mTrace;
  private State mState;
  private String mFailureMessage;
  private Listener mListener;
  private boolean mPreviewStarted;
  private Camera.PreviewCallback mPreviewCallback;
  private Handler mUIThreadHandler;
  private Handler mBackgroundThreadHandler;
  private Properties mProperties;

  /**
   * Object containing camera preview properties; immutable for thread safety.
   * These are derived from Camera.Parameters
   */
  public static class Properties extends Freezable.Mutable {

    @Override
    public Freezable getMutableCopy() {
      Properties p = new Properties();
      p.mFormat = mFormat;
      p.mRotation = mRotation;
      for (IPoint pt : mPreviewSizes)
        p.mPreviewSizes.add(new IPoint(pt));
      p.mPreviewSizeIndex = mPreviewSizeIndex;
      return p;
    }

    /**
     * Get selected preview size
     */
    public IPoint previewSize() {
      if (mPreviewSizeIndex < 0)
        throw new IllegalStateException("No preview size selected");
      return mPreviewSizes.get(mPreviewSizeIndex);
    }

    /**
     * Get value derived from Parameters.getPreviewFormat()
     */
    public int format() {
      return mFormat;
    }

    /**
     * Get the rotation, in degrees, that must be applied to the preview image
     * in order to display it correctly to the user
     */
    public int rotation() {
      return mRotation;
    }

    public List<IPoint> previewSizes() {
      assertFrozen();
      return mPreviewSizes;
    }

    void setPreviewSizes(Camera.Parameters parameters) {
      mutate();
      List<Size> sizes = parameters.getSupportedPreviewSizes();
      mPreviewSizes.clear();
      for (Size size : sizes) {
        mPreviewSizes.add(new IPoint(size.width, size.height));
      }
    }

    IPoint setPreviewSizeIndex(int sizeIndex) {
      mutate();
      mPreviewSizeIndex = sizeIndex;
      return mPreviewSizes.get(sizeIndex);
    }

    private List<IPoint> mPreviewSizes = new ArrayList<IPoint>();
    private int mPreviewSizeIndex = -1;
    private int mFormat;
    private int mRotation;

  }
}
