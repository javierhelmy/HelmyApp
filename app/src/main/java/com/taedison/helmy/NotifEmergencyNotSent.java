package com.taedison.helmy;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/***
 * Launches a new notification that displays the reason for not sending the alert
 */
class NotifEmergencyNotSent {

    private static final String CHANNEL_ID = "ChannelNotifEmergencyCancelled";
    private final static int NOTIFICATION_CANCELLED_ID = 4;

    static void Launch(Context context, String msg){
        createNotificationChannel(context);
        //Launch new notification
        Intent cancelIntent = new Intent(context, ActivityEmergency.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, cancelIntent,0);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID);
        builder.setContentTitle(msg)
                .setSmallIcon(R.mipmap.ic_launcher_helmet_round)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        notificationManager.notify(NOTIFICATION_CANCELLED_ID, builder.build());
    }

    private static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Emergency ServiceHelmyM Channel",
                    NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
