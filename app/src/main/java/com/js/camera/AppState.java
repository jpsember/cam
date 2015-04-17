package com.js.camera;

import android.content.Context;

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
    return sPhotoFile;
  }

  private static Context context() {
    assertPrepared();
    return sContext;
  }

  private static void constructPhotoFile() {
    sPhotoFile = new PhotoFile(context());
    sPhotoFile.open();
  }

  private static PhotoFile sPhotoFile;
  private static Context sContext;
}
