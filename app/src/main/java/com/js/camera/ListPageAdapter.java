package com.js.camera;

import android.support.v4.view.PagerAdapter;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * A page adapter which works with a large data set by reusing views
 * <p/>
 * Taken from:
 * http://stackoverflow.com/questions/18117754/using-page-adapter-without-fragments
 */
abstract class ListPageAdapter<T> extends PagerAdapter {

  @Override
  public int getCount() {
    return mItems.size();
  }

  @Override
  public boolean isViewFromObject(View v, Object obj) {
    return v == mBoundViews.get(mItems.indexOf(obj));
  }

  @Override
  public void destroyItem(ViewGroup container, int position, Object object) {
    View view = mBoundViews.get(position);
    if (view != null) {
      mRecycledViews.add(view);
      mBoundViews.remove(position);
      container.removeView(view);
    }
  }

  @Override
  public Object instantiateItem(ViewGroup container, int position) {
    View child = mRecycledViews.isEmpty() ?
        createView(position) :
        mRecycledViews.remove(0);

    T data = mItems.get(position);
    initView(child, data, position);

    mBoundViews.append(position, child);
    container.addView(child, 0);
    return data;
  }

  /**
   * Construct a view to display an item
   */
  public abstract View createView(int position);

  /**
   * Prepare view for displaying an item
   */
  public abstract void initView(View v, T item, int position);

  /**
   * Remove all items
   * <p/>
   * A call must be made to notifyDataSetChanged() at some point after this method
   */
  public void clear() {
    mItems.clear();
  }

  /**
   * Add an item to the end of the list
   * <p/>
   * A call must be made to notifyDataSetChanged() at some point after this method
   */
  public void add(T item) {
    mItems.add(item);
  }

  /**
   * Remove an item at a particular position
   * <p/>
   * A call must be made to notifyDataSetChanged() at some point after this method
   *
   * @return the removed item
   */
  public T remove(int position) {
    return mItems.remove(position);
  }

  // Views that can be reused.
  private final List<View> mRecycledViews = new ArrayList<View>();
  // Views that are already in use.
  private final SparseArray<View> mBoundViews = new SparseArray<View>();

  private final ArrayList<T> mItems = new ArrayList<T>();
}