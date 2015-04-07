package com.js.camera;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
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
import com.js.basic.IPoint;
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
    mUIThreadHandler = new Handler(Looper.getMainLooper());
  }

  /**
   * Install a PreviewCallback for test purposes
   */
  private void installPreviewCallback() {
    final MyCamera previewCamera = mCamera;
    previewCamera.setPreviewCallback(new Camera.PreviewCallback() {
      @Override
      public void onPreviewFrame(byte[] data, Camera camera) {
        // This is running in a different thread!  We must
        // verify that the camera is still open, and avoid using
        // getParameters() or other methods that are not threadsafe
        MyCamera.Properties properties = previewCamera.getProperties();

        IPoint previewSize = properties.previewSize();
        if (mCounter++ <= 4)
          timeStamp("onPreviewFrame, previewSize " + previewSize + " " + nameOf(Thread.currentThread()));
        if (previewSize == null)
          return;

        if (mCounter % 40 != 0)
          return;

        if (properties.format() != ImageFormat.NV21)
          throw new UnsupportedOperationException("Unsupported preview image format: " + properties.format());

        int rgba[] = new int[previewSize.x * previewSize.y * 2];
        decodeYUV420SP(rgba, data, previewSize.x, previewSize.y);

        Bitmap bitmap = Bitmap.createBitmap(rgba, previewSize.x, previewSize.y, Bitmap.Config.ARGB_8888);

        int rotation = properties.rotation();
        if (rotation != 0) {
          Matrix matrix = new Matrix();
          matrix.postRotate(rotation);
          bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }

        final Bitmap finalBitmap = bitmap;
        mUIThreadHandler.post(new Runnable() {
          public void run() {
            mImageView.setImageBitmap(finalBitmap);
          }
        });
      }

      private int mCounter;
    });
  }

  /**
   * Decode YUV 4:2:0 bitmap to ARGB_8888 format.
   * Note that a (fully opaque) alpha channel is added;
   * if we omit this, I can't then see how to create a Bitmap from the result
   */
  private static void decodeYUV420SP(int[] argb, byte[] yuv420sp, int width, int height) {
    final int frameSize = width * height;

    int uvpBase = frameSize;
    for (int j = 0, yp = 0; j < height; j++) {
      int u = 0;
      int v = 0;
      int uvp = uvpBase;
      for (int i = 0; i < width; i++, yp++) {
        int y = (0xff & ((int) yuv420sp[yp])) - 16;
        if (y < 0) y = 0;
        if ((i & 1) == 0) {
          v = (0xff & yuv420sp[uvp++]) - 128;
          u = (0xff & yuv420sp[uvp++]) - 128;
        }

        int y1192 = 1192 * y;
        int r = (y1192 + 1634 * v);
        int g = (y1192 - 833 * v - 400 * u);
        int b = (y1192 + 2066 * u);

        if (r < 0) r = 0;
        else if (r > 262143) r = 262143;
        if (g < 0) g = 0;
        else if (g > 262143) g = 262143;
        if (b < 0) b = 0;
        else if (b > 262143) b = 262143;

        argb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
      }
      if ((j & 1) != 0)
        uvpBase += width;
    }
  }

  private final static int BGND_COLOR = Color.DKGRAY;

  private View buildContentView() {

    // Construct a linear layout that contains the Camera view, and some other views as well
    final LinearLayout container = UITools.linearLayout(this, false);

    mCameraViewContainer = new FrameLayout(this);
    {
      mCameraViewContainer.setBackgroundColor(BGND_COLOR);
      mCameraViewContainer.setPadding(10, 10, 10, 10);
    }
    container.addView(buildImageView());
    ShrinkingView.build(container, 1.0f);
    container.addView(mCameraViewContainer, UITools.layoutParams(container, 1.0f));

    return container;
  }

  private void resumeCamera() {
    mCamera = new MyCamera(this);
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
          if (false) {
            pr("  toggling preview");
            mCamera.setPreviewStarted(!mCamera.isPreviewStarted());
            return;
          } else {
            pr("  taking picture");
            mCamera.takePicture();
          }
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
      mCamera.startPreview();

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

  private View buildImageView() {
    mImageView = new ImageView(this);
    mImageView.setBackgroundColor(UITools.debugColor());
    mImageView.setLayoutParams(new LinearLayout.LayoutParams(240, LinearLayout.LayoutParams.MATCH_PARENT));
    mImageView.setImageResource(R.drawable.ic_launcher);
    return mImageView;
  }

  private MyCamera mCamera;
  private CameraPreview mPreview;
  private FrameLayout mCameraViewContainer;
  private ImageView mImageView;
  private Handler mUIThreadHandler;
}
