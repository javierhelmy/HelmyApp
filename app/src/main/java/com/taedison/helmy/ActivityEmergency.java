package com.taedison.helmy;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;

public class ActivityEmergency extends AppCompatActivity {

    private final String TAG = "EmergencyAct";

    TextView btnSendEmergency, btnCancelEmergency;
    TextView tvMsg, tvTimer;
    ImageView imgCircle;

    SingletonSharedPreferences preferences;

    AnimatorSet circleRotationAnim;

    CountDownTimer timer;

    boolean canRetry = false; // user can try to send the alert again if it failed due to GPS or phone carrier (operador)
    boolean turnOnGPSwasRequested = false; // used to prevent from requesting multiple times to turn on the GPS

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency);

        setVolumeControlStream(AudioManager.STREAM_MUSIC); // so that volume keys change the multimedia volume and not the ringtone

        preferences = SingletonSharedPreferences.getInstance(this.getApplicationContext());

        btnSendEmergency = findViewById(R.id.btnSendEmergency);
        btnCancelEmergency = findViewById(R.id.btnCancelEmergency);
        tvMsg = findViewById(R.id.tvEmergencyMsg);
        tvTimer = findViewById(R.id.tvTimer);
        imgCircle = findViewById(R.id.imgCircle);

        displayViews();
    }

    private void displayViews() {
        if(ServiceEmergency.running){
            registerReceiver(receiverFromService, new IntentFilter(Static_AppVariables.ACTIONFILTER_ALERT_SENT));

            // Bind to LocalService
            Intent intent = new Intent(this, ServiceEmergency.class);
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

            checkGPS();
        } else {
            // if it is not running it is because it was cancelled from the notification button or there was a problem with the GPS or SMS phone carrier
            tvMsg.setText(preferences.getMessageAboutAlert());
            tvTimer.setVisibility(View.INVISIBLE);
            imgCircle.setVisibility(View.INVISIBLE);
            btnCancelEmergency.setVisibility(View.INVISIBLE);
            if(preferences.getMessageId_AboutAlert() == 1 || preferences.getMessageId_AboutAlert() == 4){
                // if alert was sent successfully or cancelled
                btnSendEmergency.setVisibility(View.INVISIBLE);
            } else {
                // display retry button if there was an error sending the sms
                canRetry = true;
                btnSendEmergency.setVisibility(View.VISIBLE);
                btnSendEmergency.setText(getResources().getString(R.string.retry));
            }
        }
    }

    BroadcastReceiver receiverFromService = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int alert_sent = intent.getIntExtra(Static_AppVariables.INTENTEXTRA_ALERT_SENT, 0);
            Log.d(TAG, "IntExtra from service: " + alert_sent);
            if(alert_sent == 1){
                // SMS was sent
                // update UI
                tvMsg.setText(R.string.alertWasSentKeepCalm);
                tvTimer.setVisibility(View.INVISIBLE);
                imgCircle.setVisibility(View.INVISIBLE);
                circleRotationAnim.cancel();
                btnCancelEmergency.setVisibility(View.INVISIBLE);
                btnSendEmergency.setVisibility(View.INVISIBLE);
            } else if (alert_sent == 2) {
                // update UI
                tvMsg.setText(R.string.EmergencyAlertWasNotSentNoGPS);
                tvTimer.setVisibility(View.INVISIBLE);
                imgCircle.setVisibility(View.INVISIBLE);
                circleRotationAnim.cancel();
                btnCancelEmergency.setVisibility(View.INVISIBLE);
                canRetry = true;
                btnSendEmergency.setVisibility(View.VISIBLE);
                btnSendEmergency.setText(getResources().getString(R.string.retry));
            } else if (alert_sent == 3) {
                // update UI
                tvMsg.setText(R.string.EmergencyAlertWasNotSentByCarrier);
                tvTimer.setVisibility(View.INVISIBLE);
                imgCircle.setVisibility(View.INVISIBLE);
                circleRotationAnim.cancel();
                btnCancelEmergency.setVisibility(View.INVISIBLE);
                canRetry = true;
                btnSendEmergency.setVisibility(View.VISIBLE);
                btnSendEmergency.setText(getResources().getString(R.string.retry));
            } else if (alert_sent == 4) {
                // update UI
                tvMsg.setText(R.string.EmergencyAlertWasCancelled);
                tvTimer.setVisibility(View.INVISIBLE);
                imgCircle.setVisibility(View.INVISIBLE);
                circleRotationAnim.cancel();
                btnCancelEmergency.setVisibility(View.INVISIBLE);
                btnSendEmergency.setVisibility(View.INVISIBLE);
                finish();
            }

            if(alert_sent == 0){
                // service is notifying that it has started
                displayViews();
            } else {
                // Unbind fromActivity the service because alert was sent or cancelled
                if(timer != null){
                    timer.cancel();
                }

                if (mBound) {
                    unbindService(mConnection);
                    mBound = false;
                }
                Intent intentB = new Intent(ActivityEmergency.this, ServiceEmergency.class);
                stopService(intentB);
            }
        }
    };

    public void click_sendEmergency(View view) {
        // method registered in layout
        if(canRetry){
            // restart activity and service
            startService(new Intent(this, ServiceEmergency.class));
            ServiceEmergency.running = true;
            Intent intent = getIntent();
            finish();
            startActivity(intent);
        } else {
            final AlertMessageButton alert = new AlertMessageButton(this);
            alert.setDialogMessage( getResources().getString(R.string.areYouSureSendAlert) );
            alert.setDialogPositiveButton(getResources().getString(R.string.Yes),
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            ServiceEmergency.counterSMS.cancel();
                            serviceEmergency.sendEmergencySMS();

                            // update UI. Cannot longer cancel or send the alert
                            tvMsg.setText(R.string.sending);
                            tvTimer.setVisibility(View.INVISIBLE);
                            btnCancelEmergency.setVisibility(View.INVISIBLE);
                            btnSendEmergency.setVisibility(View.INVISIBLE);

                            alert.dismissAlert();
                        }
                    });
            alert.setDialogNegativeButton(getResources().getString(R.string.No), new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    alert.dismissAlert();
                }
            });
            alert.showAlert();
        }
    }

    public void click_cancelEmergency(View view) {
        // method registered in layout
        final AlertMessageButton alert = new AlertMessageButton(this);
        alert.setDialogMessage( getResources().getString(R.string.areYouSureCancelAlert) );
        alert.setDialogPositiveButton(getResources().getString(R.string.Yes),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        detroyService();
//                        Intent va = new Intent(ActivityEmergency.this, ActivityGoAs.class);
//                        va.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
//                        startActivity(va);
                        alert.dismissAlert();
                        finish();
                    }
                });
        alert.setDialogNegativeButton(getResources().getString(R.string.No), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alert.dismissAlert();
            }
        });
        alert.showAlert();

    }

    void detroyService(){
        serviceEmergency.cancelService();
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }

        Intent intentSS = new Intent(ActivityEmergency.this, ServiceEmergency.class);
        stopService(intentSS);

        // Launch new notification saying the SMS was not sent
        NotifEmergencyNotSent.Launch(ActivityEmergency.this, getResources().getString(R.string.EmergencyAlertWasCancelled));

        preferences.set_demoWasLaunched(); // set demo as launched because the user does not need to see a demo again even if he has not launch the demo, an accident was already detected
    }

    ServiceEmergency serviceEmergency; //instance is autocreated
    boolean mBound = false;

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // animation
            ObjectAnimator rotation = ObjectAnimator.ofFloat(imgCircle, "rotation", 360);
            rotation.setDuration(2000);
            rotation.setRepeatCount(Animation.INFINITE);
            circleRotationAnim = new AnimatorSet();
            circleRotationAnim.play(rotation);

            // We've bound to LocalService, cast the IBinder and get LocalService instance
            ServiceEmergency.LocalBinder binder = (ServiceEmergency.LocalBinder) service;
            serviceEmergency = binder.getService();
            // Get remaining time of the timer and start timer
            long remainingTime = serviceEmergency.getRemainigTimeCounter_ms();
            if(remainingTime > 0){
                // alert is still running and counting down
                tvMsg.setVisibility(View.VISIBLE);
                tvMsg.setText(getResources().getString(R.string.EmergencyWillBeSentIn));
                tvTimer.setVisibility(View.VISIBLE);
                imgCircle.setVisibility(View.VISIBLE);
                btnCancelEmergency.setVisibility(View.VISIBLE);
                btnSendEmergency.setVisibility(View.VISIBLE);

                timer = new CountDownTimer(remainingTime, 1000) {
                    //this timer does not interact with ServiceEmergency, it just display the counter
                    public void onTick(long millisUntilFinished) {
                        int remaining = (int) Math.ceil(millisUntilFinished/1000);
                        tvTimer.setText( String.valueOf( remaining ) );
                        if (remaining % 10d == 0) {
                            // multiple of 10
                            checkGPS();
                        }
                    }
                    public void onFinish() {
                        // update UI. Cannot longer cancel or send the alert
                        if(ServiceEmergency.running) {
                            tvMsg.setText(R.string.sending);
                            tvTimer.setVisibility(View.INVISIBLE);
                            btnCancelEmergency.setVisibility(View.INVISIBLE);
                            btnSendEmergency.setVisibility(View.INVISIBLE);
                        }
                    }
                };
                timer.start();
            } else {
                // alert is still running but counter finished
                tvMsg.setVisibility(View.VISIBLE);
                tvMsg.setText(getResources().getString(R.string.sending));
                tvTimer.setVisibility(View.INVISIBLE);
                imgCircle.setVisibility(View.VISIBLE);
                btnCancelEmergency.setVisibility(View.INVISIBLE);
                btnSendEmergency.setVisibility(View.INVISIBLE);
            }


            //start animation
            circleRotationAnim.start();

            mBound = true;
            Log.d(TAG, "Connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    void checkGPS() {
        LocationRequest locationRequest = LocationRequest.create();

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);

        SettingsClient settingsClient = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = settingsClient.checkLocationSettings(builder.build());

        task.addOnFailureListener(this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull final Exception e) {
                Log.d(TAG+"GPS_main", "GPS off");
                // GPS off. We then request to turn it on. If user closes the app then the user is warned by voice from the ServiceEmergency
                if( !turnOnGPSwasRequested ){
                    if (e instanceof ResolvableApiException) {
                        ResolvableApiException resolvable = (ResolvableApiException) e;
                        try {
                            resolvable.startResolutionForResult(ActivityEmergency.this, Static_AppVariables.REQUESTCODE_TURNON_GPS); // no need for request code since we will not handle the change
                            turnOnGPSwasRequested = true; // this way we do not accumulate request on screen
                        } catch (IntentSender.SendIntentException e1) {
                            e1.printStackTrace();
                        }
                    }
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == Static_AppVariables.REQUESTCODE_TURNON_GPS) {
            turnOnGPSwasRequested = false; // we can request again to turn the GPS in case the user turns it off again or the user refuses to turn it on
        }
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        try{
            unregisterReceiver(receiverFromService);
        } catch(Exception ignored){}
    }

    @Override
    public void onBackPressed() {
        if(!ServiceEmergency.running){
            super.onBackPressed();
        }
    }
}
