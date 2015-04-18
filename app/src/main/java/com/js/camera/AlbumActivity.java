package com.js.camera;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
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

import com.js.basic.IPoint;
import com.js.camera.camera.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    mBackgroundThreadHandler = AppState.buildBackgroundHandler("AlbumActivity");
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
    mThumbnailMap.clear();
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
    mGridView = buildGridView();
    return mGridView;
  }

  public ViewGroup buildGridView() {
    GridView v = new GridView(this);
    v.setBackgroundColor(Color.GREEN);
    unimp("use density pixels throughout");
    mSpacing = 3;
    mThumbSize = new IPoint(350 - mSpacing, 350 - mSpacing);
    v.setColumnWidth(mThumbSize.x + mSpacing);
    v.setNumColumns(GridView.AUTO_FIT);
    v.setVerticalSpacing(mSpacing);
    v.setHorizontalSpacing(mSpacing);
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
      case BitmapConstructed: {
        final PhotoInfo photo = (PhotoInfo) params[1];
        final Bitmap bitmap = (Bitmap) params[2];
        trace("BitmapConstructed for " + photo + ": " + bitmap);
        // If we already have a thumbnail, ignore
        if (mThumbnailMap.containsKey(photo.getId())) {
          trace("thumbnail already exists");
          break;
        }
        // If we're not seeking a thumbnail for this photo, ignore
        final Integer thumbnailItemPosition = mBuildingThumbnailMap.get(photo.getId());
        if (thumbnailItemPosition == null)
          break;
        trace("thumbnail item position = " + thumbnailItemPosition);
        mBackgroundThreadHandler.post(new Runnable() {
          @Override
          public void run() {
            // Have background thread construct thumbnail from this (full size) bitmap
            final Bitmap thumbnailBitmap = constructThumbnailFor(photo, bitmap);
            trace("constructed thumbnail; " + BitmapTools.size(thumbnailBitmap));
            AppState.postUIEvent(new Runnable() {
              @Override
              public void run() {
                trace("storing thumbnail bitmap " + nameOf(thumbnailBitmap) + " within map, key " + photo);
                mThumbnailMap.put(photo.getId(), thumbnailBitmap);
                mBuildingThumbnailMap.remove(photo.getId());
                ImageView cell = mGridViewItemToImageViewMap.get(thumbnailItemPosition);
                if (cell == null)
                  throw new IllegalArgumentException("no ImageView found for position " + thumbnailItemPosition);
                cell.setImageBitmap(thumbnailBitmap);
              }
            });
          }
        });
      }
      break;
    }
  }

  private Bitmap constructThumbnailFor(PhotoInfo photo, Bitmap bitmap) {
    assertBgndThread();
    int origWidth = (int) (bitmap.getWidth() * .8f);
    int origHeight = (int) (bitmap.getHeight() * .8f);
    int origSize = Math.min(origWidth, origHeight);
    float scaleFactor = mThumbSize.x / (float) origSize;
    Matrix m = new Matrix();

    m.postScale(scaleFactor, scaleFactor);
    Bitmap thumbnail = Bitmap.createBitmap(bitmap,
        (bitmap.getWidth() - origSize) / 2, (bitmap.getHeight() - origSize) / 2,
        origSize, origSize, m, true);
    trace("constructThumbnail for photo " + photo);
    return thumbnail;
  }

  private void assertBgndThread() {
    if (!isUIThread())
      return;
    throw new IllegalStateException("Attempt to call from non-bgnd thread " + nameOf(Thread.currentThread()));
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
      ImageView imageView;
      if (convertView == null) {
        // if it's not recycled, initialize some attributes
        imageView = new ImageView(mContext);
        imageView.setLayoutParams(new GridView.LayoutParams(mThumbSize.x, mThumbSize.y));
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        trace("getView position " + position + " =>    built " + nameOf(imageView));
      } else {
        imageView = (ImageView) convertView;
        trace("getView position " + position + " => existing " + nameOf(imageView));
      }
      mGridViewItemToImageViewMap.put(position, imageView);
      final PhotoInfo photo = getPhoto(position);

      // If thumbnail exists for this photo, use it;
      // otherwise, build it asynchronously and refresh this item when it's available
      Bitmap bitmap = getThumbnailForPhoto(photo);
      if (bitmap != null) {
        imageView.setImageBitmap(bitmap);
      } else {
        trace("getView position:" + position + ", no thumbnail found");
        imageView.setImageBitmap(null);
        mBuildingThumbnailMap.put(photo.getId(), position);
        // Ask photo file for (full size) bitmap, and when it's returned, we'll construct a thumbnail
        mPhotoFile.getBitmap(photo);
        // Simplify this by passing a continuation block (to be executed on the UI thread)
      }
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

  private Bitmap getThumbnailForPhoto(PhotoInfo photo) {
    Bitmap bitmap = mThumbnailMap.get(photo.getId());
    return bitmap;
  }

  private int mSpacing;
  private IPoint mThumbSize;
  private boolean mTrace;
  private BaseAdapter mAdapter;
  private PhotoFile mPhotoFile;
  private ViewGroup mGridView;
  private List<PhotoInfo> mPhotos = new ArrayList();
  private Map<Integer, Bitmap> mThumbnailMap = new HashMap();
  private Handler mBackgroundThreadHandler;
  // For building thumbnails, a map of which GridView item is associated with a photo
  private Map<Integer, Integer> mBuildingThumbnailMap = new HashMap();
  // Map of which ImageView is mapped to each GridView item
  private Map<Integer, ImageView> mGridViewItemToImageViewMap = new HashMap();
}
