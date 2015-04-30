package com.js.camera;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import com.js.android.UITools;
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
    super.onCreate(savedInstanceState);
    doNothingAndroid();
    AppState.prepare(this);
//    setTrace(true);

    mPhotoFile = AppState.photoFile(this);
    setContentView(buildContentView());

    mSavedInstanceState = savedInstanceState;
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    UITools.persist(outState, "album", mGridView);
  }

  @Override
  protected void onResume() {
    mResumed = true;
    trace("onResume");
//    showFreeMemory(this,"Resuming AlbumActivity");
    super.onResume();
    mPhotoFile.addObserver(this);
    rebuildAlbumIfPhotosAvailable();
  }

  private void rebuildAlbumIfPhotosAvailable() {
    if (!mPhotoFile.isOpen())
      return;
    mPhotos.clear();
    mPhotos.addAll(mPhotoFile.getPhotos(0, Integer.MAX_VALUE / 10));
    ((ImageAdapter) mGridView.getAdapter()).notifyDataSetChanged();
    // Now that list has been populated, restore previously saved state
    UITools.restore(this, mSavedInstanceState, "album", mGridView);
  }

  @Override
  protected void onPause() {
    mResumed = false;
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
        startActivityForResult(CameraActivity.buildIntent(this), 0);
      }
      break;
      case R.id.action_experiments: {
        startActivityForResult(GraphicsExperimentActivity.buildIntent(this), 0);
      }
      break;
      default:
        return super.onOptionsItemSelected(item);
    }
    return true;
  }

  private View buildContentView() {
    GridView v = new GridView(this);
    mGridView = v;
    mThumbSize = UITools.dpToPixels(UITools.smallScreenSize() ? 120 : 200);
    int spacing = UITools.dpToPixels(5);
    v.setNumColumns(GridView.AUTO_FIT);
    v.setVerticalSpacing(spacing);
    v.setHorizontalSpacing(spacing);
    v.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
    v.setGravity(Gravity.CENTER);
    v.setAdapter(new ImageAdapter());

    v.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      public void onItemClick(AdapterView<?> parent, View v,
                              int position, long id) {
        trace("showing photo id " + id);
        startActivityForResult(ViewPhotoActivity.buildIntentForPhoto(AlbumActivity.this, (int) id), 0);
      }
    });

    return mGridView;
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

    public ImageAdapter() {
    }

    @Override
    public int getCount() {
      return mPhotos.size();
    }

    @Override
    public Object getItem(int position) {
      PhotoInfo photo = mPhotos.get(position);
      return photo;
    }

    @Override
    public long getItemId(int position) {
      return getPhoto(position).getId();
    }

    private PhotoInfo getPhoto(int position) {
      return (PhotoInfo) getItem(position);
    }

    private Context context() {
      return AlbumActivity.this;
    }

    // create a new ImageView for each item referenced by the Adapter
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      trace("getView for position " + position + ", convertView " + nameOf(convertView, false));
      ImageView imageView;
      AdapterImageViewHolder holder;
      if (convertView == null) {
        // if it's not recycled, initialize some attributes
        imageView = new ImageView(context());
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        trace("getView position " + position + " =>    built " + nameOf(imageView));

        holder = new AdapterImageViewHolder();
        holder.imageView = imageView;
        imageView.setTag(holder);
        imageView.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, mThumbSize));
      } else {
        imageView = (ImageView) convertView;
        holder = (AdapterImageViewHolder) imageView.getTag();
        holder.imageView = imageView;
        trace("getView position " + position + " => existing " + nameOf(imageView));
      }
      PhotoInfo photo = getPhoto(position);
      mPhotoFile.loadBitmapIntoView(AlbumActivity.this, photo, mThumbSize, holder.imageView);
      return imageView;
    }
  }

  /**
   * Object stored in ImageView's tag field, so asynchronously loaded images that are stale
   * do not get stored to views that have been recycled to hold other images
   * (document this pattern later, after tracking down some examples in the web)
   */
  private static class AdapterImageViewHolder {
    ImageView imageView;
  }

  @SuppressWarnings("UnusedDeclaration")
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

  private boolean mResumed;
  private int mThumbSize;
  private boolean mTrace;
  private PhotoFile mPhotoFile;
  private GridView mGridView;
  private List<PhotoInfo> mPhotos = new ArrayList();
  private Bundle mSavedInstanceState;
}
