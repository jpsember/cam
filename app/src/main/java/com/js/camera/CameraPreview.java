package com.js.camera;

import java.io.IOException;
import java.util.List;

import android.content.Context;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import com.js.basic.IPoint;
import com.js.basic.MyMath;
import com.js.basic.Rect;

import static com.js.basic.Tools.*;

/**
 * Container view for the camera preview view, a SurfaceView
 */
public class CameraPreview extends ViewGroup implements SurfaceHolder.Callback, MyCamera.Listener {

  public CameraPreview(Context context, MyCamera camera) {
    super(context);
//    setTrace(true);
    mCamera = camera;
    camera.setListener(this);

    // Add a zero-height SurfaceView to avoid the flashing problem; see issue #7
    {
      View view = new SurfaceView(this.getContext());
      view.setLayoutParams(new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, 0));
      addView(view);
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    // We purposely disregard child measurements because act as a
    // wrapper to a SurfaceView that centers the mCamera preview instead
    // of stretching it.
    int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
    int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
    setMeasuredDimension(width, height);
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    trace("onLayout");
    if (mSurfaceView == null)
      return;
    mPreviewSize = calculatePreviewSize();
    Rect innerRect = new Rect(0, 0, mPreviewSize.x, mPreviewSize.y);
    Rect outerRect = new Rect(0, 0, right - left, bottom - top);
    innerRect.apply(MyMath.calcRectFitRectTransform(innerRect, outerRect));
    mSurfaceView.layout((int) innerRect.x, (int) innerRect.y,
        (int) innerRect.endX(), (int) innerRect.endY());
  }

  // ------------- MyCamera.Listener interface
  @Override
  public void cameraChanged(Camera camera) {
    trace("cameraChanged to " + nameOf(camera));
    if (camera != null) {
      if (mSurfaceView == null) {
        mSurfaceView = new SurfaceView(this.getContext());
        addView(mSurfaceView);
        mSurfaceView.getHolder().addCallback(this);
      }
      mCamera.startPreview();
    }
  }

  // ------------- SurfaceHolder.Callback interface

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    trace("surfaceCreated()");
    try {
      if (mCamera.isOpen())
        mCamera.camera().setPreviewDisplay(holder);
    } catch (IOException exception) {
      die(exception);
    }
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    trace("surfaceChanged() " + mCamera + " surface size " + new IPoint(width, height));
    if (!mCamera.isOpen())
      return;
    if (mPreviewSize == null)
      throw new IllegalStateException();
    mCamera.setPreviewSize(mPreviewSize);
    mCamera.startPreview();
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    trace("surfaceDestroyed(), camera.isOpen=" + d(mCamera.isOpen()));
    mCamera.stopPreview();
  }

  /**
   * Calculate the preview size, by choosing the candidate that best matches our view
   */
  private IPoint calculatePreviewSize() {
    IPoint viewSize = new IPoint(getWidth(), getHeight());
    trace("calculatePreviewSize, container size " + viewSize);
    if (viewSize.x == 0 || viewSize.y == 0)
      throw new IllegalStateException();

    List<IPoint> sizes = mCamera.getPreviewSizes();
    trace("sizes: " + d(sizes));
    IPoint optimalSize = null;

    // Choose the largest preview size that will fit within us, its container.
    // Do two passes.  On the first pass, omit any preview sizes that are larger
    // than the target in either dimension
    for (int pass = 0; pass < 2; pass++) {
      int minError = Integer.MAX_VALUE / 10;
      for (IPoint size : sizes) {
        int widthError = size.x - viewSize.x;
        int heightError = size.y - viewSize.y;
        if (pass == 0 && (widthError > 0 || heightError > 0))
          continue;

        int error = Math.min(Math.abs(heightError), Math.abs(widthError));
        if (error < minError) {
          optimalSize = size;
          minError = error;
        }
      }
      if (optimalSize != null)
        break;
    }

    if (optimalSize == null)
      throw new IllegalStateException();
    trace("surfaceView size: " + optimalSize);
    return optimalSize;
  }

  public void setTrace(boolean state) {
    mTrace = state;
    if (state)
      warning("Turning tracing on");
  }

  private void trace(Object msg) {
    if (mTrace)
      pr("-- CameraPreview --: " + msg);
  }

  private boolean mTrace;
  private MyCamera mCamera;
  // The SurfaceView that will display the camera preview
  private SurfaceView mSurfaceView;
  // The preview size, one of the candidate sizes provided by the camera.
  private IPoint mPreviewSize;
}
