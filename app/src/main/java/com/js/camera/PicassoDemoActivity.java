package com.js.camera;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.js.android.UITools;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Transformation;

import java.io.File;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import static com.js.android.AndroidTools.*;
import static com.js.basic.Tools.*;

public class PicassoDemoActivity extends Activity implements Observer {

  @Override
  public void onCreate(Bundle savedInstanceState) {
    doNothing();
    doNothingAndroid();
    AppState.prepare(this);
    super.onCreate(savedInstanceState);

    setContentView(buildContentView());
    mPhotoFile = AppState.photoFile(this);
  }

  private View buildContentView() {
    LinearLayout outer = UITools.linearLayout(this, false);


    for (int pass = 0; pass < 2; pass++) {
      final boolean parity = pass == 1;
      LinearLayout layout = UITools.linearLayout(this, true);
      outer.addView(layout, UITools.layoutParams(outer, 1));

      ImageView imageView = new ImageView(this);
      if (pass == 0) mImageView1 = imageView;
      else
        mImageView2 = imageView;
      imageView.setBackgroundColor(UITools.debugColor());
      layout.addView(imageView, UITools.layoutParams(layout, 1));

      LinearLayout buttonPanel = UITools.linearLayout(this, false);
      layout.addView(buttonPanel, UITools.layoutParams(layout, 0));

      Button b = new Button(this);
      b.setText("<<<");
      buttonPanel.addView(b, UITools.layoutParams(buttonPanel, 1));
      b.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          if (mPhotos == null) return;
          mPhotoIndex = myMod(mPhotoIndex - 1, mPhotos.size());
          loadImage(parity, mPhotos.get(mPhotoIndex));
        }
      });
      b = new Button(this);
      b.setText(">>>");
      buttonPanel.addView(b, UITools.layoutParams(buttonPanel, 1));
      b.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          if (mPhotos == null) return;
          mPhotoIndex = myMod(mPhotoIndex + 1, mPhotos.size());
          loadImage(parity, mPhotos.get(mPhotoIndex));
        }
      });
    }

    return outer;
  }

  private void loadImage(boolean parity, PhotoInfo photo) {
    pr("loadImage, thread " + nameOf(Thread.currentThread()));
    File file = mPhotoFile.tempGetPhotoPath(photo.getId());
    buildTransforms();
    Picasso.with(this).load(file).transform(parity ? mTrans2 : mTrans1).into(
        parity ? mImageView2 : mImageView1);
  }

  @Override
  protected void onResume() {
    super.onResume();
    mPhotoFile.addObserver(this);
  }

  // Observer interface

  @Override
  public void update(Observable observable, Object data) {
    Object[] params = (Object[]) data;
    switch ((PhotoFile.Event) params[0]) {
      case StateChanged:
        mPhotos = mPhotoFile.getPhotos(0, 1000);
        break;
    }
  }

  private void buildTransforms() {
    if (mTrans1 != null) return;
    mTrans1 = new Transformation() {
      @Override
      public Bitmap transform(Bitmap bitmap) {
        Bitmap bitmap2 = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        bitmap.recycle();
        UITools.tagBitmap(bitmap2);
        return bitmap2;
      }

      @Override
      public String key() {
        return "1";
      }
    };
    mTrans2 = new Transformation() {
      @Override
      public Bitmap transform(Bitmap bitmap) {
        sleepFor(1250);
        Bitmap bitmap2 = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        bitmap.recycle();
        UITools.tagBitmap(bitmap2);
        return bitmap2;
      }

      @Override
      public String key() {
        return "2";
      }
    };

  }

  private Transformation mTrans1, mTrans2;
  private ImageView mImageView1, mImageView2;
  private PhotoFile mPhotoFile;
  private List<PhotoInfo> mPhotos;
  private int mPhotoIndex;
}
