package com.js.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.AbsListView;
import android.widget.LinearLayout;

import static com.js.basic.Tools.*;

public final class UITools {

  public static final boolean SET_DEBUG_COLORS = false;

  private static int debugColors[] = {
      //
      // check out http://www.colorpicker.com/
      //
      0x10, 0x10, 0xe0, // dark blue
      0x37, 0x87, 0x3E, // dark green
      0x73, 0x5E, 0x22, // brown
      0xC7, 0x32, 0x00, // dark red
      0x8C, 0x26, 0xBF, // purple
      0x82, 0xB6, 0xBA, // blue/gray
      0xA3, 0x62, 0x84, // plum
      0xC7, 0x92, 0x00, // burnt orange
  };

  public static int debugColor() {
    return debugColor(sDebugColorIndex++);
  }

  public static int debugColor(int index) {
    index = myMod(index, debugColors.length / 3) * 3;
    return Color.argb(255, debugColors[index], debugColors[index + 1],
        debugColors[index + 2]);
  }

  public static void applyDebugColors(View view) {
    if (SET_DEBUG_COLORS)
      view.setBackgroundColor(debugColor());
  }

  /**
   * Apply a background color to a view, and print a warning
   */
  public static void applyTestColor(View view, int color) {
    warning("applying test color to view " + nameOf(view));
    view.setBackgroundColor(color);
  }

  /**
   * Construct a LinearLayout
   *
   * @param verticalOrientation true if it is to have a vertical orientation
   */
  public static LinearLayout linearLayout(Context context,
                                          boolean verticalOrientation) {
    LinearLayout view = new LinearLayout(context);
    view.setOrientation(verticalOrientation ? LinearLayout.VERTICAL
        : LinearLayout.HORIZONTAL);
    applyDebugColors(view);
    return view;
  }

  /**
   * Construct LayoutParams for child views of a LinearLayout container.
   * <p/>
   * The conventions being followed are:
   * <p/>
   * If the container has horizontal orientation, then the 'matched' dimension
   * is height, and the 'variable' dimension is width. Otherwise, matched =
   * width and variable = height.
   * <p/>
   * A view is either 'stretchable' or 'fixed' in its variable dimension. If
   * it's fixed, it is assumed that the view has some content, e.g. so that
   * setting WRAP_CONTENT works properly (it won't for Views that have no
   * content; see issue #5).
   * <p/>
   * Setting the weight parameter to zero indicates that the view is
   * stretchable, whereas a positive weight indicates that it's fixed.
   * <p/>
   * The LayoutParams constructed will have
   * <p/>
   * a) MATCH_PARENT in their matched dimension;
   * <p/>
   * b) either zero (if the view is stretchable) or WRAP_CONTENT (if it is
   * fixed) in its variable dimension
   * <p/>
   * c) weight in its weight field
   *
   * @param verticalOrientation true iff the containing LinearLayout has vertical orientation
   * @return LayoutParams appropriate to the container's orientation
   */
  public static LinearLayout.LayoutParams layoutParams(
      boolean verticalOrientation, float weight) {

    int width, height;
    int variableSize = (weight != 0) ? 0 : LayoutParams.WRAP_CONTENT;
    if (!verticalOrientation) {
      width = variableSize;
      height = LayoutParams.MATCH_PARENT;
    } else {
      width = LayoutParams.MATCH_PARENT;
      height = variableSize;
    }
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width,
        height);
    params.weight = weight;
    return params;
  }

  public static LinearLayout.LayoutParams layoutParams(LinearLayout container,
                                                       float weight) {
    return layoutParams(container.getOrientation() == LinearLayout.VERTICAL,
        weight);
  }

  /**
   * Get (brief) information about a MotionEvent
   * <p/>
   * This is similar to MotionEvent.actionToString(...), but that method is not
   * supported for older API levels
   */
  public static String dump(MotionEvent event) {
    if (event == null)
      return d((MotionEvent) null);

    int action = event.getActionMasked();
    int index = event.getActionIndex();
    StringBuilder sb = new StringBuilder("ACTION_");
    switch (action) {
      default:
        sb.append("***UNKNOWN:" + action + "***");
        break;
      case MotionEvent.ACTION_CANCEL:
        sb.append("CANCEL");
        break;
      case MotionEvent.ACTION_DOWN:
        sb.append("DOWN");
        break;
      case MotionEvent.ACTION_UP:
        sb.append("UP");
        break;
      case MotionEvent.ACTION_MOVE:
        sb.append("MOVE");
        break;
      case MotionEvent.ACTION_POINTER_DOWN:
        sb.append("DOWN(" + index + ")");
        break;
      case MotionEvent.ACTION_POINTER_UP:
        sb.append("UP(" + index + ")");
        break;
    }
    return sb.toString();
  }

  private static String layoutElement(int n) {
    switch (n) {
      case LayoutParams.MATCH_PARENT:
        return "MATCH_PARENT";
      case LayoutParams.WRAP_CONTENT:
        return "WRAP_CONTENT";
      default:
        return d(n, 11);
    }
  }

  public static String dump(android.view.ViewGroup.LayoutParams p) {
    StringBuilder sb = new StringBuilder("LayoutParams");
    sb.append(" width:" + layoutElement(p.width));
    sb.append(" height:" + layoutElement(p.height));
    if (p instanceof LinearLayout.LayoutParams) {
      LinearLayout.LayoutParams p2 = (LinearLayout.LayoutParams) p;
      sb.append(" weight:" + com.js.basic.Tools.d(p2.weight));
    }
    return sb.toString();
  }

  private static final int POSITIVE_INTEGER_OFFSET = 1;

  /**
   * Save integer value prior to leaving an activity
   */
  public static void persist(Bundle state, String key, int value) {
    state.putInt(key, value);
  }

  /**
   * Save object's state prior to leaving an activity
   *
   * @param object a widget;
   *               a warning is generated if object type unsupported
   */
  public static void persist(Bundle state, String key, Object object) {
    if (object == null)
      return;

    if (object instanceof AbsListView) {
      AbsListView absListView = (AbsListView) object;
      String ourKey = "absListView:" + key;
      state.putInt(ourKey, POSITIVE_INTEGER_OFFSET + absListView.getFirstVisiblePosition());
      return;
    }
    warning("unable to persist " + nameOf(object));
  }

  /**
   * Restore integer value
   *
   * @param activity           activity, to read value from intent if found
   * @param savedInstanceState bundle being resumed from; if state is found here,
   *                           it overrides any value found in the intent
   */
  public static int restore(Activity activity, Bundle savedInstanceState, String key, int defaultValue) {
    int value = defaultValue;
    if (savedInstanceState != null && savedInstanceState.containsKey(key)) {
      value = savedInstanceState.getInt(key);
    } else {
      Intent intent = activity.getIntent();
      Bundle b = intent.getExtras();
      if (b != null && b.containsKey(key)) {
        value = b.getInt(key);
      }
    }
    return value;
  }

  /**
   * Restore widget's state
   *
   * @param activity           activity, to read state from intent if found
   * @param savedInstanceState bundle being resumed from; if state is found here,
   *                           it overrides any value found in the intent
   */
  public static void restore(Activity activity, Bundle savedInstanceState, String key, Object object) {

    if (object instanceof AbsListView) {
      AbsListView absListView = (AbsListView) object;
      String ourKey = "absListView:" + key;
      int scrollPosition = 0;
      if (savedInstanceState != null) {
        scrollPosition = savedInstanceState.getInt(ourKey);
      }
      if (scrollPosition == 0) {
        Intent intent = activity.getIntent();
        Bundle b = intent.getBundleExtra("widgets");
        if (b != null) {
          scrollPosition = b.getInt(ourKey);
        }
      }
      if (scrollPosition != 0)
        absListView.setSelection(scrollPosition - POSITIVE_INTEGER_OFFSET);
      return;
    }
    warning("unable to restore " + nameOf(object));
  }

  public static void prepare(Context context) {
    if (prepared())
      return;
    sDisplayMetrics = context.getResources().getDisplayMetrics();
    sConfiguration = context.getResources().getConfiguration();
  }

  public static DisplayMetrics displayMetrics() {
    if (!prepared()) throw new IllegalStateException("UITools not prepared");
    return sDisplayMetrics;
  }

  public static Configuration configuration() {
    if (!prepared()) throw new IllegalStateException("UITools not prepared");
    return sConfiguration;
  }

  public static int dpToPixels(float densityPixels) {
    return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, densityPixels, displayMetrics());
  }

  public static int screenSize() {
    return configuration().screenLayout &
        Configuration.SCREENLAYOUT_SIZE_MASK;
  }

  /**
   * Determine if device is 'smallish'; normal or smaller
   */
  public static boolean smallScreenSize() {
    return screenSize() <= Configuration.SCREENLAYOUT_SIZE_NORMAL;
  }

  private static boolean prepared() {
    return sConfiguration != null;
  }

  private static Configuration sConfiguration;
  private static DisplayMetrics sDisplayMetrics;
  private static int sDebugColorIndex;

}
