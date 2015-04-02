package com.js.android;

import java.io.InputStream;

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

}
