package com.taedison.helmy;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/***
 * Activity displays the progress of entering the personal data, emergency contacts and Helmy devices
 * User can continue without HelmyM, as HelmyC can be sold without HelmyM
 */
public class ActivityProgress extends AppCompatActivity {

    final String TAG = "ProgressAct";

    //views
    ImageView imgBulletPersonalData, imgBulletEmergency, imgBulletHelmet, imgBulletBike;
    TextView btnProgressDone;
    ProgressBar pbLogout;

    //shared preferences
    SingletonSharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_progress);

        //views
        imgBulletPersonalData = findViewById(R.id.imgBulletPersonalData);
        imgBulletEmergency = findViewById(R.id.imgBulletEmergency);
        imgBulletHelmet = findViewById(R.id.imgBulletHelmet);
        imgBulletBike = findViewById(R.id.imgBulletBike);
        btnProgressDone = findViewById(R.id.btnProgressDone);
        pbLogout = findViewById(R.id.pbProgress);

        //shared preferences
        preferences = SingletonSharedPreferences.getInstance(this.getApplicationContext()); // for the last user logged in

        preferences.set_downloadWasCompleteAfterLogin(); // if user reached this activity, it was because data from server was downloaded and saved successfully

        if( !TextUtils.isEmpty(preferences.getUserPhone()) ){
            imgBulletPersonalData.setImageDrawable(getResources().getDrawable(R.drawable.green_progress));
        }
        String emergencyContact = preferences.getUserEmergencyPhone();
        if(!TextUtils.isEmpty(emergencyContact)){
            imgBulletEmergency.setImageDrawable(getResources().getDrawable(R.drawable.green_progress));
        }
        ArrayList<String> helmets = preferences.get_helmets_saved_MACs();
        if(helmets.size() > 0){
            imgBulletHelmet.setImageDrawable(getResources().getDrawable(R.drawable.green_progress));
        }
        ArrayList<String> bikes = preferences.get_bikes_saved_MACs();
        if(bikes.size() > 0){
            imgBulletBike.setImageDrawable(getResources().getDrawable(R.drawable.green_progress));
        }

        if( TextUtils.isEmpty(preferences.getUserPhone()) || TextUtils.isEmpty(emergencyContact)
                || helmets.size() < 1 ){
            btnProgressDone.setVisibility(View.GONE);
        }
    }

    public void click_progressDone(View view) {
        ArrayList<String> bikes = preferences.get_bikes_saved_MACs();
        if(bikes.size() < 1){
            final AlertMessageButton alert = new AlertMessageButton(this);
            alert.setDialogMessage(getResources().getString(R.string.wantToContinueWithoutBike));
            alert.setDialogPositiveButton(getResources().getString(R.string.Yes),
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            preferences.userRegistered_data_devices();
                            Intent intent = new Intent(ActivityProgress.this, ActivityGoAs.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            alert.dismissAlert();
                        }
                    });
            alert.setDialogNegativeButton(getResources().getString(R.string.No),
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            alert.dismissAlert();
                        }
                    });
            alert.showAlert();
        } else {
            preferences.userRegistered_data_devices();
            Intent intent = new Intent(ActivityProgress.this, ActivityGoAs.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }
    }

    public void click_personalData(View view) {
        Intent intent = new Intent(this, ActivityRegisterUser_PersonalInfo.class);
        if(TextUtils.isEmpty(preferences.getUserNames()) ){
            launchAlert(getResources().getString(R.string.whyPersonalData), intent);
        } else {
            // alert is not shown because user will edit the info
            startActivity(intent);
        }
    }

    public void click_emergencyContact(View view) {
        Intent intent = new Intent(this, ActivityRegisterEmergencyContact1.class);
        if(TextUtils.isEmpty(preferences.getUserEmergencyPhone()) ){
            launchAlert(getResources().getString(R.string.whyContacts), intent);
        } else {
            // alert is not shown because user will edit the info
            startActivity(intent);
        }
    }

    public void click_registerHelmet(View view) {
        Intent intent = new Intent(this, ActivityRegisterHelmet.class);
        if( preferences.get_helmets_saved_MACs().size() == 0 ){
            launchAlert(getResources().getString(R.string.whyRegisterHelmet), intent);
        } else {
            // alert is not shown because user will edit the info
            intent.putExtra("edit_MAC_helmet", preferences.get_primaryHelmet_MAC() );
            startActivity(intent);
        }
    }

    public void click_registerBike(View view) {
        if( TextUtils.isEmpty(preferences.getUserPhone()) ){
            Toast.makeText(this, "Primero completa tus datos personales.", Toast.LENGTH_SHORT).show();
        } else if( preferences.get_bikes_saved_MACs().size() == 0 ){
            Intent intent = new Intent(this, ActivityRegisterBikeOCR.class);
            launchAlert(getResources().getString(R.string.whyRegisterBike), intent);
        } else {
            // alert is not shown because user will edit the info
            Intent intent = new Intent(this, ActivityRegisterBike.class);
            intent.putExtra("edit_MAC_bike", preferences.get_primaryBike_MAC() );
            startActivity(intent);
        }
    }

    public void logoutFromProgress(View view) {
        Static_AppMethods.logOut(this, pbLogout);
    }

    private void launchAlert(String message, final Intent intent){
        final AlertMessageButton alert = new AlertMessageButton(this);
        alert.setDialogMessage(message);
        alert.setDialogPositiveButton(getResources().getString(R.string.Ok), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(intent);
                alert.dismissAlert();
            }
        });
        alert.showAlert();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // check if data or password were updated from website
        if(preferences.get_wasPasswordUpdated()) {
            // password was updated, then we need to logout
            preferences.set_passwordUpdatedInServer(false); // clear prefs
            preferences.set_dataUpdatedInServer(false); // clear prefs
            Static_AppMethods.logOut(this, pbLogout);
        } else if(preferences.get_wasDataUpdated()){
            // data was updated, then download data
            preferences.set_passwordUpdatedInServer(false); // clear prefs
            preferences.set_dataUpdatedInServer(false); // clear prefs
            Intent intent = new Intent(this, ActivityRetrieveDataFromServer.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }
    }
}
