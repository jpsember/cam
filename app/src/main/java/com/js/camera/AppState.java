package com.js.camera;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import com.js.android.AppPreferences;
import com.js.android.UITools;

import static com.js.basic.Tools.*;
import static com.js.android.AndroidTools.*;

/**
 * Class encapulating data accessible throughout the application, that is,
 * by any activity.  It's a singleton.
 */
public class AppState {

  public static void prepare(Context context) {
    if (prepared())
      return;
    startApp(context);
    UITools.prepare(context);
    showFreeMemory(context, "Starting app");
    AppPreferences.prepare(context);
    sUIThreadHandler = new Handler(Looper.getMainLooper());
    HandlerThread handlerThread = new HandlerThread("Background handler thread");
    handlerThread.start();
    sBgndThreadHandler = new Handler(handlerThread.getLooper());
  }

  private static boolean prepared() {
    return sUIThreadHandler != null;
  }

  private static void assertPrepared() {
    if (!prepared())
      throw new IllegalStateException("AppState not prepared");
  }

  public static PhotoFile photoFile(Context context) {
    assertPrepared();
    if (sPhotoFile == null)
      constructPhotoFile(context);
    return sPhotoFile;
  }

  public static void postUIEvent(Runnable r) {
    assertPrepared();
    sUIThreadHandler.post(r);
  }

  public static void postBgndEvent(Runnable r) {
    assertPrepared();
    sBgndThreadHandler.post(r);
  }

  private static void constructPhotoFile(Context context) {
    sPhotoFile = new PhotoFile();
    sPhotoFile.open(context);
  }

  private static PhotoFile sPhotoFile;
  private static Handler sUIThreadHandler;
  private static Handler sBgndThreadHandler;
}
