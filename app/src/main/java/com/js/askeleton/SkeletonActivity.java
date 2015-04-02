package com.js.askeleton;

import static com.js.basic.Tools.*;

import com.js.android.AppPreferences;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

public class SkeletonActivity extends Activity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    startApp(this);
    AppPreferences.prepare(this);

    super.onCreate(savedInstanceState);
    View view = prepareView();
    setContentView(view);
  }


  private View prepareView() {
    View view = new View(this);
    {
      int colorIndex = AppPreferences.getUniqueIdentifier("color");
      int[] colors = {Color.GREEN, Color.BLUE, Color.MAGENTA, Color.DKGRAY};
      view.setBackgroundColor(colors[colorIndex % colors.length]);
    }
    return view;
  }
}
