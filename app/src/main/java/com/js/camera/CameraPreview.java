package com.js.camera;

import java.io.IOException;
import java.util.List;

import android.content.Context;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import com.js.basic.IPoint;
import com.js.basic.MyMath;
import com.js.basic.Rect;

import static com.js.basic.Tools.*;

public class CameraPreview extends ViewGroup implements SurfaceHolder.Callback {

  public CameraPreview(Context context, MyCamera camera) {
    super(context);
    ASSERT(camera != null);
    setTrace(true);
    mCamera = camera;
    addSurfaceView();
    mHolder.addCallback(this);
  }

  private void addSurfaceView() {
    SurfaceView surfaceView = new SurfaceView(this.getContext());
    mHolder = surfaceView.getHolder();
    addView(surfaceView);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    // We purposely disregard child measurements because act as a
    // wrapper to a SurfaceView that centers the mCamera preview instead
    // of stretching it.
    int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
    int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
    setMeasuredDimension(width, height);
    trace("onMeasure, setMeasuredDimension to " + width + " x " + height);

    // Don't calculate optimal size if either dimension is zero;
    // the layout is probably in an intermediate state due to a view
    // being added or removed
    if (mCamera.isOpen() && width > 0 && height > 0) {
      getOptimalPreviewSize(width, height);
    }
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    trace("onLayout, changed=" + d(changed));
    if (changed && getChildCount() > 0) {
      View child = getChildAt(0);

      int width = right - left;
      int height = bottom - top;

      int previewWidth = width;
      int previewHeight = height;
      if (mPreviewSize != null) {
        previewWidth = mPreviewSize.x;
        previewHeight = mPreviewSize.y;
      }

      Rect innerRect = new Rect(0, 0, previewWidth, previewHeight);
      Rect outerRect = new Rect(0, 0, width, height);

      innerRect.apply(MyMath.calcRectFitRectTransform(innerRect, outerRect));
      child.layout((int) innerRect.x, (int) innerRect.y,
          (int) innerRect.endX(), (int) innerRect.endY());
    }
  }

  // ------------- SurfaceHolder.Callback interface

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    trace("surfaceCreated()");
    // The Surface has been created, acquire the mCamera and tell it where
    // to draw.
    try {
      unimp("we need to be able to make this call when the camera finally becomes open");
      if (mCamera.isOpen())
        mCamera.camera().setPreviewDisplay(holder);
    } catch (IOException exception) {
      die(exception);
    }
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
    trace("surfaceChanged(), camera.isOpen=" + d(mCamera.isOpen()) + " mPreviewSize=" + mPreviewSize);
    if (!mCamera.isOpen())
      return;
    mCamera.setPreviewSize(mPreviewSize);
    requestLayout();

    // Important: Call startPreview() to start updating the preview surface.
    // Preview must be started before you can take a picture.
    mCamera.startPreview();
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    trace("surfaceDestroyed(), camera.isOpen=" + d(mCamera.isOpen()));
    // Surface will be destroyed when we return, so stop the preview.
    mCamera.stopPreview();
  }

  private static float aspectRatio(int width, int height) {
    return width / (float) height;
  }

  private void getOptimalPreviewSize(int targetWidth, int targetHeight) {
    trace("getOptimalPreviewSize, target " + new IPoint(targetWidth, targetHeight));
    if (targetWidth == 0 || targetHeight == 0)
      throw new IllegalArgumentException();

    List<IPoint> sizes = mCamera.getPreviewSizes();
    trace("sizes: " + d(sizes));
    IPoint optimalSize = null;

    // Choose the largest preview size that will fit within our available view.
    // Do two passes.  On the first pass, omit any preview sizes that are larger
    // than the target in either dimension
    for (int pass = 0; pass < 2; pass++) {
      int minError = Integer.MAX_VALUE / 10;
      for (IPoint size : sizes) {
        int widthError = size.x - targetWidth;
        int heightError = size.y - targetHeight;
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
    mPreviewSize = optimalSize;
    trace("set preview size to " + mPreviewSize);
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
  private IPoint mPreviewSize;
  private MyCamera mCamera;
  private SurfaceHolder mHolder;
}
