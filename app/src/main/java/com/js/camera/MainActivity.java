package com.js.camera;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.js.android.AppPreferences;
import com.js.android.UITools;
import com.js.camera.camera.R;

import static com.js.basic.Tools.*;
import static com.js.android.AndroidTools.*;

public class MainActivity extends Activity {

  @Override
  public void onCreate(Bundle savedInstanceState) {
    startApp(this);
    AppPreferences.prepare(this);
    doNothingAndroid();

    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

    mCamera = new MyCamera();
    mCamera.setActivity(this);

    setContentView(buildContentView());

    Toast.makeText(this, getString(R.string.take_photo_help), Toast.LENGTH_LONG).show();
  }

  private View buildContentView() {
    buildCameraView();

    // Construct a linear layout that contains the Camera view, and some other views as well
    final LinearLayout container = UITools.linearLayout(this, false);

    FrameLayout cameraViewContainer = new FrameLayout(this);
    {
      int bgndColor = Color.DKGRAY;
      cameraViewContainer.setBackgroundColor(bgndColor);
      cameraViewContainer.setPadding(20, 20, 20, 20);
      cameraViewContainer.addView(mPreview);
      mPreview.setBackgroundColor(bgndColor);
    }

    // Have the camera view container and the shrinking views share the screen side by side
    ShrinkingView.build(container, 1.0f);
    container.addView(cameraViewContainer, UITools.layoutParams(container, 1.0f));

    return container;
  }


  private void buildCameraView() {
    mPreview = new CameraPreview(this, mCamera);

    int style = 0;
    if (style >= 2) {
      mPreview.setGlassColor(0xc0600000);
      mPreview.setFrameRadius(50);
    }
    if (style % 2 != 0)
      mPreview.setFrameStyle(CameraPreview.FRAMESTYLE_NONE);

    mPreview.setKeepScreenOn(true);
    mPreview.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View arg0) {
        if (mCamera.isOpen()) {
          pr("==== having camera take picture");
          mCamera.camera().takePicture(shutterCallback, rawCallback, jpegCallback);
          pr("==== done taking picture");
        }
      }
    });
  }

  @Override
  protected void onResume() {
    super.onResume();
    mCamera.open();
  }

  @Override
  protected void onPause() {
    mCamera.close();
    super.onPause();
  }

  private void refreshGallery(File file) {
    Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
    mediaScanIntent.setData(Uri.fromFile(file));
    sendBroadcast(mediaScanIntent);
  }

  ShutterCallback shutterCallback = new ShutterCallback() {
    public void onShutter() {
      pr("...onShutter callback");
    }
  };

  PictureCallback rawCallback = new PictureCallback() {
    public void onPictureTaken(byte[] data, Camera camera) {
      pr("...PictureCallback");
    }
  };

  PictureCallback jpegCallback = new PictureCallback() {
    public void onPictureTaken(byte[] data, Camera camera) {
      pr("...jpegCallback");
      mCamera.camera().startPreview();

      warning("skipping save picture");
      if (false) {
        new SaveImageTask().execute(data);
      }
    }
  };

  private class SaveImageTask extends AsyncTask<byte[], Void, Void> {

    @Override
    protected Void doInBackground(byte[]... data) {

      // Write to SD Card
      try {
        File sdCard = Environment.getExternalStorageDirectory();
        File dir = new File(sdCard.getAbsolutePath() + "/camtest");
        dir.mkdirs();

        String fileName = String.format("%d.jpg", System.currentTimeMillis());
        File outFile = new File(dir, fileName);

        FileOutputStream outStream = new FileOutputStream(outFile);
        outStream.write(data[0]);
        outStream.flush();
        outStream.close();

        pr("onPictureTaken - wrote bytes: " + data.length + " to " + outFile.getAbsolutePath());

        refreshGallery(outFile);
      } catch (FileNotFoundException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      }
      return null;
    }

  }

  private MyCamera mCamera;
  private CameraPreview mPreview;
}
