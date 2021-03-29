package com.taedison.helmy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/***
 * Foreground service created if bike gets disconnected after HelmyM is enabled for ignition
 * this service will speak "Bike will be disconnected in ...", even if app closed.
 */

public class ServiceBikeDisconnected extends Service {

    private String TAG = "ServiceBikeDisconnected";

    public static final String CHANNEL_ID = "NotifChannelBikeDisconnected";
    static Vibrator vibrator;
    static CountDownTimer counter;
    private int timeIlimitSec = 90;
    private static int remainingTime = 0;
    final static int NOTIFICATION_ID = Static_AppVariables.notifID_BikeDisconnected;

    public static boolean running = false; // set to true outside of the service, before calling
    // start. This faster than setting true in onCreate or onStartCommand,
    // and prevents fromActivity starting the service multiple times, e.g.,
    // when helmet sends multiples times in a row the emergency signal

    //notifications
    Intent notificationIntent;
    PendingIntent pendingIntent;
    NotificationManagerCompat notificationManager;

    //TTS
    private SingletonTSS_Helmet mTTS;

    @Override
    public void onCreate() {
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        // TTS
        mTTS = SingletonTSS_Helmet.getInstance(this.getApplicationContext());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

//        Log.d(TAG, "onStartCommand");

        createNotificationChannel();
        notificationIntent = new Intent(this, ActivityMain.class); // clicking on the notification will take the user back to ActivityMain
        pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);

        //Create an Intent for the BroadcastReceiver
        Intent cancelIntent = new Intent(this, ReceiverCancelEmergency.class);
        cancelIntent.putExtra("notificationId", NOTIFICATION_ID);
        //Create the PendingIntent
        final PendingIntent cancelPendingIntent = PendingIntent.getBroadcast(this,
                Static_AppVariables.notifCancelBtn_requestCode_BikeDisconnected, cancelIntent, 0);

        notificationManager = NotificationManagerCompat.from(this);
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID);
        builder.setContentTitle(getResources().getString(R.string.bikeWillTurnOffIn) + " " + timeIlimitSec)
                .setSmallIcon(R.mipmap.ic_launcher_helmet_round)
                .setContentIntent(pendingIntent)
                .setProgress(timeIlimitSec, 0, false)
                .addAction(0, getResources().getString(R.string.CancelEmergencyBtn), cancelPendingIntent);

        final Notification notification = builder.build();

        startForeground(NOTIFICATION_ID, notification);

        //counter
        counter = new CountDownTimer(timeIlimitSec * 1000, 1000) {

            public void onTick(long millisUntilFinished) {
                builder.setContentTitle(getResources().getString(R.string.bikeWillTurnOffIn) + " " + (int) (millisUntilFinished / 1000L))
                        .setProgress(timeIlimitSec, (int) (timeIlimitSec - millisUntilFinished / 1000L), false);
                notificationManager.notify(NOTIFICATION_ID, builder.build());
                vibrateSMS();

                remainingTime = (int) Math.ceil(millisUntilFinished/1000);
                if (remainingTime % 10d == 0) {
                    // multiple of 10
                    if(remainingTime > 5){
                        mTTS.speakSentence( getResources().getString(R.string.bikeWillTurnOffIn)
                                + " " + remainingTime + " " + getResources().getString(R.string.seconds) );
                    }
//                    Log.d("TTsEmergency", "Remaining= " + remainingTime + " in millis= " + millisUntilFinished);
                }
            }

            public void onFinish() {
                stopSelf();
            }

        }.start();

        // Text to speech: bike will shut off in 90 seconds. User has 90 seconds to cancel it.
        mTTS.speakSentence(getResources().getString(R.string.bikeWillTurnOffIn90));
//        Log.d("TTsEmergency", "90 seconds");

        // If we get killed because of insuficient memory, START NOT STICKY do not restart when memory is released
        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Emergency ServiceHelmyM Channel",
                    NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    public void vibrateSMS() {
        if(vibrator != null) {
            // Vibrate at intervals of 500 milliseconds (0 off, 500 on, 500 off)
            long[] pattern = {0, 500, 500};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0)); //off on off. Repeat start at index 0
            } else {
                //deprecated in API 26
                vibrator.vibrate(pattern, 0); //off on off. Repeat start at index 0
            }
        }
    }

    public static int getRemainigTimeCounter_ms(){
//        Log.d("ServiceBikeDisconnected", "destroyed");
        return remainingTime;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onDestroy() {
        running = false;
//        Log.d(TAG, "destroyed");
//        Toast.makeText(this, "Bike service done", Toast.LENGTH_SHORT).show();
        counter.cancel();
        if(vibrator != null) {
            vibrator.cancel();
        }
    }
}
