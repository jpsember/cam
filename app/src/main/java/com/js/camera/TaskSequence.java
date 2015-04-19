package com.js.camera;

import java.util.Random;

import static com.js.basic.Tools.*;

/**
 * Coordinates a sequence of tasks to execute alternating between
 * UI and background threads.
 * <p/>
 * Each task has a stage number, starting at zero.  Those with even
 * numbers execute on the background thread; those with odd execute
 * on the UI thread.
 * <p/>
 * Clients can use subclasses of TaskSequence with appropriate instance fields.
 * These fields will not require synchronization, since each stage's execution
 * will not overlap despite occurring on different threads.
 */
public abstract class TaskSequence {

  private final int MAX_STAGES = 100;

  /**
   * Start the sequence of tasks
   */
  public void start() {
    assertStarted(false);

    nRunnable = new Runnable() {
      @Override
      public void run() {
        runAux();
      }
    };

    startNextStage();
  }

  /**
   * Add simulated delays between each stage
   *
   * @param ms delay in ms
   */
  public void addSimulatedDelays(int ms) {
    assertStarted(false);
    if (ms > 0) {
      nSleepTime = ms;
      nRandom = new Random();
    }
  }

  /**
   * Execute the next stage of the task
   *
   * @param stageNumber stage number (0...n-1)
   * @return true if task is complete (or should be aborted); false if there are more stages
   */
  protected abstract boolean execute(int stageNumber);

  private void startNextStage() {
    if (nStage == MAX_STAGES)
      throw new IllegalStateException("runaway task");
    if (nStage % 2 == 0)
      AppState.postBgndEvent(nRunnable);
    else
      AppState.postUIEvent(nRunnable);
  }

  private void runAux() {
    if (nRandom != null) {
      float f = (nRandom.nextFloat() * .8f) + .6f;
      int delay = (int) (f * nSleepTime);
      sleepFor(delay);
    }

    if (execute(nStage))
      return;
    nStage++;
    startNextStage();
  }

  private boolean started() {
    return nRunnable != null;
  }

  private void assertStarted(boolean started) {
    if (started() != started)
      throw new IllegalStateException();
  }

  private Runnable nRunnable;
  private int nStage;
  private Random nRandom;
  private int nSleepTime;
}
