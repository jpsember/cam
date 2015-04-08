package com.js.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import com.js.basic.Files;
import com.js.basic.JSONTools;

import org.apache.commons.io.FilenameUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import static com.js.android.AndroidTools.*;
import static com.js.basic.Tools.*;

/**
 * Organizes the photos stored on user device, including background processing
 */
public class PhotoFile {

  private static final boolean SIMULATED_DELAYS = false;
  private static final boolean WITH_ASSERTIONS = true;

  public interface Listener {
    void stateChanged();

    void photoCreated(PhotoInfo photoInfo);
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
    Start, Opening, Open, Closing, Closed, Failed
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

    setState(State.Closing);
    mBackgroundThreadHandler.post(new Runnable() {
      public void run() {
        backgroundThreadCloseFile();
      }
    });
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

  public void createPhoto(final byte[] jpegData, final int rotationToApply) {
    assertOpen();
    mBackgroundThreadHandler.post(new Runnable() {
      public void run() {
        try {
          final PhotoInfo photoInfo = backgroundThreadCreatePhoto(jpegData, rotationToApply);
          mUIThreadHandler.post(new Runnable() {
            public void run() {
              mListener.photoCreated(photoInfo);
            }
          });
        } catch (IOException e) {
          bgndFail("create photo", e);
        }
      }
    });
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
    mListener.stateChanged();
  }

  private void assertUIThread() {
    if (!WITH_ASSERTIONS)
      return;
    if (isUIThread())
      return;
    throw new IllegalStateException("Attempt to call from non-UI thread " + nameOf(Thread.currentThread()));
  }

  private void setState(State state) {
    assertUIThread();
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

  // --------------- Methods called only within background thread
  // --------------- (consider putting these in their own class for simplicity?  Or add prefix?)

  private void assertBgndThread() {
    if (!WITH_ASSERTIONS)
      return;
    if (!isUIThread())
      return;
    throw new IllegalStateException("Attempt to call from non-bgnd thread " + nameOf(Thread.currentThread()));
  }

  private void backgroundThreadOpenFile() {
    assertBgndThread();
    trace("backgroundThreadOpenFile");

    if (SIMULATED_DELAYS)
      sleepFor(1200);

    do {

      if (!isExternalStorageWritable()) {
        setFailed("No writable external storage found");
        break;
      }
      if (!prepareRootDirectory()) {
        break;
      }
    } while (false);

    if (SIMULATED_DELAYS)
      sleepFor(1200);

    mUIThreadHandler.post(new Runnable() {
      public void run() {
        setState(State.Open);
        mListener.stateChanged();
      }
    });
  }

  private void backgroundThreadCloseFile() {
    assertBgndThread();

    trace("backgroundThreadCloseFile");

    try {
      flush();
    } catch (IOException e) {
      bgndFail("closing file", e);
    }

    mUIThreadHandler.post(new Runnable() {
      public void run() {
        setState(State.Closed);
      }
    });
  }

  private boolean prepareRootDirectory() {
    assertBgndThread();
    mRootDirectory = new File(mContext.getExternalFilesDir(null), "Photos");

    try {
      if (!mRootDirectory.exists()) {
        mRootDirectory.mkdir();
        if (!mRootDirectory.exists())
          return false;
        mModified = true;
        flush();
      } else {
        readFileState();
      }
      readPhotoRecords();
    } catch (IOException e) {
      bgndFail("preparing root", e);
      return false;
    }
    trace("Opened root directory " + mRootDirectory);

    return true;
  }

  private boolean isExternalStorageWritable() {
    assertBgndThread();
    String state = Environment.getExternalStorageState();
    if (Environment.MEDIA_MOUNTED.equals(state)) {
      return true;
    }
    return false;
  }

  private void flush() throws IOException {
    assertBgndThread();
    if (!mModified)
      return;
    writeFileState();
    mModified = false;
  }

  private void writeFileState() throws IOException {
    assertBgndThread();
    JSONObject map = new JSONObject();
    String jsonString;
    try {
      map.put("nextid", mNextPhotoId);
      jsonString = map.toString(4);
    } catch (JSONException e) {
      throw new IOException(e);
    }
    trace("Writing file state:\n" + jsonString);
    Files.writeString(getStateFile(), jsonString);
  }

  private File getStateFile() {
    assertBgndThread();
    return new File(mRootDirectory, "state");
  }

  private void readFileState() throws IOException {
    assertBgndThread();
    File stateFile = getStateFile();
    if (!stateFile.exists())
      return;
    String jsonString = Files.readString(stateFile);
    trace("Reading file state from: " + jsonString);
    try {
      JSONObject map = JSONTools.parseMap(jsonString);
      mNextPhotoId = map.getInt("nextid");
    } catch (JSONException e) {
      throw new IOException(e);
    }
  }

  private void bgndFail(Object message, Throwable t) {
    assertBgndThread();
    String failMessage = message.toString();
    if (t != null)
      failMessage += "; " + d(t);
    final String finalMessage = failMessage;

    mUIThreadHandler.post(new Runnable() {
      public void run() {
        setFailed(finalMessage);
      }
    });
  }

  private PhotoInfo backgroundThreadCreatePhoto(byte[] jpegData, int rotationToApply) throws IOException {
    assertBgndThread();
    Bitmap bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
    bitmap = BitmapTools.rotateBitmap(bitmap, rotationToApply);
    PhotoInfo info = createPhotoInfo();

    // Scale photo to size appropriate to starting state
    unimp("scale photo to starting state");

    File photoPath = getPhotoPath(info.getId());
    trace("Writing " + info + " to " + photoPath);
    OutputStream stream = new FileOutputStream(photoPath);
    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);

    return info;
  }

  private PhotoInfo createPhotoInfo() throws IOException {
    PhotoInfo info = PhotoInfo.create();
    info.setId(getUniquePhotoId());
    info.freeze();

    // Write photo info to file, and store in map
    File path = getPhotoInfoPath(info.getId());
    String content = info.toJSON();
    Files.writeString(path, content);
    trace("writing " + info + " to " + path + ", content=<" + content + ">");

    mPhotoMap.put(info.getId(), info);

    // Flush the changes, i.e. the unique id
    flush();
    return info;

    // TODO: we don't need to store the unique id, since we'll be reading all records into mem
  }

  private int getUniquePhotoId() {
    assertBgndThread();
    int id = mNextPhotoId;
    mNextPhotoId++;
    mModified = true;
    return id;
  }

  private File getPhotoPath(int photoId) {
    assertBgndThread();
    return new File(mRootDirectory, "" + photoId + ".jpg");
  }

  private File getPhotoInfoPath(int photoId) {
    assertBgndThread();
    return new File(mRootDirectory, "" + photoId + ".json");
  }

  private void readPhotoRecords() {
    mPhotoMap = new HashMap();
    File[] fList = mRootDirectory.listFiles();
    for (File file : fList) {
      if (file.isFile()) {
        String fileStr = file.getName();
        String extension = FilenameUtils.getExtension(fileStr);
        if (!extension.equals("jpg"))
          continue;
        String baseName = FilenameUtils.getBaseName(fileStr);
        int id;
        try {
          id = Integer.parseInt(baseName);
        } catch (NumberFormatException e) {
          continue;
        }
        PhotoInfo photoInfo;
        try {
          File photoInfoPath = getPhotoInfoPath(id);
          String jsonString = Files.readString(photoInfoPath);
          photoInfo = PhotoInfo.parseJSON(jsonString);
        } catch (Throwable t) {
          warning("Failed to read or parse " + file);
          continue;
        }
        mPhotoMap.put(photoInfo.getId(), photoInfo);
        trace("read " + photoInfo);
      }
    }
  }

  private boolean mTrace;
  private State mState;
  private String mFailureMessage;
  private File mRootDirectory;
  private final Context mContext;
  private final Listener mListener;
  private Handler mUIThreadHandler;
  private Handler mBackgroundThreadHandler;

  // These fields should only be accessed by the background thread

  private boolean mModified;
  private int mNextPhotoId;
  private Map<Integer, PhotoInfo> mPhotoMap;
}
