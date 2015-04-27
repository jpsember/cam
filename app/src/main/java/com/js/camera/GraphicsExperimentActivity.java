package com.js.camera;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.js.basic.IPoint;
import com.js.basic.MyMath;
import com.js.basic.Point;

import java.util.Random;

import static com.js.android.AndroidTools.*;
import static com.js.basic.Tools.*;

public class GraphicsExperimentActivity extends Activity {

  private enum ActivityState {
    Paused,
    Resumed,
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    doNothing();
    doNothingAndroid();
    AppState.prepare(this);
    super.onCreate(savedInstanceState);

    setContentView(buildContentView());
  }

  @Override
  protected void onResume() {
    super.onResume();
    setState(ActivityState.Resumed);
    new BuildImageTask().start();
  }

  @Override
  protected void onPause() {
    super.onPause();
    setState(ActivityState.Paused);
  }

  private View buildContentView() {
    FrameLayout layout = new FrameLayout(this);

    mImageView = new ImageView(this);
    layout.addView(mImageView);

    return layout;
  }

  private void setState(ActivityState state) {
    mState = state;
  }

  private class BuildImageTask extends TaskSequence {

    @Override
    protected void execute(int stageNumber) {
      if (mState != ActivityState.Resumed) {
        abort();
        return;
      }
      switch (stageNumber) {
        case 0:
          constructImage();
          break;
        case 1:
          mImageView.setImageBitmap(mBitmap);
          finish();
          break;
      }
    }

    private void constructImage() {
      constructCanvas();

      int gridCellSize = 64;
      IPoint gridSize = new IPoint((int) (mBitmap.getWidth() / (float) gridCellSize),
          (int) (mBitmap.getHeight() / (float) gridCellSize));
      PerlinNoise noise = new PerlinNoise(gridSize);

      noise.buildGrid();

      int gridWidthPixels = gridSize.x * gridCellSize;
      int gridHeightPixels = gridSize.y * gridCellSize;

      for (int py = 0; py < gridHeightPixels; py++) {
        float gy = py / (float) gridCellSize;
        for (int px = 0; px < gridWidthPixels; px++) {
          float gx = px / (float) gridCellSize;

          float value = noise.noiseAt(gx, gy);
          int gray = (int) (value * 255);
          int color = Color.argb(0xff, gray, gray, gray);
          mBitmap.setPixel(px, py, color);
        }
      }
    }

    private void constructCanvas() {
      IPoint targetSize = PhotoInfo.getLogicalMaximumSize(false);
      mBitmap = Bitmap.createBitmap(targetSize.x, targetSize.y, Bitmap.Config.ARGB_8888);
      mCanvas = new Canvas();
      mCanvas.setBitmap(mBitmap);
    }

    private Bitmap mBitmap;
    private Canvas mCanvas;
  }

  private ActivityState mState = ActivityState.Paused;
  private ImageView mImageView;

  private static class PerlinNoise {

    public PerlinNoise(IPoint gridSize) {
      mGridSize = gridSize;
    }

    public void buildGrid() {
      buildGradients();
    }

    /**
     * Evaluate noise value at a pixel, where integer portion of coordinate
     * represents grid cell index
     */
    public float noiseAt(float x, float y) {
      float v = x + y;
      v = (float) (v - Math.floor(v));
      return v;
    }

    private void buildGradients() {
      Random r = new Random(1965);
      int numGradients = (mGridSize.x + 1) * (mGridSize.y + 1);
      mGradients = new float[2 * numGradients];

      int cursor = 0;
      for (int y = 0; y <= mGridSize.y; y++) {
        for (int x = 0; x <= mGridSize.x; x++) {
          Point pt = MyMath.pointOnCircle(Point.ZERO, r.nextFloat() * MyMath.PI * 2, 1.0f);
          mGradients[cursor + 0] = pt.x;
          mGradients[cursor + 1] = pt.y;
          cursor += 2;
        }
      }
    }

    private IPoint mGridSize;
    private float[] mGradients;
  }
}
