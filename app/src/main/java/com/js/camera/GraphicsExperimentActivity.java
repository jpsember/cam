package com.js.camera;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.js.android.UITools;
import com.js.basic.IPoint;
import com.js.basic.MyMath;

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

      int gridCellSize = 20;
      IPoint gridSize = new IPoint((int) (mBitmap.getWidth() / (float) gridCellSize),
          (int) (mBitmap.getHeight() / (float) gridCellSize));
      PerlinNoise noise = new PerlinNoise(gridSize);

      int gridWidthPixels = gridSize.x * gridCellSize;
      int gridHeightPixels = gridSize.y * gridCellSize;

      final int numBands = 12;
      int prevBand = -1;
      for (int py = 0; py < gridHeightPixels; py++) {
        int band = Math.min(numBands - 1, py / (gridHeightPixels / numBands));
        if (band != prevBand) {
          prevBand = band;
          noise.setSeed(2 + band);
//          noise.setTileSize(3,3);
          noise.setMaxGradients(2 + (band / 3) * (band / 4));
          noise.buildGrid();
          continue;
        }

        float gy = py / (float) gridCellSize;
        for (int px = 0; px < gridWidthPixels; px++) {
          float gx = px / (float) gridCellSize;

          float value = noise.noiseAt(gx, gy);
          value = (value + 1) / 2;
          int gray = (int) (value * 255.99f);
          int color = Color.argb(0xff, gray, gray, gray);
          mBitmap.setPixel(px, py, color);
        }
      }
    }

    private void constructCanvas() {
      IPoint targetSize = PhotoInfo.getLogicalMaximumSize(UITools.configuration().orientation == Configuration.ORIENTATION_PORTRAIT);
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

    public static enum Interpolation {
      LINEAR,
      CUBIC,
      QUINTIC,
    }

    public PerlinNoise(IPoint gridSize) {
      mGridSize = gridSize;
    }

    public void setTileSize(int x, int y) {
      mTileSize = new IPoint(x, y);
    }

    public void setMaxGradients(int maxGradients) {
      mMaxGradients = maxGradients;
    }

    private static int hash(int x) {
      x = ((x >>> 16) ^ x) * 0x45d9f3b;
      x = ((x >>> 16) ^ x) * 0x45d9f3b;
      x = ((x >>> 16) ^ x);
      return x;
    }

    public void buildGrid() {
      buildGradients();
    }

    public void setInterpolation(Interpolation interpolation) {
      mInterpolation = interpolation;
    }

    private int getGradientIndex(int xGrid, int yGrid) {
      if (mTileSize != null) {
        xGrid = xGrid % mTileSize.x;
        yGrid = yGrid % mTileSize.y;
      }
      int i = (yGrid * (mGridSize.x + 1) + xGrid);
      if (mMaxGradients == 0)
        return i;
      // Pick a pseudorandom integer [0..max) using vertex as seed
      return myMod(hash(i), mMaxGradients);
    }

    private float dotGridGradient(float xGrid, float yGrid, float xQuery, float yQuery) {
      int gradientIndex = 2 * getGradientIndex((int) xGrid, (int) yGrid);
      float dx = xQuery - xGrid;
      float dy = yQuery - yGrid;
      float xGrad = mGradients[gradientIndex + 0];
      float yGrad = mGradients[gradientIndex + 1];
      return (xGrad * dx) + (yGrad * dy);
    }

    private float lerp(float a0, float a1, float w) {
      switch (mInterpolation) {
        case LINEAR:
          break;
        case CUBIC: {
          float w2 = w * w;
          float w3 = w2 * w;
          w = -2 * w3 + 3 * w2;
        }
        break;
        case QUINTIC: {
          float w2 = w * w;
          float w3 = w2 * w;
          float w4 = w3 * w;
          float w5 = w4 * w;
          w = 6 * w5 - 15 * w4 + 10 * w3;
        }
        break;
      }
      return (1.0f - w) * a0 + w * a1;
    }

    /**
     * Evaluate noise value at a pixel, where integer portion of coordinate
     * represents grid cell index.
     *
     * @return value within [-1..1)
     */
    public float noiseAt(float x, float y) {
      int cellX = (int) x;
      int cellY = (int) y;
      if (cellX < 0 || cellX >= mGridSize.x || cellY < 0 || cellY >= mGridSize.y) {
        throw new IllegalArgumentException();
      }
      float gridX = cellX;
      float gridY = cellY;

      float sx = x - gridX;
      float sy = y - gridY;
      if (sx < 0 || sx > 1 || sy < 0 || sy > 1) throw new IllegalArgumentException();

      float d00 = dotGridGradient(gridX + 0, gridY, x, y);
      float d10 = dotGridGradient(gridX + 1, gridY, x, y);
      float aValue = lerp(d00, d10, sx);

      gridY += 1;
      float d01 = dotGridGradient(gridX + 0, gridY, x, y);
      float d11 = dotGridGradient(gridX + 1, gridY, x, y);
      float bValue = lerp(d01, d11, sx);

      float value = lerp(aValue, bValue, sy);
      if (value < -1 || value >= 1) throw new IllegalArgumentException();
      return value;
    }

    private void buildGradients() {
      Random r = new Random(mSeed);

      // There is a grid point at each cell corner, thus
      // we must add 1 to each dimension
      int numGradients = mMaxGradients;
      if (numGradients == 0)
        numGradients = (mGridSize.x + 1) * (mGridSize.y + 1);
      mGradients = new float[2 * numGradients];

      int cursor = 0;
      for (int g = 0; g < numGradients; g++) {
        float angle = r.nextFloat() * MyMath.PI * 2;
        float cx = (float) Math.cos(angle);
        float cy = (float) Math.sin(angle);
        mGradients[cursor + 0] = cx;
        mGradients[cursor + 1] = cy;
        cursor += 2;
      }
    }

    public void setSeed(int seed) {
      mSeed = seed;
    }

    private IPoint mGridSize;
    private float[] mGradients;
    private Interpolation mInterpolation = Interpolation.CUBIC;
    private IPoint mTileSize;
    private int mMaxGradients;
    private int mSeed = 1;
  }

}
