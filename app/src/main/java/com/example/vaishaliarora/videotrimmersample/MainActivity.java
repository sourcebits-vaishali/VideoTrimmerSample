package com.example.vaishaliarora.videotrimmersample;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;

/**
 * Created by vaishaliarora on 21/06/16.
 */
public class MainActivity extends Activity {

    private static final int SELECT_VIDEO = 1001;
    private static final String TAG = MainActivity.class.getSimpleName();

    private String mSelectedVideoPath = "" ;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        (findViewById(R.id.choose)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent mediaChooser = new Intent(Intent.ACTION_GET_CONTENT);
                mediaChooser.setType("video/*");
             /*   startActivityForResult(mediaChooser, 1);
                Intent i = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI);*/
                startActivityForResult(mediaChooser, SELECT_VIDEO);
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == SELECT_VIDEO) {
                mSelectedVideoPath = getPath(data.getData());
                try {
                    if(mSelectedVideoPath == null) {
                        Log.e(TAG, "selected video path = null!");
                        finish();
                    } else {
                       Bundle bundle = new Bundle();
                        bundle.putString(Util.VIDEO_PATH , mSelectedVideoPath);
                        Intent intent = new Intent(this, VideoEditorActivity.class);
                        intent.putExtras(bundle);
                        startActivity(intent);
                    }
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage());
                }
            }
        }
    }

    private String getPath(Uri uri) {
        String[] projection = { MediaStore.Images.Media.DATA };
        Cursor cursor = managedQuery(uri, projection, null, null, null);
        if(cursor!=null) {
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        }
        else return null;
    }
}
