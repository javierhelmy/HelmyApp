package com.taedison.helmy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.gson.JsonObject;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/***
 * Service executes the alert as a foreground service, it keeps on running even if app is closed
 * The alert is, for now, just an sms to emergency contacts, in the future it will also include
 * the police and an ambulance).
 * To send the alert it is necessary to get the current GPS location, therefore GPS must be enabled.
 * If GPS is off, it will play a voice command to let the user know he needs to turn it on. Request
 * to turn it on is only send from ActivityEmergency
 * Also, to send the sms alert is necessary to have an sim card and have an active service with the
 * phone carrier
 * If an alert fails, user can try to send it again from ActivityEmergency, from the main menu
 * (ActivityGoAs), or if HelmyC is connected to HelmyApp, user can also restart the alert
 * by clicking three times in the main button in HelmyC
 */
public class ServiceEmergency extends Service {

    private static String TAG = "EmergencyService";

    public static final String CHANNEL_ID = "NotifChannelEmergency";
    static Vibrator vibrator;
    static CountDownTimer counterSMS;
    private int timeIlimitSec = 120;
    private long remainigTimeCounter_ms;
    final static int NOTIFICATION_ID = Static_AppVariables.notifID_Emergency;
    static String bikeMAC;

    BroadcastReceiver cancelEmergencyReceiver;

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
    public static boolean emergencyCancelled = false;

    SingletonSharedPreferences preferences;

//    String SENT = "SMS_SENT";
//    PendingIntent sentPendingIntent;

    CountDownTimer timerLocation;

    @Override
    public void onCreate() {
        //Vibrator
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        // TTS
        mTTS = SingletonTSS_Helmet.getInstance(this.getApplicationContext());

        preferences = SingletonSharedPreferences.getInstance(this.getApplicationContext());

//        activityEmergencyNotified = false;

        Log.d(TAG, "Service emergency created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.d(TAG, "NotificationEmergency");

        createNotificationChannel();
        notificationIntent = new Intent(this, ActivityEmergency.class);
        pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);

        //Create an Intent for the BroadcastReceiver
        final Intent cancelIntent = new Intent(this, ReceiverCancelEmergency.class);
        cancelIntent.putExtra("notificationId", NOTIFICATION_ID);
        //Create the PendingIntent
        final PendingIntent cancelPendingIntent = PendingIntent.getBroadcast(this,
                Static_AppVariables.notifCancelBtn_requestCode_Emergency, cancelIntent, 0);

        notificationManager = NotificationManagerCompat.from(this);
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID);
        builder.setContentTitle(getResources().getString(R.string.EmergencyWillBeSentIn) + " " + timeIlimitSec)
                .setSmallIcon(R.mipmap.ic_launcher_helmet_round)
                .setContentIntent(pendingIntent)
                .setProgress(timeIlimitSec, 0, false)
                .addAction(R.mipmap.ic_launcher_helmet_round, getResources().getString(R.string.CancelEmergencyBtn), cancelPendingIntent)
                .setPriority(Notification.PRIORITY_MAX)
                .setWhen(0);

        final Notification notification = builder.build();

        startForeground(NOTIFICATION_ID, notification);

        //counter
        counterSMS = new CountDownTimer(timeIlimitSec * 1000, 1000) {

            public void onTick(long millisUntilFinished) {
                builder.setContentTitle(getResources().getString(R.string.EmergencyWillBeSentIn) + " " + (int) (millisUntilFinished / 1000L))
                        .setProgress(timeIlimitSec, (int) (timeIlimitSec - millisUntilFinished / 1000L), false);
                notificationManager.notify(NOTIFICATION_ID, builder.build());
                vibrateSMS();
                remainigTimeCounter_ms = millisUntilFinished;

                int remaining = (int) Math.ceil(millisUntilFinished/1000);
                if (remaining % 10d == 0) {
                    // multiple of 10
                    checkGPS(remaining);

                    Log.d(TAG, "TTsEmergency Remaining= " + remaining + " in millis= " + millisUntilFinished);
                }
            }

            public void onFinish() {
                sendEmergencySMS();
            }

        }.start();

        // sendBroadcast to EmergencyActivity so that it starts animating the circle and displays views
        sendActivityAlertSent(0);

        // Text to speech: alert will be sent in 120 seconds. User has 120 seconds to cancel it.
        mTTS.speakSentence(getResources().getString(R.string.EmergencyWillBeSentIn120));
        Log.d(TAG, "TTsEmergency 120 seconds");

        cancelEmergencyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d("CancelBtn", "from emergency");
                cancelService();
                NotifEmergencyNotSent.Launch(ServiceEmergency.this, getResources().getString(R.string.EmergencyAlertWasCancelled));
                sendActivityAlertSent(4);
                preferences.setMessageAboutAlert(getResources().getString(R.string.EmergencyAlertWasCancelled), 4);
                running = false;
                stopSelf();
            }
        };

        registerReceiver(cancelEmergencyReceiver, new IntentFilter(Static_AppVariables.ACTIONFILTER_EMERGENCY));

        // If we get killed because of insuficient memory, START NOT STICKY do not restart when memory is released
        return START_NOT_STICKY;
    }

    public long getRemainigTimeCounter_ms(){
        return remainigTimeCounter_ms;
    }

    void checkGPS(final int remainingTime) {
        LocationRequest locationRequest = LocationRequest.create();

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);

        SettingsClient settingsClient = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = settingsClient.checkLocationSettings(builder.build());

        task.addOnSuccessListener(new OnSuccessListener<LocationSettingsResponse>() {
            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                if(remainingTime > 5){
                    mTTS.speakSentence( getResources().getString(R.string.EmergencyWillBeSentIn)
                            + " " + remainingTime + " " + getResources().getString(R.string.seconds));
                }
            }
        });

        task.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                // GPS off
                // We cannot request to turn it on from foreground services. Request is launched from ActivityEmergency if it is open
                // from this service we only warn the user by voice
                mTTS.speakSentence(getResources().getString(R.string.turnOnGPStoSendAlertIn)
                        + remainingTime + getResources().getString(R.string.seconds));
            }
        });
    }

    private void alertWasSentMsg_TTS_notif(){
        // Text to speech: alert was sent
        mTTS.speakSentence(getResources().getString(R.string.alertWasSentKeepCalm));
        Log.d(TAG, "TTsEmergency Sent");

        // send SMS
        if(vibrator != null) {
            vibrator.cancel();
        }

        // cancel foreground state and notification
        stopForeground(true);
        //launch new notification without cancel button
        NotificationCompat.Builder builder2 = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID);
        builder2.setContentTitle(getResources().getString(R.string.HelpOnItsWay))
                .setContentText(getResources().getString(R.string.emergencyAlertWasSent))
                .setSmallIcon(R.mipmap.ic_launcher_helmet_round)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        notificationManager.notify(NOTIFICATION_ID, builder2.build());
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

    public void sendEmergencySMS() {
        if( preferences.wasDemoAlreadyLaunched() ){
            // if true, then demo was already launch, this is a possible accident
            getDeviceLocation();
        } else {
            // sms is not sent in the demo
            alertWasSentMsg_TTS_notif();
            sendActivityAlertSent(1);
            preferences.setMessageAboutAlert(getResources().getString(R.string.alertWasSentKeepCalm), 1);
            preferences.set_demoWasLaunched();
        }
    }

    FusedLocationProviderClient fusedLocationClient;
    LocationCallback mLocationCallback;
    void getDeviceLocation(){
        remainigTimeCounter_ms = 0; // set to 0 in case user sent the alert before time was up
        //IMPORTANT: we can only get a "few" location updates per hour according to the
        // FusedLocationClient android documentation
        // We get update of the location to get the most recent location. We did not use the method
        // "getLastKnownLocation" because it does not work the first time the GPS is back on. In
        // order for it to work it is necessary to get an update, as we do here.
        final LocationRequest mLocationRequest = LocationRequest.create();
        mLocationRequest.setInterval(5000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    // location not available
                    notifyGPSerror();
                } else {
                    Log.d(TAG, "num locations: " + locationResult.getLocations().size() );
                    for (Location location : locationResult.getLocations()) {
                        if (location != null) {
                            fusedLocationClient.removeLocationUpdates(mLocationCallback); // so that it stops listening for updates

                            timerLocation.cancel(); // cancel so that the OnFinish method does not execute
                            String locationLink = "https://www.google.com/maps/search/?api=1&query="
                                    + location.getLatitude() + "," + location.getLongitude();
                            Log.d(TAG, "LocationLink: " + locationLink);

                            String smsText = "HELMY: " + preferences.getUserNames() + " "
                                    + getResources().getString(R.string.EmergencySMS) + locationLink;

                            if ( !TextUtils.isEmpty(bikeMAC) && !TextUtils.isEmpty(preferences.getBike2ndPolicy(bikeMAC)) ){
                                smsText = smsText + "\n\n" + getResources().getString(R.string.smsPrivatePolicyNumber)
                                        + " " + preferences.getBike2ndPolicy(bikeMAC) +
                                        getResources().getString(R.string.smsPrivatePolicyPhone) + " " +
                                        preferences.getBike2ndPolicyPhone(bikeMAC);
                            }

                            mTTS.speakSentence(getResources().getString(R.string.sending));
                            sendSMSs(smsText);
                        } else {
                            // location not available
                            notifyGPSerror();
                        }
                    }
                }
            }

            @Override
            public void onLocationAvailability(LocationAvailability locationAvailability) {
                if( !locationAvailability.isLocationAvailable() ){
                    // location not available
                    notifyGPSerror();
                }
            }
        };

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        fusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null);

        // if after 20 seconds we do not get an update, the user is probably is closed space (e.g.,
        // a tunnel)
        timerLocation = new CountDownTimer(20000, 20000) {
            @Override
            public void onTick(long l) {
                // nothing
            }

            @Override
            public void onFinish() {
                // in case we do not get an update within this time frame
                notifyGPSerror();
            }
        }.start();
    }

    void notifyGPSerror(){
        fusedLocationClient.removeLocationUpdates(mLocationCallback); // so that it stops listening for updates
        timerLocation.cancel(); // cancel so that the OnFinish method does not execute
        sendActivityAlertSent(2);
        String msg = getResources().getString(R.string.EmergencyAlertWasNotSentNoGPS);
        preferences.setMessageAboutAlert(msg, 2);
        mTTS.speakSentence(msg);
        NotifEmergencyNotSent.Launch(ServiceEmergency.this, msg);
    }

    void sendSMSs(final String smsText){
        // get array of emergency phone numbers in an array
        final ArrayList<String> arrayPhones = new ArrayList<>();
        arrayPhones.add(preferences.getUserEmergencyPhone());
        if( !TextUtils.isEmpty(preferences.getUserEmergencyPhone2()) ){
            arrayPhones.add(preferences.getUserEmergencyPhone2());
        }

//        String[] arrayPhones;
//        if( !TextUtils.isEmpty(preferences.getUserEmergencyPhone2()) ){
//            arrayPhones = new String[]{preferences.getUserEmergencyPhone(), preferences.getUserEmergencyPhone2()};
//        } else {
//            arrayPhones = new String[]{preferences.getUserEmergencyPhone()};
//        }

        String url = "https://oe4wdllwf5.execute-api.us-west-2.amazonaws.com/";

        Map<String, String> params = new HashMap<>();
        params.put("telefono", arrayPhones.toString());
        params.put("mensaje", smsText);

//        JSONObject jsonBody = new JSONObject();
//        try {
//            jsonBody.put("telefono", arrayPhones);
//            jsonBody.
//            jsonBody.put("mensaje", smsText);
//        } catch (JSONException e) {
//            e.printStackTrace();
//        }
        Log.d(TAG+"SMS", "json sent: " + new JSONObject(params));

        JsonObjectRequest jsonRequest = new JsonObjectRequest(url, new JSONObject(params),
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d(TAG+"SMS", "response: " + response);
                        Calendar calendar = Calendar.getInstance();
                        String dateSMS = calendar.get(Calendar.YEAR) +"-"+ (calendar.get(Calendar.MONTH)+1)
                                +"-"+ calendar.get(Calendar.DAY_OF_MONTH) +"-"+ calendar.get(Calendar.HOUR_OF_DAY)
                                +"-"+ calendar.get(Calendar.MINUTE)+"-"+ calendar.get(Calendar.SECOND);

                        try{
                            JSONArray jsonArray = response.getJSONArray("respuesta");
                            Log.d(TAG+"SMS", "json array: " + jsonArray);
                            JSONObject j = jsonArray.getJSONObject(0);
                            Log.d(TAG+"SMS", "json element: " + j);

                            if( jsonArray.length() == 0 ){
                                // an error occured, SMS were not sent from AWS
                                sendActivityAlertSent(5);
                                String msg = getResources().getString(R.string.EmergencyAlertWasNotSent_SMSservice);
                                preferences.setMessageAboutAlert(msg, 5);
                                mTTS.speakSentence(msg);
                                NotifEmergencyNotSent.Launch(ServiceEmergency.this, msg);
                                sendAlertRegistryToServer("0", dateSMS);
                                running = false;
                                stopSelf(); // stop service
                            } else if( jsonArray.length() == 1 && !TextUtils.isEmpty( jsonArray.getJSONObject(0).getString("id") ) ){
                                // alert was sent successfully to phone1
                                alertWasSentMsg_TTS_notif();
                                sendActivityAlertSent(1);
                                String msg = getResources().getString(R.string.alertWasSentKeepCalm);
                                preferences.setMessageAboutAlert(msg, 1);
                                sendAlertRegistryToServer( "0", dateSMS);
                                running = false;
                                stopSelf(); // stop service because one the sms was sent successfully
                            } else if( jsonArray.length() == 2 && ( !TextUtils.isEmpty( jsonArray.getJSONObject(0).getString("id") ) )
                                    || !TextUtils.isEmpty( jsonArray.getJSONObject(0).getString("id") ) ) {
                                // alert was sent successfully to phone1 or phone2
                                alertWasSentMsg_TTS_notif();
                                sendActivityAlertSent(1);
                                String msg = getResources().getString(R.string.alertWasSentKeepCalm);
                                preferences.setMessageAboutAlert(msg, 1);
                                sendAlertRegistryToServer( "0", dateSMS);
                                running = false;
                                stopSelf(); // stop service because one the sms was sent successfully
                            } else {
                                // phones numbers do not exist
                                sendActivityAlertSent(6);
                                String msg = getResources().getString(R.string.EmergencyAlertWasNotSent_NonExistingPhones);
                                preferences.setMessageAboutAlert(msg, 6);
                                mTTS.speakSentence(msg);
                                NotifEmergencyNotSent.Launch(ServiceEmergency.this, msg);
                                sendAlertRegistryToServer("0", dateSMS);
                                running = false;
                                stopSelf(); // stop service because one the sms was sent successfully
                            }

                        } catch (Exception ignored){
                            // an error occured, SMS were not sent from AWS
                            sendActivityAlertSent(5);
                            String msg = getResources().getString(R.string.EmergencyAlertWasNotSent_SMSservice);
                            preferences.setMessageAboutAlert(msg, 5);
                            mTTS.speakSentence(msg);
                            NotifEmergencyNotSent.Launch(ServiceEmergency.this, msg);
                            sendAlertRegistryToServer("0", dateSMS);
                            running = false;
                            stopSelf(); // stop service
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e(TAG+"SMS", error.toString());
                        // possibly user did not have internet connection
                        Static_AppMethods.checkResponseCode(error, preferences);
                        sendActivityAlertSent(3);
                        String msg = getResources().getString(R.string.EmergencyAlertWasNotSent_Internet);
                        preferences.setMessageAboutAlert(msg, 3);
                        mTTS.speakSentence(msg);
                        NotifEmergencyNotSent.Launch(ServiceEmergency.this, msg);
                        running = false;
                        stopSelf(); // stop service because one the sms was sent successfully
                    }
                }) {

//                        @Override
//                        protected Map<String, String> getParams() {
//                            Map<String, String> params = new HashMap<>();
//                            params.put("telefono", arrayPhones.toString());
//                            params.put("mensaje", smsText);
//                            return params;
//                        }

                        @Override
                        public Map<String, String> getHeaders() throws AuthFailureError {
                            Map<String, String>  params = new HashMap<String, String>();
                            params.put("Authorization", "AWS4-HMAC-SHA256 Credential=AKIAQVS75OZKOFUWMN5S/20210120/us-east-1/execute-api/aws4_request, SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature=bac4e231b74b32d8d9d4647e1a7ca7e326d5a5ca839e40b6840339d837cd3851");
                            params.put("X-Amz-Content-Sha256", "beaead3198f7da1e70d03ab969765e0821b24fc913697e929e726aeaebf0eba3");
                            params.put("X-Amz-Date", "20210120T053513Z");
                            return params;
                        }

                    };



        // increase the timeout period because for some reason this URL end point is throwing: BasicNetwork.logSlowRequests
        jsonRequest.setRetryPolicy(new DefaultRetryPolicy(
                DefaultRetryPolicy.DEFAULT_TIMEOUT_MS*4,
                0,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        //Volley
        RequestQueue queue = Volley.newRequestQueue(this);
        queue.add(jsonRequest);
    }

//    int numSMSparts;
//    void sendSMSs(String smsText){
//        sentPendingIntent = PendingIntent.getBroadcast(ServiceEmergency.this, 0, new Intent(
//                SENT), 0);
//        registerReceiver(receiverSMS_sent_sim0, new IntentFilter(SENT));
//
//        try {
//            Log.d(TAG, "----------------------------------");
//            SmsManager smsManager = SmsManager.getDefault();
//            Log.d(TAG, "getDefault");
////            ArrayList<String> parts = smsManager.divideMessage(smsText);
//            ArrayList<String> parts = splitEqually(smsText, 67); // smsManager.divideMessage fails in some Android Go devices
//            Log.d(TAG, "parts: " + parts);
//
//            ArrayList<PendingIntent> pendingIntentsArray = new ArrayList<>(); // pending intents are necessary for each part of the message sent
//            for (String ignored : parts){
//                pendingIntentsArray.add(sentPendingIntent); // assign the same pending intent for all parts
//            }
//
//            //SendSMS From default Sim
//            smsManager.sendMultipartTextMessage(preferences.getUserEmergencyPhone(), null, parts,
//                    pendingIntentsArray, null);
//
//            numSMSparts = 1;
//
//            if( !TextUtils.isEmpty(preferences.getUserEmergencyPhone2()) ){
//                smsManager.sendMultipartTextMessage(preferences.getUserEmergencyPhone2(), null, parts,
//                        pendingIntentsArray, null);
//
//                numSMSparts = 2;
//            }
//
//            numSMSparts = numSMSparts * parts.size();
//
//        } catch (Exception ex) {
//            Toast.makeText(getApplicationContext(), ex.getMessage(), Toast.LENGTH_LONG).show();
//            ex.printStackTrace();
//            Log.d(TAG, "Error: " + ex.getMessage());
//        }
//    }

//    boolean activityEmergencyNotified = false;
//    BroadcastReceiver receiverSMS_sent_sim0 = new BroadcastReceiver() {
//        @Override
//        public void onReceive(Context arg0, Intent arg1) {
//            Log.d(TAG, "SMS broadcast receiver. Result code: " + getResultCode() + " notified= " + activityEmergencyNotified );
//            if(!activityEmergencyNotified){
//                Calendar calendar = Calendar.getInstance();
//                String dateSMS = calendar.get(Calendar.YEAR) +"-"+ (calendar.get(Calendar.MONTH)+1)
//                        +"-"+ calendar.get(Calendar.DAY_OF_MONTH) +"-"+ calendar.get(Calendar.HOUR_OF_DAY)
//                        +"-"+ calendar.get(Calendar.MINUTE)+"-"+ calendar.get(Calendar.SECOND);
//                if (getResultCode() == Activity.RESULT_OK ) {
//                    // one of all SMS was send succesfully
//                    Log.d(TAG, "SMS sent OK");
//                    activityEmergencyNotified = true; // so that it does not enter here multiple times because sms is broken up into several parts
//                    alertWasSentMsg_TTS_notif();
//                    sendActivityAlertSent(1);
//                    String msg = getResources().getString(R.string.alertWasSentKeepCalm);
//                    preferences.setMessageAboutAlert(msg, 1);
//                    sendAlertRegistryToServer( "0", dateSMS);
//                    running = false;
//                    stopSelf(); // stop service because one the sms was sent successfully, no need for waiting for confirmation of the other sms
//                } else {
//                    // one of the messages was not sent
//                    numSMSparts--;
//                    if(numSMSparts == 0){
//                        activityEmergencyNotified = true; // so that it does not enter here multiple times because sms is broken up into several parts
//                        sendActivityAlertSent(3);
//                        String msg = getResources().getString(R.string.EmergencyAlertWasNotSentByCarrier);
//                        preferences.setMessageAboutAlert(msg, 3);
//                        mTTS.speakSentence(msg);
//                        NotifEmergencyNotSent.Launch(ServiceEmergency.this, msg);
//                        sendAlertRegistryToServer("0", dateSMS);
//                        running = false;
//                        stopSelf(); // stop service because one the sms was sent successfully, no need for waiting for confirmation of the other sms
//                    }
//                    Log.d(TAG, "SMS error");
//                }
//            }
//        }
//    };
//
//    private static ArrayList<String> splitEqually(String text, int size) {
//        ArrayList<String> ret = new ArrayList<>((text.length() + size - 1) / size);
//        for (int start = 0; start < text.length(); start += size) {
//            ret.add(text.substring(start, Math.min(text.length(), start + size)));
//        }
//        return ret;
//    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "ServiceEmergency",
                    NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private final IBinder mBinder = new ServiceEmergency.LocalBinder();

    class LocalBinder extends Binder {
        ServiceEmergency getService() {
            // Return this instance of LocalService so clients can call public methods
            return ServiceEmergency.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return mBinder;
    }

    public void cancelService(){
        emergencyCancelled = true;
        counterSMS.cancel();
        if(vibrator != null) {
            vibrator.cancel();
        }
        Log.d(TAG, "cancelService");

        mTTS.speakSentence(getResources().getString(R.string.EmergencyAlertWasCancelled));

        if( preferences.wasDemoAlreadyLaunched() ){
            // if true, then demo was already launch, this was an accident
            Calendar calendar = Calendar.getInstance();
            String dateCanceled = calendar.get(Calendar.YEAR) +"-"+ (calendar.get(Calendar.MONTH)+1)
                    +"-"+ calendar.get(Calendar.DAY_OF_MONTH) +"-"+ calendar.get(Calendar.HOUR_OF_DAY)
                    +"-"+ calendar.get(Calendar.MINUTE)+"-"+ calendar.get(Calendar.SECOND);
            sendAlertRegistryToServer(dateCanceled, "0");
        }

        Log.d("TTsEmergency", "Cancelled");
    }

    private void sendAlertRegistryToServer(String dateCanceled, String dateSMS){
        // alert was sent or cancelled and we need to send that information to the server using
        // ServiceAlerts
        String temp = preferences.getMostRecentAlert();
        if( !TextUtils.isEmpty(temp) ){
            String mostRecentToSend = temp + ";" + dateCanceled + ";" + dateSMS;
            preferences.addAlertRegistryPendingToUpload(mostRecentToSend);
            preferences.removeAlertRegistryAlreadyUploaded(temp);

            if( !ServiceAlerts.running && preferences.getSetAlertsPendingToUpload().size() > 0){
                // prevent from running twice the service. Anyways, this new file will be uploaded from service
                Intent intent = new Intent(this, ServiceAlerts.class);
                startService(intent);
            }
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        Log.d(TAG, "ServiceEmergency destroyed");
//        Toast.makeText(this, "Emergency service done", Toast.LENGTH_SHORT).show();
//        try{
//            unregisterReceiver(receiverSMS_sent_sim0);
//            Log.d(TAG, "SMS receiver unregistered");
//        } catch (Exception ignored){}
        if(cancelEmergencyReceiver != null){
            unregisterReceiver(cancelEmergencyReceiver);
            Log.d(TAG, "cancel receiver unregistered");
        }
        if(vibrator != null) {
            vibrator.cancel();
        }
        if(timerLocation != null){
            timerLocation.cancel();
        }
    }

    private void sendActivityAlertSent(int alertSent) {
        // notify the activity
        Intent intent = new Intent(Static_AppVariables.ACTIONFILTER_ALERT_SENT);
        intent.putExtra(Static_AppVariables.INTENTEXTRA_ALERT_SENT, alertSent);
        sendBroadcast(intent);
    }
}
