package com.example.vaishaliarora.videotrimmersample;

/**
 * Created by vaishaliarora on 15/06/16.
 */

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Build;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.HashMap;

public class AndroidUtilities {

    public static int statusBarHeight = 0;
    public static float density = 1;
    public static Point displaySize = new Point();
    public static DisplayMetrics displayMetrics = new DisplayMetrics();
    public static int leftBaseline;
    public static boolean usingHardwareInput;
    private static Boolean isTablet = null;
    private static File telegramPath;
    private static int MEDIA_DIR_DOCUMENT = 1;
    private static int MEDIA_DIR_VIDEO = 2;
    public static Integer photoSize = null;


    static {
        density = MyApplication.applicationContext.getResources().getDisplayMetrics().density;
        leftBaseline = isTablet() ? 80 : 72;
        checkDisplaySize();
    }


    public static int dp(float value) {
        if (value == 0) {
            return 0;
        }
        return (int) Math.ceil(density * value);
    }


    public static File getCacheDir() {
       /* File cacheFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/DCIM/VideoTrimmer");
        return cacheFile;*/
        String state = null;
        try {
            state = Environment.getExternalStorageState();
        } catch (Exception e) {
            Log.e("tmessages", e.getMessage());
        }
        if (state == null || state.startsWith(Environment.MEDIA_MOUNTED)) {
            try {
                File file = MyApplication.applicationContext.getExternalCacheDir();
                if (file != null) {
                    return file;
                }
            } catch (Exception e) {
                Log.e("tmessages", e.getMessage());
            }
        }
        try {
            File file = MyApplication.applicationContext.getCacheDir();
            if (file != null) {
                return file;
            }
        } catch (Exception e) {
            Log.e("tmessages", e.getMessage());
        }
        return new File("");
    }
    public static void checkDisplaySize() {
        try {
            Configuration configuration = MyApplication.applicationContext.getResources().getConfiguration();
            usingHardwareInput = configuration.keyboard != Configuration.KEYBOARD_NOKEYS && configuration.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO;
            WindowManager manager = (WindowManager) MyApplication.applicationContext.getSystemService(Context.WINDOW_SERVICE);
            if (manager != null) {
                Display display = manager.getDefaultDisplay();
                if (display != null) {
                    display.getMetrics(displayMetrics);
                    if (Build.VERSION.SDK_INT < 13) {
                        displaySize.set(display.getWidth(), display.getHeight());
                    } else {
                        display.getSize(displaySize);
                    }
                    Log.e("tmessages", "display size = " + displaySize.x + " " + displaySize.y + " " + displayMetrics.xdpi + "x" + displayMetrics.ydpi);
                }
            }
        } catch (Exception e) {
            Log.e("tmessages", e.getMessage());
        }
    }


    public static void runOnUIThread(Runnable runnable) {
        runOnUIThread(runnable, 0);
    }

    public static void runOnUIThread(Runnable runnable, long delay) {
        if (delay == 0) {
            MyApplication.applicationHandler.post(runnable);
        } else {
            MyApplication.applicationHandler.postDelayed(runnable, delay);
        }
    }


    public static boolean isTablet() {
        if (isTablet == null) {
            isTablet = MyApplication.applicationContext.getResources().getBoolean(R.bool.isTablet);
        }
        return isTablet;
    }
    public static int getPhotoSize() {

        if (photoSize == null) {
            if (Build.VERSION.SDK_INT >= 16) {
                photoSize = 1280;
            } else {
                photoSize = 800;
            }
        }
        return photoSize;
    }

    public static String formatFileSize(long size) {
        if (size < 1024) {
            return String.format("%d B", size);
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0f);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / 1024.0f / 1024.0f);
        } else {
            return String.format("%.1f GB", size / 1024.0f / 1024.0f / 1024.0f);
        }
    }

    public static HashMap<Integer, File> createMediaPaths() {
        HashMap<Integer, File> mediaDirs = new HashMap<>();
        File cachePath = AndroidUtilities.getCacheDir();
        if (!cachePath.isDirectory()) {
            try {
                cachePath.mkdirs();
            } catch (Exception e) {
                Log.e("tmessages", e.getMessage());
            }
        }
        try {
            new File(cachePath, ".nomedia").createNewFile();
        } catch (Exception e) {
            Log.e("tmessages", e.getMessage());
        }

        mediaDirs.put(FileLoader.MEDIA_DIR_CACHE, cachePath);
        Log.e("tmessages", "cache path = " + cachePath);

        try {
            if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                telegramPath = new File(cachePath, "/Telegram");
                telegramPath.mkdirs();


                if (telegramPath.isDirectory()) {


                    try {
                        File videoPath = new File(telegramPath, "/Telegram Video");
                        videoPath.mkdir();
                        if (videoPath.isDirectory() && canMoveFiles(cachePath, videoPath, MEDIA_DIR_VIDEO)) {
                            mediaDirs.put(2, videoPath);
                            Log.e("tmessages", "video path = " + videoPath);
                        }
                    } catch (Exception e) {
                        Log.e("tmessages", e.getMessage());
                    }


                    try {
                        File documentPath = new File(telegramPath, "Telegram Documents");
                        documentPath.mkdir();
                        if (documentPath.isDirectory() && canMoveFiles(cachePath, documentPath, MEDIA_DIR_DOCUMENT)) {
                            new File(documentPath, ".nomedia").createNewFile();
                            mediaDirs.put(1, documentPath);
                            Log.e("tmessages", "documents path = " + documentPath);
                        }
                    } catch (Exception e) {
                        Log.e("tmessages", e.getMessage());
                    }
                }
            } else {
                Log.e("tmessages", "this Android can't rename files");
            }
//            MediaController.getInstance().checkSaveToGalleryFiles();
        } catch (Exception e) {
            Log.e("tmessages", e.getMessage());
        }

        return mediaDirs;
    }
    private static boolean canMoveFiles(File from, File to, int type) {
        RandomAccessFile file = null;
        try {
            File srcFile = null;
            File dstFile = null;
            if (type == MEDIA_DIR_DOCUMENT) {
                srcFile = new File(from, "000000000_999999_temp.doc");
                dstFile = new File(to, "000000000_999999.doc");
            }  else if (type == MEDIA_DIR_VIDEO) {
                srcFile = new File(from, "000000000_999999_temp.mp4");
                dstFile = new File(to, "000000000_999999.mp4");
            }
            byte[] buffer = new byte[1024];
            srcFile.createNewFile();
            file = new RandomAccessFile(srcFile, "rws");
            file.write(buffer);
            file.close();
            file = null;
            boolean canRename = srcFile.renameTo(dstFile);
            srcFile.delete();
            dstFile.delete();
            if (canRename) {
                return true;
            }
        } catch (Exception e) {
            Log.e("tmessages", e.getMessage());
        } finally {
            try {
                if (file != null) {
                    file.close();
                }
            } catch (Exception e) {
                Log.e("tmessages", e.getMessage());
            }
        }
        return false;
    }
}
