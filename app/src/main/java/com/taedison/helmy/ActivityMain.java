package com.taedison.helmy;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/***
 * Activity connects to HelmyC and HelmyM
 * Activity alerts the user if bluetooth or gps turn off, and if volume goes to 0 (user would not hear important voice alerts)
 * It shows the temperature section if HelmyC is covid version
 *
 * Logic:
 * - The app will try to connect first to HelmyC and then to HelmyM. HelmyM cant be enabled if HelmyC is not connected and put on
 * - Once everything is ready, user will be told that he/she can start
 * - If HelmyC launches the emergency alert, the ActivityEmergency will be launched but this will
 *      continue in the background so that helmy devices continue connected
 * - If HelmyM gets disconnected AFTER it has been enaled, the disconnection alert will be launched
 * - If user closes the app AFTER HelmyM has been enaled, the disconnection alert will be launched.
 *      User should go back to the main menu so that the alert is not launched. This was done because
 *      HelmyM does not have hardware to know if bike is on or off, we just know the connection state with HelmyM
 */
public class ActivityMain extends AppCompatActivity {
    private final String TAG = "actDriver";

    //Views
    //--heltmet--
    ProgressBar  pbHelmetBattery;
    ImageView imgHelmetConnected, imgRingHelmetConnected, imgMsgHelmetConnected,
            imgHelmetOnHead, imgRingHelmetOnHead, imgMsgHelmetOnHead,
            imgRingHelmetTemperature, imgMsgHelmetTemperature, imgThermometer, imgHelmetBattery;
    TextView tvHelmetConnected, tvHelmetOnHead, tvHelmetBatteryLevel, tvTemperature, tvTemperatureLabel;
    LinearLayout LLtemperature;
    boolean isCovidVersion;
    BLE_HelmyC bluetoothHelmet;

    //--bike--
    ImageView imgBikeConnected, imgRingBikeConnected, imgMsgBikeConnected;
    TextView tvBikeConnected, tvBikeUser_BondId;
    BLE_HelmyM bluetoothBike;

    //ActivityMain variables
    ConstraintLayout CLalertReady;

    //BLUETOOTH
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    ArrayList<String> arrayBleDevices = new ArrayList<>();
    private static final long SCAN_PERIOD = 5000; // scan for bles everys 5 seconds

    BroadcastReceiver bleHelmetDataReceiver, bleDeviceConnectionReceiver, bleErrorReceiver,
            bikeTurnOnOffReceiver, blueWrite_HelmyC_PhoneLowBatteryReceiver;

    //Helmy variables
    private String primaryHelmet_MAC;
    private String bike_MAC;

    // Bluetooth connection variables
    private boolean primaryHelmet_connected, bike_connected;
    private boolean primaryHelmet_worn, primaryHelmet_fastened;

    //preferences
    SingletonSharedPreferences preferences;

    //TTS
    SingletonTSS_Helmet mTTS;

    Vibrator vibrator;

    private BroadcastReceiver bluetoothStateReceiver, mGpsSwitchStateReceiver, batteryReceiver,
            phoneVolumeReceiver;

    String dateOfTheTrip;

    boolean isCharging, lowBattery;
    float batteryPct;

    AlertMessageButton alertTripFinished; // alert used to ask user if he wants to finish the trip and to stop ServiceBikeDisconnected
    CountDownTimer timerTripFinished; // to show remaining time in alertTripFinished
    boolean exitMotionLessThan5KmH = false;
    boolean exitPhoneInLowBattery = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setVolumeControlStream(AudioManager.STREAM_MUSIC); // so that volume keys change the multimedia volume and not the ringtone

        // VIEWS
        //Helmet

        imgHelmetConnected = findViewById(R.id.imgHelmetConnected);
        imgRingHelmetConnected = findViewById(R.id.imgRingHelmetConnected);
        imgMsgHelmetConnected = findViewById(R.id.imgLabelHelmetConnected);
        tvHelmetConnected = findViewById(R.id.tvHelmetConnected);

        imgHelmetOnHead = findViewById(R.id.imgHelmetOnHead);
        imgRingHelmetOnHead = findViewById(R.id.imgRingHelmetOnHead);
        imgMsgHelmetOnHead = findViewById(R.id.imgLabelHelmetOnHead);
        tvHelmetOnHead = findViewById(R.id.tvHelmetOnHead);

        imgHelmetBattery = findViewById(R.id.batteryMain);
        pbHelmetBattery = findViewById(R.id.pbHelmetBattery);
        tvHelmetBatteryLevel = findViewById(R.id.tvBatteryLevelMain);

        imgRingHelmetTemperature = findViewById(R.id.imgRingHelmetTemperature);
        imgMsgHelmetTemperature = findViewById(R.id.imgLabelHelmetTemperature);
        imgThermometer = findViewById(R.id.imgThermometer);
        tvTemperatureLabel = findViewById(R.id.tvTemperatureLabel);
        tvTemperature = findViewById(R.id.tvTemperature);
        LLtemperature = findViewById(R.id.LLtemperature);

        tvBikeUser_BondId = findViewById(R.id.tvBikeBlockchainId);


        //bike
        imgBikeConnected = findViewById(R.id.imgBikeConnected);
        imgRingBikeConnected = findViewById(R.id.imgRingBikeConnected);
        imgMsgBikeConnected = findViewById(R.id.imgLabelBikeConnected);
        tvBikeConnected = findViewById(R.id.tvBikeConnected);

        CLalertReady = findViewById(R.id.alertReady);

        //get preferences
        preferences = SingletonSharedPreferences.getInstance(this.getApplicationContext());
        //download thresholds for Helmy-C
        requestHelmyCthresholds();

        // Get the primary helmet and bike fromActivity preferences
        primaryHelmet_MAC = preferences.get_primaryHelmet_MAC();
        bike_MAC = preferences.get_primaryBike_MAC();

        // display bondId
        tvBikeUser_BondId.setText("ID:" + preferences.getBikeBondId(bike_MAC));

        //check if covid version
        isCovidVersion = preferences.getHelmet_isCovid(primaryHelmet_MAC);

        // ANIMATIONS
        imgRingHelmetConnected.startAnimation( AnimationUtils.loadAnimation(
                ActivityMain.this, R.anim.rotation_360_anim) );
        imgRingHelmetOnHead.startAnimation( AnimationUtils.loadAnimation(
                ActivityMain.this, R.anim.rotation_360_anim) );
        if(isCovidVersion){
            imgRingHelmetTemperature.startAnimation( AnimationUtils.loadAnimation(
                    ActivityMain.this, R.anim.rotation_360_anim) );
        } else {
            // in case Temperature is implemented in HelmyC-covid
            // gray temperature
            tvTemperature.setText("");
            tvTemperatureLabel.setText("¿Cómo conseguir?");
            tvTemperatureLabel.setTextColor(getResources().getColor(R.color.textViews));
            imgThermometer.setImageDrawable(getResources().getDrawable(R.drawable.thermometer_gray));
            imgRingHelmetTemperature.setImageDrawable(getResources().getDrawable(R.drawable.ring_gray));
            imgMsgHelmetTemperature.setImageDrawable(getResources().getDrawable(R.drawable.label_gray));
            LLtemperature.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.helmy.com.co"));
                    startActivity(browserIntent);
                }
            });
        }

        imgRingBikeConnected.startAnimation( AnimationUtils.loadAnimation(
                ActivityMain.this, R.anim.rotation_360_anim) );

        // Bluetooth
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        setupBroadcastReceivers();

        // BLE instances
        bluetoothHelmet = new BLE_HelmyC(this);
        bluetoothBike = new BLE_HelmyM(this);

        // TTS
        mTTS = SingletonTSS_Helmet.getInstance(this.getApplicationContext());

        mTTS.speakSentence(getResources().getString(R.string.connecting2devices));

        //Vibrator
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // if app was closed and ServiceBikeDisconnected was running, app will come back here if user opens it before timer is up
        if(ServiceBikeDisconnected.running){
            launchAlertTripFinished_BikeDisconnected(ServiceBikeDisconnected.getRemainigTimeCounter_ms());
        }

        //Start the bluetooth connection
        Toast.makeText(getApplicationContext(), "Connecting...", Toast.LENGTH_SHORT).show();
        if(primaryHelmet_MAC != null || bike_MAC != null){
            // start looking helmy-C and helmy-M
            doDiscovery();
            scanLeDevice(true);
        }
    }

    private void setupBroadcastReceivers(){

        //Broadcast receiver for processing the incoming helmet data
        bleHelmetDataReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Get extra data included in the Intent
                String data = intent.getStringExtra(Static_AppVariables.INTENTEXTRA_HELMET_DATA);
//                Log.d(TAG, "Data =" + data);
                if(data != null){
                    processDataReceived(data);
                }
                turnBike_On_Off();
            }
        };

        // Broadcast that handles the connectivity of the bles
        bleDeviceConnectionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean connected = intent.getBooleanExtra(Static_AppVariables.INTENTEXTRA_BLE_CONNECTION, false);
                String deviceMAC = intent.getStringExtra(Static_AppVariables.INTENTEXTRA_BLE_MAC);
//                Log.d(TAG, "Device = " + deviceMAC + " connnected " + connected);
//                Log.d(TAG, "Device =" + deviceMAC + "connnected" + connected);
                if(deviceMAC != null){
//                    Log.d(TAG, "Device = " + deviceMAC + "  Saved = " + primaryHelmet_MAC
//                            + " Comparison = " + (deviceMAC.equals(primaryHelmet_MAC)) );
                    if(deviceMAC.equals(primaryHelmet_MAC)){
                        if(!connected){
                            if(primaryHelmet_connected){
                                // if the helmet was connected and now it is disconnected
                                mTTS.speakSentence( getResources().getString(R.string.helmetDisconnected) );
                            }
//                            Log.d(TAG, "disconnnected = " + connected);

                            primaryHelmet_connected = false;

                            imgRingHelmetConnected.clearAnimation();
                            imgRingHelmetConnected.setImageDrawable(getResources().getDrawable(R.drawable.ring_red));
                            imgHelmetConnected.setImageDrawable(getResources().getDrawable(R.drawable.bluetooth_red));
                            imgMsgHelmetConnected.setImageDrawable(getResources().getDrawable(R.drawable.label_red));
                            tvHelmetConnected.setText(getResources().getString(R.string.helmetDisconnected));

                            // start scanning
                            scanLeDevice(true);
                            imgHelmetConnected.setImageDrawable(getResources().getDrawable(R.drawable.bluetooth_red));
                            imgRingHelmetConnected.setImageDrawable(getResources().getDrawable(R.drawable.ring_red));
                            imgRingHelmetConnected.startAnimation( AnimationUtils.loadAnimation(
                                    ActivityMain.this, R.anim.rotation_360_anim) );
                            imgMsgHelmetConnected.setImageDrawable(getResources().getDrawable(R.drawable.label_red));
                            tvHelmetConnected.setText(getResources().getString(R.string.Searching));

                            // helmet not worn
                            imgRingHelmetOnHead.clearAnimation();
                            imgRingHelmetOnHead.setImageDrawable(getResources().getDrawable(R.drawable.ring_red));
                            imgHelmetOnHead.setImageDrawable(getResources().getDrawable(R.drawable.helmet_red));
                            imgMsgHelmetOnHead.setImageDrawable(getResources().getDrawable(R.drawable.label_red));
                            tvHelmetOnHead.setText(getResources().getString(R.string.helmetNotOnTheHead));

                            if(isCovidVersion) {
                                // not temperature
                                imgRingHelmetTemperature.clearAnimation();
                                tvTemperature.setText("--\u00B0"); // keep one decimal only. u00B0 for degrees symbol
                                imgRingHelmetTemperature.setImageDrawable(getResources().getDrawable(R.drawable.ring_red));
                                imgMsgHelmetTemperature.setImageDrawable(getResources().getDrawable(R.drawable.label_red));
                            } // else, it will continue gray

                            primaryHelmet_worn = false;
                            primaryHelmet_fastened = false;
                        } else {
                            mTTS.speakSentence( getResources().getString(R.string.helmetConnected) );

                            primaryHelmet_connected = true;

                            imgRingHelmetConnected.clearAnimation();
                            imgRingHelmetConnected.setImageDrawable(getResources().getDrawable(R.drawable.ring_green));
                            imgHelmetConnected.setImageDrawable(getResources().getDrawable(R.drawable.bluetooth_green));
                            imgMsgHelmetConnected.setImageDrawable(getResources().getDrawable(R.drawable.label_green));
                            tvHelmetConnected.setText(getResources().getString(R.string.helmetConnected));

                            if(bike_connected || TextUtils.isEmpty(bike_MAC) ){
                                // if Helmy-M is also connected or Helmy-M is not registered, then stop scanning for BLEs
                                scanLeDevice(false);
                            }

                            if(thresholdsDownloaded){
                                // if thresholds were not downloaded, e.g. user did not have internet connection, then HelmyC will work with value previously stored
                                bluetoothHelmet.scheduleSendThredsholds(impact, ang_inferior, ang_superior);
                            }

                            // todo delete
//                            Intent i = new Intent(Static_AppVariables.ACTIONFILTER_DATA_AVAILABLE);
//                            i.putExtra(Static_AppVariables.INTENTEXTRA_HELMET_DATA, "1");
//                            sendBroadcast(i);
                        }
                    } else if (deviceMAC.equals(bike_MAC)){
                        if(!connected){
                            if(bike_connected){
                                mTTS.speakSentence( getResources().getString(R.string.bikeDisconnected) );
                                if( bikeAlreadyOn ) {
                                    // if the bike was connected and now it is disconnected,
                                    // then alert that the bike will turn off in 90 seconds
                                    Intent intentB = new Intent(ActivityMain.this, ServiceBikeDisconnected.class);
                                    stopService(intentB);
                                    ServiceBikeDisconnected.running = true;
                                    startService(intentB);

                                    launchAlertTripFinished_BikeDisconnected(90);
                                }

                            }
                            bike_connected = false;
                            bikeAlreadyOn = false;

                            dateOfTheTrip = ""; // clear date so that

                            imgRingBikeConnected.clearAnimation();
                            imgRingBikeConnected.setImageDrawable(getResources().getDrawable(R.drawable.ring_red));
                            imgBikeConnected.setImageDrawable(getResources().getDrawable(R.drawable.bike_red));
                            imgMsgBikeConnected.setImageDrawable(getResources().getDrawable(R.drawable.label_red));
                            tvBikeConnected.setText(getResources().getString(R.string.bikeDisconnected));

                            scanLeDevice(true);
                            // start scanning again
                            imgBikeConnected.setImageDrawable(getResources().getDrawable(R.drawable.bike_red));
                            imgRingBikeConnected.setImageDrawable(getResources().getDrawable(R.drawable.ring_red));
                            imgRingBikeConnected.startAnimation( AnimationUtils.loadAnimation(
                                    ActivityMain.this, R.anim.rotation_360_anim) );
                            imgMsgBikeConnected.setImageDrawable(getResources().getDrawable(R.drawable.label_red));
                            tvBikeConnected.setText(getResources().getString(R.string.Searching));

                        } else {

                            stopService(new Intent(ActivityMain.this, ServiceBikeDisconnected.class)); // stop service even if it was not started
                            if(alertTripFinished != null && timerTripFinished != null){
                                alertTripFinished.dismissAlert();
                                timerTripFinished.cancel();
                                alertTripFinished = null;
                            }

                            mTTS.speakSentence( getResources().getString(R.string.bikeConnected) );
                            if(vibrator != null){
                                vibrator.cancel();
                            }

                            if( (primaryHelmet_worn && primaryHelmet_fastened)){
                                // bike reconnected while this activity was on, then bike will turn on right a way
                                bluetoothBike.turnOnOFF_WriteCharacteristic(true);
                            }

                            bike_connected = true;

                            imgRingBikeConnected.clearAnimation();
                            imgRingBikeConnected.setImageDrawable(getResources().getDrawable(R.drawable.ring_green));
                            imgBikeConnected.setImageDrawable(getResources().getDrawable(R.drawable.bike_green));
                            imgMsgBikeConnected.setImageDrawable(getResources().getDrawable(R.drawable.label_green));
                            tvBikeConnected.setText(getResources().getString(R.string.bikeConnected));

                            // set the trip date as the current date and time
                            Calendar calendar = Calendar.getInstance();
                            dateOfTheTrip = calendar.get(Calendar.YEAR) +"-"+ (calendar.get(Calendar.MONTH)+1)
                                    +"-"+ calendar.get(Calendar.DAY_OF_MONTH) +"-"+ calendar.get(Calendar.HOUR_OF_DAY)
                                    +"-"+ calendar.get(Calendar.MINUTE)+"-"+ calendar.get(Calendar.SECOND);
                            // HelmyM starts reading the velocity as soon as it connects to the app.
                            // HelmyM's code would have to change to start reading once HelmyM is enabled for turning on the bike.
                            bluetoothBike.startReadVelocityRPM(dateOfTheTrip);

                            if( primaryHelmet_connected ){
                                // if helmy-c is also connected, then stop scanning for BLEs
                                scanLeDevice(false);
                            }
                        }
                    }
                }
            }
        };

        // broadcast receiver of errors from bles
        bleErrorReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String error = intent.getStringExtra("Error");
//                Log.d(TAG, "Error =" + error);
                if(error != null){
                    Toast.makeText(getApplicationContext(), error, Toast.LENGTH_SHORT).show();
                }
            }
        };

        // receiver for enabling/disabling HelmyM
        bikeTurnOnOffReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean success = intent.getBooleanExtra(Static_AppVariables.INTENTEXTRA_BIKE_ONOFF_WRITE, false);
//                Log.d(TAG,"helmy_M_turnOnOff Write was successful: " + success);
                if (success) {
                    if(exitMotionLessThan5KmH){
                        Intent i = new Intent(ActivityMain.this, ActivityGoAs.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                    } else {
                        // bike was turned on successfully
                        mTTS.speakSentence( getResources().getString(R.string.allConditionsMet) );
                        bikeAlreadyOn = true;
                        CLalertReady.setVisibility(View.VISIBLE);

                        CountDownTimer timerHideAlert = new CountDownTimer(5000, 500) {
                            @Override
                            public void onTick(long l) {            }

                            public void onFinish() {
                                CLalertReady.setVisibility(View.GONE);
                            }
                        };
                        timerHideAlert.start();
                    }
                }
            }
        };

        // receiver for confirming that HelmyC started as Master, in order to connect directly with HelmyM. This is when phone is in low battery
        blueWrite_HelmyC_PhoneLowBatteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean success = intent.getBooleanExtra(Static_AppVariables.INTENTEXTRA_HELMET_PHONE_LOWBATTERY, false);
//                Log.d(TAG, "PhoneLowBattery Write was successful: " + success);
                if (success) {
                    // PhoneLowBattery was wrote successfully
                    exitPhoneInLowBattery = true; // set to true so that ServiceBikeDisconnected is not started in OnDestroy method
                    finish();
                }
                // in case it isn't successful, once the battery drops another percent-point, it will try again
            }
        };

        // receiver for tracking the state of the phone's bluetooth
        bluetoothStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
//                Log.d(TAG + "receiverBT", "state= " + mBluetoothAdapter.getState() );
                if (state == BluetoothAdapter.STATE_OFF) {
                    alertBluetoothIsOff();
                } else if(state == BluetoothAdapter.STATE_ON) {
                    // if user enables bluetooth from the notifications and not from the dialog
                    stopAlert_bluetoothIsBackOn();
                }
            }
        };

        // receiver for tracking the state of the phone's gps
        mGpsSwitchStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
//                Log.d(TAG+"GPS_main", "triggered" );
                if (intent.getAction() != null && intent.getAction().matches(LocationManager.PROVIDERS_CHANGED_ACTION)) {
                    Toast.makeText(ActivityMain.this, "GPS changed", Toast.LENGTH_SHORT).show();
                    processGPSreceiver();
                }

            }
        };

        // receiver for tracking the state of the phone's battery
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // charging
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING;

                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

                // battery low?
                batteryPct = level * 100 / (float)scale;

                if(batteryPct <= 15 && !isCharging){
                    lowBattery = true;
                    if(primaryHelmet_connected) {
                        bluetoothHelmet.phoneBatteryLow_Write();

                        mTTS.speakSentence(getResources().getString(R.string.lowBattery_PhoneWillBeDisconnectedForSafety));

                        if (vibrator != null) {
                            long[] pattern = {0, 500, 500};
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0)); // repeat at index 0
                            } else {
                                //deprecated in API 26
                                vibrator.vibrate(pattern, 0); // repeat at index 0
                            }
                        }

                        /***
                         * TODO eliminar despues de que helmyC implemente la confirmacion de phoneLowBattery
                         */
                        Handler h = new Handler();
                        h.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                sendActivityWritePhoneLowBatteryStatus(true);
                            }
                        }, 1000);
                    }
                }
            }
        };

        // receiver for tracking the state of the phone's volume
        phoneVolumeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
//                Log.d(TAG, "phoneVolume Action : ");
                try{
                    int currentVolume = (Integer) intent.getExtras().get("android.media.EXTRA_VOLUME_STREAM_VALUE");
//                    Log.d(TAG, "phoneVolume Action : "+ intent.getAction() + " / volume : "+currentVolume);
                    checkVolume(currentVolume);
                } catch (Exception ignored){
                    // if it fails because of the extra does not exists, possibly for some Android devices
                    // read the volume this way
                    AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    int currentVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);

                    checkVolume(currentVolume);
                }
            }
        };

        registerReceiver(bleHelmetDataReceiver, new IntentFilter(Static_AppVariables.ACTIONFILTER_DATA_AVAILABLE));
        registerReceiver(bleDeviceConnectionReceiver, new IntentFilter(Static_AppVariables.ACTIONFILTER_GATT_CONNECTION));
        registerReceiver(bleErrorReceiver, new IntentFilter("ErrorIntent"));
        registerReceiver(bikeTurnOnOffReceiver, new IntentFilter(Static_AppVariables.ACTIONFILTER_BIKE_ONOFF));
        registerReceiver(blueWrite_HelmyC_PhoneLowBatteryReceiver, new IntentFilter(Static_AppVariables.ACTIONFILTER_HELMET_PHONE_LOWBATTERY));
        registerReceiver(mGpsSwitchStateReceiver, new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));
        registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        registerReceiver(phoneVolumeReceiver, new IntentFilter("android.media.VOLUME_CHANGED_ACTION")); //

        IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryReceiver, iFilter);
    }

    long time = 0;
    private void processDataReceived(String stringReceived){

        if(stringReceived != null && !stringReceived.isEmpty()){
//            Log.d(TAG+"Bluetooth", "InputReceived= " + stringReceived + "length=" + stringReceived.length());

//            Toast.makeText(this, stringReceived, Toast.LENGTH_SHORT).show();

            if(stringReceived.equals("1")){
                // Helmet well suited
                imgRingHelmetOnHead.clearAnimation();
                imgRingHelmetOnHead.setImageDrawable(getResources().getDrawable(R.drawable.ring_green));
                imgHelmetOnHead.setImageDrawable(getResources().getDrawable(R.drawable.helmet_green));
                imgMsgHelmetOnHead.setImageDrawable(getResources().getDrawable(R.drawable.label_green));
                tvHelmetOnHead.setText(getResources().getString(R.string.HELMET_ON_HEAD));

                primaryHelmet_worn = true;
                primaryHelmet_fastened = true;
            } else if(stringReceived.equals("4")){
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED){
//                        || ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_DENIED) {
                    mTTS.speakSentence(getResources().getString(R.string.alertNotLaunchedPermissions));
                } else if(!ServiceEmergency.running){
                    ServiceEmergency.running = true;
                    ServiceEmergency.bikeMAC = bike_MAC;

                    // save the alert as cancelled (by default), and if alert is sent after 90s,
                    // then it will be removed
                    if( TextUtils.isEmpty(dateOfTheTrip) ){
                        // user activated the alert pressing 3 times on HelmyC, before bike was connected and a velocity txt file was created
                        dateOfTheTrip = "0"; // there is no
                    }

                    Calendar calendar = Calendar.getInstance();
                    String dateImpact = calendar.get(Calendar.YEAR) +"-"+ (calendar.get(Calendar.MONTH)+1)
                            +"-"+ calendar.get(Calendar.DAY_OF_MONTH) +"-"+ calendar.get(Calendar.HOUR_OF_DAY)
                            +"-"+ calendar.get(Calendar.MINUTE)+"-"+ calendar.get(Calendar.SECOND);

                    String alertName = dateImpact +";"+ dateOfTheTrip +";"+ preferences.get_lastUser_Id_logged() +";"+ bike_MAC;
                    preferences.addAlertRegistryPendingToUpload(alertName);

                    Intent intent = new Intent(this, ServiceEmergency.class);
                    startService(intent);

                    Intent intentAct = new Intent(this, ActivityEmergency.class);
                    startActivity(intentAct); // ActivityMain will continue in stack
                }
            } else if (stringReceived.equals("5")){
//                Log.d(TAG+"assistantG", "HelmyC triggered");
                if ( (System.currentTimeMillis() - time) > 2000 || time == 0 ) {
                    // allows to trigger the assistant every 2 seconds max
                    Intent intentAssistant = new Intent(Intent.ACTION_VOICE_COMMAND).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (intentAssistant.resolveActivity(getPackageManager()) != null ) {
                        time = System.currentTimeMillis();
                        startActivity(intentAssistant);
//                        Log.d(TAG+"assistantG", "Activated");
                    } else {
                        mTTS.speakSentence(getResources().getString(R.string.installGoogleAssistant));
                    }
                }
            } else if (stringReceived.contains("T")){
                String[] t_num = stringReceived.split("=");
                if(t_num.length == 2){
                    double temperature = Double.parseDouble(t_num[1]);
                    tvTemperature.setText(new DecimalFormat("##.#").format(temperature) + "\u00B0"); // keep one decimal only. u00B0 for degrees symbol
                    if(temperature >= 38){
                        imgRingHelmetTemperature.setImageDrawable(getResources().getDrawable(R.drawable.ring_red));
                        imgMsgHelmetTemperature.setImageDrawable(getResources().getDrawable(R.drawable.label_red));
                    } else if(temperature > 37){
                        imgRingHelmetTemperature.setImageDrawable(getResources().getDrawable(R.drawable.ring_orange));
                        imgMsgHelmetTemperature.setImageDrawable(getResources().getDrawable(R.drawable.label_orange));
                    } else {
                        imgRingHelmetTemperature.setImageDrawable(getResources().getDrawable(R.drawable.ring_green));
                        imgMsgHelmetTemperature.setImageDrawable(getResources().getDrawable(R.drawable.label_green));
                    }
                    // stop animation
                    imgRingHelmetTemperature.clearAnimation();
                }
            } else {
//                Log.d(TAG+"battery_bt", "value= " + stringReceived);
                try {
                    float battery10_255 = Float.parseFloat(stringReceived);
                    int batteryLevel = (int) (((battery10_255 - 30f) / (42f - 30f))*100f); // normalize between 0-1 and multiply by 100
                    if(batteryLevel >= 0 && batteryLevel <= 100){
                        pbHelmetBattery.setProgress(batteryLevel);
                        if(batteryLevel < 15){
                            imgHelmetBattery.setImageDrawable(getResources().getDrawable(R.drawable.battery_red));
                            pbHelmetBattery.getProgressDrawable().setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);
                            tvHelmetBatteryLevel.setTextColor(Color.RED);

                        } else if (batteryLevel < 50){
                            imgHelmetBattery.setImageDrawable(getResources().getDrawable(R.drawable.battery_yellow));
                            pbHelmetBattery.getProgressDrawable().setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN);
                            tvHelmetBatteryLevel.setTextColor(Color.YELLOW);
                        } else {
                            imgHelmetBattery.setImageDrawable(getResources().getDrawable(R.drawable.battery_green));
                            pbHelmetBattery.getProgressDrawable().setColorFilter(getResources().
                                    getColor(R.color.progresBars), PorterDuff.Mode.SRC_IN);
                            tvHelmetBatteryLevel.setTextColor(getResources().
                                    getColor(R.color.progresBars));
                        }
//                        Log.d(TAG+"battery_bt", "Battery = " + batteryLevel);

                        tvHelmetBatteryLevel.setText(batteryLevel + "%");
                    }

                } catch(NumberFormatException nfe) {
                    //nothing
                }
            }

            helmetReady_current = primaryHelmet_worn && primaryHelmet_fastened;
        }
    }

    // this function will write to HelmyM only when there is a state change in the conditions
    private boolean bikeAlreadyOn, helmetReady_past, helmetReady_current;
    private void turnBike_On_Off() {

        if(helmetReady_past != helmetReady_current){
            if(primaryHelmet_worn && primaryHelmet_fastened){
                mTTS.speakSentence(getResources().getString(R.string.helmetReady));
                helmetReady_current = true;
                if(!bikeAlreadyOn && bike_connected){
                    // bike has not been turned on fromActivity app and bike is connected, then turn bike on
                    bluetoothBike.turnOnOFF_WriteCharacteristic(true);
                }
            } else {
                mTTS.speakSentence( getResources().getString(R.string.helmetNotWellWorn) );
                helmetReady_current = false;
            }
        }

        helmetReady_past = helmetReady_current;
    }

    AlertMessageButton alertBluetoothOff;
    private void alertBluetoothIsOff(){
//        Log.d(TAG, "alertBluetoothIsOff");
        if ( !mBluetoothAdapter.isEnabled() ) {
            alertBluetoothOff = new AlertMessageButton(ActivityMain.this);
            alertBluetoothOff.setDialogMessage(getResources().getString(R.string.bluetoothTurnedOff));
            alertBluetoothOff.setDialogPositiveButton(getResources().getString(R.string.btnEnableBluetooth),
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            alertBluetoothOff.dismissAlert();

                            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                            startActivityForResult(enableBtIntent, Static_AppVariables.REQUESTCODE_TURNON_BLUETOOTH);
                        }
                    });
            alertBluetoothOff.hideCancelButton();
            alertBluetoothOff.setCancellable(false);
            alertBluetoothOff.showAlert();

            vibrate();
            mTTS.speakSentence(getResources().getString(R.string.bluetoothTurnedOff_TTS));

            if(preferences.get_isAndroidGO()){
                // android Go devices do not continue working with the bluetooth off, we must exit the interface and show the reason in ActivityGoAs
                // if bike was On, then it should trigger ServiceBikeDisconnected automatically
                Intent intent = new Intent(this, ActivityGoAs.class);
                if(bikeAlreadyOn){
                    intent.putExtra(Static_AppVariables.INTENTEXTRA_BLUETOOTHOFF, 1); // 1: bluetooth off while bike was on, dialog in ActivityGoAs must say Bike will be disconnected
                } else {
                    intent.putExtra(Static_AppVariables.INTENTEXTRA_BLUETOOTHOFF, 0); // 0: bluetooth off while bike was off, just show bluetooth off message in ActivityGoAs
                }
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }
        }
    }

    private void stopAlert_bluetoothIsBackOn(){
        mTTS.speakSentence(getResources().getString(R.string.bluetoothOn));
        Toast.makeText(this, getResources().getString(R.string.bluetoothOn), Toast.LENGTH_SHORT).show();
        if(alertBluetoothOff != null){
            alertBluetoothOff.dismissAlert();
            alertBluetoothOff = null;
        }
        // vibrations stop in handlerVibrate
    }

    AlertMessageButton alertGPSoff;
    private void alertGPSIsOff(String msg){
//        Log.d(TAG+"GPS_main", "alertGPSIsOff");
        // mGpsSwitchStateReceiver is triggered multiple times for some reason, check if alertGPSoff is null so that it is laucnh once
        if(alertGPSoff == null){
            alertGPSoff = new AlertMessageButton(ActivityMain.this);
            alertGPSoff.setDialogMessage(msg);
            alertGPSoff.setDialogPositiveButton(getResources().getString(R.string.btnEnableBluetooth),
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            alertGPSoff.dismissAlert();
                            checkGPS();
                        }
                    });
            alertGPSoff.hideCancelButton();
            alertGPSoff.setCancellable(false);
            alertGPSoff.showAlert();

            vibrate();

            mTTS.speakSentence(msg);
        }
    }

    private void stopAlert_GPSIsBackOn(){
        mTTS.speakSentence(getResources().getString(R.string.gpsBackOn));
        Toast.makeText(this, getResources().getString(R.string.gpsBackOn), Toast.LENGTH_SHORT).show();
        if(alertGPSoff != null){
            alertGPSoff.dismissAlert();
            alertGPSoff = null;
        }
        // vibrations stop in handlerVibrate
    }

    void checkGPS() {
        LocationRequest locationRequest = LocationRequest.create();

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);

        SettingsClient settingsClient = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = settingsClient.checkLocationSettings(builder.build());

        task.addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
//                Log.d(TAG+"GPS_main", "OnSuccess");
                // GPS is ON
                stopAlert_GPSIsBackOn();
            }
        });

        task.addOnFailureListener(this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull final Exception e) {
//                Log.d(TAG+"GPS_main", "GPS off");
                // GPS off
                if (e instanceof ResolvableApiException) {
                    ResolvableApiException resolvable = (ResolvableApiException) e;
                    try {
                        resolvable.startResolutionForResult(ActivityMain.this, Static_AppVariables.REQUESTCODE_TURNON_GPS);
                    } catch (IntentSender.SendIntentException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });
    }

    private void processGPSreceiver(){
        LocationManager manager = (LocationManager) getSystemService( Context.LOCATION_SERVICE );

        if (manager != null && !manager.isProviderEnabled( LocationManager.GPS_PROVIDER ) ) {
//            Log.d(TAG+"GPS_main", "off");
            if ( !manager.isProviderEnabled( LocationManager.NETWORK_PROVIDER ) ) {
//                Log.d(TAG+"GPS_main", "network provider off");
                alertGPSIsOff(getResources().getString(R.string.gpsAndNetworkProviderNotEnbled));
            } else {
                // network provider is still working for getting the location
                alertGPSIsOff(getResources().getString(R.string.gpsOff_NetworkProviderEnbled));
            }
        } else {
            // if user enables GPS from the notifications and not from the dialog
//            Log.d(TAG+"GPS_main", "on");
            stopAlert_GPSIsBackOn();
        }
    }

    private void checkVolume(int volume){
        if(volume == 0){
            if(alertVol0 == null){
                // alert has not been launched
                alertVolume0();
            }
        } else {
            if(alertVol0 != null){
                // alert was shown and now the user is turning the volume up, so reproduce once a message through TTS
                stopAlertVolume0();
            }
        }
    }

    AlertMessageButton alertVol0;
    private void alertVolume0(){
        alertVol0 = new AlertMessageButton(this);
        alertVol0.setDialogMessage(getResources().getString(R.string.volume0));
        alertVol0.hideCancelButton();
        alertVol0.setCancellable(false);
        alertVol0.showAlert();

        vibrate();
    }

    private void stopAlertVolume0(){
        mTTS.speakSentence(getResources().getString(R.string.volumeUp));
        if(alertVol0 != null){
            alertVol0.dismissAlert();
            alertVol0 = null;
        }
        // vibrations stop in handlerVibrate
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if(requestCode == Static_AppVariables.REQUESTCODE_TURNON_BLUETOOTH) {
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled
                stopAlert_bluetoothIsBackOn();
            } else {
                // continue alerting
                alertBluetoothIsOff();
            }
        }
        else if(requestCode == Static_AppVariables.REQUESTCODE_TURNON_GPS) {
            switch (resultCode) {
                case Activity.RESULT_OK:
                    // GPS was turned on
                    stopAlert_GPSIsBackOn();
                    break;
                case Activity.RESULT_CANCELED:
                    // continue alerting
                    processGPSreceiver();
                    break;
                default:
                    break;
            }
        }
    }

    Handler handlerVibrate;
    private void vibrate(){
        // this handler will keep vibrator running even with screen off (vibrator stops when the phone has the screen off)
//        Log.d(TAG, "vibrate");
        handlerVibrate = new Handler();
        handlerVibrate.post(new Runnable() {
            @Override
            public void run() {
                if(vibrator != null) {
//                    Log.d(TAG, "handler");
                    long[] pattern = {0, 500, 500};
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0)); // repeat at index 0
                    } else {
                        //deprecated in API 26
                        vibrator.vibrate(pattern, 0); // repeat at index 0
                    }
                }
                if(alertVol0 != null || alertBluetoothOff != null || alertGPSoff != null){
                    // keep launching so that it keeps vibrating even with the screen off
                    handlerVibrate.postDelayed(this, 1000);
                } else {
                    if(vibrator != null) {
                        vibrator.cancel();
                    }
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
//        Log.d(TAG, "onresume");
        // read every time the user resume the app on this activity
        if(bluetoothHelmet != null && bluetoothHelmet.bluetoothGatt != null){
            bluetoothHelmet.readBattery();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if(vibrator != null){
            vibrator.cancel();
        }

        // Unregister broadcast listeners
        unregisterReceiver(bleHelmetDataReceiver);
        unregisterReceiver(bleDeviceConnectionReceiver);
        unregisterReceiver(bleErrorReceiver);
        unregisterReceiver(bikeTurnOnOffReceiver);
        unregisterReceiver(mGpsSwitchStateReceiver);
        unregisterReceiver(batteryReceiver);
        unregisterReceiver(blueWrite_HelmyC_PhoneLowBatteryReceiver);
        unregisterReceiver(bluetoothStateReceiver);

        stopBluetoothConnections_helmet_bike();

        if( bikeAlreadyOn && bike_connected && !exitMotionLessThan5KmH && !exitPhoneInLowBattery ) {
            // if bike was already on and connected and user did not exit by pressing on back button (user was moving at less than 5Km/h)
            Intent intent = new Intent(this, ServiceBikeDisconnected.class);
            startService(intent);
            ServiceBikeDisconnected.running = true;
        }

        if(handlerVibrate != null){
            handlerVibrate.removeCallbacksAndMessages(null);
        }

        if(timerTripFinished != null){
            timerTripFinished.cancel();
        }

    }


    private void stopBluetoothConnections_helmet_bike(){
        // Cancel any thread currently running a connection
        if (bluetoothHelmet.bluetoothGatt != null) {
            bluetoothHelmet.finishConnection();
        }

        // Cancel any thread currently running a connection
        if (bluetoothBike.bluetoothGatt != null) {
            bluetoothBike.finishConnection();
        }
    }

    @Override
    public void onBackPressed() {
        if( ServiceBikeDisconnected.running ){
            launchAlertTripFinished_BikeDisconnected(ServiceBikeDisconnected.getRemainigTimeCounter_ms());
        } else if ( bikeAlreadyOn && primaryHelmet_worn) {
            if (bluetoothBike.lastVelocityValue < 5){
                // if user is moving at less than 5km/h, then we will turn off the bike right away
                launchAlertTripFinished();
            } else {
                launchAlertCantExit();
            }
        } else if ( bike_connected || primaryHelmet_connected ) {
            Static_AppMethods.launchAlertGoBack2MainMenu(this);
        } else {
            super.onBackPressed();
        }
    }

    void launchAlertTripFinished_BikeDisconnected(int timeBeforeTurnOff){
        if(ServiceBikeDisconnected.running){
            alertTripFinished = new AlertMessageButton(this);
            String msg = getResources().getString(R.string.askTripFinished) + "\n\n"
                    + getResources().getString(R.string.bikeWillTurnOffIn) + "\n" + timeBeforeTurnOff;
            alertTripFinished.setDialogMessage( msg );
            alertTripFinished.setDialogPositiveButton( getResources().getString(R.string.Yes), new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    stopService( new Intent( ActivityMain.this, ServiceBikeDisconnected.class));
                    Intent intent = new Intent(ActivityMain.this, ActivityGoAs.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    alertTripFinished.dismissAlert();
                    alertTripFinished = null;
                }
            });
            alertTripFinished.setDialogNegativeButton( getResources().getString(R.string.No), new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    alertTripFinished.dismissAlert();
                }
            });
            alertTripFinished.hideCancelButton();
            alertTripFinished.setCancellable(false);
            alertTripFinished.showAlert();

            timerTripFinished = new CountDownTimer(timeBeforeTurnOff * 1000, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    String msg = getResources().getString(R.string.askTripFinished) + "\n\n"
                            + getResources().getString(R.string.bikeWillTurnOffIn) + "\n" +
                            (int) (millisUntilFinished / 1000L);
                    if(alertTripFinished != null){
                        alertTripFinished.setDialogMessage( msg );
                    }
                }

                @Override
                public void onFinish() {
                    String msg = getResources().getString(R.string.askTripFinished) + "\n\n"
                            + getResources().getString(R.string.bikeWillTurnOffIn) + "\n" + "0";
                    if(alertTripFinished != null){
                        alertTripFinished.setDialogMessage( msg );
                    }
                }
            };
            timerTripFinished.start();
        }
    }

    void launchAlertTripFinished(){
        final AlertMessageButton alert = new AlertMessageButton(this);
        alert.setDialogMessage( getResources().getString(R.string.askTripFinished) );
        alert.setDialogPositiveButton( getResources().getString(R.string.Yes), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(bluetoothBike != null){
                    // turn it off because velocity is less than 5km/h and exit in bikeTurnOnOffReceiver
                    exitMotionLessThan5KmH = true;
                    bluetoothBike.turnOnOFF_WriteCharacteristic(false);
                }
                alert.dismissAlert();
            }
        });
        alert.setDialogNegativeButton( getResources().getString(R.string.No), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alert.dismissAlert();
            }
        });
        alert.showAlert();
    }

    void launchAlertCantExit(){
        final AlertMessageButton alert = new AlertMessageButton(this);
        alert.setDialogMessage( getResources().getString(R.string.cantFinishtrip_UserMoving) );
        alert.setDialogPositiveButton( getResources().getString(R.string.Ok), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alert.dismissAlert();
            }
        });
        alert.showAlert();
    }

    public void goBack(View view) {
        onBackPressed();
    }

    private void sendActivityWritePhoneLowBatteryStatus(boolean writeWasSuccessful) {
        Intent intent = new Intent(Static_AppVariables.ACTIONFILTER_HELMET_PHONE_LOWBATTERY);
        intent.putExtra(Static_AppVariables.INTENTEXTRA_HELMET_PHONE_LOWBATTERY, writeWasSuccessful);
        sendBroadcast(intent);
    }

    public void click_helmetQuestion(View view) {
        final AlertMessageButton alert = new AlertMessageButton(this);
        alert.setDialogMessage(R.string.howHelmetWorks);
        alert.setDialogPositiveButton(getResources().getString(R.string.Ok), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alert.dismissAlert();
            }
        });
        alert.showAlert();
    }

    public void click_bikeQuestion(View view) {
        final AlertMessageButton alert = new AlertMessageButton(this);
        alert.setDialogMessage(R.string.howBikeWorks);
        alert.setDialogPositiveButton(getResources().getString(R.string.Ok), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alert.dismissAlert();
            }
        });
        alert.showAlert();
    }

    public void click_hideAlertReady(View view) {
        CLalertReady.setVisibility(View.GONE);
    }

    // discover bles before trying to connect
    final String TAG_ble = TAG+"bleScan";
    private void doDiscovery() {
        // If we're already discovering, stop it
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
        // Request discover fromActivity BluetoothAdapter
        mBluetoothAdapter.startDiscovery();
    }

    private BluetoothAdapter.LeScanCallback leScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi,
                                     byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String device2add = device.getName() + ";" + device.getAddress();

//                            Log.d(TAG_ble, "BleFound: " + device2add + " " + arrayBleDevices.indexOf(device2add)
//                                    + " " + arrayBleDevices.indexOf(device2add));

                            //check if device2add was already listed and if it is the primaryHelmet
                            if( !TextUtils.isEmpty(primaryHelmet_MAC) && device.getAddress().equals(primaryHelmet_MAC)
                                    && arrayBleDevices.indexOf(device2add) < 0 && !primaryHelmet_connected){
                                arrayBleDevices.add(device2add);
                                bluetoothHelmet.initiateBluetoothConexion(
                                        mBluetoothAdapter.getRemoteDevice(primaryHelmet_MAC), true, bike_MAC);
                            }
                            if( !TextUtils.isEmpty(bike_MAC) && device.getAddress().equals(bike_MAC)
                                    && arrayBleDevices.indexOf(device2add) < 0 && !bike_connected
                                    && primaryHelmet_connected
                            ){
                                // bike will connect only after helmet is connected
                                arrayBleDevices.add(device2add);
                                bluetoothBike.initiateBluetoothConexion(mBluetoothAdapter.getRemoteDevice(bike_MAC));
                            }
                        }
                    });
                }
            };

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            arrayBleDevices.clear();
//            Log.d(TAG_ble,"ble start scanning");
            // Stops scanning after a pre-defined scan period.
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if(mScanning){
                        // if still scanning, then restart search
                        mBluetoothAdapter.stopLeScan(leScanCallback);
                        scanLeDevice(true);
                    } else {
                        // if scanLeDevice(false) was called, then do not restart scan
                        mBluetoothAdapter.stopLeScan(leScanCallback);
                    }
                }
            }, SCAN_PERIOD);
            mScanning = true;
            mBluetoothAdapter.startLeScan(leScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(leScanCallback);
        }
    }

    // get impact and angle threshold for Helmy-C.
    // Development team can change those variable for HelmyC to better detect the gesture and impact
    float impact = 0, ang_superior = 0, ang_inferior = 0;
    boolean thresholdsDownloaded = false;
    private void requestHelmyCthresholds() {
        String url;
        if(preferences.get_isPrimaryServerDown()){
            // primary server is down, so we will use the secondary
            url = Static_AppVariables.url_thresholds2;
        } else {
            url = Static_AppVariables.url_thresholds;
        }

        StringRequest strRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
//                        Log.d(TAG+"Thresholds", "response: " + response);
                        try {
                            JSONObject jsonArray = new JSONObject(response);
                            String impactTH = jsonArray.getString("impacto");
                            String angSupTH = jsonArray.getString("ang_superior");
                            String angInfTH = jsonArray.getString("ang_inferior");

                            try {
                                impact = Float.parseFloat(impactTH) * 10;
                                ang_superior = Float.parseFloat(angSupTH) * 100;
                                ang_inferior = Float.parseFloat(angInfTH) * 100;

                                preferences.saveImpactThreshold(impact);
                                preferences.saveAngleSuperiorThreshold(ang_superior);
                                preferences.saveAngleInferiorThreshold(ang_inferior);

                                thresholdsDownloaded = true;

                                if(primaryHelmet_connected){
                                    bluetoothHelmet.scheduleSendThredsholds(impact, ang_inferior, ang_superior);
                                    // if HelmyC is not connected, then scheduleSendThredsholds will be called when it gets connected
                                }

                            } catch (Exception ignored){
//                                Log.e(TAG+"Thresholds", "error with thresholds sent from server. Error=" + ignored.toString() );
                                // if thresholds were not downloaded, e.g. user did not have internet connection, then HelmyC will work with value previously stored
                            }

                        } catch (JSONException e) {
//                            Log.e(TAG+"Thresholds", "error with thresholds sent from server. Error=" + e.toString() );
                            // if thresholds were not downloaded, e.g. user did not have internet connection, then HelmyC will work with value previously stored
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
//                        Log.e(TAG+"Thresholds", error.toString());
                        Static_AppMethods.checkResponseCode(error, preferences);
                        // if thresholds were not downloaded, e.g. user did not have internet connection, then HelmyC will work with value previously stored
                    }
                }) {

            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("download", "download");
                return params;
            }
        };

        //Volley
        RequestQueue requestQueue = SingletonVolley.getInstance(this.getApplicationContext()).getRequestQueue();
        requestQueue.add(strRequest);
    }
}