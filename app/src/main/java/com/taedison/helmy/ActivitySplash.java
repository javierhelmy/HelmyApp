package com.taedison.helmy;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.animation.AlphaAnimation;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/***
 * This activity displays the logo and checks for conditions to use the app
 * Activity checks:
 *      if it is android Go, it will alert the user that Voice Assisant does not work when screen off
 *      checks if phone has a sim card
 *      checks Text-to-speech capabilities
 *      checks and asks user to grant permissions
 *      checks if phone has bluetooth
 *
 * This activity also checks for updates in user's data or login password that were done from the Helmy website
 * We also check if the primary server is down
 */
public class ActivitySplash extends AppCompatActivity {

    final String TAG = "SplashAct";

    ImageView imgLogo, imgLogoLetters;

    //shared preferences
    SingletonSharedPreferences preferences;

    // TTS
    SingletonTSS_Helmet mTTS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        setVolumeControlStream(AudioManager.STREAM_MUSIC); // so that volume keys change the multimedia volume and not the ringtone

        imgLogo = findViewById(R.id.imgLogoIntro);
        imgLogoLetters = findViewById(R.id.imgLogoLettersIntro);

        //shared preferences
        preferences = SingletonSharedPreferences.getInstance(this.getApplicationContext()); // for the last user logged in

        Log.d("SplashAct", "Oncreate");

        checkServerForDataLoginUpdates();

        // start uploading velocity txt files and upload registries of emergency alerts that were not uploaded from previous trips,
        // e.g., user did not have an internet connection
        if( !ServiceUploadTxt.running && preferences.getSetFilesPendingToUpload_name_userId_MAC().size() > 0){
            // prevent from running twice the service. Anyways, this new file will be uploaded from service
            Intent intent = new Intent(this, ServiceUploadTxt.class);
            startService(intent);
        }
        if( !ServiceAlerts.running && preferences.getSetAlertsPendingToUpload().size() > 0){
            // prevent from running twice the service. Anyways, this new file will be uploaded from service
            Intent intent = new Intent(this, ServiceAlerts.class);
            startService(intent);
        }

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // Emergency is priority in this if-then-else structure
        if(ServiceEmergency.running){
            // come back to ActivityEmergency because user closed the app while the alert was running
            Intent intent = new Intent(this, ActivityEmergency.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        } else if( ServiceBikeDisconnected.running){
            // bike was disconnected during a trip
            if(bluetoothAdapter.isEnabled()){
                // if bluetooth is on we can go back to ActivityMain
                Intent intent = new Intent(this, ActivityMain.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            } else {
                // go back to ActivityGoAs so that user can see that he/she needs to turn on the bluetooth
                Intent intent = new Intent(this, ActivityGoAs.class);
                intent.putExtra(Static_AppVariables.INTENTEXTRA_BLUETOOTHOFF, 1); // 1: bluetooth off while bike was on, dialog in ActivityGoAs must say Bike will be disconnected
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }
        } else {
            // check for conditions
            if(preferences.was_androidGO_checked()){
                checkSimReady();
            } else {
                checkAndroidGo(); // androidGo is checked only once
            }
        }
    }

    void checkAndroidGo(){
        if(isAndroidGoEdition(this)){
            final AlertMessageButton alert = new AlertMessageButton(this);
            alert.setDialogMessage(getResources().getString(R.string.isAndroidGO));
            alert.setDialogPositiveButton(getResources().getString(R.string.Ok), new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    checkSimReady();
                    alert.dismissAlert();
                }
            });
            alert.setCancellable(false);
            alert.hideCancelButton();
            alert.showAlert();
            preferences.set_isAndroidGo(true); // it is android go
        } else {
            preferences.set_isAndroidGo(false); // it is NOT android go
            checkSimReady();
        }
        preferences.androidGO_wasChecked();
    }

    void checkSimReady(){
        TelephonyManager telMgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (telMgr != null) {
            int simState = telMgr.getSimState(); // for the default sim, even if the sim in on slot number 2
            if(simState == TelephonyManager.SIM_STATE_READY){
                checkTTS();
            } else {
                final AlertMessageButton alert = new AlertMessageButton(this);
                alert.setDialogMessage(getResources().getString(R.string.simNotReady));
                alert.setDialogPositiveButton(getResources().getString(R.string.Ok),
                        new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        alert.dismissAlert();
                    }
                });
                alert.showAlert();
            }
        }
    }

    private void checkTTS() {
        // check the existence of a TTS package
        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(checkIntent, Static_AppVariables.REQUESTCODE_TTS_CHECK);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Static_AppVariables.REQUESTCODE_TTS_CHECK) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                // Yes, device has TTS. Initialize TTS
                mTTS = SingletonTSS_Helmet.getInstance(this.getApplicationContext());

                checkPermissions();

            } else {
                Toast.makeText(this, R.string.youNeedTTS, Toast.LENGTH_LONG).show();
                // missing data, install a TTS package
                Intent installIntent = new Intent();
                installIntent.setAction(
                        TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(installIntent);
            }
        }
    }

    private void checkPermissions() {
        // these are mandatory, it will continue asking until user grants the permissions
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.SEND_SMS},
                Static_AppVariables.REQUESTCODE_PERMISSIONS);
    }

    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (requestCode == Static_AppVariables.REQUESTCODE_PERMISSIONS && permissions.length > 0) {
            // access_fine_location incluye el permiso de coarse location
            if (grantResults.length <= 0 ||
                grantResults[0] == PackageManager.PERMISSION_DENIED || //fine_location
                grantResults[1] == PackageManager.PERMISSION_DENIED    //sms
                ) {

                if (Build.VERSION.SDK_INT >= 23) {
                    // in version above 23 user can reject permission and request not be asked again
                    boolean showRationale = shouldShowRequestPermissionRationale(permissions[0])
                            || shouldShowRequestPermissionRationale(permissions[1]) ;
                    if (!showRationale) {
                        // user denied permission and CHECKED "never ask again"
                        launchAlertActivatePersimissionsManually();
                    } else {
                        launchAlertSMSLocationIsaMust();
                        // user did NOT check "never ask again"
                    }
                } else {
                    launchAlertSMSLocationIsaMust();
                }

            } else {
                checkBluetooth();
            }
        }
    }

    private void launchAlertActivatePersimissionsManually() {
        final AlertMessageButton alert = new AlertMessageButton(this);
        alert.setDialogMessage(getResources().getString(R.string.enable_SMS_Location_PermissionsManually));
        alert.setDialogPositiveButton(getResources().getString(R.string.AlreadyEnabledPermissions), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkPermissions();
                alert.dismissAlert();
            }
        });
        alert.setDialogNegativeButton(getResources().getString(R.string.Go2Settings), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // go to the settings of the app
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
                alert.dismissAlert();
            }
        });
        alert.showAlert();
    }

    private void launchAlertSMSLocationIsaMust() {
        final AlertMessageButton alert = new AlertMessageButton(this);
        alert.setDialogMessage(getResources().getString(R.string.SMS_LocationPermissionExplanation));
        alert.setDialogPositiveButton(getResources().getString(R.string.Ok),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        checkPermissions();
                        alert.dismissAlert();
                    }
                });
        alert.showAlert();
    }

    private void checkBluetooth(){
        // Check if device supports bluetooth
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            AlertMessageButton alert = new AlertMessageButton(this);
            alert.setDialogMessage(getResources().getString(R.string.DeviceWithoutBluetooth));
            alert.showAlert();
        } else {
            // It does support Bluetooth
            Go2NextActivity();
        }
    }

    public void Go2NextActivity(){

        int TIME_SPLASH_MS = 2000;

        String lastUser = preferences.get_lastUser_email_logged();
        Log.d("SplashAct", "Last user = " + lastUser);

        startAnimationLogo();

        if( !TextUtils.isEmpty(lastUser) ){
            // User is already logged in
            final Intent intent;
            if( TextUtils.isEmpty(preferences.get_lastUser_Id_logged()) ){
                // if empty, then the user has not validated its email. Id is only saved when logging
                intent = new Intent(ActivitySplash.this, ActivityConfirmEmail.class);
            } else if( !preferences.get_downloadWasCompleteAfterLogin() ){
                // if user closed the ActivityRetrieveDataFromServer, before downloading and saving the data
                intent = new Intent(ActivitySplash.this, ActivityRetrieveDataFromServer.class);
            } else if( preferences.didUserRegister_data_devices() ){
                // if user already registered his/her personal info, emergency contacts and helmy devices
                intent = new Intent(ActivitySplash.this, ActivityGoAs.class);
            } else {
                // user needs to register his/her personal info, emergency contacts or helmy devices
                intent = new Intent(ActivitySplash.this, ActivityProgress.class);
            }

            new Handler().postDelayed(new Runnable(){
                public synchronized void run(){
                    // go to the next activity if TTS is ready to use
                    if(mTTS.isTTSready()) {
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
//                        throw new RuntimeException("Test Crash"); // Force a crash
                    } else {
                        checkTTS();
                    }
                }
            }, TIME_SPLASH_MS);
        } else {
            // no user has logged in
            new Handler().postDelayed(new Runnable(){
                public synchronized void run(){
                    // go to the next activity if TTS is ready to use
                    if(mTTS.isTTSready()){
                        Intent intent = new Intent(ActivitySplash.this, ActivityLoginRegister.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
//                        throw new RuntimeException("Test Crash"); // Force a crash
                    } else {
                        checkTTS();
                    }
                }
            }, TIME_SPLASH_MS);
        }
    }

    public boolean isAndroidGoEdition(Context context) {
        // apps that usually come pre-installed
        final String GMAIL_GO = "com.google.android.gm.lite";
        final String YOUTUBE_GO = "com.google.android.apps.youtube.mango";
        final String GOOGLE_GO = "com.google.android.apps.searchlite";
        final String ASSISTANT_GO = "com.google.android.apps.assistant";

        boolean isGmailGoPreInstalled = isPreInstalledApp(context, GMAIL_GO);
        boolean isYoutubeGoPreInstalled = isPreInstalledApp(context, YOUTUBE_GO);
        boolean isGoogleGoPreInstalled = isPreInstalledApp(context, GOOGLE_GO);
        boolean isAssistantGoPreInstalled = isPreInstalledApp(context, ASSISTANT_GO);

        if(isGoogleGoPreInstalled || isAssistantGoPreInstalled){
            // it is Android Go
            return true;
        }
        return isGmailGoPreInstalled && isYoutubeGoPreInstalled;
    }

    private boolean isPreInstalledApp(Context context, String packageName){
        //check if an app is installed
        try {
            PackageManager pacMan = context.getPackageManager();
            PackageInfo packageInfo = pacMan.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            if(packageInfo != null){
                //Check if comes with the image OS
                int mask = ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
                return (packageInfo.applicationInfo.flags & mask) != 0;
            }
        } catch (PackageManager.NameNotFoundException e) {
            //The app isn't installed
        }
        return false;
    }

    private void startAnimationLogo(){
        // animations
        long duration = 1500;
        ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(imgLogo, "scaleX", 1f); // set scale back to 1 (in XML was set to 0.5)
        ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(imgLogo, "scaleY", 1f); // set scale back to 1 (in XML was set to 0.5)
        final AnimatorSet scaleDownAnimator = new AnimatorSet();
        scaleDownAnimator.play(scaleUpX).with(scaleUpY);
        scaleDownAnimator.setDuration(duration);
        scaleDownAnimator.start();

        AlphaAnimation animation1 = new AlphaAnimation(0.0f, 1.0f); // from 0 to 1 in alpha (opacity)
        animation1.setDuration(duration);
        animation1.setFillAfter(true);
        imgLogoLetters.startAnimation(animation1);
    }

    private void checkServerForDataLoginUpdates(){
        // run this code to check if user data in server was updated or if user updated the password
        // from the website
        preferences.set_primaryServerIsDown(false); // set to false until one of the volley requests says otherwise
        if( !TextUtils.isEmpty(preferences.get_lastUser_email_logged()) ){
            // there is a user logged in
            final RequestQueue requestQueue = SingletonVolley.getInstance(this.getApplicationContext()).getRequestQueue();
            final Thread thread = new Thread(){
                @Override
                public void run() {
                    String url;
                    if(preferences.get_isPrimaryServerDown()){
                        // primary server is down, so we will use the secondary
                        url = Static_AppVariables.url_dataLoginUpdates2;
                    } else {
                        url = Static_AppVariables.url_dataLoginUpdates;
                    }

                    StringRequest strRequest = new StringRequest(Request.Method.POST, url,
                            new Response.Listener<String>()
                            {
                                @Override
                                public void onResponse(String response)
                                {
                                    Log.d(TAG+"Volley", response);
                                    try {
                                        JSONObject jsonArray = new JSONObject(response);
                                        String passwordUptaded = jsonArray.getString("updatePassword");
                                        String dataUptaded = jsonArray.getString("updateData");
                                        boolean mustUpdate = false; // if password or data was updated, we must update the variables that track such changes in the server
                                        if(!passwordUptaded.equals("0")){
                                            // user changed the password from the website
                                            mustUpdate = true;
                                            preferences.set_passwordUpdatedInServer(true);
                                        }
                                        if(!dataUptaded.equals("0")){
                                            // user updated data from the website
                                            preferences.set_dataUpdatedInServer(true);
                                            mustUpdate = true;
                                        }
                                        if(mustUpdate){
                                            requestQueue.add(requestNotifyServer());
                                        } else {
                                            Thread.currentThread().interrupt(); // kill thread
                                        }
                                        // if data or passwrod were updated, actions will taken in ActivityGoAs and ActivityRetrieveDataFromServer
                                    } catch (JSONException ignored) {
                                        // data or password has not changed
                                        ignored.printStackTrace();
                                        Thread.currentThread().interrupt(); // kill thread
                                    }
                                }
                            },
                            new Response.ErrorListener()
                            {
                                @Override
                                public void onErrorResponse(VolleyError error)
                                {
                                    Log.d(TAG+"Volley", error.toString());
                                    // we also check if server is down
                                    if(error.networkResponse.statusCode == 503){
                                        // server is down
                                        if(preferences.get_isPrimaryServerDown()){
                                            // The secondary server is down as well. We know this because get_isPrimaryServerDown returned true,
                                            // it was set to true when the first attempt of this volley request failed
                                            Thread.currentThread().interrupt(); // kill thread
                                        } else {
                                            // This volley request was done to the primary server
                                            preferences.set_primaryServerIsDown(true);
                                            checkServerForDataLoginUpdates(); // try again but now with the secondary server
                                        }
                                    } else {
                                        // there was other error, just ignore.
                                        Thread.currentThread().interrupt(); // kill thread
                                    }

                                }
                            })
                    {
                        @Override
                        protected Map<String, String> getParams()
                        {
                            Map<String, String> params = new HashMap<>();
                            params.put("userId", preferences.get_lastUser_Id_logged());
                            return params;
                        }
                    };

                    requestQueue.add(strRequest);
                }
            };
            thread.start();
        }
    }

    private StringRequest requestNotifyServer(){
        // update variables in server that track changes in data and password

        String url;
        if(preferences.get_isPrimaryServerDown()){
            // primary server is down, so we will use the secondary
            url = Static_AppVariables.url_dataLoginUpdates2;
        } else {
            url = Static_AppVariables.url_dataLoginUpdates;
        }
        return new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response)
                    {
                        Log.d(TAG+"Volley", "requestNotifyServer: " + response);
                        // if there is a response, server was notified successfully, we don't do anything else
                        Thread.currentThread().interrupt(); // kill thread
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                        // we also check if server is down
                        Log.d(TAG+"Volley", error.toString());
                        Thread.currentThread().interrupt(); // kill thread
                    }
                })
        {
            @Override
            protected Map<String, String> getParams()
            {
                Map<String, String> params = new HashMap<>();
                params.put("userId", preferences.get_lastUser_Id_logged());
                params.put("updatePassword", "0");
                params.put("updateData", "0");
                Log.d(TAG+"Volley", "requestNotifyServer params: " + params.toString());
                return params;
            }
        };
    }
}
