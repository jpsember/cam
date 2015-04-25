package com.js.camera;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.widget.ImageView;

import android.widget.LinearLayout;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.js.android.UITools;

import java.util.List;
import java.util.Observable;
import java.util.Observer;

import static com.js.android.AndroidTools.*;
import static com.js.basic.Tools.*;

public class ViewPhotoActivity extends Activity implements Observer {
  private static final String PHOTO_ID_KEY = "photoid";

  public static Intent buildIntentForPhoto(Context context, int photoId) {
    Intent intent = new Intent(context, ViewPhotoActivity.class);
    Bundle b = new Bundle();
    b.putInt(PHOTO_ID_KEY, photoId);
    intent.putExtras(b);
    return intent;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    doNothingAndroid();
    AppState.prepare(this);

    mPhotoFile = AppState.photoFile(this);
    Bundle b = getIntent().getExtras();
    if (b == null)
      throw new IllegalArgumentException();
    mFocusPhotoId = b.getInt(PHOTO_ID_KEY);

    super.onCreate(savedInstanceState);
    setContentView(buildContentView());
  }

  @Override
  protected void onResume() {
    mResumed = true;
    super.onResume();
    mPhotoFile.addObserver(this);
    addPhotoPagesIfPhotosReady();
  }

  private void addPhotoPagesIfPhotosReady() {
    if (!mPhotoFile.isOpen())
      return;

    MyAdapter adapter = adapter();
    List<PhotoInfo> photos = mPhotoFile.getPhotos(0, Integer.MAX_VALUE);
    adapter.clear();
    int focusIndex = -1;
    for (int cursor = 0; cursor < photos.size(); cursor++) {
      PhotoInfo photoInfo = photos.get(cursor);
      adapter.add(photoInfo.getId());
      if (photoInfo.getId() == mFocusPhotoId)
        focusIndex = cursor;

    }
    adapter.notifyDataSetChanged();
    if (focusIndex >= 0) {
      mPager.setCurrentItem(focusIndex);
    }
  }

  @Override
  protected void onPause() {
    mPhotoFile.deleteObserver(this);
    super.onPause();
    mResumed = false;
  }

  private View buildContentView() {
    LinearLayout layout = UITools.linearLayout(this, true);

    mPager = new ViewPager(this);
    mPager.setAdapter(new MyAdapter());

    layout.addView(mPager, UITools.layoutParams(layout, 1.0f));
    return layout;
  }

  // Observer interface

  @Override
  public void update(Observable observable, Object data) {
    Object[] params = (Object[]) data;
    switch ((PhotoFile.Event) params[0]) {
      case StateChanged:
        addPhotoPagesIfPhotosReady();
        break;
      case BitmapConstructed: {
        PhotoInfo photo = (PhotoInfo) params[1];
        Bitmap bitmap = (Bitmap) params[2];
        adapter().bitmapArrived(photo, bitmap);
      }
      break;
    }
  }

  private MyAdapter adapter() {
    return (MyAdapter) mPager.getAdapter();
  }

  /**
   * Adapter for list of photo ids, to be displayed in ViewPager
   */
  private class MyAdapter extends ListPageAdapter<Integer> {

    @Override
    public View createView(int position) {
      ImageView imageView = new ImageView(ViewPhotoActivity.this);
      imageView.setBackgroundColor(Color.BLACK);
      return imageView;
    }

    @Override
    public void initView(View v, Integer photoId, int position) {
      ImageView view = (ImageView) v;
      mViewToPhotoIdBiMap.forcePut(view, photoId);
      view.setImageBitmap(null);

      PhotoInfo info = mPhotoFile.getPhoto(photoId);
      if (info == null) {
        warning("no photo id " + photoId + " found");
        return;
      }
      // Attempt to load bitmap for this photo
      mPhotoFile.getBitmap(ViewPhotoActivity.this, info);
    }

    public void bitmapArrived(PhotoInfo photoInfo, Bitmap bitmap) {
      ImageView view = mViewToPhotoIdBiMap.inverse().get(photoInfo.getId());
      if (view == null) {
        warning("no view corresponding to " + photoInfo);
        return;
      }
      view.setImageBitmap(bitmap);
    }
  }

  private ViewPager mPager;
  // Bidirectional map to determine view <=> photo correspondence
  private BiMap<ImageView, Integer> mViewToPhotoIdBiMap = HashBiMap.create();
  private PhotoFile mPhotoFile;
  private int mFocusPhotoId;
  private boolean mResumed;
}

