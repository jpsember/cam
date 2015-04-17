package com.js.camera;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Toast;

import com.js.camera.camera.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import static com.js.basic.Tools.*;
import static com.js.android.AndroidTools.*;

public class AlbumActivity extends Activity implements Observer {

  @Override
  public void onCreate(Bundle savedInstanceState) {
    doNothingAndroid();
    AppState.prepare(this);
    setTrace(true);

    mPhotoFile = AppState.photoFile();

    super.onCreate(savedInstanceState);
    setContentView(buildContentView());

  }

  @Override
  protected void onResume() {
    trace("onResume");
    super.onResume();
    mPhotoFile.addObserver(this);
    rebuildAlbumIfPhotosAvailable();
  }

  private void rebuildAlbumIfPhotosAvailable() {
    if (!mPhotoFile.isOpen())
      return;
    mPhotos.clear();
    mPhotos.addAll(mPhotoFile.getPhotos(0, Integer.MAX_VALUE / 10));
    mAdapter.notifyDataSetChanged();
  }

  @Override
  protected void onPause() {
    trace("onPause");
    mPhotoFile.deleteObserver(this);
    mPhotos.clear();
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
        Intent intent = new Intent(this, CameraActivity.class);
        startActivityForResult(intent, 0);
      }
      return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  private View buildContentView() {
    return buildGridView();
  }

  public View buildGridView() {
    GridView v = new GridView(this);

    // Use density pixels here
    v.setColumnWidth(300);
    v.setNumColumns(GridView.AUTO_FIT);
    v.setVerticalSpacing(10);
    v.setHorizontalSpacing(10);
    v.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
    v.setGravity(Gravity.CENTER);

    mAdapter = new ImageAdapter(this);
    v.setAdapter(mAdapter);

    v.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      public void onItemClick(AdapterView<?> parent, View v,
                              int position, long id) {
        Toast.makeText(AlbumActivity.this, "" + position,
            Toast.LENGTH_SHORT).show();
      }
    });
    return v;
  }

  // Observer interface

  @Override
  public void update(Observable observable, Object data) {
    Object[] params = (Object[]) data;
    switch ((PhotoFile.Event) params[0]) {
      case StateChanged:
        rebuildAlbumIfPhotosAvailable();
        break;
    }
  }

  private class ImageAdapter extends BaseAdapter {
    private Context mContext;

    public ImageAdapter(Context c) {
      mContext = c;
    }

    @Override
    public int getCount() {
      return mPhotos.size();
    }

    @Override
    public Object getItem(int position) {
      PhotoInfo photo = mPhotos.get(position);
      trace("getItem position=" + position + " returning " + d(photo));
      return photo;
    }

    @Override
    public long getItemId(int position) {
      return getPhoto(position).getId();
    }

    private PhotoInfo getPhoto(int position) {
      return (PhotoInfo) getItem(position);
    }

    // create a new ImageView for each item referenced by the Adapter
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      trace("getView position=" + position);
      ImageView imageView;
      if (convertView == null) {
        // if it's not recycled, initialize some attributes
        trace("building ImageView");
        imageView = new ImageView(mContext);
        imageView.setLayoutParams(new GridView.LayoutParams(85, 85));
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setPadding(8, 8, 8, 8);
      } else {
        imageView = (ImageView) convertView;
      }
      PhotoInfo photo = getPhoto(position);
      unimp("retrieve photo bitmap asynchronously for " + photo);
      imageView.setImageResource(getThumbId(position));
      return imageView;
    }

    private int getThumbId(int position) {
      return mThumbIds[myMod(position, mThumbIds.length)];
    }

    private int[] mThumbIds = {
        R.drawable.ic_launcher, R.drawable.round_button, R.drawable.ic_launcher,
    };
  }

  public void setTrace(boolean state) {
    mTrace = state;
    if (state)
      warning("Turning tracing on");
  }

  private void trace(Object msg) {
    if (mTrace) {
      String threadMessage = "";
      if (!isUIThread()) {
        threadMessage = "(" + nameOf(Thread.currentThread()) + ") ";
      }
      pr("--      AlbumActivity " + threadMessage + "--: " + msg);
    }
  }

  private boolean mTrace;
  private BaseAdapter mAdapter;
  private PhotoFile mPhotoFile;
  private List<PhotoInfo> mPhotos = new ArrayList();
}
