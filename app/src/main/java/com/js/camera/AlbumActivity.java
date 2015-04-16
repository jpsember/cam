package com.js.camera;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
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

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle presses on the action bar items
    switch (item.getItemId()) {
      case R.id.action_camera: {
        Intent intent = new Intent(this, MainActivity.class);
        startActivityForResult(intent, 0);
      }
      return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  private View buildContentView() {

    View view = new View(this);
    view.setBackgroundColor(UITools.debugColor());
    return view;
  }

}
