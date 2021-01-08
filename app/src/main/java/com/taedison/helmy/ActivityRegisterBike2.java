package com.taedison.helmy;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.RecyclerView;

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
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.taedison.helmy.Static_AppMethods.checkField;

/***
 * This activity allows the user to enter additional information about his/her bike and the
 * backup password for HelmyM. That information and the information entered in ActivityRegisterBike
 * is then sent to the server and blockchain for unique registration.
 * All data is encrypted except for: alias, mac addresses.
 * bikeIds are sent to the blockchain without encryption. Blockchain is the safest place.
 *
 * LOGIC:
 * Case 1: New HelmyM
 * - As HelmyM is new, it should not have a unique 32-digit bikeId stored, therefore we have to
 * request one from the server
 * - Once the bikeId is received, it is packed in a 16-byte array and send to HelmyM.
 * - then, we send the bikeId to the Blockchain for registration. (A HelmyM can belong to only ONE user)
 * - backup password is written to HelmyM Ble
 * - finally, all data is sent to the server with encryption
 *
 * Case 2: Used HelmyM
 * - As HelmyM is used, it should have a unique 32-digit bikeId stored, we send the bikeId to the
 * Blockchain for registration. (A HelmyM can belong to only ONE user)
 * - then, user is asked if he/she wants to update or keep the previous backup password if it is different.
 * - finally, all data is sent to the server with encryption
 *
 * Case 3: editing HelmyM
 * - If User modified any data about the Bike except for the backup password, then there is no need to connect
 * to Helmy, the data is send to the server.
 *
 * If any of the steps fails (e.g., Ble disconnected or bad internet connection), the user has to try
 * again to repeat the process.
 * Blockchain: as a HelmyM can belong to only ONE user, we have to make sure that the bikeId is not
 * registered in the blockchain or that it belongs to the userId making the request
 */
public class ActivityRegisterBike2 extends AppCompatActivity {

    final String TAG = "registerBike2";

    EditText etBikePlate, etTireWidth, etTirePercentage, etWheelDiameter;
    Spinner spinnerBikePassword1, spinnerBikePassword2, spinnerBikePassword3;
    ProgressBar pbBike2;
    ImageButton imgBtnDelete;

    String nickname, soat, policy2, policy2_phone, brand, chasis;
    String oldPass1, oldPass2, oldPass3;

    ArrayList<String> arrayBikesSaved_MACs, arrayListNumbers;

    //shared preferences
    SingletonSharedPreferences preferences;

    BluetoothAdapter mBluetoothAdapter;
    private AlertList BluetoothDevicesDialog;
    private BLE_HelmyM bluetoothBike;
    BroadcastReceiver blueDeviceConnectionReceiver, blueWritePasswordReceiver, blueWriteBikeIdReceiver;
    final String TAG_receivers = "BLE_receivers";
    boolean helmyM_connected = false;

    int contPassword = 1, contBikeId = 0;

    String primaryBikeSelectedMAC;
    AdapterListDevices adapterBleNames;

    boolean isKeyboardShowing = false;

    private String edit_MAC;

    //Volley
    private RequestQueue requestQueue;

    private byte[] bikeId_bytes;
    private String bikePassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_bike2);

        etBikePlate = findViewById(R.id.etBikePlate);
        etTireWidth = findViewById(R.id.etTireWidth);
        etTirePercentage = findViewById(R.id.etTirePercentage);
        etWheelDiameter = findViewById(R.id.etWheelDiameter);
        spinnerBikePassword1 = findViewById(R.id.spinnerBikePassword1);
        spinnerBikePassword2 = findViewById(R.id.spinnerBikePassword2);
        spinnerBikePassword3 = findViewById(R.id.spinnerBikePassword3);

        imgBtnDelete = findViewById(R.id.imgBtnDeleteBike2);

        pbBike2 = findViewById(R.id.pbRegBike2);
        pbBike2.setVisibility(View.INVISIBLE);

        ImageView circle = findViewById(R.id.circle);
        Static_AppMethods.animateProgressCircle(circle);

        //shared preferences
        preferences = SingletonSharedPreferences.getInstance(this.getApplicationContext()); // for the last user logged in

        arrayBikesSaved_MACs = preferences.get_bikes_saved_MACs();

        //spinner password
        arrayListNumbers = new ArrayList<>();
        for(int c = 0; c <= 9; c++){
            arrayListNumbers.add(String.valueOf(c));
        }
        spinnerBikePassword1.setAdapter(new ClassSpinnerAdapter(this, R.layout.textview_template_spinner, arrayListNumbers, spinnerBikePassword1));
        spinnerBikePassword2.setAdapter(new ClassSpinnerAdapter(this, R.layout.textview_template_spinner, arrayListNumbers, spinnerBikePassword2));
        spinnerBikePassword3.setAdapter(new ClassSpinnerAdapter(this, R.layout.textview_template_spinner, arrayListNumbers, spinnerBikePassword3));

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        arrayBleDevices = new ArrayList<>();

        bluetoothBike = new BLE_HelmyM(this);

        // receiver that listen for ble connection with HelmyM
        blueDeviceConnectionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean connected = intent.getBooleanExtra(Static_AppVariables.INTENTEXTRA_BLE_CONNECTION, false);
                String deviceMAC = intent.getStringExtra(Static_AppVariables.INTENTEXTRA_BLE_MAC);
                Log.d(TAG_receivers, "Device = " + deviceMAC + " connnected " + connected);
                Log.d(TAG_receivers, "Device =" + deviceMAC + "connnected" + connected);

                if (!connected) {
                    Toast.makeText(ActivityRegisterBike2.this, R.string.bluetoothNotFoundTryAgain, Toast.LENGTH_LONG).show();
                    helmyM_connected = false;

                    saving = false;
                    pbBike2.setVisibility(View.INVISIBLE);
                } else {
                    helmyM_connected = true;
                    saving = true; // will start saving data into helmyM, server, and preferences
                    bikeId_bytes = bluetoothBike.getBikeIDbytes();

                    Log.d(TAG_receivers, "Encrypted bytes ID read= " + Arrays.toString(bikeId_bytes) );

                    // if bike does not have an ID, we request a random unique ID from the server and it is sent to HelmyM.
                    // 1. If HelmyM is turned off before sending it complete, HelmyM wont save it (HelmyMs code)
                    // 2. If App is closed before sending it completely, HelmyM wont be save it (HelmyMs code)
                    // After sending BikeID, then all the information will be saved along with the bikeID
                    // 3. If the app is closed before saving into the server, then it does not matter because HelmyM has already a code. User has to try and register it again
                    boolean bikeHasBikeId = false;
                    if(bikeId_bytes.length == 16){
                        for(byte b : bikeId_bytes){
                            if(b != 0){
                                //one of the bytes is different from 0, therefore it has a bikeId saved
                                bikeHasBikeId = true;
                                break;
                            }
                        }
                    }
                    if(bikeHasBikeId){
                        // bike already has a code. Now try to register in Blockchain
                        encryptBikeId_n_registerInBlockchain();
                    } else {
                        StringRequest joRequest = requestBikeCode();
                        requestQueue.add(joRequest);
                    }
                }
            }
        };

        // receiver listens when backup password is written to HelmyM Ble
        blueWritePasswordReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean success = intent.getBooleanExtra(Static_AppVariables.INTENTEXTRA_BIKE_PASSWORD_WRITE, false);
                Log.d(TAG_receivers, "Write was successful: " + success + " cont=" + contPassword);
                if (!success) {
                    Toast.makeText(ActivityRegisterBike2.this, R.string.bluetoothErrorTryAgain, Toast.LENGTH_LONG).show();
                    // user will have to hit on the button to restart writing the password fromActivity the beggining
                    contPassword = 1;
                    saving = false;
                    pbBike2.setVisibility(View.INVISIBLE);
                } else {
                    if(contPassword == 1){
                        // first digit was wrote successfully, then write the second
                        bluetoothBike.bikePassword_2_WriteCharacteristic(spinnerBikePassword2.getSelectedItemPosition());
                        contPassword = 2;
                    } else if(contPassword == 2){
                        // second digit was wrote successfully, then write the third
                        bluetoothBike.bikePassword_3_WriteCharacteristic(spinnerBikePassword3.getSelectedItemPosition());
                        contPassword = 3;
                    } else if(contPassword == 3){
                        // third digit was wrote successfully, the whole password was wrote to BLE_HelmyM successfully
                        // save data to the server and get Unique bike identifier
                        saveBikeData_inServer();
                    }
                }
            }
        };

        // receiver listens when a bikeId byte is written in HelmyM Ble
        blueWriteBikeIdReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean success = intent.getBooleanExtra(Static_AppVariables.INTENTEXTRA_BIKE_ID_WRITE, false);
                Log.d(TAG_receivers, "Write was successful: " + success + " cont=" + contBikeId);
                if (!success) {
                    Toast.makeText(ActivityRegisterBike2.this, R.string.bluetoothErrorTryAgain, Toast.LENGTH_LONG).show();
//                    bluetoothBike.bikePassword_1_WriteCharacteristic(spinnerBikePassword1.getSelectedItemPosition());
                    // user will have to hit on the button to restart writing the password from the beginning
                    contBikeId = 0;
                    saving = false;
                    pbBike2.setVisibility(View.INVISIBLE);
                } else {
                    if(contBikeId < 15){
                        // byte was wrote successfully, then write the next in the array
                        contBikeId ++;
                        byte[] tempByte = {bikeId_bytes[contBikeId]};
                        bluetoothBike.bikeId_write(tempByte, contBikeId);
                    } else {
                        // all bikeID bytes were written successfully. Now try to register in Blockchain with encryption
                        encryptBikeId_n_registerInBlockchain();
                    }
                }
            }
        };

        registerReceiver(blueDeviceConnectionReceiver, new IntentFilter(Static_AppVariables.ACTIONFILTER_GATT_CONNECTION));
        registerReceiver(blueWritePasswordReceiver, new IntentFilter(Static_AppVariables.ACTIONFILTER_BIKE_PASSWORD));
        registerReceiver(blueWriteBikeIdReceiver, new IntentFilter(Static_AppVariables.ACTIONFILTER_BIKE_ID));


        final LinearLayout LLtitle = findViewById(R.id.LLtitle_bike2);
        final ConstraintLayout contentView = findViewById(R.id.CL_registerBike2);
        final ConstraintLayout CL_progress_logo = findViewById(R.id.CL_progress_logo_b2);

        contentView.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        // this is used to hide/show views when the keyboard is hidden/shown.
                        // AdjustPan does not work in activities with no action bar
                        Rect r = new Rect();
                        contentView.getWindowVisibleDisplayFrame(r);
                        int screenHeight = contentView.getRootView().getHeight();

                        // r.bottom is the position above soft keypad or device button.
                        // if keypad is shown, the r.bottom is smaller than that before.
                        int keypadHeight = screenHeight - r.bottom;

                        if (keypadHeight > screenHeight * 0.15) { // 0.15 ratio is perhaps enough to determine keypad height.
                            // keyboard is opened
                            if (!isKeyboardShowing) {
                                isKeyboardShowing = true;
                                CL_progress_logo.setVisibility(View.GONE);
                                LLtitle.setVisibility(View.GONE);
                            }
                        }
                        else {
                            // keyboard is closed
                            if (isKeyboardShowing) {
                                isKeyboardShowing = false;
                                CL_progress_logo.setVisibility(View.VISIBLE);
                                LLtitle.setVisibility(View.VISIBLE);
                            }
                        }
                    }
                });

        // get data entered in the previous activity
        Intent i = getIntent();
        nickname = i.getStringExtra("nickname");
        soat = i.getStringExtra("soat");
        policy2 = i.getStringExtra("2nd_policy");
        if(policy2 == null){
            policy2 = "";
        }
        policy2_phone = i.getStringExtra("2nd_policy_phone");
        if(policy2_phone == null){
            policy2_phone = "";
        }
        brand = i.getStringExtra("brand");
        chasis = i.getStringExtra("chasis");
        if (!TextUtils.isEmpty(i.getStringExtra("placa"))) {
            //if placa was extracted from SOAT using the camera
            etBikePlate.setText(i.getStringExtra("placa"));
        }


        edit_MAC = i.getStringExtra("edit_MAC_bike");
        if(TextUtils.isEmpty(edit_MAC)){
            imgBtnDelete.setVisibility(View.GONE);
        } else {
            if(!TextUtils.isEmpty(preferences.getBikeNickname(edit_MAC))){
                // show data already stored
                try{
                    etBikePlate.setText( preferences.getBikePlate(edit_MAC) );

                    etTireWidth.setText( preferences.getBikeTireWidth(edit_MAC) );
                    etTirePercentage.setText( preferences.getBikeTirePercentage(edit_MAC) );
                    etWheelDiameter.setText( preferences.getBikeWheelDiameter(edit_MAC) );

                    int sel = arrayListNumbers.indexOf( preferences.getBikePass1(edit_MAC) );
                    if (sel > 0) {
                        spinnerBikePassword1.setSelection(sel);
                    }
                    oldPass1 = spinnerBikePassword1.getSelectedItem().toString();

                    Log.d(TAG, "oldpass1= " + oldPass1);

                    sel = arrayListNumbers.indexOf( preferences.getBikePass2(edit_MAC) );
                    if (sel > 0) {
                        spinnerBikePassword2.setSelection(sel);
                    }
                    oldPass2 = spinnerBikePassword2.getSelectedItem().toString();

                    sel = arrayListNumbers.indexOf( preferences.getBikePass3(edit_MAC) );
                    if (sel > 0) {
                        spinnerBikePassword3.setSelection(sel);
                    }
                    oldPass3 = spinnerBikePassword3.getSelectedItem().toString();

                } catch (Exception ignored){}

                primaryBikeSelectedMAC = edit_MAC;
            }
        }

        //volley
        requestQueue = SingletonVolley.getInstance(this.getApplicationContext()).getRequestQueue();
    }

    private void update_keepBikePassword(){
        bikePassword = bluetoothBike.getBikePassword();
        String passwordEntered = spinnerBikePassword1.getSelectedItemPosition() + ";" +
                spinnerBikePassword2.getSelectedItemPosition() + ";" +
                spinnerBikePassword3.getSelectedItemPosition();
        if(bikePassword.equals("0;0;0")){
            // once connected, it must send the password if bike does not have a password
            bluetoothBike.bikePassword_1_WriteCharacteristic(spinnerBikePassword1.getSelectedItemPosition());
        } else if(bikePassword.equals(passwordEntered)){
            // if user entered the same password
            saveBikeData_inServer();
        } else {
            // Bike has a password. Ask the user if he/she wants to updated
            final AlertMessageButton alert = new AlertMessageButton(ActivityRegisterBike2.this);
            alert.setDialogMessage(getResources().getString(R.string.bikeHasPassword) + "\n" + bikePassword );
            alert.setDialogPositiveButton(getResources().getString(R.string.Yes), new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // update password, then write it to the BLE
                    bluetoothBike.bikePassword_1_WriteCharacteristic(spinnerBikePassword1.getSelectedItemPosition());
                    alert.dismissAlert();
                }
            });
            alert.setDialogNegativeButton(getResources().getString(R.string.No), new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // user does not want to update it, then update spinners
                    String[] passwordSegments = bikePassword.split(";");
                    if(passwordSegments.length == 3) {
                        int sel = arrayListNumbers.indexOf( String.valueOf(passwordSegments[0]) );
                        if (sel > 0) {
                            spinnerBikePassword1.setSelection(sel);
                        }
                        sel = arrayListNumbers.indexOf( String.valueOf(passwordSegments[1]) );
                        if (sel > 0) {
                            spinnerBikePassword2.setSelection(sel);
                        }
                        sel = arrayListNumbers.indexOf( String.valueOf(passwordSegments[2]) );
                        if (sel > 0) {
                            spinnerBikePassword3.setSelection(sel);
                        }
                    }
                    alert.dismissAlert();
                    saveBikeData_inServer();
                }
            });
            alert.setCancellable(false);
            alert.showAlert();
        }
    }

    private boolean mScanning;

    ArrayList<String> arrayBleDevices;

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            Log.d("BLEdevices","start scanning");
            // Stops scanning after a pre-defined scan period.
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(leScanCallback);
                    BluetoothDevicesDialog.setDialogMessage(R.string.ChooseNewDevice);
                    BluetoothDevicesDialog.setDialogPositiveButton(getResources().getString(R.string.retry), new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Log.d("RegisterBike", "Reintentar");
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    BluetoothDevicesDialog.dismissAlert();
                                }
                            });
                            displayListBluetoothDevices();
                        }
                    });
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(leScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(leScanCallback);
            BluetoothDevicesDialog.setDialogMessage(R.string.ChooseNewDevice);
        }

    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback leScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi,
                                     byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String device2add = device.getName() + ";" + device.getAddress();
                            Log.d("BleFound", device2add + " " + arrayBleDevices.indexOf(device2add)
                                    + " " + arrayBleDevices.indexOf(device2add));
                            //check if device2add was already listed and if it is the primaryHelmet, bike or pillion and that the name is HELMY
                            if(device.getName() != null
                                    && arrayBleDevices.indexOf(device2add) < 0
                                    && arrayBikesSaved_MACs.indexOf(device.getAddress()) < 0
                                    && device2add.contains("HELMYM") ){

                                arrayBleDevices.add(device2add);
                                adapterBleNames.notifyDataSetChanged();
                            }
                        }
                    });
                }
            };

    private void displayListBluetoothDevices() {

        arrayBleDevices.clear();
        if(mScanning){
            scanLeDevice(false);
            scanLeDevice(true);
        } else {
            scanLeDevice(true);
        }

        adapterBleNames = new AdapterListDevices(arrayBleDevices, this);
        BluetoothDevicesDialog = new AlertList(this);
        BluetoothDevicesDialog.setRecyclerView(adapterBleNames);
        BluetoothDevicesDialog.setDialogMessage(getResources().getString(R.string.searchingHelmy));

        final RecyclerView recyclerView = BluetoothDevicesDialog.getRecyclerView();
        adapterBleNames.setOnItemClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int position = recyclerView.getChildAdapterPosition(view);
                mBluetoothAdapter.cancelDiscovery();
                String nameMAC = arrayBleDevices.get(position);
                String[] separated = nameMAC.split(";");
                BluetoothDevice deviceSelected = mBluetoothAdapter.getRemoteDevice(separated[1]);
                primaryBikeSelectedMAC = deviceSelected.getAddress();

                Toast.makeText(ActivityRegisterBike2.this, R.string.connecting, Toast.LENGTH_SHORT).show();
                bluetoothBike.initiateBluetoothConexion(deviceSelected);

                pbBike2.setVisibility(View.VISIBLE);

                BluetoothDevicesDialog.dismissAlert();
            }
        });
        BluetoothDevicesDialog.showAlert();
    }

    boolean saving;
    public void pairWithBike(View view) {
        checkField(etBikePlate);
        checkField(etTireWidth);
        checkField(etTirePercentage);
        checkField(etWheelDiameter);
        checkField(spinnerBikePassword1);
        checkField(spinnerBikePassword2);
        checkField(spinnerBikePassword3);

        if( TextUtils.isEmpty(etBikePlate.getText())
                || TextUtils.isEmpty(etTireWidth.getText().toString())
                || TextUtils.isEmpty(etTirePercentage.getText().toString())
                || TextUtils.isEmpty(etWheelDiameter.getText().toString())
                || spinnerBikePassword1.getSelectedItemPosition() == 0
                || spinnerBikePassword2.getSelectedItemPosition() == 0
                || spinnerBikePassword3.getSelectedItemPosition() == 0) {
            Toast.makeText(this, R.string.AllFieldsAreRequired, Toast.LENGTH_SHORT).show();
        } else if( etBikePlate.getText().toString().length() != 6 ){
            etBikePlate.setBackgroundResource(R.drawable.redcontour_rounded);
            Toast.makeText(this, getResources().getString(R.string.mustBe6CharactersLong), Toast.LENGTH_SHORT).show();
        } else if( !etTireWidth.getText().toString().matches("[0-9]+") ||
                !etTirePercentage.getText().toString().matches("[0-9]+") ||
                !etWheelDiameter.getText().toString().matches("[0-9]+")){
            Toast.makeText(this, getResources().getString(R.string.justNumbers), Toast.LENGTH_SHORT).show();
        } else if(nickname.equals(preferences.getBikeNickname(primaryBikeSelectedMAC))
                && soat.equals(preferences.getBikeSOAT(primaryBikeSelectedMAC))
                && policy2.equals(preferences.getBike2ndPolicy(primaryBikeSelectedMAC))
                && policy2_phone.equals(preferences.getBike2ndPolicyPhone(primaryBikeSelectedMAC))
                && brand.equals(preferences.getBikeBrand(primaryBikeSelectedMAC))
                && chasis.equals(preferences.getBikeChasis(primaryBikeSelectedMAC))
                && etBikePlate.getText().toString().equals(preferences.getBikePlate(primaryBikeSelectedMAC))
                && etTireWidth.getText().toString().equals(preferences.getBikeTireWidth(primaryBikeSelectedMAC))
                && etTirePercentage.getText().toString().equals(preferences.getBikeTirePercentage(primaryBikeSelectedMAC))
                && etWheelDiameter.getText().toString().equals(preferences.getBikeWheelDiameter(primaryBikeSelectedMAC))
                && spinnerBikePassword1.getSelectedItem().toString().equals(preferences.getBikePass1(primaryBikeSelectedMAC))
                && spinnerBikePassword2.getSelectedItem().toString().equals(preferences.getBikePass2(primaryBikeSelectedMAC))
                && spinnerBikePassword3.getSelectedItem().toString().equals(preferences.getBikePass3(primaryBikeSelectedMAC))
        ){
            // user did not change any data
            Log.d("actregisterbike", "did not changed");
            Go2NextActivity();
        } else {
            Log.d("actregisterbike", "helmyM_connected= " + helmyM_connected + " edit_MAC= " +
                    edit_MAC );

            if(!saving) {
                Log.d("actregisterbike", "entered connected");
                checkLocationPermission();
            }
        }
    }

    private void checkBluetoothON() {
        if (mBluetoothAdapter.isEnabled()) {
            checkGPS();
        } else {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, Static_AppVariables.REQUESTCODE_TURNON_BLUETOOTH);
        }
    }

    int REQUEST_ENABLE_GPS = 101;
    void checkGPS() {
        LocationRequest locationRequest = LocationRequest.create();

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);

        SettingsClient settingsClient = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = settingsClient.checkLocationSettings(builder.build());

        task.addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                Log.d("LocRequest", "OnSuccess");
                // GPS is ON
                list_connect_or_update();
            }
        });

        task.addOnFailureListener(this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull final Exception e) {
                Log.d("LocRequest", "GPS off");
                // GPS off
                if (e instanceof ResolvableApiException) {
                    ResolvableApiException resolvable = (ResolvableApiException) e;
                    try {
                        resolvable.startResolutionForResult(ActivityRegisterBike2.this, REQUEST_ENABLE_GPS);
                    } catch (IntentSender.SendIntentException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if(requestCode == Static_AppVariables.REQUESTCODE_TURNON_BLUETOOTH) {
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled
                pairWithBike(null); // check conditions again to pair
            } else {
                Static_AppMethods.launchAlertBluetooth(this, this, Static_AppVariables.REQUESTCODE_TURNON_BLUETOOTH);
            }
        } else if(requestCode == REQUEST_ENABLE_GPS) {
            switch (resultCode) {
                case Activity.RESULT_OK:
                    // GPS was turned on
                    pairWithBike(null); // check conditions again to pair
                    break;
                case Activity.RESULT_CANCELED:
                    final AlertMessageButton alert = new AlertMessageButton(this);
                    alert.setDialogMessage(getResources().getString(R.string.whyGPSmustBeOnForRegister));
                    alert.setDialogPositiveButton(getResources().getString(R.string.Ok), new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            checkGPS();
                            alert.dismissAlert();
                        }
                    });
                    alert.setDialogNegativeButton(getResources().getString(R.string.No), new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            onBackPressed();
                            alert.dismissAlert();
                        }
                    });
                    alert.hideCancelButton();
                    alert.showAlert();
                    break;
                default:
                    break;
            }
        }

    }

    private void list_connect_or_update(){
        if (TextUtils.isEmpty(edit_MAC)) {
            final AlertMessageButton alert = new AlertMessageButton(this);
            alert.setDialogMessage(getResources().getString(R.string.reasonToDisplayHelmyMList));
            alert.setDialogPositiveButton(getResources().getString(R.string.Ok), new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // first add the BLE and then the intercom bluetooth
                    alert.dismissAlert();
                    displayListBluetoothDevices();
                    Log.d("actregisterbike", "displayBLEs");
                }
            });
            alert.hideCancelButton();
            alert.showAlert();
        } else {
            // user want to edit HelmyM
            Log.d("actregisterbike", "primaryBikeSelectedMAC= " + primaryBikeSelectedMAC);

            if (oldPass1.equals(String.valueOf(spinnerBikePassword1.getSelectedItemPosition()))
                    && oldPass2.equals(String.valueOf(spinnerBikePassword2.getSelectedItemPosition()))
                    && oldPass3.equals(String.valueOf(spinnerBikePassword3.getSelectedItemPosition()))) {
                // user did not change password, then there is no need to connect
                // to the bike, just save in server and then in preferences
                bikeId_bytes = string32_toBytes(preferences.getBikeId(edit_MAC));
                saveBikeData_inServer();
            } else {
                Toast.makeText(ActivityRegisterBike2.this, R.string.connecting, Toast.LENGTH_SHORT).show();
                bluetoothBike.initiateBluetoothConexion(mBluetoothAdapter.getRemoteDevice(edit_MAC));
            }
            pbBike2.setVisibility(View.VISIBLE);
        }
    }

    private void saveBikeData_inServer() {
        String plate = etBikePlate.getText().toString();
        String wheelRef = etTireWidth.getText().toString() + ";" +
                etTirePercentage.getText().toString() + ";" +
                etWheelDiameter.getText().toString();
        String password123 = spinnerBikePassword1.getSelectedItemPosition() + ";" +
                spinnerBikePassword2.getSelectedItemPosition() + ";" +
                spinnerBikePassword3.getSelectedItemPosition();

        try {
            String email = preferences.get_lastUser_email_logged();
            String soatEncrypted = Static_AppMethods.encryptAES_toString64(this,
                    soat, email, preferences);
            String policy2Encrypted = Static_AppMethods.encryptAES_toString64(this,
                    policy2, email, preferences);
            String policy2phoneEncrypted = Static_AppMethods.encryptAES_toString64(this,
                    policy2_phone, email, preferences);
            String brandEncrypted = Static_AppMethods.encryptAES_toString64(this,
                    brand, email, preferences);
            String chasisEncrypted = Static_AppMethods.encryptAES_toString64(this,
                    chasis, email, preferences);
            String plateEncrypted = Static_AppMethods.encryptAES_toString64(this,
                    plate, email, preferences);
            String wheelRefEncrypted = Static_AppMethods.encryptAES_toString64(this,
                    wheelRef, email, preferences);
            String password123Encrypted = Static_AppMethods.encryptAES_toString64(this,
                    password123, email, preferences);
            String bikeIdEncrypted = Static_AppMethods.encryptAES_toString64(this,
                    bikeIdbytes_toString32_decrypted(), email, preferences);

            StringRequest joRequest = requestBike(nickname, soatEncrypted, policy2Encrypted,
                    policy2phoneEncrypted, brandEncrypted, chasisEncrypted, plateEncrypted,
                    wheelRefEncrypted, password123Encrypted, bikeIdEncrypted);
            requestQueue.add(joRequest);
        } catch (Exception e) {
            Log.e(TAG+"Encrypt", "Encryption Error= " + e.getMessage());
            Static_AppMethods.ToastEncryptionError(this);
        }
    }

    private void Go2NextActivity(){
        Intent intent;
        if( preferences.didUserRegister_data_devices() ){
            intent = new Intent(ActivityRegisterBike2.this, ActivityChooseDevices.class);
        } else {
            intent = new Intent(ActivityRegisterBike2.this, ActivityProgress.class);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    public void whatIsWheelRef(View view) {
        final AlertImage alert = new AlertImage(this);
        alert.setDialogMessage(getResources().getString(R.string.whatIsWheelReference));
        alert.setDialogImage(getResources().getDrawable(R.drawable.wheel_reference));
        alert.setDialogPositiveButton(getResources().getString(R.string.Ok), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alert.dismissAlert();
            }
        });
        alert.showAlert();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // check discovery is off, otherwise cancel
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }

        bluetoothBike.finishConnection();

        unregisterReceiver(blueDeviceConnectionReceiver);
        unregisterReceiver(blueWritePasswordReceiver);
        unregisterReceiver(blueWriteBikeIdReceiver);
    }

    public void whatIsLockPassword(View view) {

        final AlertImage alert = new AlertImage(this);
        alert.setDialogMessage(getResources().getString(R.string.whatIsLockPassword));
        alert.setDialogImage(getResources().getDrawable(R.drawable.helmym_gray));
        alert.setDialogPositiveButton(getResources().getString(R.string.Ok), new View.OnClickListener() {
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

    public void click_deleteBike(View view) {
        if(!TextUtils.isEmpty(edit_MAC)){
            lauchAlertDeleteBike();
        }
    }

    private void lauchAlertDeleteBike(){
        final AlertMessageButton alert = new AlertMessageButton(this);
        alert.setDialogMessage(getResources().getString(R.string.areYouSureToDelete));
        alert.setDialogPositiveButton(getResources().getString(R.string.Yes), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pbBike2.setVisibility(View.VISIBLE);
                if( !TextUtils.isEmpty( preferences.getBikeId(edit_MAC)) ){
                    deleteBikeFromBlockchain();
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

    private StringRequest requestBike(final String alias, final String soat, final String privatePolicy,
                                      final String privatePolicyPhone, final String brand,
                                      final String chasis, final String plate,
                                      final String wheelRef, final String password123, final String bikeCode){
        String url;
        if(preferences.get_isPrimaryServerDown()){
            // primary server is down, so we will use the secondary
            url = Static_AppVariables.url_motorcycle2;
        } else {
            url = Static_AppVariables.url_motorcycle;
        }


        return new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response)
                    {
                        Log.d(TAG, "requestBike response= " + response);
                        if( !TextUtils.isEmpty(response) ) {
                            try {
                                JSONObject jsonArray = new JSONObject(response);
                                String status = jsonArray.getString("status");
                                if (status.equals("2")) {
                                    //data was updated. Continue to update wheelref and password
                                    Toast.makeText(getApplicationContext(), R.string.dataUpdated, Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(getApplicationContext(), R.string.dataSaved, Toast.LENGTH_SHORT).show();
                                }
                                // set it as primary bike
                                preferences.saveBikePreferences(primaryBikeSelectedMAC);
                                preferences.savePrimaryBikePreferences(primaryBikeSelectedMAC);

                                preferences.saveBikePlate_encrypted(primaryBikeSelectedMAC, plate);

                                preferences.saveBikeId_encrypted(primaryBikeSelectedMAC, bikeCode);
                                preferences.setBikeNickname(primaryBikeSelectedMAC, nickname);
                                preferences.saveBikeSOAT_encrypted(primaryBikeSelectedMAC, soat);
                                preferences.saveBike2ndPolicy_encrypted(primaryBikeSelectedMAC, privatePolicy);
                                preferences.saveBike2ndPolicyPhone_encrypted(primaryBikeSelectedMAC, privatePolicyPhone);
                                preferences.saveBikeBrand_encrypted(primaryBikeSelectedMAC, brand);
                                preferences.saveBikeChasis_encrypted(primaryBikeSelectedMAC, chasis);

                                try {
                                    String email = preferences.get_lastUser_email_logged();
                                    String widthEncrypted = Static_AppMethods.encryptAES_toString64(ActivityRegisterBike2.this,
                                            etTireWidth.getText().toString(), email, preferences);
                                    String percentageEncrypted = Static_AppMethods.encryptAES_toString64(ActivityRegisterBike2.this,
                                            etTirePercentage.getText().toString(), email, preferences);
                                    String diameterEncrypted = Static_AppMethods.encryptAES_toString64(ActivityRegisterBike2.this,
                                            etWheelDiameter.getText().toString(), email, preferences);
                                    String pass1Encrypted = Static_AppMethods.encryptAES_toString64(ActivityRegisterBike2.this,
                                            spinnerBikePassword1.getSelectedItem().toString(), email, preferences);
                                    String pass2Encrypted = Static_AppMethods.encryptAES_toString64(ActivityRegisterBike2.this,
                                            spinnerBikePassword2.getSelectedItem().toString(), email, preferences);
                                    String pass3Encrypted = Static_AppMethods.encryptAES_toString64(ActivityRegisterBike2.this,
                                            spinnerBikePassword3.getSelectedItem().toString(), email, preferences);

                                    preferences.saveBikeTireWidth_encrypted( primaryBikeSelectedMAC, widthEncrypted );
                                    preferences.saveBikeTirePercentage_encrypted( primaryBikeSelectedMAC, percentageEncrypted );
                                    preferences.saveBikeWheelDiameter_encrypted( primaryBikeSelectedMAC, diameterEncrypted );

                                    preferences.saveBikePass1_encrypted(primaryBikeSelectedMAC, pass1Encrypted );
                                    preferences.saveBikePass2_encrypted(primaryBikeSelectedMAC, pass2Encrypted);
                                    preferences.saveBikePass3_encrypted(primaryBikeSelectedMAC, pass3Encrypted );
                                    //we finished saving data
                                    Go2NextActivity();

                                } catch (Exception e) {
                                    Log.e(TAG+"Encrypt", "Encryption Error= " + e.getMessage());
                                    Static_AppMethods.ToastEncryptionError(ActivityRegisterBike2.this);
                                    bluetoothBike.finishConnection();
                                }
                            } catch (JSONException e) {
                                Log.e(TAG, "requestBike Error= " + e.getMessage());
                                Static_AppMethods.ToastTryAgain(ActivityRegisterBike2.this);
                                bluetoothBike.finishConnection();
                            }
                        } else {
                            // most likely the user has mobile data on but without internet service. Worst case, something went wrong with the server
                            Static_AppMethods.ToastCheckYourInternet(ActivityRegisterBike2.this);
                        }
                        saving = false;
                        pbBike2.setVisibility(View.INVISIBLE);
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                        saving = false;
                        pbBike2.setVisibility(View.INVISIBLE);
                        Log.d("RequestVolleyReg2", error.toString());
                        Static_AppMethods.ToastCheckYourInternet(ActivityRegisterBike2.this);
                        bluetoothBike.finishConnection();
                        Static_AppMethods.checkResponseCode(error, preferences);
                    }
                })
        {
            @Override
            protected Map<String, String> getParams()
            {
                Log.d("RequestVolleyReg1", "userId= " + preferences.get_lastUser_Id_logged());
                Map<String, String> params = new HashMap<>();
                params.put("userId", preferences.get_lastUser_Id_logged());
                params.put("alias", alias);
                params.put("policySoat", soat);
                params.put("noPolicyTwo", privatePolicy);
                params.put("policyTelephoneTwo", privatePolicyPhone);
                params.put("brand", brand);
                params.put("chassis", chasis);
                params.put("plate", plate);
                params.put("threeDigitsWheelReference", wheelRef);
                params.put("threeDigitsBackupPowerKey", password123);
                params.put("mac", primaryBikeSelectedMAC);
                params.put("codeM", bikeCode); // it is encrypted
                Log.d(TAG, "params requestBike: " + params.toString() );
                return params;
            }
        };
    }

    private StringRequest requestBikeCode(){
        String url;
        if(preferences.get_isPrimaryServerDown()){
            // primary server is down, so we will use the secondary
            url = Static_AppVariables.url_motorcycle2;
        } else {
            url = Static_AppVariables.url_motorcycle;
        }

        return new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response)
                    {
                        Log.d(TAG, "requestBikeCode response: " + response);
                        if( !TextUtils.isEmpty(response) ) {
                            try {
                                JSONObject jsonArray = new JSONObject(response);
                                String status = jsonArray.getString("status");
                                if(status.length() > 1){
                                    // the id received from the server is unique and it is 32 digit long,
                                    // we have to partition it to 16 bytes and send it to HelmyM
                                    bikeId_bytes = string32_toBytes(status);
                                    // start writing the first segment of bikeID created here
                                    byte[] tempByte = {bikeId_bytes[0]};
                                    bluetoothBike.bikeId_write(tempByte, 0);
                                }
                            } catch (JSONException e) {
                                Static_AppMethods.ToastCheckYourInternet(ActivityRegisterBike2.this);
                                bluetoothBike.finishConnection();
                                pbBike2.setVisibility(View.GONE);
                                saving = false;
                            }
                        } else {
                            // most likely the user has mobile data on but without internet service. Worst case, something went wrong with the server
                            Static_AppMethods.ToastCheckYourInternet(ActivityRegisterBike2.this);
                            bluetoothBike.finishConnection();
                            pbBike2.setVisibility(View.GONE);
                            saving = false;
                        }

                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                        saving = false;
                        pbBike2.setVisibility(View.INVISIBLE);
                        bluetoothBike.finishConnection();
                        Log.d("RequestVolleyReg1", error.toString());
                        Static_AppMethods.ToastCheckYourInternet(ActivityRegisterBike2.this);
                        Static_AppMethods.checkResponseCode(error, preferences);
                    }
                })
        {
            @Override
            protected Map<String, String> getParams()
            {
                Log.d("RequestVolleyReg1", "userId= " + preferences.get_lastUser_Id_logged());
                Map<String, String> params = new HashMap<>();
                params.put("code", preferences.get_lastUser_Id_logged());

                return params;
            }
        };
    }

    private void requestDeleteBike(){
        String url;
        if(preferences.get_isPrimaryServerDown()){
            // primary server is down, so we will use the secondary
            url = Static_AppVariables.url_motorcycle2;
        } else {
            url = Static_AppVariables.url_motorcycle;
        }

        StringRequest request = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response)
                    {
                        Log.d(TAG, "requestDeleteBike response: " + response);
                        pbBike2.setVisibility(View.GONE);
                        if( !TextUtils.isEmpty(response) ){
                            preferences.deleteBikeFromPreferences(edit_MAC);
                            Go2NextActivity();
                        } else {
                            // most likely the user has mobile data on but without internet service. Worst case, something went wrong with the server
                            Static_AppMethods.ToastCheckYourInternet(ActivityRegisterBike2.this);
                        }
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                        saving = false;
                        pbBike2.setVisibility(View.INVISIBLE);
                        Log.d(TAG, error.toString());
                        Static_AppMethods.ToastCheckYourInternet(ActivityRegisterBike2.this);
                        bluetoothBike.finishConnection();
                        Static_AppMethods.checkResponseCode(error, preferences);
                    }
                })
        {
            @Override
            protected Map<String, String> getParams()
            {
                Log.d(TAG, "userId= " + preferences.get_lastUser_Id_logged());
                Map<String, String> params = new HashMap<>();
                params.put("userId", preferences.get_lastUser_Id_logged());
                params.put("mac", primaryBikeSelectedMAC);
                Log.d(TAG, "params requestDeleteBike: " + params.toString() );
                return params;
            }
        };

        requestQueue.add(request);
    }

    public void deleteBikeFromBlockchain() {
        String url;
        if(preferences.get_isPrimaryServerDown()){
            // primary server is down, so we will use the secondary
            url = Static_AppVariables.url_blockchainDelete_fromBikeID2;
        } else {
            url = Static_AppVariables.url_blockchainDelete_fromBikeID;
        }
        StringRequest request = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response) {
                        Log.d(TAG_blockchain, "Deletion response: " + response);
                        if( !TextUtils.isEmpty(response) ){
                            try {
                                JSONObject jsonArray = new JSONObject(response);
                                String status = jsonArray.getString("status");
                                if(status.equals("1")){
                                    // bike is being deleted in blockchain, now delete from server
                                    final AlertMessageButton alert = new AlertMessageButton(ActivityRegisterBike2.this);
                                    alert.setDialogMessage(getResources().getString(R.string.deletingFromBlockchain));
                                    alert.setDialogPositiveButton(getResources().getString(R.string.Ok),
                                            new View.OnClickListener() {
                                                @Override
                                                public void onClick(View view) {
                                                    requestDeleteBike();
                                                    alert.dismissAlert();
                                                }
                                            });
                                    alert.setCancellable(false);
                                    alert.hideCancelButton();
                                    alert.showAlert();

                                } else if(status.equals("2")) {
                                    Toast.makeText(ActivityRegisterBike2.this, getResources().
                                            getString(R.string.bikeNotRegisteredInBlockchain), Toast.LENGTH_SHORT).show();
                                    requestDeleteBike();
                                    pbBike2.setVisibility(View.GONE);
                                    bluetoothBike.finishConnection();
                                } else {
                                    Toast.makeText(ActivityRegisterBike2.this, getResources().
                                            getString(R.string.errorTryAgain), Toast.LENGTH_SHORT).show();
                                    pbBike2.setVisibility(View.GONE);
                                    bluetoothBike.finishConnection();
                                }
                            } catch (Exception ignored){
                                Static_AppMethods.ToastTryAgain(ActivityRegisterBike2.this);
                                bluetoothBike.finishConnection();
                                pbBike2.setVisibility(View.GONE);
                            }
                        } else {
                            // most likely the user has mobile data on but without internet service. Worst case, something went wrong with the server
                            Static_AppMethods.ToastCheckYourInternet(ActivityRegisterBike2.this);
                            pbBike2.setVisibility(View.GONE);
                        }
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                        Log.e(TAG_blockchain, error.toString());
                        Static_AppMethods.ToastCheckYourInternet(ActivityRegisterBike2.this);
                        pbBike2.setVisibility(View.GONE);
                        bluetoothBike.finishConnection();
                        Static_AppMethods.checkResponseCode(error, preferences);
                    }
                })
        {
            @Override
            protected Map<String, String> getParams()
            {
                Map<String, String> params = new HashMap<>();
                params.put("bikeId", preferences.getBikeId(edit_MAC));
                Log.d(TAG_blockchain, "params delete" + params.toString());
                return params;
            }
        };

        requestQueue.add(request);
    }

    private void encryptBikeId_n_registerInBlockchain(){
        // we have to remove the ==\n in the bikeId because NodeJS does not accept it
        //  we do not encrypt brand, chasis, plate because blockchain helmy website is not fully ready
        registerBikeInBlockchain(bikeIdbytes_toString32_decrypted(),
                brand, chasis, etBikePlate.getText().toString());
    }

    private String bikeIdbytes_toString32_decrypted(){
        // join the 16 bytes to form a 32 digit number (generated by server)
        StringBuilder bikeId = new StringBuilder();
        for (byte bikeId_byte : bikeId_bytes) {
            if(bikeId_byte <= 9){
                // when it only has one digit, add a 0
                bikeId.append("0").append(bikeId_byte);
            } else {
                bikeId.append(bikeId_byte);
            }
        }
        Log.d(TAG, "bikeIdbytes_toString32= " + bikeId.toString() +"\nBytes= " + Arrays.toString(bikeId_bytes));
        return bikeId.toString();
    }

    private byte[] string32_toBytes(String bikeId_32){
        // first add zeros to complete 32 digits
        int numZeros = 32 - bikeId_32.length();
        String bikeId = "";
        for(int i=0; i<numZeros; i++){
            bikeId = "0" + bikeId;
        }
        bikeId = bikeId + bikeId_32;
        // now form the byte array to send to HelmyM
        try{
            byte[] temp = new byte[bikeId.length()/2];
            int arrayIdx = 0;
            for(int idx = 0; idx < bikeId.length(); idx += 2 ){
                String value0 = String.valueOf(bikeId.charAt(idx));
                String value1 = String.valueOf(bikeId.charAt(idx+1));
                temp[arrayIdx] = (byte) Integer.parseInt( value0 + value1 );
                arrayIdx++;
            }
            Log.d(TAG, "string32_toBytes= " + bikeId_32 +"\nBytes= " + Arrays.toString(temp));
            return temp;
        } catch (Exception ignored){
            // there was an error, probably parsing
            Log.e(TAG+"encrypt", "string32_toBytes Error: " + ignored.getMessage());
            Static_AppMethods.ToastTryAgain(ActivityRegisterBike2.this);
        }
        return new byte[16]; // return empty byte array by default
    }

    final String TAG_blockchain = TAG + "Blockchain";
    private void registerBikeInBlockchain(final String bikeId, final String brand, final String chassis, final String plate) {
        String url;
        if(preferences.get_isPrimaryServerDown()){
            // primary server is down, so we will use the secondary
            url = Static_AppVariables.url_blockchainRegister2;
        } else {
            url = Static_AppVariables.url_blockchainRegister;
        }

        StringRequest request = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response) {
                        Log.d(TAG_blockchain, "Registration response: " + response);
                        if( !TextUtils.isEmpty(response) ){
                            try {
                                JSONObject jsonArray = new JSONObject(response);
                                String status = jsonArray.getString("status");
                                if(status.equals("1")){
                                    // bike is being registered in blockchain. Now check if user wants to update or keep the password
                                    final AlertMessageButton alert = new AlertMessageButton(ActivityRegisterBike2.this);
                                    alert.setDialogMessage(getResources().getString(R.string.registeringInBlockchain));
                                    alert.setDialogPositiveButton(getResources().getString(R.string.Ok),
                                            new View.OnClickListener() {
                                                @Override
                                                public void onClick(View view) {
                                                    update_keepBikePassword();
                                                    alert.dismissAlert();
                                                }
                                            });
                                    alert.setCancellable(false);
                                    alert.hideCancelButton();
                                    alert.showAlert();

                                } else if(status.equals("2")) {
                                    getUserId_fromBikeId_blockchain(bikeId);
                                } else {
                                    Toast.makeText(ActivityRegisterBike2.this, getResources().
                                            getString(R.string.errorTryAgain), Toast.LENGTH_SHORT).show();
                                    saving = false;
                                    pbBike2.setVisibility(View.GONE);
                                    bluetoothBike.finishConnection();
                                }
                            } catch (Exception ignored){
                                Static_AppMethods.ToastTryAgain(ActivityRegisterBike2.this);
                                bluetoothBike.finishConnection();
                                pbBike2.setVisibility(View.GONE);
                            }
                        } else {
                            // most likely the user has mobile data on but without internet service. Worst case, something went wrong with the server
                            Static_AppMethods.ToastCheckYourInternet(ActivityRegisterBike2.this);
                            pbBike2.setVisibility(View.GONE);
                        }
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                        Log.e(TAG_blockchain, error.toString());
                        Static_AppMethods.ToastCheckYourInternet(ActivityRegisterBike2.this);
                        Static_AppMethods.checkResponseCode(error, preferences);
                        saving = false;
                        pbBike2.setVisibility(View.GONE);
                        bluetoothBike.finishConnection();
                    }
                })
        {
            @Override
            protected Map<String, String> getParams()
            {
                Map<String, String> params = new HashMap<>();
                params.put("userId", preferences.get_lastUser_Id_logged());
                params.put("bikeId", bikeIdbytes_toString32_decrypted() ); // blockcahin stores the bikeIds without encryption
                params.put("names", preferences.getUserNames());
                params.put("surnames", preferences.getUserSurnames());
                params.put("brand", brand);
                params.put("chassis", chassis);
                params.put("plate", plate);
                Log.d(TAG_blockchain, "params register: " + params.toString() );
                return params;
            }
        };

        requestQueue.add(request);
    }

    public void getUserId_fromBikeId_blockchain(final String bikeId) {
        String url;
        if(preferences.get_isPrimaryServerDown()){
            // primary server is down, so we will use the secondary
            url = Static_AppVariables.url_blockchainGetUserIDfromBikeID2;
        } else {
            url = Static_AppVariables.url_blockchainGetUserIDfromBikeID;
        }

        StringRequest strRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response) {
                        Log.d(TAG_blockchain, "getUserId_fromBikeId response: " + response);
                        if( !TextUtils.isEmpty(response) ){
                            try {
                                JSONObject jsonArray = new JSONObject(response);
                                String status = jsonArray.getString("status");
                                if(status.equals("0")){
                                    Toast.makeText(ActivityRegisterBike2.this, getResources().getString
                                            (R.string.errorTryAgain), Toast.LENGTH_SHORT).show();
                                    saving = false;
                                    pbBike2.setVisibility(View.GONE);
                                    bluetoothBike.finishConnection();
                                } else {
                                    if(status.equals("2")){
                                        Toast.makeText(ActivityRegisterBike2.this, getResources().getString
                                                (R.string.bikeNotRegisteredInBlockchain), Toast.LENGTH_SHORT).show();
                                        saving = false;
                                        pbBike2.setVisibility(View.GONE);
                                        bluetoothBike.finishConnection();
                                    } else {
                                        if(status.equals(preferences.get_lastUser_Id_logged())){
                                            // bike already registered in blockchain but belongs to the same user
                                            update_keepBikePassword();
                                        } else {
                                            Toast.makeText(ActivityRegisterBike2.this, getResources().getString
                                                    (R.string.bikeAlreadyRegisteredInBlockchain), Toast.LENGTH_SHORT).show();
                                            saving = false;
                                            pbBike2.setVisibility(View.GONE);
                                            bluetoothBike.finishConnection();
                                        }
                                    }
                                }
                            } catch (Exception ignored){
                                Static_AppMethods.ToastTryAgain(ActivityRegisterBike2.this);
                                pbBike2.setVisibility(View.GONE);
                            }
                        } else {
                            // most likely the user has mobile data on but without internet service. Worst case, something went wrong with the server
                            Static_AppMethods.ToastCheckYourInternet(ActivityRegisterBike2.this);
                            pbBike2.setVisibility(View.GONE);
                        }
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                        Log.e(TAG_blockchain, error.toString());
                        Static_AppMethods.ToastCheckYourInternet(ActivityRegisterBike2.this);
                        Static_AppMethods.checkResponseCode(error, preferences);
                        saving = false;
                        pbBike2.setVisibility(View.GONE);
                        bluetoothBike.finishConnection();
                    }
                })
        {
            @Override
            protected Map<String, String> getParams()
            {
                Log.d(TAG_blockchain, "params loaded");
                Map<String, String> params = new HashMap<>();
                params.put("bikeId", bikeIdbytes_toString32_decrypted() ); // blockcahin stores the bikeIds without encryption
                Log.d(TAG_blockchain, "params getUserId_fromBikeId: " + params.toString() );
                return params;
            }
        };

        requestQueue.add(strRequest);
    }

    private void checkLocationPermission() {
        if (Build.VERSION.SDK_INT >= 23 && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) {
            // display reason for permissions
            launchAlertExplanationForLocation();
        } else {
            // check the rest of the conditions
            checkBluetoothON();
        }
    }

    private void requestLocationPermission() {
        // SMS and Location permissions are mandatory, it will continue asking until user grants the permissions
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                Static_AppVariables.REQUESTCODE_LOCATION_PERMISSION);
    }

    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (requestCode == Static_AppVariables.REQUESTCODE_LOCATION_PERMISSION && permissions.length > 0) {
            // access_fine_location incluye el permiso de coarse location
            if (grantResults.length <= 0 ||
                    grantResults[0] == PackageManager.PERMISSION_DENIED  //fine_location
            ) {

                if (Build.VERSION.SDK_INT >= 23) {
                    // in version above 23 user can reject permission and request not be asked again
                    boolean showRationale = shouldShowRequestPermissionRationale(permissions[0]);
                    if (!showRationale) {
                        // user denied permission and CHECKED "never ask again"
                        launchAlertActivatePersimissionManually();
                    } else {
                        // check the rest of the conditions
                        checkBluetoothON();
                    }
                } else {
                    // check the rest of the conditions
                    checkBluetoothON();
                }

            } else {
                // check the rest of the conditions
                checkBluetoothON();
            }
        }
    }

    private void launchAlertActivatePersimissionManually() {
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
                alert.dismissAlert();
            }
        });
        alert.showAlert();
    }

    private void launchAlertExplanationForLocation() {
        final AlertMessageButton alert = new AlertMessageButton(this);
        alert.setDialogMessage(getResources().getString(R.string.locationPermissionExplanationRegister));
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
}

