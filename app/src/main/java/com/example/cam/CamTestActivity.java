package com.example.cam;

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

import com.js.android.UITools;

import static com.js.basic.Tools.*;
import static com.js.android.AndroidTools.*;

public class CamTestActivity extends Activity {

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

    setContentView(buildContentView());


    Toast.makeText(this, getString(R.string.take_photo_help), Toast.LENGTH_LONG).show();
  }

  private View buildContentView() {
    buildCameraView();

    // Construct a linear layout that contains the Camera view, and some other views as well
    LinearLayout container = UITools.linearLayout(this, false);

    FrameLayout cameraViewContainer = new FrameLayout(this);
    {
      cameraViewContainer.setBackgroundColor(Color.GRAY);
      cameraViewContainer.setPadding(20, 20, 20, 20);
      cameraViewContainer.addView(mPreview);
    }

    // Have the camera view container and a dummy view share the screen side by side
    container.addView(cameraViewContainer, UITools.layoutParams(container, 1.0f));

    {
      View blankView = new View(this);
      blankView.setBackgroundColor(Color.GREEN);
      container.addView(blankView, UITools.layoutParams(container, 1.0f));
    }
    return container;
  }

  private void buildCameraView() {
    mPreview = new CameraView(this);
    mPreview.setKeepScreenOn(true);
    mPreview.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View arg0) {
        mCamera.takePicture(shutterCallback, rawCallback, jpegCallback);
      }
    });
  }

  @Override
  protected void onResume() {
    super.onResume();
    int numCams = Camera.getNumberOfCameras();
    if (numCams > 0) {
      try {
        mCamera = Camera.open(0);
        mCamera.startPreview();
        mPreview.setCamera(mCamera);
      } catch (RuntimeException ex) {
        showException(this, ex, null);
      }
    }
  }

  @Override
  protected void onPause() {
    if (mCamera != null) {
      mCamera.stopPreview();
      mPreview.setCamera(null);
      mCamera.release();
      mCamera = null;
    }
    super.onPause();
  }

  private void resetCam() {
    mCamera.startPreview();
    mPreview.setCamera(mCamera);
  }

  private void refreshGallery(File file) {
    Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
    mediaScanIntent.setData(Uri.fromFile(file));
    sendBroadcast(mediaScanIntent);
  }

  ShutterCallback shutterCallback = new ShutterCallback() {
    public void onShutter() {
    }
  };

  PictureCallback rawCallback = new PictureCallback() {
    public void onPictureTaken(byte[] data, Camera camera) {
      pr("onPictureTaken - raw");
    }
  };

  PictureCallback jpegCallback = new PictureCallback() {
    public void onPictureTaken(byte[] data, Camera camera) {
      new SaveImageTask().execute(data);
      resetCam();
      pr("onPictureTaken - jpeg");
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

  private CameraView mPreview;
  private Camera mCamera;
}
