package com.js.camera;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;

import java.util.List;
import java.util.Observable;
import java.util.Observer;

import static com.js.android.AndroidTools.*;

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

    mPhotoFile = AppState.photoFile();
//    mBackgroundThreadHandler = AppState.buildBackgroundHandler("ViewPhotoActivity");
    Bundle b = getIntent().getExtras();
    if (b == null) throw new IllegalArgumentException();
    mPhotoId = b.getInt(PHOTO_ID_KEY);


    super.onCreate(savedInstanceState);
    setContentView(buildContentView());
  }

  @Override
  protected void onResume() {
    mResumed = true;
    super.onResume();
    mPhotoFile.addObserver(this);
    displayDesiredPhotoIfPhotosReady();
  }

  private void displayDesiredPhotoIfPhotosReady() {
    if (!mPhotoFile.isOpen())
      return;
    if (mPhotoInfo == null) {
      List<PhotoInfo> list = mPhotoFile.getPhotos(mPhotoId, 1);
      if (list.isEmpty())
        return;
      PhotoInfo info = list.get(0);
      if (info.getId() != mPhotoId)
        return;
      mPhotoInfo = info;
    }
    // Attempt to load requested photo
    mPhotoFile.getBitmap(mPhotoInfo);
  }

  @Override
  protected void onPause() {
    mPhotoFile.deleteObserver(this);
    super.onPause();
    mResumed = false;
  }

  private View buildContentView() {
    ImageView v = new ImageView(this);
    v.setBackgroundColor(Color.BLUE);
    mImageView = v;
    return v;
  }

  // Observer interface

  @Override
  public void update(Observable observable, Object data) {
    Object[] params = (Object[]) data;
    switch ((PhotoFile.Event) params[0]) {
      case StateChanged:
        displayDesiredPhotoIfPhotosReady();
        break;
      case BitmapConstructed: {
        final PhotoInfo photo = (PhotoInfo) params[1];
        if (photo.getId() != mPhotoId)
          break;
        final Bitmap bitmap = (Bitmap) params[2];
        mImageView.setImageBitmap(bitmap);
      }
      break;
    }
  }

  private PhotoFile mPhotoFile;
  //  private Handler mBackgroundThreadHandler;
  private ImageView mImageView;
  private PhotoInfo mPhotoInfo;
  private int mPhotoId;
  private boolean mResumed;
}

