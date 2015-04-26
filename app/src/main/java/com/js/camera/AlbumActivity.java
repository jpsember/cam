package com.js.camera;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
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

import com.js.android.UITools;
import com.js.basic.IPoint;
import com.js.camera.camera.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;

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
      default:
        return super.onOptionsItemSelected(item);
    }
    return true;
  }

  private View buildContentView() {
    GridView v = new GridView(this);
    mGridView = v;
    int spacing = UITools.dpToPixels(5);
    int thumbSize = UITools.dpToPixels(UITools.smallScreenSize() ? 120 : 200);
    mThumbSize = new IPoint(thumbSize - spacing, thumbSize - spacing);
    v.setColumnWidth(mThumbSize.x + spacing);
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
      case BitmapConstructed: {
        PhotoInfo photo = (PhotoInfo) params[1];
        Bitmap bitmap = (Bitmap) params[2];
        trace("BitmapConstructed for " + photo + ": " + bitmap);
        // If we're not constructing a thumbnail, ignore
        if (!mThumbnailRequestedSet.contains(photo.getId())) {
          trace("not requesting thumbnail");
          break;
        }
        if (mPhotoFile.getBitmapFromMemCache(memCacheKeyFor(photo)) != null) {
          throw new IllegalStateException("thumbnail already exists");
        }
        // Start a new TaskSequence to build and deal with thumbnail
        new BuildThumbnailTask(photo, bitmap).start();
      }
      break;
    }
  }

  private class BuildThumbnailTask extends TaskSequence {

    public BuildThumbnailTask(PhotoInfo photo, Bitmap originalBitmap) {
      mPhoto = photo;
      mOriginalBitmap = originalBitmap;
    }

    @Override
    protected void execute(int stageNumber) {
      if (!mResumed) {
        abort();
        return;
      }
      switch (stageNumber) {
        case 0:
          constructThumbnail();
          break;
        case 1:
          receivedThumbnail();
          finish();
          break;
      }
    }

    private void constructThumbnail() {
      Bitmap bitmap = mOriginalBitmap;
      assertBgndThread();
      int origWidth = (int) (bitmap.getWidth() * .8f);
      int origHeight = (int) (bitmap.getHeight() * .8f);
      int origSize = Math.min(origWidth, origHeight);
      float scaleFactor = mThumbSize.x / (float) origSize;
      Matrix m = new Matrix();

      m.postScale(scaleFactor, scaleFactor);
      mThumbnailBitmap = Bitmap.createBitmap(bitmap,
          (bitmap.getWidth() - origSize) / 2, (bitmap.getHeight() - origSize) / 2,
          origSize, origSize, m, true);
    }

    private void receivedThumbnail() {
      trace("storing thumbnail bitmap " + nameOf(mThumbnailBitmap)
          + " within map, key " + mPhoto);
      mPhotoFile.addBitmapToMemoryCache(memCacheKeyFor(mPhoto), mThumbnailBitmap);

      int start = mGridView.getFirstVisiblePosition();
      for (
          int i = start, j = mGridView.getLastVisiblePosition();
          i <= j; i++) {
        PhotoInfo itemPhoto = (PhotoInfo) mGridView.getItemAtPosition(i);
        if (mPhoto.getId() == itemPhoto.getId()) {
          View view = mGridView.getChildAt(i - start);
          mGridView.getAdapter().getView(i, view, mGridView);
          break;
        }
      }
      // We're no longer requesting a thumbnail for this photo
      mThumbnailRequestedSet.remove(mPhoto.getId());
      trace("thumbnail requested set now " + d(mThumbnailRequestedSet, false));
    }

    private PhotoInfo mPhoto;
    private Bitmap mOriginalBitmap;
    private Bitmap mThumbnailBitmap;
  }

  private String memCacheKeyFor(PhotoInfo photo) {
    return "thumbnail_" + photo.getId();
  }

  private void assertBgndThread() {
    if (!isUIThread())
      return;
    throw new IllegalStateException("Attempt to call from non-bgnd thread " + nameOf(Thread.currentThread()));
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
      if (convertView == null) {
        // if it's not recycled, initialize some attributes
        imageView = new ImageView(context());
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        trace("getView position " + position + " =>    built " + nameOf(imageView));
      } else {
        imageView = (ImageView) convertView;
        // Dispose of any old bitmap
        imageView.setImageBitmap(null);
        trace("getView position " + position + " => existing " + nameOf(imageView));
      }
      final PhotoInfo photo = getPhoto(position);

      // If thumbnail exists for this photo, use it;
      // otherwise, build it asynchronously and refresh this item when it's available
      Bitmap bitmap = mPhotoFile.getBitmapFromMemCache(memCacheKeyFor(photo));
      if (bitmap != null) {
        trace("using existing thumbnail " + nameOf(bitmap, false));
        imageView.setImageBitmap(bitmap);
      } else {
        trace("getView position:" + position + ", no thumbnail found");
        // If we're already requesting a thumbnail for this photo, ignore
        if (mThumbnailRequestedSet.add(photo.getId())) {
          trace("requesting bitmap for this view, to construct thumbnail; set now " + d(mThumbnailRequestedSet, false));
          // Ask photo file for (full size) bitmap, and when it's returned, we'll construct a thumbnail
          mPhotoFile.getBitmap(AlbumActivity.this, photo);
        }
        // Simplify this by passing a continuation block (to be executed on the UI thread)
      }
      return imageView;
    }

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

  private boolean mResumed;
  private IPoint mThumbSize;
  private boolean mTrace;
  private PhotoFile mPhotoFile;
  private GridView mGridView;
  private List<PhotoInfo> mPhotos = new ArrayList();
  private Set<Integer> mThumbnailRequestedSet = new HashSet();
  private Bundle mSavedInstanceState;
}
