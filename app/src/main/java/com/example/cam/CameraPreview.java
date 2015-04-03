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

    if (mCamera.isOpen()) {
      getOptimalPreviewSize(width, height);
    }
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
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
    if (mCamera.isOpen()) {
      warning("is this necessary?");
      requestLayout();
      mCamera.camera().startPreview();
    }
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    // Surface will be destroyed when we return, so stop the preview.
    if (mCamera.isOpen())
      mCamera.camera().stopPreview();
  }

  private void getOptimalPreviewSize(int width, int height) {
    Camera.Parameters parameters = mCamera.camera().getParameters();
    List<Size> sizes = parameters.getSupportedPreviewSizes();

    float ASPECT_TOLERANCE = 0.1f;
    float targetRatio = width / (float) height;

    Size optimalSize = null;
    float minDiff = Float.MAX_VALUE;

    int targetHeight = height;

    // Try to find an size match aspect ratio and size
    for (Size size : sizes) {
      float ratio = size.width / (float) size.height;
      if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
      if (Math.abs(size.height - targetHeight) < minDiff) {
        optimalSize = size;
        minDiff = Math.abs(size.height - targetHeight);
      }
    }

    // Cannot find the one match the aspect ratio, ignore the requirement
    if (optimalSize == null) {
      minDiff = Float.MAX_VALUE;
      for (Size size : sizes) {
        if (Math.abs(size.height - targetHeight) < minDiff) {
          optimalSize = size;
          minDiff = Math.abs(size.height - targetHeight);
        }
      }
    }
    mPreviewSize = optimalSize;
  }

  private Size mPreviewSize;
  private MyCamera mCamera;
  private SurfaceHolder mHolder;

}
