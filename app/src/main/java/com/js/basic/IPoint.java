package com.js.basic;

import static com.js.basic.Tools.*;

public final class IPoint {

  public IPoint() {
  }

  public IPoint(float x, float y) {
    this.x = (int) x;
    this.y = (int) y;
  }

  public IPoint(int x, int y) {
    this.x = x;
    this.y = y;
  }

  public int x() {
    return x;
  }

  public int y() {
    return y;
  }

  @Override
  public String toString() {
    return d(x) + " " + d(y);
  }

  public String dumpUnlabelled() {
    return d(x) + " " + d(y) + " ";
  }

  public Point toPoint() {
    return new Point(x, y);
  }

  public int x;
  public int y;
}
