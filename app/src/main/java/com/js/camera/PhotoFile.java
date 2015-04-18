package com.js.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.Handler;

import com.js.basic.Files;
import com.js.basic.JSONTools;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Observable;
import java.util.SortedSet;
import java.util.TreeSet;

import static com.js.android.AndroidTools.*;
import static com.js.basic.Tools.*;

/**
 * Organizes the photos stored on user device, including background processing
 */
public class PhotoFile extends Observable {

  public static final boolean SIMULATED_DELAYS = true;
  private static final boolean WITH_ASSERTIONS = true;
  // Start with a fresh photo directory on each run?
  private static final boolean DELETE_ROOT_DIRECTORY = false;

  public static enum Event {
    StateChanged,
    PhotoCreated,
    BitmapConstructed,
  }

  public PhotoFile(Context context) {
    if (context == null)
      throw new IllegalArgumentException();
    mContext = context;
    mState = State.Start;
//    setTrace(true);
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

    mBackgroundThreadHandler = AppState.buildBackgroundHandler("PhotoFile");

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
          AppState.postUIEvent(new Runnable() {
            public void run() {
              notifyEventObservers(Event.PhotoCreated, photoInfo);
            }
          });
        } catch (IOException e) {
          bgndFail("create photo", e);
        }
      }
    });
  }

  public List<PhotoInfo> getPhotos(int startId, int maxCount) {
    ArrayList<PhotoInfo> list = new ArrayList();
    PhotoInfo sentinel = PhotoInfo.buildSentinel(startId);
    synchronized (mPhotoSet) {
      SortedSet<PhotoInfo> view = mPhotoSet.tailSet(sentinel);
      for (PhotoInfo info : view) {
        if (list.size() >= maxCount)
          break;
        list.add(info);
      }
    }
    return list;
  }

  /**
   * Construct a suitably aged bitmap for a photo
   */
  public void getBitmap(final PhotoInfo photoInfo) {
    assertOpen();
    photoInfo.assertFrozen();
    mBackgroundThreadHandler.post(new Runnable() {
      public void run() {
        constructBitmap(photoInfo);
      }
    });
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
    notifyEventObservers(Event.StateChanged);
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

    if (SIMULATED_DELAYS) {
      pr("photoFile sleeping...");
      sleepFor(3000);
      pr("photoFile waking up...");
    }

    AppState.postUIEvent(new Runnable() {
      public void run() {
        setState(State.Open);
        notifyEventObservers(Event.StateChanged);
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

    AppState.postUIEvent(new Runnable() {
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
        if (DELETE_ROOT_DIRECTORY) {
          warning("deleting photos root directory");
          FileUtils.cleanDirectory(mRootDirectory);
        }
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

  private static final String KEY_NEXTID = "nextid";
  private static final String KEY_RANDOMSEED = "randomseed";

  private void writeFileState() throws IOException {
    assertBgndThread();
    JSONObject map = new JSONObject();
    String jsonString;
    try {
      map.put(KEY_NEXTID, mNextPhotoId);
      map.put(KEY_RANDOMSEED, mRandomSeed);
      jsonString = map.toString();
    } catch (JSONException e) {
      throw new IOException(e);
    }
    trace("Writing file state: " + jsonString);
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
    trace("Reading file state: " + jsonString);
    try {
      JSONObject map = JSONTools.parseMap(jsonString);
      mNextPhotoId = map.getInt(KEY_NEXTID);
      mRandomSeed = map.optInt(KEY_RANDOMSEED, 1);
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

    AppState.postUIEvent(new Runnable() {
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

    File photoPath = getPhotoBitmapPath(info.getId());
    trace("Writing " + info + " to " + photoPath);
    OutputStream stream = new FileOutputStream(photoPath);
    bitmap.compress(Bitmap.CompressFormat.JPEG, PhotoInfo.JPEG_QUALITY_MAX, stream);

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

    synchronized (mPhotoSet) {
      mPhotoSet.add(info);
    }

    // Flush the changes, i.e. the unique id
    flush();
    return info;
  }

  private int getUniquePhotoId() {
    assertBgndThread();
    int id = mNextPhotoId;
    mNextPhotoId++;
    mModified = true;
    return id;
  }

  private File getPhotoBitmapPath(int photoId) {
    assertBgndThread();
    return new File(mRootDirectory, "" + photoId + ".jpg");
  }

  private File getPhotoInfoPath(int photoId) {
    assertBgndThread();
    return new File(mRootDirectory, "" + photoId + ".json");
  }

  private void readPhotoRecords() {
    SortedSet<PhotoInfo> photoSet = new TreeSet<PhotoInfo>(new Comparator<PhotoInfo>() {
      @Override
      public int compare(PhotoInfo i1, PhotoInfo i2) {
        return i1.getId() - i2.getId();
      }
    });

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
        photoSet.add(photoInfo);
      }
    }
    mPhotoSet = photoSet;
  }

  /**
   * Construct a suitably aged bitmap for a photo
   */
  private void constructBitmap(final PhotoInfo photoInfo) {
    Bitmap bitmap = readBitmapFromFile(photoInfo);

    PhotoManipulator m = new PhotoManipulator(this, photoInfo, bitmap);

    final Bitmap finalBitmap = m.getManipulatedBitmap();

    AppState.postUIEvent(new Runnable() {
      public void run() {
        notifyEventObservers(Event.BitmapConstructed, photoInfo, finalBitmap);
      }
    });
  }

  private Bitmap readBitmapFromFile(PhotoInfo photoInfo) {
    File photoPath = getPhotoBitmapPath(photoInfo.getId());
    trace("Reading " + photoInfo + " bitmap from " + photoPath.getName());
    Bitmap bitmap = BitmapFactory.decodeFile(photoPath.getPath());
    return bitmap;
  }

  public int getRandomSeed() {
    return mRandomSeed;
  }

  public Context getContext() {
    return mContext;
  }

  /**
   * Notify registered observers of an event
   *
   * @param args the first element must be an Event, the rest are dependent upon the type of Event
   */
  private void notifyEventObservers(Object... args) {
    assertUIThread();
    setChanged();
    notifyObservers(args);
  }

  private boolean mTrace;
  private State mState;
  private String mFailureMessage;
  private final Context mContext;
  private Handler mBackgroundThreadHandler;
  // This is a constant once the file has been created, so thread doesn't matter
  private int mRandomSeed;

  // These fields should only be accessed by the background thread

  private File mRootDirectory;
  private boolean mModified;
  private int mNextPhotoId;
  private SortedSet<PhotoInfo> mPhotoSet;
}
