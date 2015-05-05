package com.js.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Environment;
import android.widget.ImageView;

import com.js.basic.Files;
import com.js.basic.IPoint;
import com.js.basic.JSONTools;
import com.squareup.picasso.MemoryPolicy;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;
import com.squareup.picasso.Transformation;

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

  // For development purposes, keep copies of original (unaged) photos
  private static final boolean KEEP_ORIGINAL_COPIES = false;

  // For development purposes, start with unaged versions
  private static final boolean START_WITH_ORIGINAL = false;

  // Prefix used for (development only) original copies of bitmap, info files
  private static final String ORIGINAL_COPY_PREFIX = "_orig_";

  private static final int PHOTO_LIFETIME_DAYS = 30;

  private static final boolean SIMULATE_DELETE_PHOTO = false;

  public static enum Event {
    StateChanged,
    PhotoCreated,
    PhotoDeleted,
  }

  public PhotoFile() {
    mState = State.Start;
//    setTrace(true);
    doNothing();
    doNothingAndroid();
    mPhotoSet = new TreeSet<PhotoInfo>(new Comparator<PhotoInfo>() {
      @Override
      public int compare(PhotoInfo i1, PhotoInfo i2) {
        return i1.getId() - i2.getId();
      }
    });
  }

  public enum State {
    Start, Opening, Open, Closing, Closed, Failed
  }

  public State state() {
    return mState;
  }

  private class OpenPhotoFileTask extends TaskSequence {
    public OpenPhotoFileTask(Context context) {
      mContext = context;
    }

    @Override
    protected void execute(int stageNumber) {
      switch (stageNumber) {
        case 0: {
          trace("OpenFile");

          if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            mFailMessage = "No writable external storage found";
          }
          if (failure()) break;
          prepareRootDirectory();
          if (failure()) break;
          readPhotoRecords();
          if (failure()) break;
          updatePhotoAges();
          if (failure()) break;
        }
        break;

        case 1:
          if (failure()) {
            setFailed(mFailMessage);
            abort();
          }
          setState(State.Open);
          notifyEventObservers(Event.StateChanged);
          finish();
          break;
      }
    }

    private boolean failure() {
      return mFailMessage != null;
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

    private void restoreOriginalVersions() {
      File[] fList = mRootDirectory.listFiles();
      for (File file : fList) {
        if (!file.isFile()) continue;
        String fileStr = file.getName();
        String extension = FilenameUtils.getExtension(fileStr);
        pr("restore original, file " + file + ", extension " + extension);
        if (!extension.equals("jpg"))
          continue;
        String baseName = FilenameUtils.getBaseName(fileStr);
        if (!baseName.startsWith(ORIGINAL_COPY_PREFIX))
          continue;
        baseName = baseName.substring(ORIGINAL_COPY_PREFIX.length());
        int id;
        try {
          id = Integer.parseInt(baseName);
        } catch (NumberFormatException e) {
          warning("failed to parse id from " + fileStr);
          continue;
        }
        try {
          File photoInfoPathOriginal = getPhotoInfoPath(id, true);
          File photoInfoPath = getPhotoInfoPath(id, false);
          File photoBitmapPath = getPhotoBitmapPath(id, false);
          if (!photoInfoPathOriginal.exists()) {
            warning("no original path found: " + photoInfoPathOriginal);
            continue;
          }
          FileUtils.copyFile(getPhotoBitmapPath(id, true), photoBitmapPath);
          FileUtils.copyFile(photoInfoPathOriginal, photoInfoPath);
        } catch (Throwable t) {
          warning("Failed to read or parse " + file);
        }
      }
    }

    private void readPhotoRecords() {
      mPhotoSet.clear();

      File[] fList = mRootDirectory.listFiles();
      if (START_WITH_ORIGINAL) {
        restoreOriginalVersions();
      }

      for (File file : fList) {
        if (file.isFile()) {
          String fileStr = file.getName();
          String extension = FilenameUtils.getExtension(fileStr);
          if (!extension.equals("jpg"))
            continue;
          String baseName = FilenameUtils.getBaseName(fileStr);
          if (baseName.startsWith(ORIGINAL_COPY_PREFIX))
            continue;
          int id;
          try {
            id = Integer.parseInt(baseName);
          } catch (NumberFormatException e) {
            continue;
          }
          if (id <= 0) {
            warning("Skipping illegal photo id: " + id + " from " + fileStr);
            continue;
          }
          PhotoInfo photoInfo;
          try {
            File photoInfoPath = getPhotoInfoPath(id, false);
            String jsonString = Files.readString(photoInfoPath);
            photoInfo = PhotoInfo.parseJSON(jsonString);
            if (KEEP_ORIGINAL_COPIES)
              createOriginalIfNecessary(photoInfo);
          } catch (Throwable t) {
            warning("Failed to read or parse " + file);
            continue;
          }
          mPhotoSet.add(photoInfo);
        }
      }
    }

    private void updatePhotoAges() {

      final int SECONDS_PER_DAY = 24 * 3600;
      final int SECONDS_PER_AGE_STATE = (PHOTO_LIFETIME_DAYS * SECONDS_PER_DAY) / PhotoInfo.AGE_STATE_MAX;

      List<PhotoInfo> updatedPhotosList = new ArrayList<PhotoInfo>();
      int currentTime = PhotoInfo.currentSecondsSinceEpoch();
      for (PhotoInfo photo : mPhotoSet) {
        int timeSinceCreated = currentTime - photo.getCreationTime();
        if (timeSinceCreated < 0)
          timeSinceCreated = 0;
        int targetAge = Math.min(timeSinceCreated / SECONDS_PER_AGE_STATE, PhotoInfo.AGE_STATE_MAX);
        if (targetAge != photo.getTargetAgeState()) {
          trace(photo + " days since created " + (timeSinceCreated / SECONDS_PER_DAY)
              + " new target " + targetAge + " currently " + photo.getTargetAgeState());

          if (targetAge == PhotoInfo.AGE_STATE_MAX) {
            File f = getPhotoInfoPath(photo.getId(), false);
            f.delete();
            f = getPhotoBitmapPath(photo.getId(), false);
            f.delete();
            continue;
          }

          if (targetAge > photo.getTargetAgeState()) {
            photo.setTargetAgeState(targetAge);
            trace("updating");
            try {
              writePhotoInfo(photo);
            } catch (IOException e) {
              mFailMessage = "writing photo info; " + d(e);
              return;
            }
          }
        }
        updatedPhotosList.add(photo);
      }
      mPhotoSet.clear();
      mPhotoSet.addAll(updatedPhotosList);
    }

    private String mFailMessage;
    private final Context mContext;
  }

  public void open(Context context) {
    assertUIThread();
    if (mState != State.Start)
      throw new IllegalStateException();

    trace("open()");

    setState(State.Opening);

    TaskSequence t = new OpenPhotoFileTask(context);
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

  @SuppressWarnings("UnusedDeclaration")
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

  @SuppressWarnings("UnusedDeclaration")
  public void setTrace(boolean state) {
    mTrace = state;
    if (state)
      warning("Turning tracing on");
  }

  @SuppressWarnings("UnusedDeclaration")
  public String getFailureMessage() {
    return mFailureMessage;
  }

  private class CreatePhotoTask extends TaskSequence {
    public CreatePhotoTask(byte[] jpegData, IPoint imageSize, int rotationToApply) {
      mJPEGData = jpegData;
      mImageSize = imageSize;
      mRotationToApply = rotationToApply;
    }

    @Override
    protected void execute(int stageNumber) {
      switch (stageNumber) {
        case 0:
          try {
            BitmapFactory.Options opt;
            Bitmap bitmap;
            {
              opt = new BitmapFactory.Options();
              opt.inTempStorage = new byte[16 * 1024];

              float mb = (mImageSize.x * mImageSize.y) / 1024000.0f;

              if (mb > 4f)
                opt.inSampleSize = 4;
              else if (mb > 3f)
                opt.inSampleSize = 2;
              bitmap = BitmapFactory.decodeByteArray(mJPEGData, 0, mJPEGData.length, opt);
              mJPEGData = null;
              trace("Image size " + mImageSize + ", inSampleSize " + opt.inSampleSize + ", Bitmap size " + BitmapTools.size(bitmap));
            }

            // Scale bitmap down to our maximum size, if it's larger;
            // do this before rotating!
            boolean isPortrait = BitmapTools.getOrientation(bitmap) == BitmapTools.ORIENTATION_PORTRAIT;
            Bitmap oldBitmap = bitmap;
            bitmap = BitmapTools.scaleBitmapToFit(oldBitmap, PhotoInfo.getLogicalMaximumSize(isPortrait), true, true);
            oldBitmap = BitmapTools.recycleOldBitmapIfDifferent(oldBitmap, bitmap);
            bitmap = BitmapTools.rotateBitmap(bitmap, mRotationToApply);
            BitmapTools.recycleOldBitmapIfDifferent(oldBitmap, bitmap);

            PhotoInfo info = createPhotoInfo();

            File photoPath = getPhotoBitmapPath(info.getId(), false);
            trace("Writing " + info + " to " + photoPath);
            OutputStream stream = new FileOutputStream(photoPath);
            bitmap.compress(Bitmap.CompressFormat.JPEG, PhotoInfo.JPEG_QUALITY_MAX, stream);
            bitmap.recycle();
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
    private byte[] mJPEGData;
    private IPoint mImageSize;
    private int mRotationToApply;
  }

  public void createPhoto(final byte[] jpegData, final IPoint imageSize, final int rotationToApply) {
    assertOpen();
    TaskSequence t = new CreatePhotoTask(jpegData, imageSize, rotationToApply);
    t.start();
  }

  public PhotoInfo getPhoto(int photoId) {
    List<PhotoInfo> list = getPhotos(photoId, 1);
    PhotoInfo element = null;
    if (!list.isEmpty()) {
      element = list.get(0);
      if (element.getId() != photoId)
        element = null;
    }
    return element;
  }

  public List<PhotoInfo> getPhotos(int startId, int maxCount) {
    ArrayList<PhotoInfo> list = new ArrayList<PhotoInfo>();
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

  private class DeletePhotoTask extends TaskSequence {
    public DeletePhotoTask(PhotoInfo photoInfo) {
      mPhotoInfo = photoInfo;
    }

    @Override
    protected void execute(int stageNumber) {
      switch (stageNumber) {
        case 0: {
          if (SIMULATE_DELETE_PHOTO) {
            warning("simulating deletion of photo");
            break;
          }
          File infoPath = getPhotoInfoPath(mPhotoInfo.getId(), false);
          File bitmapPath = getPhotoBitmapPath(mPhotoInfo.getId(), false);
          infoPath.delete();
          bitmapPath.delete();
        }
        break;
        case 1:
          mPhotoSet.remove(mPhotoInfo);
          notifyEventObservers(Event.PhotoDeleted, mPhotoInfo);
          finish();
          break;
      }
    }

    private final PhotoInfo mPhotoInfo;
  }

  public void deletePhoto(PhotoInfo photoInfo, Runnable completionCallback) {
    assertOpen();
    TaskSequence t = new DeletePhotoTask(photoInfo);
    t.setCompletionCallback(completionCallback);
    t.start();
  }

  private Bitmap readBitmapFromFile(PhotoInfo mPhotoInfo) {
    File photoPath = getPhotoBitmapPath(mPhotoInfo.getId(), false);
    // Cut down on logging noise by omitting this:
    //trace("Reading " + mPhotoInfo + " bitmap from " + photoPath.getName());
    Bitmap bitmap = BitmapFactory.decodeFile(photoPath.getPath());
    return bitmap;
  }

  private void agePhoto(PhotoInfo agedPhoto) {
    trace(".........aging " + agedPhoto + " to target " + agedPhoto.getTargetAgeState());

    // Read current bitmap as JPEG
    File photoPath = getPhotoBitmapPath(agedPhoto.getId(), false);
    try {
      byte[] jpeg = FileUtils.readFileToByteArray(photoPath);
      PhotoAger ager = new PhotoAger(agedPhoto, jpeg);
      jpeg = ager.getAgedJPEG();
      FileUtils.writeByteArrayToFile(photoPath, jpeg);
      writePhotoInfo(agedPhoto);
      trace("writing aged version: " + agedPhoto);
    } catch (IOException e) {
      // TODO: figure out how to handle this gracefully
      die(e);
    }
  }

  /**
   * @param thumbnailSize if not zero, assumes thumbnail as opposed to full size
   */
  public void loadBitmapIntoView(Context context, PhotoInfo photo, int thumbnailSize, ImageView target) {
    RequestCreator r = Picasso.with(context).load(getPhotoBitmapPath(photo.getId(), false));
    if (thumbnailSize == 0) //|| (UITools.isPortrait() && warning("always cache")))
      r.memoryPolicy(MemoryPolicy.NO_CACHE);
    r.transform(new OurTransformation(context, photo, thumbnailSize));
    r.into(target);
  }

  private class OurTransformation implements Transformation {

    public OurTransformation(Context context, PhotoInfo photo, int thumbnailSize) {
      mContext = context;
      mPhotoInfo = photo;
      mThumbnailSize = thumbnailSize;
    }

    @Override
    public Bitmap transform(Bitmap bitmap) {
      trace("transforming " + mPhotoInfo + "; thumbnail " + forThumbnail());
      // If target age is greater than current, age the photo and reload
      if (mPhotoInfo.getTargetAgeState() > mPhotoInfo.getCurrentAgeState()) {
        // Ensure that photo record and bitmap are being aged as atomic action
        synchronized (mPhotoInfo) {
          agePhoto(mPhotoInfo);
        }
        bitmap.recycle();
        bitmap = readBitmapFromFile(mPhotoInfo);
      }
      PhotoManipulator m = new PhotoManipulator(mContext, PhotoFile.this, mPhotoInfo, bitmap);
      Bitmap bitmap2 = m.getManipulatedBitmap();
      bitmap.recycle();
      bitmap = bitmap2;

      if (forThumbnail()) {
        int origWidth = (int) (bitmap.getWidth() * .8f);
        int origHeight = (int) (bitmap.getHeight() * .8f);
        int origSize = Math.min(origWidth, origHeight);
        float scaleFactor = mThumbnailSize / (float) origSize;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleFactor, scaleFactor);
        Bitmap thumbnailBitmap = Bitmap.createBitmap(bitmap,
            (bitmap.getWidth() - origSize) / 2, (bitmap.getHeight() - origSize) / 2,
            origSize, origSize, matrix, true);
        if (bitmap != thumbnailBitmap) {
          bitmap.recycle();
          bitmap = thumbnailBitmap;
        }
      }
      return bitmap;
    }

    @Override
    public String key() {
      return forThumbnail() ? "thumb" : "normal";
    }

    public boolean forThumbnail() {
      return mThumbnailSize != 0;
    }

    private final PhotoInfo mPhotoInfo;
    private final int mThumbnailSize;
    private final Context mContext;
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

    // Write photo info to file, and store in map
    writePhotoInfo(info);

    synchronized (mPhotoSet) {
      mPhotoSet.add(info);
    }

    // Flush the changes, i.e. the unique id
    flush();
    return info;
  }

  private void writePhotoInfo(PhotoInfo info) throws IOException {
    File path = getPhotoInfoPath(info.getId(), false);
    String content = info.toJSON();
    Files.writeString(path, content);
    trace("writing " + info);
    trace("path " + path);
    trace("content=<" + content + ">");
  }

  private int getUniquePhotoId() {
    assertBgndThread();
    int id = mNextPhotoId;
    mNextPhotoId++;
    mModified = true;
    return id;
  }

  private File getPhotoBitmapPath(int photoId, boolean backup) {
    String prefix = backup ? ORIGINAL_COPY_PREFIX : "";
    String ext = ".jpg";
    return new File(mRootDirectory, prefix + photoId + ext);
  }

  private File getPhotoInfoPath(int photoId, boolean backup) {
    String prefix = backup ? ORIGINAL_COPY_PREFIX : "";
    String ext = backup ? ".orig_json" : ".json";
    return new File(mRootDirectory, prefix + photoId + ext);
  }

  public int getRandomSeed() {
    return mRandomSeed;
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

  private void createOriginalIfNecessary(PhotoInfo info) {
    File originalInfoPath = getPhotoInfoPath(info.getId(), true);
    File infoPath = getPhotoInfoPath(info.getId(), false);
    if (infoPath.exists() && !originalInfoPath.exists()) {
      warning("creating original copy of photo(s)");
      try {
        trace("...creating (unaged) copy of " + infoPath + " to " + originalInfoPath);
        FileUtils.copyFile(infoPath, originalInfoPath);
      } catch (IOException e) {
        die(e);
      }
    }

    File photoPath = getPhotoBitmapPath(info.getId(), false);
    File originalPhotoPath = getPhotoBitmapPath(info.getId(), true);
    if (photoPath.exists() && !originalPhotoPath.exists()) {
      try {
        trace("...creating (unaged) copy of " + photoPath + " to " + originalPhotoPath);
        FileUtils.copyFile(photoPath, originalPhotoPath);
      } catch (IOException e) {
        die(e);
      }
    }
  }

  private boolean mTrace;
  private State mState;
  private String mFailureMessage;
  // This is a constant once the file has been created, so thread doesn't matter
  private int mRandomSeed;

  // These fields should only be accessed by the background thread
  private File mRootDirectory;
  private boolean mModified;
  private int mNextPhotoId = 1;
  private final SortedSet<PhotoInfo> mPhotoSet;
}
