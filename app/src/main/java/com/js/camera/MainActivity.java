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
import android.widget.ImageView;
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
    timeStamp("MainActivity created, thread:" + nameOf(Thread.currentThread()));

    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

    setContentView(buildContentView());

  }

  /**
   * Install a PreviewCallback for test purposes
   */
  private void installPreviewCallback() {
    mCamera.setPreviewCallback(new Camera.PreviewCallback() {
      @Override
      public void onPreviewFrame(byte[] data, Camera camera) {
        // This is running in a different thread!  We must
        // verify that the camera is still open, and avoid using
        // getParameters() or other methods that are not threadsafe

        // Verify that camera is still open; if not, ignore (Issue #18)
        if (!mCamera.isOpen())
          return;
        int frameHeight = camera.getParameters().getPreviewSize().height;
        int frameWidth = camera.getParameters().getPreviewSize().width;
        if (mCounter < 4)
          timeStamp("onPreviewFrame" + frameWidth + " x " + frameHeight + " " + nameOf(Thread.currentThread()));
        mCounter++;
//      int rgb[] = new int[frameWidth * frameHeight];
//      int[] myPixels = decodeYUV420SP(rgb, data, frameWidth, frameHeight);
      }

      private int mCounter;
    });
  }

  private final static int BGND_COLOR = Color.DKGRAY;

  private View buildContentView() {

    // Construct a linear layout that contains the Camera view, and some other views as well
    final LinearLayout container = UITools.linearLayout(this, false);

    mCameraViewContainer = new FrameLayout(this);
    {
      mCameraViewContainer.setBackgroundColor(BGND_COLOR);
      mCameraViewContainer.setPadding(20, 20, 20, 20);
    }
    buildImageView();
    container.addView(mImageView);
    ShrinkingView.build(container, 1.0f);
    container.addView(mCameraViewContainer, UITools.layoutParams(container, 1.0f));

    return container;
  }

  private void resumeCamera() {
    mCamera = new MyCamera();
    mCamera.setActivity(this);
    installPreviewCallback();
    Toast.makeText(this, getString(R.string.take_photo_help), Toast.LENGTH_LONG).show();
    buildCameraView();
    mCameraViewContainer.addView(mPreview);
    mCamera.open();
  }

  private void pauseCamera() {
    mCamera.close();
    mCamera = null;
    mCameraViewContainer.removeView(mPreview);
  }

  private void buildCameraView() {
    mPreview = new CameraPreview(this, mCamera);
    mPreview.setBackgroundColor(BGND_COLOR);

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
          if (true) {
            pr("  toggling preview");
            mCamera.setPreviewStarted(!mCamera.isPreviewStarted());
            return;
          }
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
    resumeCamera();
  }

  @Override
  protected void onPause() {
    pauseCamera();
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

  private void buildImageView() {
    mImageView = new ImageView(this);
    mImageView.setBackgroundColor(Color.GREEN);
    mImageView.setLayoutParams(new LinearLayout.LayoutParams(300, LinearLayout.LayoutParams.MATCH_PARENT));
    mImageView.setImageResource(R.drawable.ic_launcher);
  }

  private MyCamera mCamera;
  private CameraPreview mPreview;
  private FrameLayout mCameraViewContainer;
  private ImageView mImageView;
}
