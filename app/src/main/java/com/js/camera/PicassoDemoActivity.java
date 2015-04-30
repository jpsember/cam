package com.js.camera;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.js.android.UITools;

import static com.js.android.AndroidTools.*;
import static com.js.basic.Tools.*;

public class PicassoDemoActivity extends Activity {

  @Override
  public void onCreate(Bundle savedInstanceState) {
    doNothing();
    doNothingAndroid();
    AppState.prepare(this);
    super.onCreate(savedInstanceState);

    setContentView(buildContentView());
  }

  private View buildContentView() {
    FrameLayout layout = new FrameLayout(this);

    mImageView = new ImageView(this);
    mImageView.setBackgroundColor(UITools.debugColor());
    layout.addView(mImageView);

    return layout;
  }

  private ImageView mImageView;

}
