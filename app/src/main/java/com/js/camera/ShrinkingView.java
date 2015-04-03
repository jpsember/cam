package com.js.camera;

import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import com.js.android.UITools;

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

  private static final float sStateWeights[] = {1.0f, 1.5f, 2.0f,
      3.0f, 2.2f, 1.2f, .8f, .5f, .2f};

  @Override
  public boolean onTouch(View v, MotionEvent event) {
    if (event.getActionMasked() == MotionEvent.ACTION_UP) {
      mViewState = (mViewState + 1) % sStateWeights.length;
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
        mNormalWeight * sStateWeights[mViewState]);
    mGreenViewContainer.removeView(this);
    mGreenViewContainer.addView(this, mPositionInParent, param);
  }

  private int mViewState;
  private LinearLayout mGreenViewContainer;
  private float mNormalWeight;
  private int mPositionInParent;
}
