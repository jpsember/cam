package com.js.camera;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;

import android.widget.LinearLayout;

import com.js.android.UITools;

import java.util.List;
import java.util.Observable;
import java.util.Observer;

import static com.js.android.AndroidTools.*;
import static com.js.basic.Tools.*;

public class ViewPhotoActivity extends Activity implements Observer {
  private static final String PHOTO_ID_KEY = "photoid";

  private enum ActivityState {
    Paused,
    Resumed,
    PausePending,
  }

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
    super.onCreate(savedInstanceState);

    mPhotoFile = AppState.photoFile(this);

    setContentView(buildContentView());
    mFocusPhotoId = UITools.restore(this, savedInstanceState, PHOTO_ID_KEY, 0);
    if (mFocusPhotoId == 0)
      throw new IllegalArgumentException();
  }

  @Override
  protected void onResume() {
    super.onResume();
    setState(ActivityState.Resumed);
    mPhotoFile.addObserver(this);
    addPhotoPagesIfPhotosReady();
  }

  private void addPhotoPagesIfPhotosReady() {
    if (!mPhotoFile.isOpen())
      return;

    MyAdapter adapter = adapter();
    List<PhotoInfo> photos = mPhotoFile.getPhotos(0, Integer.MAX_VALUE);
    adapter.clear();
    for (int cursor = 0; cursor < photos.size(); cursor++) {
      PhotoInfo photoInfo = photos.get(cursor);
      adapter.add(photoInfo.getId());
    }
    adapter.notifyDataSetChanged();

    int focusPosition = adapter.getItems().indexOf(mFocusPhotoId);
    if (focusPosition < 0) throw new IllegalStateException();
    mPager.setCurrentItem(focusPosition);
  }

  @Override
  protected void onPause() {
    super.onPause();
    mPhotoFile.deleteObserver(this);
    setState(ActivityState.Paused);
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    UITools.persist(outState, PHOTO_ID_KEY, mFocusPhotoId);
  }

  private View buildContentView() {
    // The container will be a FrameLayout, with the lower layer the
    // ViewPager, and the upper layer the buttons.

    FrameLayout layout = new FrameLayout(this);

    {
      mPager = new ViewPager(this);
      mPager.setAdapter(new MyAdapter());
      layout.addView(mPager);

      mPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        @Override
        public void onPageSelected(int position) {
        }

        @Override
        public void onPageScrollStateChanged(int state) {
          switch (state) {
            case ViewPager.SCROLL_STATE_IDLE:
              updateButtonVisibility();
              mFocusPhotoId = adapter().getCurrentPhoto().getId();
              break;
            case ViewPager.SCROLL_STATE_DRAGGING:
              mButtons.setVisibility(View.INVISIBLE);
              break;
          }
        }
      });
    }

    {
      LinearLayout buttonContainer = UITools.linearLayout(this, false);
      mButtons = buttonContainer;
      layout.addView(buttonContainer, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
          FrameLayout.LayoutParams.WRAP_CONTENT));

      // Add spacer to take up all extra space to left
      LinearLayout.LayoutParams params = UITools.layoutParams(buttonContainer, 1.0f);
      buttonContainer.addView(new View(this), params);

      // Add buttons
      params = UITools.layoutParams(buttonContainer, 0f);
      params.height = LinearLayout.LayoutParams.WRAP_CONTENT;

      ImageButton button = new ImageButton(this);
      button.setImageResource(android.R.drawable.ic_delete);
      button.setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              deleteCurrentPhoto();
            }
          }
      );
      buttonContainer.addView(button, params);
    }

    return layout;
  }

  private void updateButtonVisibility() {
    mButtons.setVisibility((mState == ActivityState.Resumed) ? View.VISIBLE : View.INVISIBLE);
  }

  // Observer interface

  @Override
  public void update(Observable observable, Object data) {
    Object[] params = (Object[]) data;
    switch ((PhotoFile.Event) params[0]) {
      case StateChanged:
        addPhotoPagesIfPhotosReady();
        break;
    }
  }

  private MyAdapter adapter() {
    return (MyAdapter) mPager.getAdapter();
  }

  /**
   * Delete the currently displayed photo
   */
  private void deleteCurrentPhoto() {
    PhotoInfo photo = adapter().getCurrentPhoto();
    setState(ActivityState.PausePending);
    mPhotoFile.deletePhoto(photo, new Runnable() {
      @Override
      public void run() {
        // Quit the activity
        finish();
      }
    });
  }

  private void setState(ActivityState state) {
    mState = state;
    updateButtonVisibility();
  }

  /**
   * Adapter for list of photo ids, to be displayed in ViewPager
   */
  private class MyAdapter extends ListPageAdapter<Integer> {

    @Override
    public View createView(int position) {
      ImageView imageView = new ImageView(ViewPhotoActivity.this);
      imageView.setBackgroundColor(Color.BLACK);
      imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
      return imageView;
    }

    @Override
    public void initView(View v, Integer photoId, int position) {
      ImageView view = (ImageView) v;
      ViewHolder holder = new ViewHolder(position);
      holder.mPhotoId = photoId;
      view.setTag(holder);

      PhotoInfo info = mPhotoFile.getPhoto(photoId);
      if (info == null) {
        warning("no photo id " + photoId + " found");
        return;
      }
      mPhotoFile.loadBitmapIntoView(ViewPhotoActivity.this, info, 0, view);
    }

    public PhotoInfo getCurrentPhoto() {
      ViewHolder seek = new ViewHolder(mPager.getCurrentItem());
      ImageView view = (ImageView) mPager.findViewWithTag(seek);
      if (view == null)
        throw new IllegalStateException();
      ViewHolder holder = (ViewHolder) view.getTag();
      return mPhotoFile.getPhoto(holder.mPhotoId);
    }
  }

  /**
   * See http://developer.android.com/training/improving-layouts/smooth-scrolling.html#ViewHolder
   */
  private static class ViewHolder {

    /**
     * Construct ViewHolder; these are uniquely identified by their position fields
     */
    public ViewHolder(int position) {
      mPosition = position;
    }

    @Override
    public boolean equals(Object other) {
      if (other == null) return false;
      if (!(other instanceof ViewHolder)) return false;
      return mPosition == ((ViewHolder) other).mPosition;
    }

    @Override
    public int hashCode() {
      return mPosition;
    }

    int mPhotoId;
    int mPosition;
  }

  private ViewPager mPager;
  private PhotoFile mPhotoFile;
  private ViewGroup mButtons;
  private int mFocusPhotoId;
  private ActivityState mState = ActivityState.Paused;
}
