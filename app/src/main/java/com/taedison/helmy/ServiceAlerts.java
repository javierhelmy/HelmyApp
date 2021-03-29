package com.taedison.helmy;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.text.TextUtils;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/***
 * Service that sends registries of alerts that were launched. We send data that can be usedful such as:
 * Alert sent or cancelled
 * Alert sent to the police (not yet implemented in the business model)
 * Alert sent to the ambulate (not yet implemented in the business model)
 *
 * This service is executed when the app opens and uploads all pending registries
 */
public class ServiceAlerts extends Service {

    public static boolean running = false;
    private String TAG = "ServiceAlerts";
    private SingletonSharedPreferences preferences;
    private RequestQueue requestQueue;

    public ServiceAlerts() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

//        Log.d(TAG, "onStartCommand");

        running = true;

        preferences = SingletonSharedPreferences.getInstance(getApplicationContext());
        //volley
        requestQueue = SingletonVolley.getInstance(getApplicationContext()).getRequestQueue();

        uploadNextAlert();

        // If we get killed because of insuficient memory, START STICKY will restart when memory is released
        return START_STICKY;
    }

    private void uploadNextAlert(){
        String mostRecentAlert = preferences.getMostRecentAlert();
        if( !TextUtils.isEmpty(mostRecentAlert) ){
            prepareToSendToServer(mostRecentAlert);
        } else {
            ArrayList<String> listAlerts = new ArrayList<>( preferences.getSetAlertsPendingToUpload() );
            if(listAlerts.size() > 0){
                String alertInPrefs = listAlerts.get(0);
                prepareToSendToServer(alertInPrefs);
            } else {
                stopSelf();
            }
        }
    }

    private void prepareToSendToServer(String alert2send){
        String[] segments = alert2send.split(";");
        if(segments.length == 4){
            // ServiceEmergency was killed before cancelling or sending the alert, e.g., phone died or low memory
            //  thus it will be sent as a canceled alert
            String date = segments[0];
            String txtFile = segments[1];
            String userId = segments[2];
            String mac = segments[3];
            StringRequest joRequest = request(alert2send, date, txtFile, userId, mac, "-1", "-1"); //-1 indicates the phone was shut off before even trying to send the alert
            requestQueue.add(joRequest);

        } else if(segments.length == 6){
            String date = segments[0];
            String txtFile = segments[1];
            String userId = segments[2];
            String mac = segments[3];
            String dateCanceled = segments[4];
            String dateSMS = segments[5];

            StringRequest joRequest = request(alert2send, date, txtFile, userId, mac, dateCanceled, dateSMS);
            requestQueue.add(joRequest);
        } else {
            // something wrong with prefs
            stopSelf();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private StringRequest request(final String alertInPrefs, final String date, final String txtFile,
                                  final String userId, final String mac, final String wasCanceled, final String alertSMS){

        String url;
        if(preferences.get_isPrimaryServerDown()){
            // primary server is down, so we will use the secondary
            url = Static_AppVariables.url_impact2;
        } else {
            url = Static_AppVariables.url_impact;
        }

        return new StringRequest(Request.Method.POST, url,
                new com.android.volley.Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response)
                    {
                        if( !TextUtils.isEmpty(response) ) {
//                            Log.d(TAG, "Response: " + response + "\nname= " + alertInPrefs
//                                    + "\nalertSMS= " + alertSMS + "\nwasCanceled= " + wasCanceled);

                            preferences.removeAlertRegistryAlreadyUploaded(alertInPrefs);

                            uploadNextAlert();
                        } else {
                            // most likely the user has mobile data on but without internet service
                            stopSelf();
                        }

                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
//                        Log.e(TAG, "error: " + error.toString() + "\nname= " + alertInPrefs
//                                + "\nalertSMS= " + alertSMS + "\nwasCanceled= " + wasCanceled);
                        Static_AppMethods.checkResponseCode(error, preferences);
                        stopSelf();
                    }
                })
        {
            @Override
            protected Map<String, String> getParams()
            {
                Map<String, String> params = new HashMap<>();
                params.put("impact", date);
                params.put("txt", txtFile);
                params.put("userId", userId);
                params.put("mac", mac);
                params.put("canceled", wasCanceled);
                params.put("alertSMS", alertSMS);
                params.put("alertPolice","0");
                params.put("alertAmbulance","0");

                return params;
            }
        };
    }

    @Override
    public void onDestroy() {
//        Log.d(TAG, "Service Alert destroyed");
        running = false;
        super.onDestroy();
    }
}
