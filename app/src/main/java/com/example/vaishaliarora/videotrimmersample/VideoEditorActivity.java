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
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
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
import java.util.List;



@TargetApi(16)
public class VideoEditorActivity extends Activity implements TextureView.SurfaceTextureListener {

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
            return;
        }
        Bundle bundle = getIntent().getExtras();
//        videoPath = bundle.getString(Util.VIDEO_PATH);
        videoPath = "/storage/emulated/0/DCIM/Camera/20160621_112826.mp4";
//        videoPath = Environment.getExternalStorageDirectory().getAbsolutePath()+"/DCIM/sample2.mp4";

        Log.d("Vaishali ", "Path == " + videoPath);
        if (videoPath == null || !processOpenVideo()) {
            return;
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

        originalSizeTextView = (TextView) findViewById(R.id.original_size);
        editedSizeTextView = (TextView) findViewById(R.id.edited_size);
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
//        fixLayoutInternal();

        (findViewById(R.id.trim_video)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int resWidth = compressVideo.isChecked() ? resultWidth : originalWidth;
                int resHeight =  compressVideo.isChecked() ? resultHeight : originalHeight;
                trimVideo(videoPath, startTime, endTime, resWidth,resHeight,  rotationValue, originalWidth, originalHeight, originalBitrate, estimatedSize, esimatedDuration);

            }
        });

    }

    private void trimVideo(String videoPath, long startTime, long endTime, int resultWidth, int resultHeight, int rotationValue, int originalWidth, int originalHeight, int bitrate, long estimatedSize, long estimatedDuration) {
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

        DelayedMessage delayedMessage = new DelayedMessage();
        delayedMessage.originalPath = videoPath;
        delayedMessage.type = 1;

        delayedMessage.videoEditedInfo = videoEditedInfo;
        MediaController.getInstance().scheduleVideoConvert(delayedMessage);
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
}