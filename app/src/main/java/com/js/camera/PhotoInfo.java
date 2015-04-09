package com.js.camera;

import com.js.basic.Freezable;

import org.json.JSONException;
import org.json.JSONObject;

import static com.js.basic.Tools.*;

/**
 * Information about a photo, including when it was created, and its current age state
 */
public class PhotoInfo extends Freezable.Mutable {

  @Override
  public Freezable getMutableCopy() {
    PhotoInfo p = new PhotoInfo();
    p.mCreationTime = mCreationTime;
    p.mCurrentAgeState = mCurrentAgeState;
    p.mTargetAgeState = mTargetAgeState;
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
   * Build a PhotoInfo object that has only its id defined, suitable for a Comparator argument
   */
  public static PhotoInfo buildSentinel(int id) {
    PhotoInfo sentinel = new PhotoInfo();
    sentinel.mId = id;
    return sentinel;
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

  public void setCurrentAgeState(int ageState) {
    mutate();
    mCurrentAgeState = ageState;
  }

  public int getCurrentAgeState() {
    return mCurrentAgeState;
  }

  public void setTargetAgeState(int ageState) {
    if (ageState < mCurrentAgeState)
      throw new IllegalArgumentException();
    mutate();
    mTargetAgeState = ageState;
  }

  public int getTargetAgeState() {
    return mTargetAgeState;
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
    s = s + " state(curr=" + getCurrentAgeState() + ", target=" + getTargetAgeState() + ")";
    return s;
  }

  public String toJSON() {
    assertFrozen();
    if (mJSON == null) {
      synchronized (this) {
        if (mJSON == null) {
          try {
            JSONObject map = new JSONObject();
            map.put("id", getId());
            map.put("created", getCreationTime());
            map.put("currentstate", getCurrentAgeState());
            map.put("desiredstate", getTargetAgeState());
            mJSON = map.toString();
          } catch (JSONException e) {
            die(e);
          }
        }
      }
    }
    return mJSON;
  }

  public static PhotoInfo parseJSON(String jsonString) throws JSONException {
    JSONObject map = new JSONObject(jsonString);
    PhotoInfo p = new PhotoInfo();
    p.setId(map.getInt("id"));
    p.setCreationTime(map.getInt("created"));
    p.setCurrentAgeState(map.getInt("currentstate"));
    p.setTargetAgeState(map.getInt("desiredstate"));
    p.freeze();
    return p;
  }

  private int mCreationTime;
  private int mCurrentAgeState;
  private int mTargetAgeState;
  private int mId;
  private volatile String mJSON;
}
