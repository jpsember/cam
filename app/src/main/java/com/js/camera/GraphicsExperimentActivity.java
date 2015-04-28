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


}
