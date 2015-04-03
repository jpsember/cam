package com.example.cam;

import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import com.js.android.UITools;
import static com.js.basic.Tools.*;

/**
 * A view that toggles between large and small size when touched
 * (to spur resize events in other views)
 */
public class ShrinkingView extends View implements View.OnTouchListener {

  /**
   * Construct a view, and add to a container
   */
  public static void build(LinearLayout container, float weight) {
    new ShrinkingView(container, weight);
  }

  private ShrinkingView(LinearLayout container, float normalWeight) {
    super(container.getContext());
    mGreenViewContainer = container;
    mNormalWeight = normalWeight;
    mPositionInParent = container.getChildCount();
    setBackgroundColor(UITools.debugColor(mPositionInParent));
    setOnTouchListener(this);
    addToContainer();
  }

  @Override
  public boolean onTouch(View v, MotionEvent event) {
    if (event.getActionMasked() == MotionEvent.ACTION_UP) {
      mGreenViewCollapsed ^= true;
      addToContainer();
    }
    return true;
  }

  /**
   * Remove ourselves from the container, and add ourselves again, with appropriate weight
   * reflecting collapsed state
   */
  private void addToContainer() {
    LinearLayout.LayoutParams param = UITools.layoutParams(mGreenViewContainer,
        mNormalWeight * (mGreenViewCollapsed ? .3f : 1.0f));
    pr("Replacing ShrinkingView "+nameOf(this)+" within its container");
    mGreenViewContainer.removeView(this);
    mGreenViewContainer.addView(this, mPositionInParent, param);
  }

  private boolean mGreenViewCollapsed;
  private LinearLayout mGreenViewContainer;
  private float mNormalWeight;
  private int mPositionInParent;
}
