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
    trace("onMeasure()");
    // We purposely disregard child measurements because act as a
    // wrapper to a SurfaceView that centers the mCamera preview instead
    // of stretching it.
    int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
    int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
    setMeasuredDimension(width, height);
    trace("setMeasuredDimension to " + width + " x " + height);

    if (mCamera.isOpen()) {
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
    c.startPreview();
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    trace("surfaceDestroyed(), camera.isOpen=" + d(mCamera.isOpen()));
    // Surface will be destroyed when we return, so stop the preview.
    if (mCamera.isOpen())
      mCamera.camera().stopPreview();
  }

  private static float aspectRatio(int width, int height) {
    return width / (float) height;
  }

  private void getOptimalPreviewSize(int width, int height) {
    Camera.Parameters parameters = mCamera.camera().getParameters();
    List<Size> sizes = parameters.getSupportedPreviewSizes();
    Size optimalSize = null;

    float targetRatio = aspectRatio(width, height);

    int minHeightError = Integer.MAX_VALUE / 10;

    int targetHeight = height;

    // I think the aim here was to find a preview size whose height is closest
    // to the view's height, omitting those whose aspect ratios are too different.

    // Perform two passes: on the first pass, omit candidates whose aspect ratios
    // are too different.

    for (int pass = 0; pass < 2; pass++) {
      for (Size size : sizes) {
        trace("pass " + pass + ", size " + d(size.width) + " x " + d(size.height));
        if (pass == 0) {
          float ratio = aspectRatio(size.width, size.height);
          float ASPECT_TOLERANCE = 0.1f;
          trace("  aspect ratio " + d(ratio) + " vs target " + d(targetRatio));
          if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
            continue;
        }
        int heightError = Math.abs(size.height - targetHeight);
        if (heightError < minHeightError) {
          optimalSize = size;
          minHeightError = heightError;
          trace("  setting optimal (height error " + heightError + ")");
        }
      }
      if (optimalSize != null)
        break;
    }

    if (optimalSize == null)
      throw new IllegalStateException();
    trace("set preview size");

    mPreviewSize = optimalSize;
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
