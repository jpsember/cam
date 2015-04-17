package com.js.camera;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

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
    sUIThreadHandler = new Handler(Looper.getMainLooper());
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

  public static void postUIEvent(Runnable r) {
    assertPrepared();
    sUIThreadHandler.post(r);
  }

  private static Context context() {
    assertPrepared();
    return sContext;
  }

  private static void constructPhotoFile() {
    sPhotoFile = new PhotoFile(context());
    sPhotoFile.open();
  }

  public static Handler buildBackgroundHandler(String name) {
    HandlerThread handlerThread = new HandlerThread(name + " background handler thread");
    handlerThread.start();
    Handler handler = new Handler(handlerThread.getLooper());
    return handler;
  }

  private static PhotoFile sPhotoFile;
  private static Context sContext;
  private static Handler sUIThreadHandler;

}
