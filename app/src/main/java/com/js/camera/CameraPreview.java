package com.js.camera;

import java.io.IOException;
import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
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
    setGlassColor(TRANSPARENT_COLOR);
    mCamera = camera;
    camera.setListener(this);

    // Add a zero-height SurfaceView to avoid the flashing problem; see issue #7
    {
      View view = new SurfaceView(this.getContext());
      view.setLayoutParams(new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, 0));
      addView(view);
    }
  }

  /**
   * Verify that preview is still in initialization stage, and has not yet been
   * displayed
   */
  private void assertInitializing() {
    if (mSurfaceView != null)
      throw new IllegalStateException();
  }

  @Override
  public void setBackgroundColor(int color) {
    assertInitializing();
    super.setBackgroundColor(color);
    // Save the background color so it's available to the overlay views
    mBackgroundColor = color;
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

    layoutOverlayViews(innerRect);
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
        // Construct overlay views last, since they should appear in front
        constructOverlayViews();
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

  private void constructOverlayViews() {
    mOverlayView = new OverlayView(this);
    addView(mOverlayView);
  }

  private void layoutOverlayViews(Rect r) {
    mOverlayView.layout((int) r.x, (int) r.y,
        (int) r.endX(), (int) r.endY());
  }

  /**
   * View subclass for rendering things on top of the camera preview SurfaceView
   */
  private static class OverlayView extends View {

    public OverlayView(CameraPreview container) {
      super(container.getContext());
      mContainer = container;
    }

    @Override
    protected void onDraw(Canvas canvas) {
      prepareGraphicElements(canvas);
      Rect r = sRect;
      r.setTo(0, 0, getWidth(), getHeight());

      // Fill the exterior of the rectangle with the background color,
      // then use PorterDuff CLEAR mode to fill the interior of the (rounded)
      // rectangle with transparent color
      sCanvas.drawRect(r.x, r.y, r.width, r.height, sFillBgndPaint);
      fillRoundedRect(r, sTransparentPaint);

      // Don't retain reference to canvas
      prepareGraphicElements(null);
    }

    /**
     * Prepare the graphics elements, some of which are lazy-initialized.
     * We are encouraged not to construct objects during rendering
     */
    private void prepareGraphicElements(Canvas canvas) {
      sCanvas = canvas;

      if (sRect == null) {
        sRect = new Rect();
        sPath = new Path();
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        sFillBgndPaint = paint;

        paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(mContainer.mOverlayGlassColor);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        sTransparentPaint = paint;
      }
      sFillBgndPaint.setColor(mContainer.mBackgroundColor);
    }

    private static final float CORNER_RADIUS = 30.0f;

    /**
     * Paint the interior of a rounded rectangle
     */
    private static void fillRoundedRect(Rect rect,
                                        Paint paint) {
      float radius = CORNER_RADIUS;
      Path path = sPath;
      path.reset();
      path.moveTo(rect.x + radius, rect.y);
      path.lineTo(rect.endX() - radius, rect.y);
      path.quadTo(rect.endX(), rect.y, rect.endX(), rect.y + radius);
      path.lineTo(rect.endX(), rect.endY() - radius);
      path.quadTo(rect.endX(), rect.endY(), rect.endX() - radius, rect.endY());
      path.lineTo(rect.x + radius, rect.endY());
      path.quadTo(rect.x, rect.endY(), rect.x, rect.endY() - radius);
      path.lineTo(rect.x, rect.y + radius);
      path.quadTo(rect.x, rect.y, rect.x + radius, rect.y);
      sCanvas.drawPath(path, paint);
    }

    // Pre-prepared fields for performing onDraw()
    private static Canvas sCanvas;
    private static Rect sRect;
    private static Path sPath;
    private static Paint sFillBgndPaint;
    private static Paint sTransparentPaint;
    private CameraPreview mContainer;
  }

  public void setGlassColor(int color) {
    assertInitializing();
    mOverlayGlassColor = color;
  }

  private static final int TRANSPARENT_COLOR = 0x00ffffff;

  private boolean mTrace;
  private MyCamera mCamera;
  // The SurfaceView that will display the camera preview
  private SurfaceView mSurfaceView;
  // Overlaid view to appear above the surface view
  private View mOverlayView;
  // The preview size, one of the candidate sizes provided by the camera.
  private IPoint mPreviewSize;
  private int mBackgroundColor;
  private int mOverlayGlassColor;
}
