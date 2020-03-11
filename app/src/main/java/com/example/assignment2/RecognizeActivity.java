package com.example.assignment2;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.location.ActivityRecognition;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class RecognizeActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    public GoogleApiClient mApiClient;
    SimpleDateFormat ft = new SimpleDateFormat("hh:mm:ss");
    public static TextView aType;
    public static ImageView aImage;
    public static String previousActivity;
    public static long detectCount = 0;

    private static boolean isPlay = false;

    private static MediaPlayer mediaPlayer;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recognize);
        aType = findViewById(R.id.activityType);
        aImage = findViewById(R.id.activityImage);

        // Builds single client object that connects to Drive and Google+
        mApiClient = new GoogleApiClient.Builder(RecognizeActivity.this)
                .addApi(ActivityRecognition.API)
                //.addScope(SCOPE_FILE)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mApiClient.connect();

        Intent it = getIntent();
        String detectType = it.getStringExtra("Type");

            handleDetectType(detectType);


    }

    public void handleDetectType(String detectType) {
        boolean isMap = false;
        if(detectType != previousActivity){
            if(detectType.equals("Running")){
                // play music from the device musing list
                try {
                    playAudioResource();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                isPlay = true;
            } else if(detectType.equals("Walking") || detectType.equals("In Vehicle")){
                Intent i = new Intent(this,MapsActivity.class);
                startActivity(i);
                isMap = true;
                if(isPlay == true){
                    mediaPlayer.stop();
                    mediaPlayer.reset();
                    mediaPlayer.release();
                    isPlay = false;
                }
            } else if (detectType.equals("Still")) {
                if(isPlay==true){
                    mediaPlayer.stop();
                    mediaPlayer.release();
                    isPlay = false;
                }else if(isMap == true){
                    setContentView(R.layout.activity_recognize);
                    aType = findViewById(R.id.activityType);
                    aImage = findViewById(R.id.activityImage);
                    isMap = false;
                }
            }

            aType.setText("Current Activity: "+detectType);
            setActivityImage(detectType);
            SimpleDatabase db = new SimpleDatabase(this);
            long span = 0;
            Activity lastActivity = db.getLastActivity();
            if ((lastActivity.getType() != previousActivity) && (detectCount >1)) {

                if (lastActivity.getTime() != null) {
                    span = calculateTimeSpan(lastActivity);
                }
                if (span != 0) {
                    float timeSpan = (span/60);
                    String toast = "Your last activity "+ lastActivity+ " was for "+ timeSpan + " (Minutes.Seconds)";
                    Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
                }
            }
            db.close();
            previousActivity = detectType;
        } else if(detectType != null){
            aType.setText("Current Activity: "+detectType);
            setActivityImage(detectType);
        }
        detectCount++;
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Connect to Drive and Google+
        mApiClient.connect();
        //Log.e("onStart","START");
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Enqueue operation.
        // This operation will be enqueued and issued once the API clients are connected.
        // Only API retrieval operations are allowed.
        // Asynchronous callback required to not lock the UI thread.
        //Plus.PeopleApi.load(mApiClient, "me", "you", "that").setResultCallback(this);
        //Log.e("onResume","RESUME");
    }

    @Override
    public void onConnected(Bundle connectionHint) {

        //System.out.println("current previousActivity: " + previousActivity);
        //System.out.println("current detectCount: " + detectCount);

        Intent intent = new Intent(RecognizeActivity.this, ActivityRecognizedService.class);
        intent.setAction("com.example.assignment2.ACTIVITY_RECOGNIZED_SERVICE");
        PendingIntent pendingIntent = PendingIntent.getService(RecognizeActivity.this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        // Set the interval for how often the API should check the user's activity
        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(mApiClient, 1000, pendingIntent);

    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // At least one of the API client connect attempts failed
        // No client is connected
        // ...
        //System.out.println("Connection is failed.");
    }

    @Override
    public void onConnectionSuspended(int i) {
        // ...
    }

    public long calculateTimeSpan(Activity activity) {
        Date start = null;
        Date stop = new Date();
        try {
            start = ft.parse(activity.getTime());
            stop = ft.parse(ft.format(stop));
        } catch (Exception e) {
            e.printStackTrace();
        }
        long startTime = start.getTime();
        System.out.println("StartTime: "+ startTime);
        long stopTime = stop.getTime();
        System.out.println("StopTime: "+ stopTime);
        long timeSpan = stopTime - startTime;
        long diffSeconds = timeSpan/1000;

        System.out.println("Timespan: "+ timeSpan);

        return diffSeconds;
    }

    public static void setActivityImage(String s){
        if(s.equals("Still")){
            aImage.setImageResource(R.drawable.still);
        } else if(s.equals("In Vehicle")){
            aImage.setImageResource(R.drawable.vehicle);
        } else if(s.equals("Running")){
            aImage.setImageResource(R.drawable.running);
        } else if(s.equals("Walking")){
            aImage.setImageResource(R.drawable.walking);
        }
    }

    /*private void goToMain() {
        Intent i = new Intent(this,RecognizeActivity.class);
        startActivity(i);
    }*/

    public void playAudioResource() throws IOException {
        ContentResolver contentResolver = getContentResolver();
        Uri uri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Cursor cursor = contentResolver.query(uri, null, null, null, null);
        if (cursor == null) {
            // query failed, handle error.
        } else if (!cursor.moveToFirst()) {
            // no media on the device
        } else {
            int titleColumn = cursor.getColumnIndex(android.provider.MediaStore.Audio.Media.TITLE);
            int idColumn = cursor.getColumnIndex(android.provider.MediaStore.Audio.Media._ID);
            if((cursor.moveToFirst())){
                long thisId = cursor.getLong(idColumn);
                System.out.println("thisId is: "+thisId);
                String thisTitle = cursor.getString(titleColumn);
                System.out.println("thisTitle is: "+thisTitle);
                // ...process entry...
                Uri contentUri = ContentUris.withAppendedId(
                        android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, thisId);

                mediaPlayer = new MediaPlayer();
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mediaPlayer.setDataSource(getApplicationContext(), contentUri);

                // ...prepare and start...
                mediaPlayer.prepare();
                mediaPlayer.start();
            }
        }
    }

    public void checkPermission() {
        boolean isGranted = true;
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            if (this.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                isGranted = false;
            }
            if (this.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                isGranted = false;
            }
            Log.i("cbs", "isGranted == " + isGranted);
            if (!isGranted) {
                this.requestPermissions(
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission
                                .ACCESS_FINE_LOCATION,
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        102);
            }

        }

    }
}