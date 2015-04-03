package com.example.cam;

import java.io.IOException;
import java.util.List;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import com.js.basic.IPoint;

import static com.js.basic.Tools.*;

public class CameraPreview extends ViewGroup implements SurfaceHolder.Callback {

  /**
   * TODO: image seems squashed in some orientations
   */

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

  public void setCamera() {
    trace("setCamera(), currently " + mCamera);
    if (!mCamera.isOpen())
      return;
    mCamera.close();

    requestLayout();

    try {
      mCamera.setCameraDisplayOrientation();
      mCamera.camera().setPreviewDisplay(mHolder);
    } catch (IOException e) {
      e.printStackTrace();
    }

    mCamera.camera().startPreview();
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
      final View child = getChildAt(0);

      final int width = right - left;
      final int height = bottom - top;

      int previewWidth = width;
      int previewHeight = height;
      if (mPreviewSize != null) {
        previewWidth = mPreviewSize.width;
        previewHeight = mPreviewSize.height;
      }

      // Center the child SurfaceView within the parent.
      if (width * previewHeight > height * previewWidth) {
        final int scaledChildWidth = previewWidth * height / previewHeight;
        child.layout((width - scaledChildWidth) / 2, 0,
            (width + scaledChildWidth) / 2, height);
      } else {
        final int scaledChildHeight = previewHeight * width / previewWidth;
        child.layout(0, (height - scaledChildHeight) / 2,
            width, (height + scaledChildHeight) / 2);
      }
    }
  }

  // ------------- SurfaceHolder.Callback interface

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    trace("surfaceCreated()");
    // The Surface has been created, acquire the mCamera and tell it where
    // to draw.
    try {
      if (mCamera.isOpen())
        mCamera.camera().setPreviewDisplay(holder);
    } catch (IOException exception) {
      die(exception);
    }
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
    trace("surfaceChanged(), camera.isOpen=" + d(mCamera.isOpen()) + " mPreviewSize=" + d(mPreviewSize));
    if (!mCamera.isOpen())
      return;

    ASSERT(mPreviewSize != null);

    Camera c = mCamera.camera();

    Camera.Parameters parameters = c.getParameters();
    parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
    requestLayout();
    c.setParameters(parameters);

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

    Camera.Parameters parameters = mCamera.camera().getParameters();
    List<Size> sizes = parameters.getSupportedPreviewSizes();
    Size optimalSize = null;

    // Choose the largest preview size that will fit within our available view.
    // Do two passes.  On the first pass, omit any preview sizes that are larger
    // than the target in either dimension
    for (int pass = 0; pass < 2; pass++) {
      int minError = Integer.MAX_VALUE / 10;
      for (Size size : sizes) {
        int widthError = size.width - targetWidth;
        int heightError = size.height - targetHeight;
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
    trace("set preview size to " + new IPoint(mPreviewSize.width, mPreviewSize.height));
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
  private Size mPreviewSize;
  private MyCamera mCamera;
  private SurfaceHolder mHolder;

}
