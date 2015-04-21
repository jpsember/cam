package com.js.android;

import java.io.InputStream;
import java.io.PrintStream;

import com.js.basic.Files;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Looper;
import android.widget.Toast;

import static com.js.basic.Tools.*;

public final class AndroidTools {

  /**
   * A do-nothing method that can be called to avoid 'unused import' warnings
   * related to this class
   */
  public static void doNothingAndroid() {
  }

  /**
   * Display a toast message
   */
  public static void toast(Context context, String message, int duration) {
    Toast toast = Toast.makeText(context, message, duration);
    toast.show();
  }

  /**
   * Display a toast message of short duration
   */
  public static void toast(Context context, String message) {
    toast(context, message, Toast.LENGTH_SHORT);
  }

  /**
   * Display toast message describing an exception
   *
   * @param message optional message to display within toast
   */
  public static void showException(Context context, Throwable exception,
                                   String message) {
    warning("caught: " + exception);
    if (message == null)
      message = "Caught";
    toast(context, message + ": " + exception, Toast.LENGTH_LONG);
  }

  public static String readTextFileResource(Context context, int resourceId) {
    String str = null;
    try {
      InputStream stream = context.getResources().openRawResource(resourceId);
      str = Files.readString(stream);
    } catch (Throwable e) {
      die("problem reading resource #" + resourceId, e);
    }
    return str;
  }

  /**
   * Install a filter on System.out so multiple linefeeds are not filtered by the logger
   */
  public static void prepareSystemOut() {
    AndroidSystemOutFilter.install();
  }

  public static boolean isUIThread() {
    return Thread.currentThread() == Looper.getMainLooper().getThread();
  }

  public static void assertUIThread() {
    if (isUIThread())
      return;
    throw new IllegalStateException("Attempt to call from non-UI thread " + nameOf(Thread.currentThread()));
  }

  public static void assertBgndThread() {
    if (!isUIThread())
      return;
    throw new IllegalStateException("Attempt to call from UI thread " + nameOf(Thread.currentThread()));
  }

  public static void showFreeMemory(Context context, String message) {
    if (context == null)
      throw new IllegalArgumentException();

    ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
    ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    activityManager.getMemoryInfo(mi);
    float availableMb = (float) (mi.availMem / 1048576.0);
    boolean bigJumpFlag = sPrevAvailableMem != 0 && (Math.abs(availableMb - sPrevAvailableMem) >= 1.0f);
    sPrevAvailableMem = availableMb;

    StringBuilder sb = new StringBuilder();
    sb.append(bigJumpFlag ? "*** " : "    ");
    sb.append(d(availableMb, 5, 1));
    sb.append(" Mb  ");
    if (message != null)
      sb.append(message);
    sb.append(" (");
    sb.append(stackTrace(1));
    sb.append(")");
    pr(sb);
  }

  private static float sPrevAvailableMem;

  private static class AndroidSystemOutFilter extends PrintStream {

    public static void install() {
      if (System.out instanceof AndroidSystemOutFilter)
        return;
      System.setOut(new AndroidSystemOutFilter());
    }

    private AndroidSystemOutFilter() {
      super(System.out, true);
    }

    // This seems to be the only method I need to override...
    @Override
    public void write(byte[] buffer, int offset, int length) {
      for (int i = 0; i < length; i++) {
        byte ch = buffer[offset + i];
        if (ch == '\n') {
          write(' ');
        }
        write(ch);
      }
    }
  }

}
