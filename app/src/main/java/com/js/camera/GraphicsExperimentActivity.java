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

    public BuildImageTask() {
    }

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
      Paint paint = new Paint();
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(Color.GREEN);
      paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
      mCanvas.drawRect(50, 50, 200, 200, paint);
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
}
