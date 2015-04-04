package com.js.android;

import java.io.InputStream;
import java.io.PrintStream;

import com.js.basic.Files;

import android.content.Context;
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
