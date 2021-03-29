package com.taedison.helmy;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

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
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.TextAppearanceSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.DefaultRetryPolicy;
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
import com.google.android.material.navigation.NavigationView;

import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/***
 * this activity acts as the main menu.
 * To start as driver (Helmy C+M) or pillion (HelmyC only) user must have:
 * - bluetooth on
 * - GPS on
 * - Battery above 15%
 * - Volume > 0
 * - intercomm must be paired
 * - Mobile data on
 * - location permission should be granted (not mandatory)
 * if user has HelmyM, we have to check with Blockchain that it belongs to him/her
 */
public class ActivityGoAs extends AppCompatActivity {

    final String TAG = "actGoAs";

    //Views
    ProgressBar pbDisable;
    TextView btnEnable;
    LinearLayout LLbtnsGoAs;
    TextView tvHelloName, tvEmail, tvHowToStart;

    ConstraintLayout LLbtnGoAsDriver, LLbtnGoAsPillion;

    // vies of the tour
    ConstraintLayout CL_tour, CL_driverTour, CL_pillionTour;
    TextView tvTourMessage;
    ImageView imvHamburger_tour;


    //preferences
    SingletonSharedPreferences preferences;

    private boolean bikeAlwaysOn = false;
    private BLE_HelmyM bluetoothBike;
    private BLE_HelmyC bluetoothHelmet;
    BroadcastReceiver blueDeviceConnectionReceiver, blueWriteEnableReceiver;
    final String TAG_receivers = TAG + "BLE_receivers";

    //Hamburguer menu
    private DrawerLayout mDrawer;
    private NavigationView nvDrawer;

    BluetoothAdapter bluetoothAdapter;

    //Volley
    private RequestQueue requestQueue;

    boolean permissionsForEmergencyMenu = false; // to know if permissions were requested from launching emergency alert from Menu or by pressing on driver or pillion buttons

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_go_as);

        setVolumeControlStream(AudioManager.STREAM_MUSIC); // so that volume keys change the multimedia volume and not the ringtone

        LLbtnGoAsDriver = findViewById(R.id.btnGoAsDriver);
        LLbtnGoAsPillion = findViewById(R.id.btnGoAsPillion);

        tvHelloName = findViewById(R.id.tvHelloName);
        tvEmail = findViewById(R.id.tvEmail);
        tvHowToStart = findViewById(R.id.tvHowToStart);

        //tour views
        CL_tour = findViewById(R.id.CL_tour);
        CL_driverTour = findViewById(R.id.btnGoAsDriver_tour);
        CL_pillionTour = findViewById(R.id.btnGoAsPillion_tour);
        tvTourMessage = findViewById(R.id.tvTourMsg);
        imvHamburger_tour = findViewById(R.id.imv_hamburguer_tour);

        pbDisable = findViewById(R.id.pbDisableHelmy);
        btnEnable = findViewById(R.id.btnEnableHelmy);
        LLbtnsGoAs = findViewById(R.id.LLbtnsGoAs);

        preferences = SingletonSharedPreferences.getInstance(this.getApplicationContext());

        preferences.set_downloadWasCompleteAfterLogin(); // if user reached this activity, it was because data from server was downloaded and saved successfully

        //volley
        requestQueue = SingletonVolley.getInstance(this.getApplicationContext()).getRequestQueue();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        String name = preferences.getUserNames();
        String email = preferences.get_lastUser_email_logged();

//        Log.d(TAG, name + email);

        if(!TextUtils.isEmpty(name)){
            // display only the first name
            String[] names = name.split(" ");
            String helloName;
            if(names.length > 1){
                helloName = getResources().getString(R.string.hello) + "\n" + names[0];
            } else {
                helloName = getResources().getString(R.string.hello) + "\n" + name;
            }
            tvHelloName.setText(helloName);
            tvEmail.setText(email);
        }

        // views
        // Find our drawer view
        mDrawer = findViewById(R.id.drawer_layout);
        // Find our drawer view
        nvDrawer = findViewById(R.id.nvView);
        // Setup drawer view
        setupDrawerContent(nvDrawer);

        // change apperance of title in the menu
        Menu menu = nvDrawer.getMenu();
        MenuItem title = menu.findItem(R.id.menu_options_title);
        title.setTitle(name);
        SpannableString s = new SpannableString(title.getTitle());
        s.setSpan(new TextAppearanceSpan(this, R.style.TextAppearanceTitle), 0, s.length(), 0);
        title.setTitle(s);
        //hide the option disable helmy if no HelmyM has been selected
        if(TextUtils.isEmpty(preferences.get_primaryBike_MAC())){
            menu.findItem(R.id.disableHelmy).setVisible(false);
        }
        //hide the option pair with other HelmyC if no HelmyC has been selected
        if( TextUtils.isEmpty( preferences.get_primaryHelmet_MAC() ) ){
            menu.findItem(R.id.pairWithOtherIntercomm).setVisible(false);
        }

        btnEnable.setVisibility(View.GONE);
        pbDisable.setVisibility(View.GONE); // always starts GONE

        // check if emergency alert demo was launched
        if( !preferences.wasDemoAlreadyLaunched() ){
//            Log.d(TAG, "FirstTime Yes");
            launchEmergencyDemo();
        }
        // check if tour was already shown
        else if ( !preferences.wasTourShown() ){
            launchTour();
        }

        blueDeviceConnectionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // broadcasts from Helmy classes
                boolean connected = intent.getBooleanExtra(Static_AppVariables.INTENTEXTRA_BLE_CONNECTION, false);
                String deviceMAC = intent.getStringExtra(Static_AppVariables.INTENTEXTRA_BLE_MAC);
//                Log.d(TAG_receivers, "Device = " + deviceMAC + " connnected " + connected);
//                Log.d(TAG_receivers, "Device =" + deviceMAC + "connnected" + connected);

                if(deviceMAC != null) {
                    if (deviceMAC.equals(preferences.get_primaryHelmet_MAC())) {
                        if (!connected) {
                            pbDisable.setVisibility(View.GONE);
                            Toast.makeText(ActivityGoAs.this, R.string.bluetoothNotFoundTryAgain, Toast.LENGTH_LONG).show();
                        } else {
                            bluetoothHelmet.pairingMode_writeChar(true); // so that phone can pair with intercomm, or user can pair his/her intercomm with another HelmyC
                            new Handler().postDelayed(new Runnable(){
                                public void run(){
                                    if(!intercommPaired){
                                        pairIntercomm(); // we have to wait at least six seconds before attempting to pair, because 6s is what it takes for HelmyC to start pairing mode
                                    }
                                    bluetoothHelmet.finishConnection();
                                    try{
                                        unregisterReceiver(blueDeviceConnectionReceiver);
                                    } catch (Exception ignored){}
                                }
                            }, 7000); // terminate connection with helmy-c after 7 seconds since Helmy-C should be already in paring mode (see BLE_HelmyC class)

                            // now show the user the instructions for pairing
                            final AlertMessageButton alert = new AlertMessageButton(ActivityGoAs.this);
                            if(intercommPaired){
                                // user will pair intercomms with another user
                                pbDisable.setVisibility(View.GONE);
                                alert.setDialogMessage(getResources().getString(R.string.instructionsPairWithAnotherIntercomm));
                            } else {
                                // intercomm was not paired with user's phone
                                // pbDisable continues visible and it will be gone once it gets paired or an error occurs
                                alert.setDialogMessage(getResources().getString(R.string.instructionsForPairing));
                            }
                            alert.setDialogPositiveButton(getResources().getString(R.string.Ok), new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    alert.dismissAlert();
                                }
                            });
                            alert.hideCancelButton();
                            alert.showAlert();
                        }
                    } else if (deviceMAC.equals(preferences.get_primaryBike_MAC() ) ) {
                        if (!connected) {
                            Toast.makeText(ActivityGoAs.this, R.string.bluetoothNotFoundTryAgain, Toast.LENGTH_LONG).show();
                            pbDisable.setVisibility(View.GONE);
                            bikeAlwaysOn = false;
                        } else {
                            // once connected, it must enable or disable Helmy_M
                            if (!bikeAlwaysOn) {
                                Toast.makeText(ActivityGoAs.this, R.string.enablingHelmy, Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(ActivityGoAs.this, R.string.disablingHelmy, Toast.LENGTH_LONG).show();
                            }
                            bluetoothBike.enable_WriteCharacteristic(bikeAlwaysOn);
//                            Log.d(TAG_receivers, "turn on");
                        }
                    }
                }
            }
        };

        blueWriteEnableReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean success = intent.getBooleanExtra(Static_AppVariables.INTENTEXTRA_BIKE_ENABLE_WRITE, false);
//                Log.d(TAG_receivers, "Write was successful: " + success);
                if (!success) {
                    Toast.makeText(ActivityGoAs.this, R.string.bluetoothErrorTryAgain, Toast.LENGTH_LONG).show();
                    pbDisable.setVisibility(View.GONE);
                } else {
                    // HelmyM was enabled/disabled
                    pbDisable.setVisibility(View.GONE);
                    if (!bikeAlwaysOn){
                        LLbtnsGoAs.setVisibility(View.VISIBLE);
                        btnEnable.setVisibility(View.GONE);
                    } else {
                        btnEnable.setVisibility(View.VISIBLE);
                        LLbtnsGoAs.setVisibility(View.GONE);
                    }
                    finishBikeConnection();
                }
            }
        };
    }

    public void launchEmergencyDemo() {
        // demo is launched to show the user how the alert works
        final AlertMessageButton alert = new AlertMessageButton(this);
        alert.setDialogMessage(getResources().getString(R.string.HowToCancelEmergency));
        alert.setDialogPositiveButton(getResources().getString(R.string.yesLaunchDemo),
                new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!ServiceEmergency.running){
                    ServiceEmergency.running = true;

                    Intent intent = new Intent(ActivityGoAs.this, ServiceEmergency.class);
                    startService(intent);

                    // set date variables so that ServiceEmergency works fine
                    Calendar calendar = Calendar.getInstance();
                    String dateImpact = calendar.get(Calendar.YEAR) +"-"+ (calendar.get(Calendar.MONTH)+1)
                            +"-"+ calendar.get(Calendar.DAY_OF_MONTH) +"-"+ calendar.get(Calendar.HOUR_OF_DAY)
                            +"-"+ calendar.get(Calendar.MINUTE)+"-"+ calendar.get(Calendar.SECOND);
                    String txtFile = "0"; // no associated velocity txt file
                    String alertName = dateImpact +";"+ txtFile +";"+ preferences.get_lastUser_Id_logged() +";"+ preferences.get_primaryBike_MAC(); // no txt file associated
                    preferences.addAlertRegistryPendingToUpload(alertName);

                    Intent intentAct = new Intent(ActivityGoAs.this, ActivityEmergency.class);
                    startActivity(intentAct);
                }
                if ( !preferences.wasTourShown() ){
                    // leave the tour dialog open for when user comes back from demo
                    launchTour();
                }
                alert.dismissAlert();
            }
        });
        alert.setDialogNegativeButton(getResources().getString(R.string.Later), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alert.dismissAlert();
                if ( !preferences.wasTourShown() ){
                    launchTour();
                }
            }
        });
        alert.setCancellable(false);
        alert.hideCancelButton();
        alert.showAlert();
    }

    private void launchTour(){
        // tour is launched once
        final AlertMessageButton alert = new AlertMessageButton(this);
        alert.setDialogMessage(getResources().getString(R.string.tourExplanation));
        alert.setDialogPositiveButton(getResources().getString(R.string.Yes), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alert.dismissAlert();
                tourItemToShow = 0; // so that it starts showing the first item in the tour
                tvEmail.setVisibility(View.INVISIBLE);
                tvHowToStart.setVisibility(View.INVISIBLE);
                tvHelloName.setVisibility(View.INVISIBLE);
                CL_tour.setVisibility(View.VISIBLE);
                nvDrawer.setVisibility(View.GONE); // had to hide it because for some reason the hamburger button in the tour is displaying the drawer
                showNextInTour();
            }
        });
        alert.setDialogNegativeButton(getResources().getString(R.string.No), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alert.dismissAlert();
                preferences.setTourWasShown();
            }
        });
        alert.setCancellable(false);
        alert.hideCancelButton();
        alert.showAlert();
    }

    private int tourItemToShow = -1;
    private void showNextInTour() {
        if(tourItemToShow == 0){
            imvHamburger_tour.setVisibility(View.VISIBLE);
            tvTourMessage.setText(getResources().getString(R.string.additionalOptionsTour));
            tourItemToShow = 1;
        } else if (tourItemToShow == 1){
            imvHamburger_tour.setVisibility(View.INVISIBLE);
            CL_driverTour.setVisibility(View.VISIBLE);
            tvTourMessage.setText(getResources().getString(R.string.goAsDriverTour));
            tourItemToShow = 2;
        } else if (tourItemToShow == 2){
            CL_driverTour.setVisibility(View.INVISIBLE);
            CL_pillionTour.setVisibility(View.VISIBLE);
            tvTourMessage.setText(getResources().getString(R.string.goAsPillionTour));
            tourItemToShow = -1;
        } else {
            CL_tour.setVisibility(View.GONE);
            nvDrawer.setVisibility(View.VISIBLE); // had to hide it because for some reason the hamburger button in the tour is displaying the drawer
            tvEmail.setVisibility(View.VISIBLE);
            tvHowToStart.setVisibility(View.VISIBLE);
            tvHelloName.setVisibility(View.VISIBLE);
            preferences.setTourWasShown();
        }
    }

    public void click_btnNextInTour(View view) {
        showNextInTour();
    }

    private Intent intentDriverPillion;

    public void click_btnGoAsPillion(View view) {
        if ( !TextUtils.isEmpty(preferences.get_primaryHelmet_MAC()) ) {
            // user has no HelmyC registered
            intentDriverPillion = new Intent(this, ActivityPillion.class);
            if (bluetoothAdapter.isEnabled()) {
                checkGPS(); // now check GPS and the rest of conditions: volume, ...
            } else {
                // request to turn it on
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, Static_AppVariables.REQUESTCODE_TURNON_BLUETOOTH_FROM_PILLION);
            }
        } else {
            Toast.makeText(this, R.string.noHelmetSelected, Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, ActivityChooseDevices.class);
            startActivity(intent);
        }
    }

    public void click_btnGoAsDriver(View view) {
        if ( !TextUtils.isEmpty(preferences.get_primaryHelmet_MAC()) &&
                !TextUtils.isEmpty(preferences.get_primaryBike_MAC()) ) {
            intentDriverPillion = new Intent(this, ActivityMain.class);
            if (bluetoothAdapter.isEnabled()) {
                // first verify with Blockchain that HelmyM belongs to the user
                if( TextUtils.isEmpty(preferences.getBikeBondId(preferences.get_primaryBike_MAC())) ){
                    // bondId is not saved yet. We need to display it in the ActivityMain
                    getBondId_fromBikeId_blockchain();
                } else {
                    getUserId_fromBikeId_blockchain();
                }
            } else {
                // request to turn it on
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, Static_AppVariables.REQUESTCODE_TURNON_BLUETOOTH);
            }
        } else {
            if ( TextUtils.isEmpty(preferences.get_primaryHelmet_MAC()) &&
                    !TextUtils.isEmpty(preferences.get_primaryBike_MAC()) ) {
                Toast.makeText(this, R.string.noHelmetSelected, Toast.LENGTH_SHORT).show();
            } else if ( !TextUtils.isEmpty(preferences.get_primaryHelmet_MAC()) &&
                    TextUtils.isEmpty(preferences.get_primaryBike_MAC()) ) {
                Toast.makeText(this, R.string.noBikeSlected, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.noHelmetNorBikeSelected, Toast.LENGTH_SHORT).show();
            }
            Intent intent = new Intent(this, ActivityChooseDevices.class);
            startActivity(intent);
        }
    }

    public void displayMenu(View view) {
        if(tourItemToShow == -1){
            // if it is -1 then it is showing the tour, therefore do not display options
            mDrawer.openDrawer(GravityCompat.START);
        }
    }

    private void setupDrawerContent(NavigationView navigationView) {
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                        selectDrawerItem(menuItem);
                        return true;
                    }
                });
    }

    public void selectDrawerItem(MenuItem menuItem) {

        if(!bikeAlwaysOn){
            Intent intent;
            switch(menuItem.getItemId()) {
                case R.id.myDevices:
                    intent = new Intent(this, ActivityChooseDevices.class);
                    startActivity(intent);
                    break;
                case R.id.pairWithOtherIntercomm:
                    if( TextUtils.isEmpty( preferences.get_primaryHelmet_MAC() ) ){
                        Toast.makeText(this, getResources().getString(R.string.noHelmetSelected),
                                Toast.LENGTH_LONG).show();
                        intent = new Intent(this, ActivityChooseDevices.class);
                        startActivity(intent);
                    } else {
                        connect2helmet_startPairingMode();
                    }
                    break;
                case R.id.editMyData:
                    intent = new Intent(this, ActivityRegisterUser_PersonalInfo.class);
                    startActivity(intent);
                    break;
                case R.id.editContacts:
                    intent = new Intent(this, ActivityRegisterEmergencyContact1.class);
                    startActivity(intent);
                    break;
                case R.id.sendSMS:
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED){
//                            || ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_DENIED) {
                        // display reason for permissions
                        permissionsForEmergencyMenu = true;
                        launchAlertExplanationLocationForEmergency();
                    } else {
                        if(!ServiceEmergency.running){
                            ServiceEmergency.running = true;

                            preferences.set_demoWasLaunched(); // in case the user did not launch the the demo, disable it

                            intent = new Intent(this, ServiceEmergency.class);
                            startService(intent);

                            Intent intentAct = new Intent(this, ActivityEmergency.class);
                            startActivity(intentAct);
                        }
                    }
                    break;
                case R.id.disableHelmy:
                    launchAlertDisableHelmy();
                    break;
                case R.id.contactHelmy:
//                    Toast.makeText(this, "En fase de desarrollo", Toast.LENGTH_SHORT).show();
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.helmy.com.co"));
                    startActivity(browserIntent);
                    break;
                case R.id.logout_choose:
                    Static_AppMethods.logOut(this, pbDisable);
                    break;
                default:
                    break;
            }
        } else {
            Toast.makeText(ActivityGoAs.this, R.string.youMustEnableHelmy, Toast.LENGTH_LONG).show();
        }
        // Close the navigation drawer
        mDrawer.closeDrawers();
    }

    private void launchAlertDisableHelmy(){
        final AlertMessageButton alert = new AlertMessageButton(this);
        alert.setDialogMessage(getResources().getString(R.string.warningDisablingHelmy));
        alert.setDialogPositiveButton(getResources().getString(R.string.Yes), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(preferences.get_primary_bike_nickname() != null){
                    bikeAlwaysOn = true;
                    if (bluetoothAdapter.isEnabled()) {
                        connect2Bike();
                    } else {
                        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(enableBtIntent, Static_AppVariables.REQUESTCODE_BT_ENABLE_HELMY);
                    }
                } else {
                    Toast.makeText(ActivityGoAs.this,
                            R.string.bikeNotYetChosen, Toast.LENGTH_LONG).show();
                }
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


    private void connect2Bike(){
        registerReceiver(blueDeviceConnectionReceiver, new IntentFilter(Static_AppVariables.ACTIONFILTER_GATT_CONNECTION));
        registerReceiver(blueWriteEnableReceiver, new IntentFilter(Static_AppVariables.ACTIONFILTER_BIKE_ENABLE));

        bluetoothBike = new BLE_HelmyM(ActivityGoAs.this);

        Toast.makeText(ActivityGoAs.this, R.string.connecting, Toast.LENGTH_LONG).show();
        pbDisable.setVisibility(View.VISIBLE);
        bluetoothBike.initiateBluetoothConexion( bluetoothAdapter.getRemoteDevice(preferences.get_primaryBike_MAC()) );
    }

    private void connect2helmet_startPairingMode(){
        if (bluetoothAdapter.isEnabled()) {
            intercommPaired = true; // force to true so that phone does not intend to pair with intercom once it gets connected
            registerReceiver(blueDeviceConnectionReceiver, new IntentFilter(Static_AppVariables.ACTIONFILTER_GATT_CONNECTION));
            bluetoothHelmet = new BLE_HelmyC(ActivityGoAs.this);

            Toast.makeText(ActivityGoAs.this, R.string.connecting, Toast.LENGTH_LONG).show();
            pbDisable.setVisibility(View.VISIBLE);
            bluetoothHelmet.initiateBluetoothConexion( bluetoothAdapter.getRemoteDevice(preferences.get_primaryHelmet_MAC()), false, null );
        } else {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, Static_AppVariables.REQUESTCODE_TURNON_BLUETOOTH_FROM_INTERCOM);
        }
    }

    public void click_btnEnableHelmy(View view) {
        bikeAlwaysOn = false;

        if (bluetoothAdapter.isEnabled()) {
            connect2Bike();
        } else {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, Static_AppVariables.REQUESTCODE_BT_ENABLE_HELMY);
        }

    }

    private void finishBikeConnection(){
        bluetoothBike.finishConnection();
        unregisterReceiver(blueDeviceConnectionReceiver);
        unregisterReceiver(blueWriteEnableReceiver);
    }

    void checkGPS() {

        pbDisable.setVisibility(View.GONE);

        LocationRequest locationRequest = LocationRequest.create();

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);

        SettingsClient settingsClient = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = settingsClient.checkLocationSettings(builder.build());

        task.addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                // GPS is ON
                checkBatteryLevel();
            }
        });

        task.addOnFailureListener(this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull final Exception e) {
                // GPS off
                final AlertMessageButton alert = new AlertMessageButton(ActivityGoAs.this);
                alert.setDialogMessage(getResources().getString(R.string.whyGPSmustBeOnForRegister));
                alert.setDialogPositiveButton(getResources().getString(R.string.Ok), new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (e instanceof ResolvableApiException) {
                            // request to turn GPS on
                            ResolvableApiException resolvable = (ResolvableApiException) e;
                            try {
                                resolvable.startResolutionForResult(ActivityGoAs.this, Static_AppVariables.REQUESTCODE_TURNON_GPS);
                            } catch (IntentSender.SendIntentException e1) {
                                e1.printStackTrace();
                            }
                        }

                        alert.dismissAlert();
                    }
                });
                alert.showAlert();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == Static_AppVariables.REQUESTCODE_TURNON_GPS){
            if (resultCode == Activity.RESULT_OK) {// GPS was turned on
                checkBatteryLevel(); // continue checking the rest of the conditions
            }
        } else if(requestCode == Static_AppVariables.REQUESTCODE_BT_ENABLE_HELMY) {
            // User wanted to enable or disable the helmy system
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled
                connect2Bike();
            } else {
                // explain why to turn it on
                Static_AppMethods.launchAlertBluetooth(this, this, Static_AppVariables.REQUESTCODE_BT_ENABLE_HELMY );
            }
        } else if(requestCode == Static_AppVariables.REQUESTCODE_TURNON_BLUETOOTH) {
            // When the request to enable Bluetooth returns, driver button was pressed
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled
                getUserId_fromBikeId_blockchain();
            } else {
                // explain why to turn it on
                Static_AppMethods.launchAlertBluetooth(this, this, Static_AppVariables.REQUESTCODE_TURNON_BLUETOOTH);
            }
        } else if(requestCode == Static_AppVariables.REQUESTCODE_TURNON_BLUETOOTH_FROM_PILLION) {
            // When the request to enable Bluetooth returns, pillion button was pressed
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled
                checkGPS(); // now check GPS and the rest of conditions: volume, ...
            } else {
                // explain why to turn it on
                Static_AppMethods.launchAlertBluetooth(this, this, Static_AppVariables.REQUESTCODE_TURNON_BLUETOOTH);
            }
        } else if(requestCode == Static_AppVariables.REQUESTCODE_TURNON_BLUETOOTH_FROM_INTERCOM) {
            // When the request to enable Bluetooth returns, user wants to pair with intercomm other HelmyC
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled
                connect2helmet_startPairingMode();
            } else {
                // explain why to turn it on
                Static_AppMethods.launchAlertBluetooth(this, this, Static_AppVariables.REQUESTCODE_TURNON_BLUETOOTH);
            }
        }
    }

    private void checkBatteryLevel(){
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);

        // charging
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING;

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        // battery low?
        float batteryPct = level * 100 / (float) scale;

//        Log.d(TAG+"Battery_log", "isCharging= " + isCharging + " Battery= "+batteryPct);

        if(batteryPct <= 15 && !isCharging){
            final AlertMessageButton alert = new AlertMessageButton(this);
            alert.setDialogMessage(getResources().getString(R.string.wannaDeactivateHelmyBatteryLow));
            alert.setDialogPositiveButton(getResources().getString(R.string.Yes), new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    launchAlertDisableHelmy();

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
        } else {
            checkVolume();
        }
    }

    private void checkVolume() {

        AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if(audio != null){
            int currentVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
//        Toast.makeText(ActivityGoAs.this, "Volume= " + currentVolume, Toast.LENGTH_LONG).show();
            if(currentVolume>0){
                checkMobileDataON();
            } else {
                final AlertMessageButton alert = new AlertMessageButton(this);
                alert.setDialogMessage(getResources().getString(R.string.increaseVolume));
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

    boolean intercommPaired;
    private void checkIntercommPaired(){
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        // check if intercomm is paired
        intercommPaired = false;
//        Log.d(TAG+"BluetoothPair", "Associated: " + preferences.getHelmetAssociatedBluetoothClassic(
//                preferences.get_primaryHelmet_MAC() ) );
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
//                Log.d(TAG+"BluetoothPair", "Paired: " + device.getAddress() );
                if(device.getAddress().equals(
                        preferences.getHelmetAssociatedBluetoothClassic(
                                preferences.get_primaryHelmet_MAC() ))){
                        intercommPaired = true;
                }
            }
        }
        if(intercommPaired){
//            checkSMSPermission();
            checkLocationPermission();
        } else {
            final AlertMessageButton alert = new AlertMessageButton(this);
            alert.setDialogMessage(getResources().getString(R.string.intercommNotPaired));
            alert.setDialogPositiveButton(getResources().getString(R.string.pair),
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            connect2helmet_startPairingMode(); // pairIntercomm will be called after 6s that HelmyC gets connected
                            alert.dismissAlert();
                        }
                    });
            alert.showAlert();
        }
    }

    private void pairIntercomm(){
        pbDisable.setVisibility(View.VISIBLE);
        registerReceiver(bluetoothBroadcastReceiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)); // Response to pairing will handled by the bluetoothBroadcastReceiver
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(preferences.getHelmetAssociatedBluetoothClassic(
                preferences.get_primaryHelmet_MAC() ) );
        try {
//            Log.d(TAG+"pairDevice()", "Start Pairing...");
            Method m = device.getClass().getMethod("createBond", (Class[]) null);
            m.invoke(device, (Object[]) null);
//            Log.d(TAG+"pairDevice()", "Pairing finished.");
        } catch (Exception e) {
//            Log.e(TAG+"pairDevice()", e.toString() );
        }
    }

    private final BroadcastReceiver bluetoothBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                BluetoothDevice mBluetooth2Pair = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (mBluetooth2Pair != null) {
                    switch (mBluetooth2Pair.getBondState()) {
                        case BluetoothDevice.BOND_BONDED:
                            pbDisable.setVisibility(View.GONE);
                            Toast.makeText(ActivityGoAs.this, getResources().getString(R.string.paired), Toast.LENGTH_SHORT).show();
                            break;
                        case BluetoothDevice.BOND_BONDING:
                            break;
                        case BluetoothDevice.BOND_NONE:
//                            Log.d(TAG+"BTpairing", "NONE");
                            pbDisable.setVisibility(View.GONE);
                            Toast.makeText(ActivityGoAs.this, getResources().getString(R.string.errorTryAgain),
                                    Toast.LENGTH_SHORT).show();
                            break;
                    }
                }
            }
        }
    };

    final String TAG_blockchain = TAG + "Blockchain";
    public void getUserId_fromBikeId_blockchain() {
        String url;
        if(preferences.get_isPrimaryServerDown()){
            // primary server is down, so we will use the secondary
            url = Static_AppVariables.url_blockchainGetUserIDfromBikeID2;
        } else {
            url = Static_AppVariables.url_blockchainGetUserIDfromBikeID;
        }

        pbDisable.setVisibility(View.VISIBLE);

        StringRequest strRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response) {
//                        Log.d(TAG_blockchain, "response: " + response);
                        pbDisable.setVisibility(View.GONE);
                        if( !TextUtils.isEmpty(response) ){
                            try {
                                JSONObject jsonArray = new JSONObject(response);
                                String status = jsonArray.getString("status");
                                if(status.equals("0")){
                                    // nodeJs app is not ready or something went wrong
                                    Toast.makeText(ActivityGoAs.this, getResources().getString
                                            (R.string.errorTryAgain), Toast.LENGTH_SHORT).show();
                                } else {
                                    if(status.equals("2")){
                                        // it takes a about 15 seconds for HelmyM to register in blokchain, user may have to wait
                                        Toast.makeText(ActivityGoAs.this, getResources().
                                                getString(R.string.bikeNotRegisteredInBlockchain), Toast.LENGTH_SHORT).show();
                                    } else {
                                        if(status.equals(preferences.get_lastUser_Id_logged())){
                                            checkGPS(); // now check GPS and the rest of conditions: volume, ...
                                        } else {
                                            // somehow the user kept in his app the registry of a previous HelmyM, or helmy server got hacked (not blockchain)
                                            final AlertMessageButton alert = new AlertMessageButton(ActivityGoAs.this);
                                            alert.setDialogMessage(getResources().getString(R.string.bikeAlreadyRegisteredInBlockchain));
                                            alert.setDialogPositiveButton(getResources().getString(R.string.Ok),
                                                    new View.OnClickListener() {
                                                        @Override
                                                        public void onClick(View view) {
                                                            alert.dismissAlert();
                                                        }
                                                    });
                                            alert.setCancellable(false);
                                            alert.hideCancelButton();
                                            alert.showAlert();
                                        }
                                    }

                                }
                            } catch (Exception ignored){
                                Static_AppMethods.ToastTryAgain(ActivityGoAs.this);
                            }
                        } else {
                            // most likely the user has mobile data on but without internet service. Worst case, something went wrong with the server
                            // we allow the user to continue
                            checkGPS(); // now check GPS and the rest of conditions: volume, ...
                        }
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                        pbDisable.setVisibility(View.GONE);
//                        Log.e(TAG_blockchain, error.toString());
                        Static_AppMethods.checkResponseCode(error, preferences);
                        // we allow the user to continue
                        checkGPS(); // now check GPS and the rest of conditions: volume, ...
                    }
                })
        {
            @Override
            protected Map<String, String> getParams()
            {
//                Log.d(TAG_blockchain, "params loaded");
                Map<String, String> params = new HashMap<>();
                params.put("bikeId", preferences.get_primaryBike_bikeId()); // blockchain stores the bikeIds without encryption
                return params;
            }
        };

        // change timeout to 3 seconds so that user does not have to wait long in case of a bad
        // connection. If connection failed, we dont want it to try again
        strRequest.setRetryPolicy(new DefaultRetryPolicy(
                (int) TimeUnit.SECONDS.toMillis(3),
                0,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        requestQueue.add(strRequest);
    }

    private void getBondId_fromBikeId_blockchain() {
        String url;
        if(preferences.get_isPrimaryServerDown()){
            // primary server is down, so we will use the secondary
            url = Static_AppVariables.url_blockchainGetBondIDfromBikeID2;
        } else {
            url = Static_AppVariables.url_blockchainGetBondIDfromBikeID;
        }
        pbDisable.setVisibility(View.VISIBLE);

        StringRequest strRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response) {
//                        Log.d(TAG_blockchain, "response: " + response);
                        if( !TextUtils.isEmpty(response) ){
                            try {
                                JSONObject jsonArray = new JSONObject(response);
                                String status = jsonArray.getString("status");
                                if(status.equals("0")){
                                    // nodeJs app is not ready or something went wrong
                                    pbDisable.setVisibility(View.GONE);
                                    Toast.makeText(ActivityGoAs.this, getResources().getString
                                            (R.string.errorTryAgain), Toast.LENGTH_SHORT).show();
                                } else if(status.equals("2")){
                                    // it takes a about 15 seconds for HelmyM to register in blokchain, user may have to wait
                                    pbDisable.setVisibility(View.GONE);
                                    Toast.makeText(ActivityGoAs.this, getResources().getString
                                            (R.string.bikeNotRegisteredInBlockchain), Toast.LENGTH_SHORT).show();
                                } else {
                                    try {
                                        String email = preferences.get_lastUser_email_logged();
                                        String bondIdEncrypted = Static_AppMethods.encryptAES_toString64(ActivityGoAs.this,
                                                status, email, preferences);
                                        preferences.saveBikeBondId_encrypted(preferences.get_primaryBike_MAC(), bondIdEncrypted);
                                        getUserId_fromBikeId_blockchain(); // to check if bike belongs to the user
                                    } catch (Exception e) {
//                                        Log.e(TAG+"Encrypt", "Encryption Error= " + e.getMessage());
                                        Static_AppMethods.ToastEncryptionError(ActivityGoAs.this);
                                        pbDisable.setVisibility(View.GONE);
                                    }
                                }
                            } catch (Exception ignored){
                                pbDisable.setVisibility(View.GONE);
                                Toast.makeText(ActivityGoAs.this, getResources().getString
                                        (R.string.errorTryAgain), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            // most likely the user has mobile data on but without internet service. Worst case, something went wrong with the server
                            pbDisable.setVisibility(View.GONE);
                            // we let the user pass so that we dont affect the user experience
                            getUserId_fromBikeId_blockchain();
                        }
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                        pbDisable.setVisibility(View.GONE);
//                        Log.e(TAG_blockchain, error.toString());
                        Static_AppMethods.checkResponseCode(error, preferences);
                        // we let the user pass so that we dont affect the user experience
                        getUserId_fromBikeId_blockchain();
                    }
                })
        {
            @Override
            protected Map<String, String> getParams()
            {
                Map<String, String> params = new HashMap<>();
                params.put("bikeId", preferences.getBikeId(preferences.get_primaryBike_MAC()) ); // blockchain stores the bikeIds without encryption
//                Log.d(TAG_blockchain, "params getBondId: " + params.toString());
                return params;
            }
        };

        // change timeout to 3 seconds so that user does not have to wait long in case of a bad
        // connection. If connection failed, we dont want it to try again
        strRequest.setRetryPolicy(new DefaultRetryPolicy(
                (int) TimeUnit.SECONDS.toMillis(3),
                0,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        requestQueue.add(strRequest);
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true); // sends the app to the background
    }

    @Override
    protected void onResume() {
        super.onResume();
        // launch alerts if something went wrong as driver or as pillion: bluetooth was turned off in Android Go devices, or user closed the app and opend it again
        Intent intent = getIntent();
        int bluetoothOff = intent.getIntExtra(Static_AppVariables.INTENTEXTRA_BLUETOOTHOFF, -1);
        if(bluetoothOff == 1){
            // it is an AndroidGO device and exited ActivityMain because user turned off the bluetooth
            // bike was connected and got disconnected because user was stupid enough to turn off the bluetooth
            final AlertMessageButton alertTripFinished = new AlertMessageButton(this);
            alertTripFinished.setDialogMessage(getResources().getString(R.string.btTurnedOff_bikeWillTurnOff));
            alertTripFinished.setDialogPositiveButton(getResources().getString(R.string.Yes), new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    click_btnGoAsDriver(null); // this way the user has to turn on bluetooth on and once turned on will be redirected to ActivityMain
                    alertTripFinished.dismissAlert();
                }
            });
            alertTripFinished.setDialogNegativeButton(getResources().getString(R.string.No), new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    alertTripFinished.dismissAlert();
                    stopService(new Intent(ActivityGoAs.this, ServiceBikeDisconnected.class));
                }
            });
            alertTripFinished.hideCancelButton(); // user must give an answer
            alertTripFinished.setCancellable(false); // user must give an answer
            alertTripFinished.showAlert();

        } else if(bluetoothOff == 0) {
            final AlertMessageButton alertBluetoothOff = new AlertMessageButton(this);
            alertBluetoothOff.setDialogMessage(getResources().getString(R.string.bluetoothTurnedOff));
            alertBluetoothOff.setDialogPositiveButton(getResources().getString(R.string.Ok),
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            alertBluetoothOff.dismissAlert();
                        }
                    });
            alertBluetoothOff.showAlert();
        }

        // check if data or password were updated from website after checking that bluetooth wasn't turned off
        // during a trip. This way we give the opportunity to come back to driver or pillion activities,
        // rather than logging out the user or go to download the data
        else if(preferences.get_wasPasswordUpdated()) {
            // password was updated, then we need to logout
            preferences.set_passwordUpdatedInServer(false); // clear prefs
            preferences.set_dataUpdatedInServer(false); // clear prefs
            Static_AppMethods.logOut(this, pbDisable);
        } else if(preferences.get_wasDataUpdated()){
            // data was updated, then download data
            preferences.set_passwordUpdatedInServer(false); // clear prefs
            preferences.set_dataUpdatedInServer(false); // clear prefs
            Intent i = new Intent(this, ActivityRetrieveDataFromServer.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        }
    }

//    private void checkSMSPermission() {
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_DENIED) {
//            // display reason for permissions
//            launchAlertExplanationSMS();
//        } else {
//            // start trip
//            startActivity(intentDriverPillion);
//        }
//    }

    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) {
            // display reason for permissions
            launchAlertExplanationLocation();
        } else {
            // start trip
            startActivity(intentDriverPillion);
        }
    }

//    private void requestSMSpermission() {
//        // SMS and Location permissions are mandatory, it will continue asking until user grants the permissions
//        ActivityCompat.requestPermissions(this,
//                new String[]{Manifest.permission.SEND_SMS},
//                Static_AppVariables.REQUESTCODE_SMS_PERMISSION);
//    }

    private void requestLocationPermission() {
        // SMS and Location permissions are mandatory, it will continue asking until user grants the permissions
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                Static_AppVariables.REQUESTCODE_LOCATION_PERMISSION);
    }



    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {

//        if (requestCode == Static_AppVariables.REQUESTCODE_SMS_PERMISSION && permissions.length > 0) {
//            if (grantResults.length <= 0 ||
//                    grantResults[0] == PackageManager.PERMISSION_DENIED) {
//                if (Build.VERSION.SDK_INT >= 23) {
//                    // in version above 23 user can reject permission and request not be asked again
//                    boolean showRationale = shouldShowRequestPermissionRationale(permissions[0]);
//                    if (!showRationale) {
//                        // user denied permission and CHECKED "never ask again"
//                        launchAlertActivatePersimissionsManually();
//                    } else {
//                        // user did NOT check "never ask again", user rejected the permissions
//                        checkLocationPermission();
//                    }
//                } else {
//                    checkLocationPermission();
//                }
//
//            } else {
//                // SMS permission granted
//                checkLocationPermission();
//            }
//        } else
        if (requestCode == Static_AppVariables.REQUESTCODE_LOCATION_PERMISSION && permissions.length > 0) {
            // access_fine_location includes coarse location
            if (grantResults.length <= 0 ||
                    grantResults[0] == PackageManager.PERMISSION_DENIED) {

                if (Build.VERSION.SDK_INT >= 23) {
                    // in version above 23 user can reject permission and request not be asked again
                    boolean showRationale = shouldShowRequestPermissionRationale(permissions[0]) ;
                    if (!showRationale) {
                        // user denied permission and CHECKED "never ask again"
                        launchAlertActivatePersimissionsManually();
                    } else {
                        // user did NOT check "never ask again", user rejected the permissions
                        if(!permissionsForEmergencyMenu) {
                            // permissions were requested to enter into driver or pillion modes
                            // start trip
                            startActivity(intentDriverPillion);
                        }
                    }
                } else {
                    if(!permissionsForEmergencyMenu) {
                        // permissions were requested to enter into driver or pillion modes
                        // start trip
                        startActivity(intentDriverPillion);
                    }
                }

            } else {
                // permission granted
                if(permissionsForEmergencyMenu
                        && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
//                        && ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED){
                    if(!ServiceEmergency.running){
                        ServiceEmergency.running = true;

                        preferences.set_demoWasLaunched(); // in case the user did not launch the the demo, disable it

                        Intent intent = new Intent(this, ServiceEmergency.class);
                        startService(intent);

                        Intent intentAct = new Intent(this, ActivityEmergency.class);
                        startActivity(intentAct);
                    }
                } else {
                    // start trip
                    startActivity(intentDriverPillion);
                }

            }
        }
    }

    private void launchAlertActivatePersimissionsManually() {
        final AlertMessageButton alert = new AlertMessageButton(this);
        alert.setDialogMessage(getResources().getString(R.string.enableLocationPermissionManually));
        alert.setDialogPositiveButton(getResources().getString(R.string.Go2Settings), new View.OnClickListener() {
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
        alert.setDialogNegativeButton(getResources().getString(R.string.No), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!permissionsForEmergencyMenu) {
                    // permissions were requested to enter into driver or pillion modes
                    // start trip
                    startActivity(intentDriverPillion);
                }
                alert.dismissAlert();
            }
        });
        alert.showAlert();
    }

//    private void launchAlertExplanationSMS() {
//        final AlertMessageButton alert = new AlertMessageButton(this);
//        alert.setDialogMessage(getResources().getString(R.string.SMSpermissionExplanation));
//        alert.setDialogPositiveButton(getResources().getString(R.string.Ok),
//                new View.OnClickListener() {
//                    @Override
//                    public void onClick(View view) {
////                        requestSMSpermission();
//                        requestLocationPermission();
//                        alert.dismissAlert();
//                    }
//                });
//        alert.showAlert();
//    }

    private void launchAlertExplanationLocationForEmergency() {
        final AlertMessageButton alert = new AlertMessageButton(this);
        alert.setDialogMessage(getResources().getString(R.string.locationPermissionExplanationAlert));
        alert.setDialogPositiveButton(getResources().getString(R.string.Ok),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        requestLocationPermission();
                        alert.dismissAlert();
                    }
                });
        alert.showAlert();
    }

    private void launchAlertExplanationLocation() {
        final AlertMessageButton alert = new AlertMessageButton(this);
        alert.setDialogMessage(getResources().getString(R.string.locationPermissionExplanation));
        alert.setDialogPositiveButton(getResources().getString(R.string.Ok),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        requestLocationPermission();
                        alert.dismissAlert();
                    }
                });
        alert.showAlert();
    }

    void checkMobileDataON(){

        boolean mobileDataEnabled = false; // Assume disabled
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        try {
            Class cmClass = Class.forName(cm.getClass().getName());
            Method method = cmClass.getDeclaredMethod("getMobileDataEnabled");
            method.setAccessible(true); // Make the method callable
            // get the setting for "mobile data"
            mobileDataEnabled = (Boolean) method.invoke(cm);
//            Log.d("SplashAct", "Connected? = " + mobileDataEnabled);
        } catch (Exception e) {
            // Some problem accessing the private API or reflection on getMobileDataEnabled,
            // perhaps in the next Android version is not accessible, we try with the following
            // and if that fails then we have to let the user pass
            try{
                TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                if (tm != null && tm.getSimState() == TelephonyManager.SIM_STATE_READY) {
                    mobileDataEnabled = Settings.Global.getInt(getContentResolver(), "mobile_data", 1) == 1;
                }
            } catch (Exception ignored) {
                mobileDataEnabled = true; // both methods failed, we have to let the user pass
            }
//            Log.d("SplashAct", "Connected? = " + mobileDataEnabled);
        }

        if( mobileDataEnabled ) {
//            Log.d("SplashAct", "Connected = MOBILE");
            checkIntercommPaired();
        } else {
            final AlertMessageButton alert = new AlertMessageButton(this);
            alert.setDialogMessage(getResources().getString(R.string.enableData));
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
