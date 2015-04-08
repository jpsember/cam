package com.js.camera;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import java.io.File;

import static com.js.android.AndroidTools.*;
import static com.js.basic.Tools.*;

/**
 * Organizes the photos stored on user device, including background processing
 */
public class PhotoFile {

  private static final boolean SIMULATED_DELAYS = false;

  public interface Listener {
    void stateChanged();
  }

  public PhotoFile(Context context, Listener listener) {
    if (listener == null || context == null)
      throw new IllegalArgumentException();
    mContext = context;
    mListener = listener;
    mState = State.Start;
    setTrace(true);
    doNothing();
    doNothingAndroid();
  }

  public enum State {
    Start, Opening, Open, Closed, Failed
  }

  public State state() {
    return mState;
  }

  public void open() {
    assertUIThread();
    if (mState != State.Start)
      throw new IllegalStateException();

    trace("open()");

    openBackgroundHandler();

    setState(State.Opening);

    mBackgroundThreadHandler.post(new Runnable() {
      public void run() {
        backgroundThreadOpenFile();
      }
    });
  }

  public boolean isOpen() {
    return mState == State.Open;
  }

  public void close() {
    assertUIThread();
    trace("close()");
    if (!isOpen())
      return;
    setState(State.Closed);
  }

  public void assertOpen() {
    if (!isOpen())
      throw new IllegalStateException("File not open");
  }

  public void setTrace(boolean state) {
    mTrace = state;
    if (state)
      warning("Turning tracing on");
  }

  public String getFailureMessage() {
    return mFailureMessage;
  }

  private static boolean isUIThread() {
    return Thread.currentThread() == Looper.getMainLooper().getThread();
  }

  private void trace(Object msg) {
    if (mTrace) {
      String threadMessage = "";
      if (!isUIThread()) {
        threadMessage = "(" + nameOf(Thread.currentThread()) + ") ";
      }
      pr("--      PhotoFile " + threadMessage + "--: " + msg);
    }
  }

  private void setFailed(String message) {
    if (mState == State.Failed)
      return;
    setState(State.Failed);
    mFailureMessage = message;
    trace("Failed with message " + message);
  }

  private void backgroundThreadOpenFile() {

    trace("backgroundThreadOpenFile");

    if (SIMULATED_DELAYS)
      sleepFor(1200);

    do {

      if (!isExternalStorageWritable()) {
        setFailed("No writable external storage found");
        break;
      }
      if (!prepareRootDirectory()) {
        setFailed("Failed to prepare root directory");
        break;
      }
    } while (false);

    if (SIMULATED_DELAYS)
      sleepFor(1200);

    mUIThreadHandler.post(new Runnable() {
      public void run() {
        mListener.stateChanged();
      }
    });
  }

  private boolean prepareRootDirectory() {
    mRootDirectory = new File(mContext.getExternalFilesDir(null), "Photos");
    if (!mRootDirectory.exists())
      mRootDirectory.mkdir();
    if (!mRootDirectory.exists())
      return false;
    trace("Opened root directory " + mRootDirectory);
    return true;
  }

  private void assertUIThread() {
    if (isUIThread())
      return;
    throw new IllegalStateException("Attempt to call from non-UI thread " + nameOf(Thread.currentThread()));
  }

  private void setState(State state) {
    if (mState != state) {
      trace("Changing state from " + mState + " to " + state);
      mState = state;
    }
  }

  private void openBackgroundHandler() {
    mUIThreadHandler = new Handler(Looper.getMainLooper());
    HandlerThread backgroundThreadHandler = new HandlerThread("PhotoFile background thread");
    backgroundThreadHandler.start();
    mBackgroundThreadHandler = new Handler(backgroundThreadHandler.getLooper());
  }

  private boolean isExternalStorageWritable() {
    String state = Environment.getExternalStorageState();
    if (Environment.MEDIA_MOUNTED.equals(state)) {
      return true;
    }
    return false;
  }

  private boolean mTrace;
  private State mState;
  private String mFailureMessage;
  private File mRootDirectory;
  private final Context mContext;
  private final Listener mListener;
  private Handler mUIThreadHandler;
  private Handler mBackgroundThreadHandler;
}
