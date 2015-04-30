package com.js.camera;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.js.android.UITools;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import static com.js.android.AndroidTools.*;
import static com.js.basic.Tools.*;

import static com.squareup.picasso.Callback.EmptyCallback;

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
    LinearLayout layout = UITools.linearLayout(this, true);

    mImageView = new ImageView(this);
    mImageView.setBackgroundColor(UITools.debugColor());
    layout.addView(mImageView, UITools.layoutParams(layout, 1));

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
        loadImage(mPhotos.get(mPhotoIndex));
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
        loadImage(mPhotos.get(mPhotoIndex));
      }
    });

    return layout;
  }

  private void loadImage(PhotoInfo photo) {
    File file = mPhotoFile.tempGetPhotoPath(photo.getId());
    Picasso.with(this).load(file).into(mImageView, new EmptyCallback() {
      @Override
      public void onSuccess() {
        // Index 0 is the image view.
//            animator.setDisplayedChild(0);
      }
    });
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

  private ImageView mImageView;
  private PhotoFile mPhotoFile;
  private List<PhotoInfo> mPhotos;
  private int mPhotoIndex;
}
