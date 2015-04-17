package com.js.camera;

import android.content.Context;
import android.graphics.Bitmap;

import com.js.android.AppPreferences;

import static com.js.basic.Tools.*;

/**
 * Class encapulating data accessible throughout the application, that is,
 * by any activity.  It's a singleton.
 */
public class AppState {

  public static void prepare(Context context) {
    if (sContext != null)
      return;
    sContext = context;
    startApp(context);
    AppPreferences.prepare(context);
  }

  private static void assertPrepared() {
    if (sContext == null)
      throw new IllegalStateException("AppState not prepared");
  }

  public static PhotoFile photoFile() {
    assertPrepared();
    if (sPhotoFile == null)
      constructPhotoFile();
    unimp("construct photo file");
    return sPhotoFile;
  }

  private static Context context() {
    assertPrepared();
    return sContext;
  }

  private static void constructPhotoFile() {
    sPhotoFile = new PhotoFile(context(), new PhotoFile.Listener() {
      @Override
      public void stateChanged() {
        if (!sPhotoFile.isOpen())
          return;
        pr("PhotoFile state changed to " + sPhotoFile.state());
      }

      @Override
      public void photoCreated(PhotoInfo photoInfo) {
        pr("Created " + photoInfo);
      }

      @Override
      public void bitmapConstructed(PhotoInfo photoInfo, Bitmap bitmap) {
        pr("BitmapConstructed: " + photoInfo);
        if (bitmap == null) {
          warning("no bitmap for " + photoInfo);
          return;
        }
      }
    });
    sPhotoFile.open();
  }

  private static PhotoFile sPhotoFile;
  private static Context sContext;
}
