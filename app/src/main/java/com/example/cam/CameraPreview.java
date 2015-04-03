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

  public CameraPreview(Context context) {
    super(context);
    addSurfaceView();
  }

  private void addSurfaceView() {
    SurfaceView surfaceView = new SurfaceView(this.getContext());
    this.addView(surfaceView);
    surfaceView.getHolder().addCallback(this);
  }

  public void setCamera(Camera camera) {
    mCamera = camera;
    if (mCamera == null)
      return;

    mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();
    requestLayout();

    Camera.Parameters params = mCamera.getParameters();

    List<String> focusModes = params.getSupportedFocusModes();
    if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
      params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
      mCamera.setParameters(params);
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    // We purposely disregard child measurements because act as a
    // wrapper to a SurfaceView that centers the mCamera preview instead
    // of stretching it.
    final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
    final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
    setMeasuredDimension(width, height);

    if (mSupportedPreviewSizes != null) {
      mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, width, height);
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
      if (mCamera != null) {
        mCamera.setPreviewDisplay(holder);
      }
    } catch (IOException exception) {
      die(exception);
    }
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
    if (mCamera != null) {
      Camera.Parameters parameters = mCamera.getParameters();
      parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
      requestLayout();

      mCamera.setParameters(parameters);
      mCamera.startPreview();
    }
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    // Surface will be destroyed when we return, so stop the preview.
    if (mCamera != null) {
      mCamera.stopPreview();
    }
  }

  private static String dump(Size size) {
    return new IPoint(size.width, size.height).toString();
  }

  private Size getOptimalPreviewSize(List<Size> sizes, int width, int height) {
    final double ASPECT_TOLERANCE = 0.1;
    double targetRatio = (double) width / height;

    if (sizes == null)
      return null;

    Size optimalSize = null;
    double minDiff = Double.MAX_VALUE;


    int targetHeight = height;

    // Try to find an size match aspect ratio and size
    for (Size size : sizes) {
      pr(" examining size " + dump(size));
      double ratio = (double) size.width / size.height;
      if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
      if (Math.abs(size.height - targetHeight) < minDiff) {
        optimalSize = size;
        minDiff = Math.abs(size.height - targetHeight);
      }
    }

    // Cannot find the one match the aspect ratio, ignore the requirement
    if (optimalSize == null) {
      minDiff = Double.MAX_VALUE;
      for (Size size : sizes) {
        if (Math.abs(size.height - targetHeight) < minDiff) {
          optimalSize = size;
          minDiff = Math.abs(size.height - targetHeight);
        }
      }
    }
    pr(" returning optimal size " + dump(optimalSize));
    return optimalSize;
  }

  private Size mPreviewSize;
  private List<Size> mSupportedPreviewSizes;
  private Camera mCamera;
}
