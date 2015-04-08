package com.js.camera;

import com.js.basic.Freezable;

import static com.js.basic.Tools.*;

/**
 * Information about a photo, including when it was created, and its current age state
 */
public class PhotoInfo extends Freezable.Mutable {

  @Override
  public Freezable getMutableCopy() {
    PhotoInfo p = new PhotoInfo();
    p.mCreationTime = mCreationTime;
    p.mAgeState = mAgeState;
    p.mId = mId;
    return p;
  }

  private PhotoInfo() {
  }

  public static PhotoInfo create() {
    PhotoInfo p = new PhotoInfo();
    p.setCreationTime((int) (System.currentTimeMillis() / 1000));
    return p;
  }

  /**
   * Set the time this photo was created
   */
  public void setCreationTime(int secondsSinceEpoch) {
    mutate();
    mCreationTime = secondsSinceEpoch;
  }

  public int getCreationTime() {
    return mCreationTime;
  }

  public void setAgeState(int ageState) {
    mutate();
    mAgeState = ageState;
  }

  public int getAgeState() {
    return mAgeState;
  }

  public void setId(int id) {
    mutate();
    mId = id;
  }

  public int getId() {
    return mId;
  }

  @Override
  public String toString() {
    String s = "PhotoInfo";
    s = s + " id " + d(getId());
    s = s + " age " + getAgeState();
    return s;
  }

  private int mCreationTime;
  private int mAgeState;
  private int mId;
}
