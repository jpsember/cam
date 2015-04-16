package com.js.camera;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;

import com.js.android.AppPreferences;
import com.js.android.UITools;
import com.js.camera.camera.R;

import static com.js.android.AndroidTools.doNothingAndroid;
import static com.js.basic.Tools.startApp;

public class AlbumActivity extends Activity {

  @Override
  public void onCreate(Bundle savedInstanceState) {
    startApp(this);
    AppPreferences.prepare(this);
    doNothingAndroid();

    super.onCreate(savedInstanceState);

    setContentView(buildContentView());
  }

  @Override
  protected void onResume() {
    super.onResume();
  }

  @Override
  protected void onPause() {
    super.onPause();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu items for use in the action bar
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.album_activity_actions, menu);
    return super.onCreateOptionsMenu(menu);
  }

  private View buildContentView() {

    View view = new View(this);
    view.setBackgroundColor(UITools.debugColor());
    return view;
  }

}
