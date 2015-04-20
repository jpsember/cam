package com.js.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;

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

  private class OpenPhotoFileTask extends TaskSequence {
    @Override
    protected void execute(int stageNumber) {
      switch (stageNumber) {
        case 0: {
          trace("OpenFile");
          if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            mFailMessage = "No writable external storage found";
            break;
          }
          prepareRootDirectory();
          readPhotoRecords();
        }
        break;

        case 1:
          if (mFailMessage != null) {
            setFailed(mFailMessage);
            abort();
          }
          setState(State.Open);
          notifyEventObservers(Event.StateChanged);
          finish();
          break;
      }
    }

    private void prepareRootDirectory() {
      mRootDirectory = new File(mContext.getExternalFilesDir(null), "Photos");

      try {
        if (!mRootDirectory.exists()) {
          mRootDirectory.mkdir();
          if (!mRootDirectory.exists()) {
            throw new IOException("unable to create root directory");
          }
          mModified = true;
          flush();
        } else {
          if (DELETE_ROOT_DIRECTORY) {
            warning("deleting photos root directory");
            FileUtils.cleanDirectory(mRootDirectory);
          }
          readFileState();
        }
        trace("Opened root directory " + mRootDirectory);
      } catch (IOException e) {
        mFailMessage = "preparing root; " + d(e);
      }
    }

    private void readFileState() throws IOException {
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

    private String mFailMessage;
  }

  public void open() {
    assertUIThread();
    if (mState != State.Start)
      throw new IllegalStateException();

    trace("open()");

    setState(State.Opening);

    TaskSequence t = new OpenPhotoFileTask();
    t.start();
  }

  public boolean isOpen() {
    return mState == State.Open;
  }

  private class ClosePhotoFileTask extends TaskSequence {
    @Override
    protected void execute(int stageNumber) {
      switch (stageNumber) {
        case 0: {
          trace("CloseFile");
          try {
            flush();
          } catch (IOException e) {
            mFailMessage = "closing file; " + d(e);
          }
        }
        break;

        case 1:
          if (mFailMessage != null) {
            setFailed(mFailMessage);
            abort();
          }
          setState(State.Closed);
          finish();
          break;
      }
    }

    private String mFailMessage;
  }

  public void close() {
    assertUIThread();
    trace("close()");
    if (!isOpen())
      return;

    setState(State.Closing);
    TaskSequence t = new ClosePhotoFileTask();
    t.start();
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
    TaskSequence t = new TaskSequence() {

      @Override
      protected void execute(int stageNumber) {
        switch (stageNumber) {
          case 0:
            try {
              Bitmap bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
              bitmap = BitmapTools.rotateBitmap(bitmap, rotationToApply);
              PhotoInfo info = createPhotoInfo();

              // Scale photo to size appropriate to starting state
              unimp("scale photo to starting state");

              File photoPath = getPhotoBitmapPath(info.getId());
              trace("Writing " + info + " to " + photoPath);
              OutputStream stream = new FileOutputStream(photoPath);
              bitmap.compress(Bitmap.CompressFormat.JPEG, PhotoInfo.JPEG_QUALITY_MAX, stream);
              mPhotoInfo = info;
            } catch (IOException e) {
              mFailMessage = "create photo; " + d(e);
            }
            break;
          case 1:
            if (mFailMessage != null) {
              setFailed(mFailMessage);
              abort();
            } else {
              notifyEventObservers(Event.PhotoCreated, mPhotoInfo);
              finish();
            }
            break;
        }
      }

      private PhotoInfo mPhotoInfo;
      private String mFailMessage;
    };
    t.start();
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

  private class GetAgedPhotoTask extends TaskSequence {

    public GetAgedPhotoTask(PhotoInfo photoInfo) {
      photoInfo.assertFrozen();
      mPhotoInfo = photoInfo;
    }

    @Override
    protected void execute(int stageNumber) {
      switch (stageNumber) {
        case 0: {
          Bitmap bitmap = readBitmapFromFile();
          PhotoManipulator m = new PhotoManipulator(PhotoFile.this, mPhotoInfo, bitmap);
          mAgedPhoto = m.getManipulatedBitmap();
        }
        break;
        case 1:
          notifyEventObservers(Event.BitmapConstructed, mPhotoInfo, mAgedPhoto);
          finish();
          break;
      }
    }

    private Bitmap readBitmapFromFile() {
      File photoPath = getPhotoBitmapPath(mPhotoInfo.getId());
      trace("Reading " + mPhotoInfo + " bitmap from " + photoPath.getName());
      Bitmap bitmap = BitmapFactory.decodeFile(photoPath.getPath());
      return bitmap;
    }

    private Bitmap mAgedPhoto;
    private PhotoInfo mPhotoInfo;
  }

  /**
   * Construct a suitably aged bitmap for a photo
   */
  public void getBitmap(PhotoInfo photoInfo) {
    assertOpen();
    TaskSequence t = new GetAgedPhotoTask(photoInfo);
    t.start();
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

  private void setState(State state) {
    assertUIThread();
    if (mState != state) {
      trace("Changing state from " + mState + " to " + state);
      mState = state;
    }
  }

  // --------------- Methods called only within background thread
  // --------------- (consider putting these in their own class for simplicity?  Or add prefix?)

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
  // This is a constant once the file has been created, so thread doesn't matter
  private int mRandomSeed;

  // These fields should only be accessed by the background thread
  private File mRootDirectory;
  private boolean mModified;
  private int mNextPhotoId;
  private SortedSet<PhotoInfo> mPhotoSet;
}
