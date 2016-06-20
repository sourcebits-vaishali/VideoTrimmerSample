/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package com.example.vaishaliarora.videotrimmersample;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.Box;
import com.coremedia.iso.boxes.MediaBox;
import com.coremedia.iso.boxes.MediaHeaderBox;
import com.coremedia.iso.boxes.SampleSizeBox;
import com.coremedia.iso.boxes.TrackBox;
import com.coremedia.iso.boxes.TrackHeaderBox;
import com.googlecode.mp4parser.util.Matrix;
import com.googlecode.mp4parser.util.Path;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;



@TargetApi(16)
public class VideoEditorActivity extends Activity implements TextureView.SurfaceTextureListener/*, NotificationCenter.NotificationCenterDelegate*/ {

    private boolean created = false;
    private MediaPlayer videoPlayer = null;
    private VideoTimelineView videoTimelineView = null;
    private View videoContainerView = null;
    private TextView originalSizeTextView = null;
    private TextView editedSizeTextView = null;
    private View textContainerView = null;
    private ImageView playButton = null;
    private VideoSeekBarView videoSeekBarView = null;
    private TextureView textureView = null;
    private View controlView = null;
    private CheckBox compressVideo = null;
    private boolean playerPrepared = false;

    private String videoPath = null;
    private float lastProgress = 0;
    private boolean needSeek = false;

    private final Object sync = new Object();
    private Thread thread = null;

    private int rotationValue = 0;
    private int originalWidth = 0;
    private int originalHeight = 0;
    private int resultWidth = 0;
    private int resultHeight = 0;
    private int bitrate = 0;
    private int originalBitrate = 0;
    private float videoDuration = 0;
    private long startTime = 0;
    private long endTime = 0;
    private long audioFramesSize = 0;
    private long videoFramesSize = 0;
    private int estimatedSize = 0;
    private long esimatedDuration = 0;
    private long originalSize = 0;

    public static final int MATCH_PARENT = -1;

    private Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            boolean playerCheck;

            while (true) {
                synchronized (sync) {
                    try {
                        playerCheck = videoPlayer != null && videoPlayer.isPlaying();
                    } catch (Exception e) {
                        playerCheck = false;
                        Log.d("tmessages", e.getMessage());
                    }
                }
                if (!playerCheck) {
                    break;
                }
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (videoPlayer != null && videoPlayer.isPlaying()) {
                            float startTime = videoTimelineView.getLeftProgress() * videoDuration;
                            float endTime = videoTimelineView.getRightProgress() * videoDuration;
                            if (startTime == endTime) {
                                startTime = endTime - 0.01f;
                            }
                            float progress = (videoPlayer.getCurrentPosition() - startTime) / (endTime - startTime);
                            float lrdiff = videoTimelineView.getRightProgress() - videoTimelineView.getLeftProgress();
                            progress = videoTimelineView.getLeftProgress() + lrdiff * progress;
                            if (progress > lastProgress) {
                                videoSeekBarView.setProgress(progress);
                                lastProgress = progress;
                            }
                            if (videoPlayer.getCurrentPosition() >= endTime) {
                                try {
                                    videoPlayer.pause();
                                    onPlayComplete();
                                } catch (Exception e) {
                                    Log.e("tmessages", e.getMessage());
                                }
                            }
                        }
                    }
                });
                try {
                    Thread.sleep(50);
                } catch (Exception e) {
                    Log.e("tmessages", e.getMessage());
                }
            }
            synchronized (sync) {
                thread = null;
            }
        }
    };


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.video_editor_layout);

        if (created) {
            return ;
        }
        videoPath = Environment.getExternalStorageDirectory().getAbsolutePath()+"/DCIM/sample2.mp4";
        Log.d("Vaishali ","Path == "+videoPath);
        if (videoPath == null || !processOpenVideo()) {
            return ;
        }
        videoPlayer = new MediaPlayer();
        videoPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        onPlayComplete();
                    }
                });
            }
        });
        videoPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                playerPrepared = true;
                if (videoTimelineView != null && videoPlayer != null) {
                    videoPlayer.seekTo((int) (videoTimelineView.getLeftProgress() * videoDuration));
                }
            }
        });
        try {
            videoPlayer.setDataSource(videoPath);
            videoPlayer.prepareAsync();
            created = true;
        } catch (Exception e) {
            Log.e("tmessages", e.getMessage());
        }

        originalSizeTextView = (TextView)findViewById(R.id.original_size);
        editedSizeTextView = (TextView)findViewById(R.id.edited_size);
        videoContainerView = findViewById(R.id.video_container);
        textContainerView = findViewById(R.id.info_container);
        controlView = findViewById(R.id.control_layout);
        compressVideo = (CheckBox) findViewById(R.id.compress_video);
        compressVideo.setText("CompressVideo");
        SharedPreferences preferences = MyApplication.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        compressVideo.setVisibility(originalHeight != resultHeight || originalWidth != resultWidth ? View.VISIBLE : View.GONE);
        compressVideo.setChecked(preferences.getBoolean("compress_video", true));
        compressVideo.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferences preferences = MyApplication.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("compress_video", isChecked);
                editor.commit();
                updateVideoEditedInfo();
            }
        });

        TextView titleTextView = (TextView) findViewById(R.id.original_title);
        titleTextView.setText("OriginalVideo");
        titleTextView = (TextView) findViewById(R.id.edited_title);
        titleTextView.setText("EditedVideo");

        videoTimelineView = (VideoTimelineView) findViewById(R.id.video_timeline_view);
        videoTimelineView.setVideoPath(videoPath);
        videoTimelineView.setDelegate(new VideoTimelineView.VideoTimelineViewDelegate() {
            @Override
            public void onLeftProgressChanged(float progress) {
                if (videoPlayer == null || !playerPrepared) {
                    return;
                }
                try {
                    if (videoPlayer.isPlaying()) {
                        videoPlayer.pause();
                        playButton.setImageResource(R.drawable.video_play);
                    }
                    videoPlayer.setOnSeekCompleteListener(null);
                    videoPlayer.seekTo((int) (videoDuration * progress));
                } catch (Exception e) {
                    Log.e("tmessages", e.getMessage());
                }
                needSeek = true;
                videoSeekBarView.setProgress(videoTimelineView.getLeftProgress());
                updateVideoEditedInfo();
            }

            @Override
            public void onRifhtProgressChanged(float progress) {
                if (videoPlayer == null || !playerPrepared) {
                    return;
                }
                try {
                    if (videoPlayer.isPlaying()) {
                        videoPlayer.pause();
                        playButton.setImageResource(R.drawable.video_play);
                    }
                    videoPlayer.setOnSeekCompleteListener(null);
                    videoPlayer.seekTo((int) (videoDuration * progress));
                } catch (Exception e) {
                    Log.e("tmessages", e.getMessage());
                }
                needSeek = true;
                videoSeekBarView.setProgress(videoTimelineView.getLeftProgress());
                updateVideoEditedInfo();
            }
        });

        videoSeekBarView = (VideoSeekBarView) findViewById(R.id.video_seekbar);
        videoSeekBarView.delegate = new VideoSeekBarView.SeekBarDelegate() {
            @Override
            public void onSeekBarDrag(float progress) {
                if (progress < videoTimelineView.getLeftProgress()) {
                    progress = videoTimelineView.getLeftProgress();
                    videoSeekBarView.setProgress(progress);
                } else if (progress > videoTimelineView.getRightProgress()) {
                    progress = videoTimelineView.getRightProgress();
                    videoSeekBarView.setProgress(progress);
                }
                if (videoPlayer == null || !playerPrepared) {
                    return;
                }
                if (videoPlayer.isPlaying()) {
                    try {
                        videoPlayer.seekTo((int) (videoDuration * progress));
                        lastProgress = progress;
                    } catch (Exception e) {
                        Log.e("tmessages", e.getMessage());
                    }
                } else {
                    lastProgress = progress;
                    needSeek = true;
                }
            }
        };

        playButton = (ImageView) findViewById(R.id.play_button);
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                play();
            }
        });

        textureView = (TextureView) findViewById(R.id.video_view);
        textureView.setSurfaceTextureListener(this);

        updateVideoOriginalInfo();
        updateVideoEditedInfo();
        fixLayoutInternal();

        (findViewById(R.id.trim_video)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                trimVideo(videoPath, startTime, endTime, originalWidth, originalHeight, rotationValue, originalWidth, originalHeight, originalBitrate, estimatedSize, esimatedDuration);

            }
        });

    }

    private void trimVideo(String videoPath, long startTime, long endTime, int resultWidth, int resultHeight, int rotationValue, int originalWidth, int originalHeight, int bitrate, long estimatedSize, long estimatedDuration){
        VideoEditedInfo videoEditedInfo = new VideoEditedInfo();
        videoEditedInfo.startTime = startTime;
        videoEditedInfo.endTime = endTime;
        videoEditedInfo.rotationValue = rotationValue;
        videoEditedInfo.originalWidth = originalWidth;
        videoEditedInfo.originalHeight = originalHeight;
        videoEditedInfo.bitrate = bitrate;
        videoEditedInfo.resultWidth = resultWidth;
        videoEditedInfo.resultHeight = resultHeight;
        videoEditedInfo.originalPath = videoPath;
        prepareSendingVideo(videoPath, estimatedSize, estimatedDuration, resultWidth, resultHeight, videoEditedInfo);

    }

    @Override
    public void onDestroy() {
        if (videoTimelineView != null) {
            videoTimelineView.destroy();
        }
        if (videoPlayer != null) {
            try {
                videoPlayer.stop();
                videoPlayer.release();
                videoPlayer = null;
            } catch (Exception e) {
                Log.e("tmessages", e.getMessage());
            }
        }
        super.onDestroy();
    }

    private void setPlayerSurface() {
        if (textureView == null || !textureView.isAvailable() || videoPlayer == null) {
            return;
        }
        try {
            Surface s = new Surface(textureView.getSurfaceTexture());
            videoPlayer.setSurface(s);
            if (playerPrepared) {
                videoPlayer.seekTo((int) (videoTimelineView.getLeftProgress() * videoDuration));
            }
        } catch (Exception e) {
            Log.e("tmessages", e.getMessage());
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        setPlayerSurface();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (videoPlayer == null) {
            return true;
        }
        videoPlayer.setDisplay(null);
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    private void onPlayComplete() {
        if (playButton != null) {
            playButton.setImageResource(R.drawable.video_play);
        }
        if (videoSeekBarView != null && videoTimelineView != null) {
            videoSeekBarView.setProgress(videoTimelineView.getLeftProgress());
        }
        try {
            if (videoPlayer != null) {
                if (videoTimelineView != null) {
                    videoPlayer.seekTo((int) (videoTimelineView.getLeftProgress() * videoDuration));
                }
            }
        } catch (Exception e) {
            Log.e("tmessages", e.getMessage());
        }
    }

    private void updateVideoOriginalInfo() {
        if (originalSizeTextView == null) {
            return;
        }
        int width = rotationValue == 90 || rotationValue == 270 ? originalHeight : originalWidth;
        int height = rotationValue == 90 || rotationValue == 270 ? originalWidth : originalHeight;
        String videoDimension = String.format("%dx%d", width, height);
        long duration = (long) Math.ceil(videoDuration);
        int minutes = (int) (duration / 1000 / 60);
        int seconds = (int) Math.ceil(duration / 1000) - minutes * 60;
        String videoTimeSize = String.format("%d:%02d, %s", minutes, seconds, AndroidUtilities.formatFileSize(originalSize));
        originalSizeTextView.setText(String.format("%s, %s", videoDimension, videoTimeSize));
    }

    private void updateVideoEditedInfo() {
        if (editedSizeTextView == null) {
            return;
        }
        esimatedDuration = (long) Math.ceil((videoTimelineView.getRightProgress() - videoTimelineView.getLeftProgress()) * videoDuration);

        int width;
        int height;

        if (compressVideo.getVisibility() == View.GONE || compressVideo.getVisibility() == View.VISIBLE && !compressVideo.isChecked()) {
            width = rotationValue == 90 || rotationValue == 270 ? originalHeight : originalWidth;
            height = rotationValue == 90 || rotationValue == 270 ? originalWidth : originalHeight;
            estimatedSize = (int) (originalSize * ((float) esimatedDuration / videoDuration));
        } else {
            width = rotationValue == 90 || rotationValue == 270 ? resultHeight : resultWidth;
            height = rotationValue == 90 || rotationValue == 270 ? resultWidth : resultHeight;
            estimatedSize = calculateEstimatedSize((float) esimatedDuration / videoDuration);
        }

        if (videoTimelineView.getLeftProgress() == 0) {
            startTime = -1;
        } else {
            startTime = (long) (videoTimelineView.getLeftProgress() * videoDuration) * 1000;
        }
        if (videoTimelineView.getRightProgress() == 1) {
            endTime = -1;
        } else {
            endTime = (long) (videoTimelineView.getRightProgress() * videoDuration) * 1000;
        }

        String videoDimension = String.format("%dx%d", width, height);
        int minutes = (int) (esimatedDuration / 1000 / 60);
        int seconds = (int) Math.ceil(esimatedDuration / 1000) - minutes * 60;
        String videoTimeSize = String.format("%d:%02d, ~%s", minutes, seconds, AndroidUtilities.formatFileSize(estimatedSize));
        editedSizeTextView.setText(String.format("%s, %s", videoDimension, videoTimeSize));
    }

    private void fixVideoSize() {

        int viewHeight = 0;
        if (AndroidUtilities.isTablet()) {
            viewHeight = AndroidUtilities.dp(472);
        } else {
            viewHeight = AndroidUtilities.displaySize.y - AndroidUtilities.statusBarHeight - getCurrentActionBarHeight();
        }

        int width;
        int height;
        if (AndroidUtilities.isTablet()) {
            width = AndroidUtilities.dp(490);
            height = viewHeight - AndroidUtilities.dp(276 + (compressVideo.getVisibility() == View.VISIBLE ? 20 : 0));
        } else {
            if (MyApplication.applicationContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                width = AndroidUtilities.displaySize.x / 3 - AndroidUtilities.dp(24);
                height = viewHeight - AndroidUtilities.dp(32);
            } else {
                width = AndroidUtilities.displaySize.x;
                height = viewHeight - AndroidUtilities.dp(276 + (compressVideo.getVisibility() == View.VISIBLE ? 20 : 0));
            }
        }
        int vwidth = rotationValue == 90 || rotationValue == 270 ? originalHeight : originalWidth;
        int vheight = rotationValue == 90 || rotationValue == 270 ? originalWidth : originalHeight;
        float wr = (float) width / (float) vwidth;
        float hr = (float) height / (float) vheight;
        float ar = (float) vwidth / (float) vheight;

        if (wr > hr) {
            width = (int) (height * ar);
        } else {
            height = (int) (width / ar);
        }

        if (textureView != null) {
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) textureView.getLayoutParams();
            layoutParams.width = width;
            layoutParams.height = height;
            layoutParams.leftMargin = 0;
            layoutParams.topMargin = 0;
            textureView.setLayoutParams(layoutParams);
        }
    }

    public static int getCurrentActionBarHeight() {
        if (AndroidUtilities.isTablet()) {
            return AndroidUtilities.dp(64);
        } else if (MyApplication.applicationContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return AndroidUtilities.dp(48);
        } else {
            return AndroidUtilities.dp(56);
        }
    }

    private void fixLayoutInternal() {

        if (!AndroidUtilities.isTablet()/* && this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE*/) {
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) videoContainerView.getLayoutParams();
            layoutParams.topMargin = AndroidUtilities.dp(16);
            layoutParams.bottomMargin = AndroidUtilities.dp(16);
            layoutParams.width = AndroidUtilities.displaySize.x / 3 - AndroidUtilities.dp(24);
            layoutParams.leftMargin = AndroidUtilities.dp(16);
            videoContainerView.setLayoutParams(layoutParams);

            layoutParams = (FrameLayout.LayoutParams) controlView.getLayoutParams();
            layoutParams.topMargin = AndroidUtilities.dp(16);
            layoutParams.bottomMargin = 0;
            layoutParams.width = AndroidUtilities.displaySize.x / 3 * 2 - AndroidUtilities.dp(32);
            layoutParams.leftMargin = AndroidUtilities.displaySize.x / 3 + AndroidUtilities.dp(16);
            layoutParams.gravity = Gravity.TOP;
            controlView.setLayoutParams(layoutParams);

            layoutParams = (FrameLayout.LayoutParams) textContainerView.getLayoutParams();
            layoutParams.width = AndroidUtilities.displaySize.x / 3 * 2 - AndroidUtilities.dp(32);
            layoutParams.leftMargin = AndroidUtilities.displaySize.x / 3 + AndroidUtilities.dp(16);
            layoutParams.rightMargin = AndroidUtilities.dp(16);
            layoutParams.bottomMargin = AndroidUtilities.dp(16);
            textContainerView.setLayoutParams(layoutParams);
        } else {
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) videoContainerView.getLayoutParams();
            layoutParams.topMargin = AndroidUtilities.dp(16);
            layoutParams.bottomMargin = AndroidUtilities.dp(260 + (compressVideo.getVisibility() == View.VISIBLE ? 20 : 0));
            layoutParams.width = MATCH_PARENT;
            layoutParams.leftMargin = 0;
            videoContainerView.setLayoutParams(layoutParams);

            layoutParams = (FrameLayout.LayoutParams) controlView.getLayoutParams();
            layoutParams.topMargin = 0;
            layoutParams.leftMargin = 0;
            layoutParams.bottomMargin = AndroidUtilities.dp(150 + (compressVideo.getVisibility() == View.VISIBLE ? 20 : 0));
            layoutParams.width = MATCH_PARENT;
            layoutParams.gravity = Gravity.BOTTOM;
            controlView.setLayoutParams(layoutParams);

            layoutParams = (FrameLayout.LayoutParams) textContainerView.getLayoutParams();
            layoutParams.width = MATCH_PARENT;
            layoutParams.leftMargin = AndroidUtilities.dp(16);
            layoutParams.rightMargin = AndroidUtilities.dp(16);
            layoutParams.bottomMargin = AndroidUtilities.dp(16);
            textContainerView.setLayoutParams(layoutParams);
        }
        fixVideoSize();
        videoTimelineView.clearFrames();
    }

    private void fixLayout() {
       /* if (fragmentView == null) {
            return;
        }
        fragmentView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                fixLayoutInternal();
                if (fragmentView != null) {
                    if (Build.VERSION.SDK_INT < 16) {
                        fragmentView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    } else {
                        fragmentView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                }
            }
        });*/
    }

    private void play() {
        if (videoPlayer == null || !playerPrepared) {
            return;
        }
        if (videoPlayer.isPlaying()) {
            videoPlayer.pause();
            playButton.setImageResource(R.drawable.video_play);
        } else {
            try {
                playButton.setImageDrawable(null);
                lastProgress = 0;
                if (needSeek) {
                    videoPlayer.seekTo((int) (videoDuration * videoSeekBarView.getProgress()));
                    needSeek = false;
                }
                videoPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                    @Override
                    public void onSeekComplete(MediaPlayer mp) {
                        float startTime = videoTimelineView.getLeftProgress() * videoDuration;
                        float endTime = videoTimelineView.getRightProgress() * videoDuration;
                        if (startTime == endTime) {
                            startTime = endTime - 0.01f;
                        }
                        lastProgress = (videoPlayer.getCurrentPosition() - startTime) / (endTime - startTime);
                        float lrdiff = videoTimelineView.getRightProgress() - videoTimelineView.getLeftProgress();
                        lastProgress = videoTimelineView.getLeftProgress() + lrdiff * lastProgress;
                        videoSeekBarView.setProgress(lastProgress);
                    }
                });
                videoPlayer.start();
                synchronized (sync) {
                    if (thread == null) {
                        thread = new Thread(progressRunnable);
                        thread.start();
                    }
                }
            } catch (Exception e) {
                Log.e("tmessages", e.getMessage());
            }
        }
    }

    private boolean processOpenVideo() {
        try {
            File file = new File(videoPath);
            originalSize = file.length();

            IsoFile isoFile = new IsoFile(videoPath);
            List<Box> boxes = Path.getPaths(isoFile, "/moov/trak/");
            TrackHeaderBox trackHeaderBox = null;
            boolean isAvc = true;
            boolean isMp4A = true;

            Box boxTest = Path.getPath(isoFile, "/moov/trak/mdia/minf/stbl/stsd/mp4a/");
            if (boxTest == null) {
                isMp4A = false;
            }

            if (!isMp4A) {
                return false;
            }

            boxTest = Path.getPath(isoFile, "/moov/trak/mdia/minf/stbl/stsd/avc1/");
            if (boxTest == null) {
                isAvc = false;
            }

            for (Box box : boxes) {
                TrackBox trackBox = (TrackBox) box;
                long sampleSizes = 0;
                long trackBitrate = 0;
                try {
                    MediaBox mediaBox = trackBox.getMediaBox();
                    MediaHeaderBox mediaHeaderBox = mediaBox.getMediaHeaderBox();
                    SampleSizeBox sampleSizeBox = mediaBox.getMediaInformationBox().getSampleTableBox().getSampleSizeBox();
                    for (long size : sampleSizeBox.getSampleSizes()) {
                        sampleSizes += size;
                    }
                    videoDuration = (float) mediaHeaderBox.getDuration() / (float) mediaHeaderBox.getTimescale();
                    trackBitrate = (int) (sampleSizes * 8 / videoDuration);
                } catch (Exception e) {
                    Log.e("tmessages", e.getMessage());
                }
                TrackHeaderBox headerBox = trackBox.getTrackHeaderBox();
                if (headerBox.getWidth() != 0 && headerBox.getHeight() != 0) {
                    trackHeaderBox = headerBox;
                    originalBitrate = bitrate = (int) (trackBitrate / 100000 * 100000);
                    if (bitrate > 900000) {
                        bitrate = 900000;
                    }
                    videoFramesSize += sampleSizes;
                } else {
                    audioFramesSize += sampleSizes;
                }
            }
            if (trackHeaderBox == null) {
                return false;
            }

            Matrix matrix = trackHeaderBox.getMatrix();
            if (matrix.equals(Matrix.ROTATE_90)) {
                rotationValue = 90;
            } else if (matrix.equals(Matrix.ROTATE_180)) {
                rotationValue = 180;
            } else if (matrix.equals(Matrix.ROTATE_270)) {
                rotationValue = 270;
            }
            resultWidth = originalWidth = (int) trackHeaderBox.getWidth();
            resultHeight = originalHeight = (int) trackHeaderBox.getHeight();

            if (resultWidth > 640 || resultHeight > 640) {
                float scale = resultWidth > resultHeight ? 640.0f / resultWidth : 640.0f / resultHeight;
                resultWidth *= scale;
                resultHeight *= scale;
                if (bitrate != 0) {
                    bitrate *= Math.max(0.5f, scale);
                    videoFramesSize = (long) (bitrate / 8 * videoDuration);
                }
            }

            if (!isAvc && (resultWidth == originalWidth || resultHeight == originalHeight)) {
                return false;
            }
        } catch (Exception e) {
            Log.e("tmessages", e.getMessage());
            return false;
        }

        videoDuration *= 1000;

        updateVideoOriginalInfo();
        updateVideoEditedInfo();

        return true;
    }

    private int calculateEstimatedSize(float timeDelta) {
        int size = (int) ((audioFramesSize + videoFramesSize) * timeDelta);
        size += size / (32 * 1024) * 16;
        return size;
    }


    static String path = "";
    public static void prepareSendingVideo(final String videoPath, final long estimatedSize, final long duration, final int width, final int height, final VideoEditedInfo videoEditedInfo) {
        if (videoPath == null || videoPath.length() == 0) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {

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
                FileLoader.getInstance().setMediaDirs(mediaDirs);

                if (videoEditedInfo != null || videoPath.endsWith("mp4")) {
                    path = videoPath;
                    String originalPath = videoPath;
                    File temp = new File(originalPath);
                    originalPath += temp.length() + "_" + temp.lastModified();
                    if (videoEditedInfo != null) {
                        originalPath += duration + "_" + videoEditedInfo.startTime + "_" + videoEditedInfo.endTime;
                        if (videoEditedInfo.resultWidth == videoEditedInfo.originalWidth) {
                            originalPath += "_" + videoEditedInfo.resultWidth;
                        }
                    }
                    TLRPC.TL_document document = null;

                    if (document == null) {
                        Bitmap thumb = ThumbnailUtils.createVideoThumbnail(videoPath, MediaStore.Video.Thumbnails.MINI_KIND);
                        TLRPC.PhotoSize size = scaleAndSaveImage(thumb, 90, 90, 55);
                        document = new TLRPC.TL_document();
                        document.thumb = size;
                        if (document.thumb == null) {
                            document.thumb = new TLRPC.TL_photoSizeEmpty();
                            document.thumb.type = "s";
                        } else {
                            document.thumb.type = "s";
                        }
                        document.mime_type = "video/mp4";

                        TLRPC.TL_documentAttributeVideo attributeVideo = new TLRPC.TL_documentAttributeVideo();
                        document.attributes.add(attributeVideo);
                        if (videoEditedInfo != null) {
                            attributeVideo.duration = (int) (duration / 1000);
                            if (videoEditedInfo.rotationValue == 90 || videoEditedInfo.rotationValue == 270) {
                                attributeVideo.w = height;
                                attributeVideo.h = width;
                            } else {
                                attributeVideo.w = width;
                                attributeVideo.h = height;
                            }
                            document.size = (int) estimatedSize;
                            String fileName = "Vaishali" + "_1"  + ".mp4";
//                            File cacheFile = new File(Environment.getExternalStorageDirectory().getPath()+"/DCIM/"+fileName);

                            File cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);

                            path = cacheFile.getAbsolutePath();
                        } else {
                            if (temp.exists()) {
                                document.size = (int) temp.length();
                            }
                            boolean infoObtained = false;
                            if (Build.VERSION.SDK_INT >= 14) {
                                MediaMetadataRetriever mediaMetadataRetriever = null;
                                try {
                                    mediaMetadataRetriever = new MediaMetadataRetriever();
                                    mediaMetadataRetriever.setDataSource(videoPath);
                                    String width = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                                    if (width != null) {
                                        attributeVideo.w = Integer.parseInt(width);
                                    }
                                    String height = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                                    if (height != null) {
                                        attributeVideo.h = Integer.parseInt(height);
                                    }
                                    String duration = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                                    if (duration != null) {
                                        attributeVideo.duration = (int) Math.ceil(Long.parseLong(duration) / 1000.0f);
                                    }
                                    infoObtained = true;
                                } catch (Exception e) {
                                    Log.e("tmessages", e.getMessage());
                                } finally {
                                    try {
                                        if (mediaMetadataRetriever != null) {
                                            mediaMetadataRetriever.release();
                                        }
                                    } catch (Exception e) {
                                        Log.e("tmessages", e.getMessage());
                                    }
                                }
                            }
                            if (!infoObtained) {
                                try {
                                    MediaPlayer mp = MediaPlayer.create(MyApplication.applicationContext, Uri.fromFile(new File(videoPath)));
                                    if (mp != null) {
                                        attributeVideo.duration = (int) Math.ceil(mp.getDuration() / 1000.0f);
                                        attributeVideo.w = mp.getVideoWidth();
                                        attributeVideo.h = mp.getVideoHeight();
                                        mp.release();
                                    }
                                } catch (Exception e) {
                                    Log.e("tmessages", e.getMessage());
                                }
                            }
                        }
                    }
                    final TLRPC.TL_document videoFinal = document;
                    final String finalPath = path;
                    final HashMap<String, String> params = new HashMap<>();
                    if (originalPath != null) {
                        params.put("originalPath", originalPath);
                    }
                    AndroidUtilities.createMediaPaths();
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            sendMessage(null, videoEditedInfo, videoFinal, finalPath,false, null,params);
                        }
                    });
                }
            }
        }).start();
    }

    private static void sendMessage(String message, VideoEditedInfo videoEditedInfo, TLRPC.TL_document document, String path, boolean searchLinks, MessageObject retryMessageObject, HashMap<String, String> params) {

        String originalPath = null;
        if (params != null && params.containsKey("originalPath")) {
            originalPath = params.get("originalPath");
        }

        TLRPC.Message newMsg = null;
        MessageObject newMsgObj = null;
        int type = -1;


        try {

            if (document != null) {
                {
                    newMsg = new TLRPC.TL_message();
                }
                newMsg.media = new TLRPC.TL_messageMediaDocument();
                newMsg.media.caption = document.caption != null ? document.caption : "";
                newMsg.media.document = document;
                if (params != null && params.containsKey("query_id")) {
                    type = 9;
                } else if (MessageObject.isVideoDocument(document)) {
                    type = 3;
                }
                if (videoEditedInfo == null) {
                    newMsg.message = "-1";
                } else {
                    newMsg.message = videoEditedInfo.getString();
                }
                {
                    newMsg.attachPath = path;
                }

            }
            if (newMsg.attachPath == null) {
                newMsg.attachPath = "";
            }
            newMsg.out = true;


            newMsg.params = params;
            newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA;

            newMsg.unread = true;
            {
                newMsg.to_id = new TLRPC.TL_peerUser();

                if (newMsg.ttl != 0) {
                    if (MessageObject.isVideoMessage(newMsg)) {
                        int duration = 0;
                        for (int a = 0; a < newMsg.media.document.attributes.size(); a++) {
                            TLRPC.DocumentAttribute attribute = newMsg.media.document.attributes.get(a);
                            if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                                duration = attribute.duration;
                                break;
                            }
                        }
                    }
                }
            }


            newMsg.send_state = MessageObject.MESSAGE_SEND_STATE_SENDING;
            newMsgObj = new MessageObject(newMsg, null, true);
            if (!newMsgObj.isForwarded() && newMsgObj.type == 3) {
                newMsgObj.attachPathExists = true;
            }

            ArrayList<MessageObject> objArr = new ArrayList<>();
            objArr.add(newMsgObj);
            ArrayList<TLRPC.Message> arr = new ArrayList<>();
            arr.add(newMsg);

            if (type == 0 || type == 9 && message != null) {
                {
                    {
                        TLRPC.TL_messages_sendMessage reqSend = new TLRPC.TL_messages_sendMessage();
                        reqSend.message = message;
                        if (newMsg.to_id instanceof TLRPC.TL_peerChannel) {
                            reqSend.silent = MyApplication.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE).getBoolean("silent_" + 0, false);
                        }
                        reqSend.random_id = newMsg.random_id;

                        if (!searchLinks) {
                            reqSend.no_webpage = true;
                        }

                    }
                }
            } else if (type >= 1 && type <= 3 || type >= 5 && type <= 8 || type == 9 ) {
                TLRPC.InputMedia inputMedia = null;
                DelayedMessage delayedMessage = null;
                if (type == 3) {
                    if (document.access_hash == 0) {
                        if (document.thumb.location != null) {
                            inputMedia = new TLRPC.TL_inputMediaUploadedThumbDocument();
                        } else {
                            inputMedia = new TLRPC.TL_inputMediaUploadedDocument();
                        }
                        inputMedia.caption = document.caption != null ? document.caption : "";
                        inputMedia.mime_type = document.mime_type;
                        inputMedia.attributes = document.attributes;
                        delayedMessage = new DelayedMessage();
                        delayedMessage.originalPath = originalPath;
                        delayedMessage.type = 1;
                        delayedMessage.obj = newMsgObj;
                        delayedMessage.location = document.thumb.location;
                        delayedMessage.documentLocation = document;
                        delayedMessage.videoEditedInfo = videoEditedInfo;
                    } else {
                        TLRPC.TL_inputMediaDocument media = new TLRPC.TL_inputMediaDocument();
                        media.id = new TLRPC.TL_inputDocument();
                        media.caption = document.caption != null ? document.caption : "";
                        media.id.id = document.id;
                        media.id.access_hash = document.access_hash;
                        inputMedia = media;
                    }
                }

                TLObject reqSend;
                TLRPC.TL_messages_sendMedia request = new TLRPC.TL_messages_sendMedia();
                request.random_id = newMsg.random_id;
                request.media = inputMedia;
                if (delayedMessage != null) {
                    delayedMessage.sendRequest = request;
                }

                if (type == 3) {
                    if (document.access_hash == 0) {
                        performSendDelayedMessage(delayedMessage);
                    }
                }
                reqSend = request;
            }
        }
        catch (Exception e) {
            Log.e("tmessages", e.getMessage());
            if (newMsgObj != null) {
                newMsgObj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
            }
        }
    }
    protected static class DelayedMessage {
        public TLObject sendRequest;
        public TLRPC.TL_decryptedMessage sendEncryptedRequest;
        public int type;
        public String originalPath;
        public TLRPC.FileLocation location;
        public TLRPC.TL_document documentLocation;
        public String httpLocation;
        public MessageObject obj;
        public TLRPC.EncryptedChat encryptedChat;
        public VideoEditedInfo videoEditedInfo;
    }

    private static void performSendDelayedMessage(final DelayedMessage message) {
        if (message.type == 1) {

            if (message.type == 1) {
                if (message.videoEditedInfo != null) {
                    String location = message.obj.messageOwner.attachPath;
                    long id = message.documentLocation.id;
                    if (location == null) {
                        location = FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + id+ ".mp4";
                    }
                    MediaController.getInstance().scheduleVideoConvert(message , id);
                } else {
                    if (message.sendRequest != null) {
                        TLRPC.InputMedia media;
                        if (message.sendRequest instanceof TLRPC.TL_messages_sendMedia) {
                            media = ((TLRPC.TL_messages_sendMedia) message.sendRequest).media;
                        } else {
                            media = ((TLRPC.TL_messages_sendBroadcast) message.sendRequest).media;
                        }
                        if (media.file == null) {
                            String location = message.obj.messageOwner.attachPath;
                            if (location == null) {
                                location = FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + message.documentLocation.id + ".mp4";
                            }

                        } else {
                            String location = FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + message.location.volume_id + "_" + message.location.local_id + ".jpg";
//                            putToDelayedMessages(location, message);
//                            FileLoader.getInstance().uploadFile(location, false, true);
                        }
                    } else {
                        String location = message.obj.messageOwner.attachPath;
                        if (location == null) {
                            location = FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + message.documentLocation.id + ".mp4";
                        }
//                        putToDelayedMessages(location, message);
                        if (message.obj.videoEditedInfo != null) {
//                            FileLoader.getInstance().uploadFile(location, true, false, message.documentLocation.size);
                        } else {
//                            FileLoader.getInstance().uploadFile(location, true, false);
                        }
                    }
                }
            }
        }  else if (message.type == 3) {


            if (message.videoEditedInfo != null) {
                String location = message.obj.messageOwner.attachPath;
                if (location == null) {
                    location = FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + message.documentLocation.id + ".mp4";
                }
                long loc = message.documentLocation.id;
//                putToDelayedMessages(location, message);
                MediaController.getInstance().scheduleVideoConvert(message, loc);


            }
        }
    }


    public static TLRPC.PhotoSize scaleAndSaveImage(Bitmap bitmap, float maxWidth, float maxHeight, int quality) {
        return scaleAndSaveImage(bitmap, maxWidth, maxHeight, quality, 0, 0);
    }

    public static TLRPC.PhotoSize scaleAndSaveImage(Bitmap bitmap, float maxWidth, float maxHeight, int quality, int minWidth, int minHeight) {
        if (bitmap == null) {
            return null;
        }
        float photoW = bitmap.getWidth();
        float photoH = bitmap.getHeight();
        if (photoW == 0 || photoH == 0) {
            return null;
        }
        boolean scaleAnyway = false;
        float scaleFactor = Math.max(photoW / maxWidth, photoH / maxHeight);
        if (minWidth != 0 && minHeight != 0 && (photoW < minWidth || photoH < minHeight)) {
            if (photoW < minWidth && photoH > minHeight) {
                scaleFactor = photoW / minWidth;
            } else if (photoW > minWidth && photoH < minHeight) {
                scaleFactor = photoH / minHeight;
            } else {
                scaleFactor = Math.max(photoW / minWidth, photoH / minHeight);
            }
            scaleAnyway = true;
        }
        int w = (int) (photoW / scaleFactor);
        int h = (int) (photoH / scaleFactor);
        if (h == 0 || w == 0) {
            return null;
        }

        try {
            return scaleAndSaveImageInternal(bitmap, w, h, photoW, photoH, scaleFactor, quality, scaleAnyway);
        } catch (Throwable e) {
            Log.e("tmessages", e.getMessage());
//            ImageLoader.getInstance().clearMemory();
            System.gc();
            try {
                return scaleAndSaveImageInternal(bitmap, w, h, photoW, photoH, scaleFactor, quality, scaleAnyway);
            } catch (Throwable e2) {
                Log.e("tmessages", e2.getMessage());
                return null;
            }
        }
    }


    private static TLRPC.PhotoSize scaleAndSaveImageInternal(Bitmap bitmap, int w, int h, float photoW, float photoH, float scaleFactor, int quality, boolean scaleAnyway)
            throws Exception {
        Bitmap scaledBitmap;
      /*  if (scaleFactor > 1 || scaleAnyway) {
            scaledBitmap = Bitmaps.createScaledBitmap(bitmap, w, h, true);
        } else */{
            scaledBitmap = bitmap;
        }

        TLRPC.TL_fileLocation location = new TLRPC.TL_fileLocation();
        location.volume_id = Integer.MIN_VALUE;
        location.dc_id = Integer.MIN_VALUE;
        TLRPC.PhotoSize size = new TLRPC.TL_photoSize();
        size.location = location;
        size.w = scaledBitmap.getWidth();
        size.h = scaledBitmap.getHeight();
        if (size.w <= 100 && size.h <= 100) {
            size.type = "s";
        } else if (size.w <= 320 && size.h <= 320) {
            size.type = "m";
        } else if (size.w <= 800 && size.h <= 800) {
            size.type = "x";
        } else if (size.w <= 1280 && size.h <= 1280) {
            size.type = "y";
        } else {
            size.type = "w";
        }

        String fileName = "Vaishali"+location.volume_id + "_" + location.local_id + ".jpg";
        final File cacheFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/DCIM/", fileName);
        FileOutputStream stream = new FileOutputStream(cacheFile);
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream);
            size.size = (int) stream.getChannel().size();
        stream.close();
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle();
        }

        return size;
    }

}
