package com.js.camera;

import com.js.basic.MyMath;

import java.util.Random;

import static com.js.basic.Tools.*;

public class PerlinNoise {

  public static enum Interpolation {
    LINEAR,
    CUBIC,
    QUINTIC,
  }

  public void setMaxGradients(int maxGradients) {
    if (gridBuilt()) throw new IllegalStateException();
    if (maxGradients < 2) throw new IllegalArgumentException();
    mMaxGradients = maxGradients;
  }

  private static int hash(int x) {
    x = ((x >>> 16) ^ x) * 0x45d9f3b;
    x = ((x >>> 16) ^ x) * 0x45d9f3b;
    x = ((x >>> 16) ^ x);
    return x;
  }

  public void buildGrid() {
    if (gridBuilt()) throw new IllegalStateException();
    buildGradients();
  }

  public void setInterpolation(Interpolation interpolation) {
    mInterpolation = interpolation;
  }

  private int getGradientIndex(int xGrid, int yGrid) {
    // Derive an integer key from vertex coordinates
    final int PRIME = 101561;
    int vertexKey = yGrid * PRIME + xGrid;
    // Pick a pseudorandom integer [0..max) using key as seed
    return myMod(hash(vertexKey), mGradients.length / 2);
  }

  private float dotGridGradient(float xGrid, float yGrid, float xQuery, float yQuery) {
    int gradientIndex = 2 * getGradientIndex((int) xGrid, (int) yGrid);
    float dx = xQuery - xGrid;
    float dy = yQuery - yGrid;
    float xGrad = mGradients[gradientIndex + 0];
    float yGrad = mGradients[gradientIndex + 1];
    return (xGrad * dx) + (yGrad * dy);
  }

  private float interpolate(float a0, float a1, float w) {
    switch (mInterpolation) {
      case LINEAR:
        break;
      case CUBIC: {
        float w2 = w * w;
        float w3 = w2 * w;
        w = -2 * w3 + 3 * w2;
      }
      break;
      case QUINTIC: {
        float w2 = w * w;
        float w3 = w2 * w;
        float w4 = w3 * w;
        float w5 = w4 * w;
        w = 6 * w5 - 15 * w4 + 10 * w3;
      }
      break;
    }
    return (1.0f - w) * a0 + w * a1;
  }

  /**
   * Evaluate noise value at a pixel, where integer portion of coordinate
   * represents grid cell index.
   *
   * @return value within [-1..1)
   */
  public float noiseAt(float x, float y) {
    int cellX = (int) Math.floor(x);
    int cellY = (int) Math.floor(y);

    float gridX = cellX;
    float gridY = cellY;

    float sx = x - gridX;
    float sy = y - gridY;

    float d00 = dotGridGradient(gridX + 0, gridY, x, y);
    float d10 = dotGridGradient(gridX + 1, gridY, x, y);
    float aValue = interpolate(d00, d10, sx);

    gridY += 1;
    float d01 = dotGridGradient(gridX + 0, gridY, x, y);
    float d11 = dotGridGradient(gridX + 1, gridY, x, y);
    float bValue = interpolate(d01, d11, sx);

    float value = interpolate(aValue, bValue, sy);
    return value;
  }

  private void buildGradients() {
    Random r = new Random(mSeed);

    int numGradients = mMaxGradients;
    mGradients = new float[2 * numGradients];

    int cursor = 0;
    for (int g = 0; g < numGradients; g++) {
      float angle = r.nextFloat() * MyMath.PI * 2;
      float cx = (float) Math.cos(angle);
      float cy = (float) Math.sin(angle);
      mGradients[cursor + 0] = cx;
      mGradients[cursor + 1] = cy;
      cursor += 2;
    }
  }

  public void setSeed(int seed) {
    if (gridBuilt()) throw new IllegalStateException();
    mSeed = seed;
  }

  private boolean gridBuilt() {
    return mGradients != null;
  }

  private float[] mGradients;
  private Interpolation mInterpolation = Interpolation.CUBIC;
  private int mMaxGradients = 37;
  private int mSeed = 1;
}
