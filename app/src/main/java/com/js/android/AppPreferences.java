package com.js.android;

import java.util.Map;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * Simpler interface to application preferences.  Thread safe.
 * <p/>
 * Preferences are a map of (key,value) pairs, where keys are Strings.
 * <p/>
 * They are stored via the 'putTYPE(key, value)' methods.
 * <p/>
 * They are read via the 'getTYPE(key, defaultValue)' methods, where defaultValue
 * is the value to return if no such preference has been stored.
 */
public class AppPreferences {

  /**
   * No objects of this class can be constructed
   */
  private AppPreferences() {
  }

  /**
   * Prepare the preferences; must be called before any other methods.
   *
   * @param context application or activity context; if not an activity, the preferences
   *                are cleared
   */
  public static void prepare(Context context) {
    if (context instanceof Activity) {
      if (sPreferences != null)
        throw new IllegalStateException("Preferences already prepared");

      sPreferences = ((Activity) context).getPreferences(Context.MODE_PRIVATE);
    } else {
      sPreferences = context.getSharedPreferences("__test_preferences__",
          Context.MODE_PRIVATE);
      sPreferences.edit().clear().apply();
    }
  }

  public static void putString(String key, String value) {
    preferences().edit().putString(key, value).apply();
  }

  public static String getString(String key, String defaultValue) {
    return preferences().getString(key, defaultValue);
  }

  /**
   * Store a set of string values
   *
   * @param map map with string values
   */
  public static void putStrings(Map<String, String> map) {
    SharedPreferences.Editor editor = preferences().edit();
    for (Map.Entry<String, String> entry : map.entrySet()) {
      editor.putString(entry.getKey(), entry.getValue());
    }
    editor.apply();
  }

  public static void putInt(String key, int value) {
    preferences().edit().putInt(key, value).apply();
  }

  public static int getInt(String key, int defaultValue) {
    return preferences().getInt(key, defaultValue);
  }

  public static void putBoolean(String key, boolean value) {
    preferences().edit().putBoolean(key, value).apply();
  }

  public static boolean getBoolean(String key, boolean defaultValue) {
    return preferences().getBoolean(key, defaultValue);
  }

  /**
   * Toggle boolean
   *
   * @return new value
   */
  public static boolean toggle(String preferencesKey) {
    boolean val = !getBoolean(preferencesKey, false);
    putBoolean(preferencesKey, val);
    return val;
  }

  /**
   * Remove preference value (if it exists)
   */
  public static void remove(String preferencesKey) {
    preferences().edit().remove(preferencesKey).apply();
  }

  /**
   * Construct a unique (integer) identifier associated with a key
   *
   * @return one plus the previous value constructed, or 1000 if no previous value exists
   */
  public static synchronized int getUniqueIdentifier(String key) {
    int value = preferences().getInt(key, 1000);
    putInt(key, 1 + value);
    return value;
  }

  private static SharedPreferences preferences() {
    if (sPreferences == null)
      throw new IllegalStateException("AppPreferences not prepared");
    return sPreferences;
  }

  private static SharedPreferences sPreferences;

}
