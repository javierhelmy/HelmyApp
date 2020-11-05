package com.taedison.helmy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/***
 * Used for cancelling alerts from the button embedded in the notifications
 */
public class ReceiverCancelEmergency extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int notificationId = intent.getIntExtra("notificationId", 0);
        Log.d("CancelBtn", "id= " + notificationId);
        if (notificationId == Static_AppVariables.notifID_Emergency) {
            Log.d("CancelBtn", "from emergency");
            //notify ActivityEmergency that it was canceled, so that the activity unbinds fromActivity service
            Intent i = new Intent(Static_AppVariables.ACTIONFILTER_EMERGENCY);
            context.sendBroadcast(i);
        } else if(notificationId == Static_AppVariables.notifID_BikeDisconnected){
            Log.d("CancelBtn", "from bikeDisconnected");
            Intent intentSS = new Intent(context, ServiceBikeDisconnected.class);
            context.stopService(intentSS);
        }

    }

}
