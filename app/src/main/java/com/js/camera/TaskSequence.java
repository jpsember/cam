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

  /**
   * Start the sequence of tasks
   */
  public void start() {
    assertStarted(false);
    setState(State.STARTED);
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
   * @return this a convenience for chaining
   */
  public TaskSequence addSimulatedDelays(int ms) {
    assertStarted(false);
    if (ms > 0) {
      nSleepTime = ms;
      nRandom = new Random();
    }
    return this;
  }

  /**
   * Execute the next stage of the task.  The last task must call finish() when it has completed
   *
   * @param stageNumber stage number (0...n-1)
   */
  protected abstract void execute(int stageNumber);

  /**
   * Stop the task sequence abnormally
   */
  protected void abort() {
    setState(State.ABORTED);
  }

  /**
   * Stop the task sequence due to its having completed
   */
  protected void finish() {
    setState(State.FINISHED);
  }

  private void startNextStage() {
    final int MAX_STAGES = 100;
    if (nStage == MAX_STAGES)
      throw new IllegalStateException("runaway task");
    if (nStage % 2 == 0)
      AppState.postBgndEvent(nRunnable);
    else
      AppState.postUIEvent(nRunnable);
  }

  private void runAux() {
    // Do a delay if simulated delay specified
    if (nRandom != null) {
      float f = (nRandom.nextFloat() * .8f) + .6f;
      int delay = (int) (f * nSleepTime);
      sleepFor(delay);
    }
    execute(nStage);
    if (nState != State.STARTED)
      return;
    nStage++;
    startNextStage();
  }

  private void assertStarted(boolean started) {
    if (nState.equals(State.STARTED) != started)
      throw new IllegalStateException();
  }

  private enum State {
    WAITING,
    STARTED,
    ABORTED,
    FINISHED,
  }

  private void setState(State state) {
    if (nState == State.ABORTED || nState == State.FINISHED)
      return;
    nState = state;
  }

  private State nState = State.WAITING;
  private Runnable nRunnable;
  private int nStage;
  // Only used if a simulated delay has been specified:
  private Random nRandom;
  private int nSleepTime;
}
