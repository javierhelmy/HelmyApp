package com.taedison.helmy;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.taedison.helmy.Static_AppMethods.checkField;

/***
 * User enters the data about its helmet.
 * Data is encrypted, except for: alias and MAC addresses
 * Unlike HelmyM, HelmyCs can belong to multiple users
 *
 * LOGIC:
 * 1. ask user to select HelmyC Ble for connecting
 * 2. Once connected, user enters the information
 * 3. Selects the bluetooth that corresponds to the user's HelmyC
 * 4. Sends data to the server with encryption
 */
public class ActivityRegisterHelmet extends AppCompatActivity {
    final String TAG = "regHelmet";

    EditText etHelmetNickname;
    ProgressBar pbHelmet;
    SeekBar seekBarPallete;
    ImageButton imgBtnDelete;
    ScrollView scrollViewFields;
    TextView tvBtnManual;

    //shared preferences
    SingletonSharedPreferences preferences;

    ArrayList<String> arrayHelmetsMACs;

    Spinner spinnerHelmetBrand, spinnerHelmetSize;
    String spinnerHintBrand, spinnerHintSize;
    //create a list of spinnerItems for the spinner.
    ArrayList<String> arrayHelmetBrands, arrayHelmetSizes;

    //BLE
    private boolean mScanning;

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;
    BluetoothAdapter bluetoothAdapter;
    private AlertList bLEDevicesDialog;

    //Bluetooth classic
    private ArrayList<String> arrayBleDevices, arrayBclassicNamesMACs;
    private AlertList bClassicDevicesDialog;

    String primaryHelmetSelectedMAC, helmyAudioMAC="";

    AdapterListDevices adapterBleNamesMACs, adapterBclassicNames;

    private BLE_HelmyC bluetoothHelmet;
    private BroadcastReceiver blueDeviceConnectionReceiver, blueWriteColorReceiver;
    private boolean helmyC_connected;
    private int contColor = 1;
    private int helmetColor;

    private String edit_MAC;

    boolean isKeyboardShowing = false;

    //Volley
    private RequestQueue requestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_helmet);

        etHelmetNickname = findViewById(R.id.etNicknameHelmet);
        tvBtnManual = findViewById(R.id.btnManual);

        seekBarPallete = findViewById(R.id.seekBarPallete);
        seekBarPallete.getProgressDrawable().setColorFilter(getResources()
                .getColor(android.R.color.transparent), PorterDuff.Mode.MULTIPLY); // set progress to transparent
        setSeekBarListener();

        pbHelmet = findViewById(R.id.pbRegHelmet);
        scrollViewFields = findViewById(R.id.scrollview);
        scrollViewFields.setVisibility(View.INVISIBLE);

        imgBtnDelete = findViewById(R.id.imgBtnDeleteHelmet);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        arrayBclassicNamesMACs = new ArrayList<>();

        //shared preferences
        preferences = SingletonSharedPreferences.getInstance(this.getApplicationContext()); // for the last user logged in

        arrayHelmetsMACs = preferences.get_helmets_saved_MACs();

        arrayBleDevices = new ArrayList<>();

        // the following will be shown as hints within the spinners
        spinnerHintBrand = getResources().getString(R.string.HelmetBrand);
        spinnerHintSize = getResources().getString(R.string.HelmetSize);
        //create a list of spinnerItems for the spinner.
        arrayHelmetBrands = new ArrayList<>( Arrays.asList(spinnerHintBrand, "Ich", "MT", "Shaft", "Xtrong", "Xone", getResources().getString(R.string.otherBrand)) );
        arrayHelmetSizes = new ArrayList<>( Arrays.asList(spinnerHintSize, "XXS", "XS", "S", "M", "L", "XL", "XXL") );

        spinnerHelmetBrand = findViewById(R.id.spinnerHelmetBrand);
        spinnerHelmetBrand.setAdapter(new ClassSpinnerAdapter(this, R.layout.textview_template_spinner, arrayHelmetBrands, spinnerHelmetBrand));

        spinnerHelmetSize = findViewById(R.id.spinnerHelmetSize);
        spinnerHelmetSize.setAdapter(new ClassSpinnerAdapter(this, R.layout.textview_template_spinner, arrayHelmetSizes, spinnerHelmetSize));

        bluetoothHelmet = new BLE_HelmyC(this);

        setupBleBroadcastReceivers();

        setupKeyboardDisplayListener();

        //volley
        requestQueue = SingletonVolley.getInstance(this.getApplicationContext()).getRequestQueue();

        // check location permission
        checkLocationPermission();
    }

    private void setupKeyboardDisplayListener(){
        final LinearLayout LLtitle = findViewById(R.id.LLtitle_helmet);
        final ConstraintLayout contentView = findViewById(R.id.CL_registerHelmet);
        final ConstraintLayout helmyLogo = findViewById(R.id.CL_helmy_logo);

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
                                helmyLogo.setVisibility(View.GONE);
                                LLtitle.setVisibility(View.GONE);
                                tvBtnManual.setVisibility(View.GONE);
                            }
                        }
                        else {
                            // keyboard is closed
                            if (isKeyboardShowing) {
                                isKeyboardShowing = false;
                                helmyLogo.setVisibility(View.VISIBLE);
                                LLtitle.setVisibility(View.VISIBLE);
                                tvBtnManual.setVisibility(View.VISIBLE);
                            }
                        }
                    }
                });
    }

    private void checkBluetoothON() {
        if (bluetoothAdapter.isEnabled()) {
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
                list_or_connect();
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
                        resolvable.startResolutionForResult(ActivityRegisterHelmet.this, REQUEST_ENABLE_GPS);
                    } catch (IntentSender.SendIntentException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });
    }

    private void list_or_connect() {
        Intent intent = getIntent();
        edit_MAC = intent.getStringExtra("edit_MAC_helmet");
        if(TextUtils.isEmpty(edit_MAC)){
            final AlertMessageButton alert = new AlertMessageButton(this);
            alert.setDialogMessage(getResources().getString(R.string.reasonToDisplayHelmyCList));
            alert.setDialogPositiveButton(getResources().getString(R.string.Ok), new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // first add the BLE and then the intercom bluetooth
                    displayListBLEDevices();
                    imgBtnDelete.setVisibility(View.GONE);
                    alert.dismissAlert();
                }
            });
            alert.setDialogCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialogInterface) {
                    onBackPressed();
                }
            });
            alert.hideCancelButton();
            alert.showAlert();

        } else {
            imgBtnDelete.setVisibility(View.VISIBLE);
            primaryHelmetSelectedMAC = edit_MAC;
            helmyAudioMAC = preferences.getHelmetAssociatedBluetoothClassic(edit_MAC);
            helmetColor = preferences.getHelmetColor(edit_MAC);

            pbHelmet.setVisibility(View.VISIBLE);
            scrollViewFields.setVisibility(View.INVISIBLE);
            Toast.makeText(ActivityRegisterHelmet.this, R.string.connecting, Toast.LENGTH_LONG).show();
            bluetoothHelmet.initiateBluetoothConexion(bluetoothAdapter.getRemoteDevice(edit_MAC), false, null);
            try{
                etHelmetNickname.setText(preferences.getHelmetNickname(edit_MAC));

                String brand = preferences.getHelmetBrand(edit_MAC);
                int sel = arrayHelmetBrands.indexOf(brand);
                if (sel > 0) {
                    spinnerHelmetBrand.setSelection(sel);
                } else {
                    ((ClassSpinnerAdapter) spinnerHelmetBrand.getAdapter()).addOther(brand);
                }

                spinnerHelmetSize.setSelection(arrayHelmetSizes.indexOf( preferences.getHelmetSize(edit_MAC) ));

                int color = preferences.getHelmetColor(edit_MAC);
                float[] hslColor = new float[3];
                ColorUtils.colorToHSL(color, hslColor);
                seekBarPallete.setProgress((int) (hslColor[0]/3.6f));
            } catch (Exception ignored){}
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if(requestCode == Static_AppVariables.REQUESTCODE_TURNON_BLUETOOTH) {
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                checkGPS();
            } else {
                final AlertMessageButton alert = new AlertMessageButton(this);
                alert.setDialogMessage(getResources().getString(R.string.userDidnotTurnOnBluetooth));
                alert.setDialogPositiveButton(getResources().getString(R.string.btnEnableBluetooth), new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        checkBluetoothON();
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
                alert.setDialogCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        onBackPressed();
                    }
                });
                alert.showAlert();
            }
        } else if(requestCode == REQUEST_ENABLE_GPS) {
            switch (resultCode) {
                case Activity.RESULT_OK:
                    // GPS was turned on
                    list_or_connect();
                    break;
                case Activity.RESULT_CANCELED:
                    // The user was asked to turn on GPS, but did not do it. Give an explanation
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
                    alert.setDialogCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialogInterface) {
                            onBackPressed();
                        }
                    });
                    alert.showAlert();
                    break;
                default:
                    break;
            }
        }
    }

    private void setSeekBarListener() {
        seekBarPallete.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                helmetColor = ColorUtils.HSLToColor(
                        new float[]{seekBar.getProgress()*3.6f, 1, 0.5f} );
                etHelmetNickname.setTextColor(helmetColor);
                if(helmyC_connected){
                    startSendingHelmetColor();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {            }
        });
    }

    private void startSendingHelmetColor(){
        int red = Color.red( helmetColor );
        contColor = 1;
        Log.d(TAG, "HelmetColor red = " + red);
        bluetoothHelmet.helmetColorWrite(red, bluetoothHelmet.characteristicHelmetColorRed);
    }

    private void setupBleBroadcastReceivers() {

        blueDeviceConnectionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Boolean connected = intent.getBooleanExtra(Static_AppVariables.INTENTEXTRA_BLE_CONNECTION, false);
                String deviceMAC = intent.getStringExtra(Static_AppVariables.INTENTEXTRA_BLE_MAC);

                pbHelmet.setVisibility(View.GONE);

                if (!connected) {
                    Toast.makeText(ActivityRegisterHelmet.this, R.string.bluetoothNotFoundTryAgain, Toast.LENGTH_LONG).show();
                    if(TextUtils.isEmpty(edit_MAC)){
                        displayListBLEDevices();
                    } else {
                        Toast.makeText(ActivityRegisterHelmet.this, R.string.connecting, Toast.LENGTH_LONG).show();
                        bluetoothHelmet.initiateBluetoothConexion(bluetoothAdapter.getRemoteDevice(edit_MAC), false, null);
                    }
                    helmyC_connected = false;
                    scrollViewFields.setVisibility(View.INVISIBLE);
                    pbHelmet.setVisibility(View.VISIBLE);
                } else {
                    helmyC_connected = true;
                    // once connected, check if it is covid version and save it preferences
                    preferences.setHelmet_isCovid(primaryHelmetSelectedMAC, bluetoothHelmet.hasTemperatureSensor());
                    // now send the color
                    scrollViewFields.setVisibility(View.VISIBLE);
                    helmetColor = ColorUtils.HSLToColor(
                            new float[]{seekBarPallete.getProgress()*3.6f, 1, 0.5f} );
                    etHelmetNickname.setTextColor(helmetColor);
                    if(helmyC_connected){
                        startSendingHelmetColor();
                    }
                }
            }
        };

        blueWriteColorReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean success = intent.getBooleanExtra(Static_AppVariables.INTENTEXTRA_HELMET_COLOR_WRITE, false);
                Log.d(TAG, "HelmetColor Write was successful: " + success + " cont=" + contColor);
                if (!success) {
                    Toast.makeText(ActivityRegisterHelmet.this, R.string.bluetoothErrorTryAgain, Toast.LENGTH_LONG).show();
                    // bluetoothBike.bikePassword_1_WriteCharacteristic(spinnerBikePassword1.getSelectedItemPosition());
                    // user will have to hit on the button to restart writing the password fromActivity the beggining
                    contColor = 1;
                } else {
                    if(contColor == 1){
                        // red component was wrote successfully, then write the green
                        int green = Color.green( helmetColor );
                        Log.d(TAG, "HelmetColor green = " + green);
                        bluetoothHelmet.helmetColorWrite(green, bluetoothHelmet.characteristicHelmetColorGreen);
                        contColor = 2;
                    } else if(contColor == 2){
                        // green component was wrote successfully, then write the blue
                        int blue = Color.blue( helmetColor );
                        Log.d(TAG, "HelmetColor blue = " + blue);
                        bluetoothHelmet.helmetColorWrite(blue, bluetoothHelmet.characteristicHelmetColorBlue);
                        contColor = 3;
                    } else if(contColor == 3){
                        // blue component was wrote successfully.
                        contColor = 1; //restart sequence
                    }

                }

            }
        };

        registerReceiver(blueDeviceConnectionReceiver, new IntentFilter(Static_AppVariables.ACTIONFILTER_GATT_CONNECTION) );
        registerReceiver(blueWriteColorReceiver, new IntentFilter(Static_AppVariables.ACTIONFILTER_HELMET_COLOR) );
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            Log.d(TAG,"start scanning");
            // Stops scanning after a pre-defined scan period.
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    bluetoothAdapter.stopLeScan(leScanCallback);
                    bLEDevicesDialog.setDialogMessage(R.string.ChooseNewDevice);
                    bLEDevicesDialog.setDialogPositiveButton(getResources().getString(R.string.retry), new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    bLEDevicesDialog.dismissAlert();
                                }
                            });
                            displayListBLEDevices();
                        }
                    });
                }
            }, SCAN_PERIOD);

            mScanning = true;
            bluetoothAdapter.startLeScan(leScanCallback);
        } else {
            mScanning = false;
            bluetoothAdapter.stopLeScan(leScanCallback);
            bLEDevicesDialog.setDialogMessage(R.string.ChooseNewDevice);
            bLEDevicesDialog.setDialogPositiveButton(getResources().getString(R.string.retry), new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    bLEDevicesDialog.dismissAlert();
                    displayListBLEDevices();
                }
            });
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

                            Log.d(TAG, "BleFound device2add" + " " + arrayBleDevices.indexOf(device2add)
                                    + " " + arrayBleDevices.indexOf(device2add));

                            //check if device2add was already listed and if it is the primaryHelmet, bike or pillion and that the name is HELMY
                            if(device.getName() != null
                                    && arrayBleDevices.indexOf(device2add) < 0
                                    && arrayHelmetsMACs.indexOf(device.getAddress()) < 0
                                    && device2add.contains("HELMYC")  ){

                                arrayBleDevices.add(device2add);
                                adapterBleNamesMACs.notifyDataSetChanged();
                            }
                        }
                    });
                }
            };

    private void displayListBLEDevices() {
        arrayBleDevices.clear();
        if(mScanning){
            scanLeDevice(false);
            scanLeDevice(true);
        } else {
            scanLeDevice(true);
        }

        adapterBleNamesMACs = new AdapterListDevices(arrayBleDevices, this);
        bLEDevicesDialog = new AlertList(this);
        bLEDevicesDialog.setRecyclerView(adapterBleNamesMACs);
        bLEDevicesDialog.setDialogMessage(getResources().getString(R.string.searchingHelmy));
        bLEDevicesDialog.hideCancelButton();
        bLEDevicesDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                onBackPressed();
            }
        });

        final RecyclerView recyclerView = bLEDevicesDialog.getRecyclerView();
        adapterBleNamesMACs.setOnItemClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int position = recyclerView.getChildAdapterPosition(view);
                bluetoothAdapter.cancelDiscovery();
                String nameMAC = arrayBleDevices.get(position);
                String[] separated = nameMAC.split(";");
                BluetoothDevice deviceSelected = bluetoothAdapter.getRemoteDevice(separated[1]);

                //New devices will be assigned as the primary Helmet
                primaryHelmetSelectedMAC = deviceSelected.getAddress();

                bLEDevicesDialog.dismissAlert();

                pbHelmet.setVisibility(View.VISIBLE);
                scrollViewFields.setVisibility(View.INVISIBLE);
                Toast.makeText(ActivityRegisterHelmet.this, R.string.connecting, Toast.LENGTH_LONG).show();
                bluetoothHelmet.initiateBluetoothConexion(deviceSelected, false, null);
            }
        });
        bLEDevicesDialog.showAlert();
    }

    boolean saving;
    public void btn_saveHelmetInfo(View view) {
        checkField(etHelmetNickname);
        checkField(spinnerHelmetBrand);
        checkField(spinnerHelmetSize);
        ArrayList<String> helmetsNicknames = preferences.get_helmets_saved_nicknames();
        if(helmetsNicknames.indexOf( etHelmetNickname.getText().toString() ) >= 0
                && TextUtils.isEmpty(edit_MAC)){
            Toast.makeText(this, R.string.nicknameAlreadyExists, Toast.LENGTH_SHORT).show();
            etHelmetNickname.setBackground(getResources().getDrawable(R.drawable.redcontour_rounded));
        } else if(TextUtils.isEmpty(etHelmetNickname.getText())
                || spinnerHelmetBrand.getSelectedItemPosition() == 0
                || spinnerHelmetSize.getSelectedItemPosition() == 0){
            Toast.makeText(this, R.string.AllFieldsAreRequired, Toast.LENGTH_SHORT).show();
        } else {
            if(TextUtils.isEmpty(edit_MAC)){
                // user is adding a new HelmyC
                if(TextUtils.isEmpty(helmyAudioMAC)){
                    // user has not selected the intercomm
                    final AlertMessageButton alert = new AlertMessageButton(this);
                    alert.setDialogMessage(getResources().getString(R.string.reasonToDisplayIntercommList));
                    alert.setDialogPositiveButton(getResources().getString(R.string.Ok), new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            // search for intercomm
                            bluetoothHelmet.pairingMode_writeChar(true); // Start Helmy-C in pairing mode. Once written successfully, BLE_HelmyC class will turn it off after 6s
                            addnewBclassicDevice();
                            alert.dismissAlert();
                        }
                    });
                    alert.showAlert();
                } else {
                    // user selected and paired its intercomm, however, perhaps there was no internet connection
                    saveHelmetInServer();
                }
            } else {
                // user is editting HelmyC, no need to search or add intercomm
                if(etHelmetNickname.getText().toString().equals(preferences.getHelmetNickname(edit_MAC))
                        && spinnerHelmetBrand.getSelectedItem().equals(preferences.getHelmetBrand(edit_MAC))
                        && spinnerHelmetSize.getSelectedItem().equals(preferences.getHelmetSize(edit_MAC))
                        && helmetColor == preferences.getHelmetColor(edit_MAC)){
                    // user did not change any data
                    Intent intent;
                    if( preferences.didUserRegister_data_devices() ){
                        intent = new Intent(ActivityRegisterHelmet.this, ActivityChooseDevices.class);
                    } else {
                        intent = new Intent(ActivityRegisterHelmet.this, ActivityProgress.class);
                    }
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                } else {
                    // user changed data so it has to be sent to the server
                    saveHelmetInServer();
                }
            }

        }
    }

    private void saveHelmetInServer(){
        if(!saving){
            try {
                String email = preferences.get_lastUser_email_logged();
                String sizeEncrypted = Static_AppMethods.encryptAES_toString64(this,
                        spinnerHelmetSize.getSelectedItem().toString(), email, preferences);
                String brandEncrypted = Static_AppMethods.encryptAES_toString64(this,
                        spinnerHelmetBrand.getSelectedItem().toString(), email, preferences);

                saving = true; //prevents fromActivity entering again

                StringRequest joRequest = request(etHelmetNickname.getText().toString(), sizeEncrypted, brandEncrypted,
                        Integer.toString(helmetColor), primaryHelmetSelectedMAC, helmyAudioMAC);

                requestQueue.add(joRequest);

                pbHelmet.setVisibility(View.VISIBLE);
            } catch (Exception e) {
                Log.e(TAG+"Encrypt", "Encryption Error= " + e.getMessage());
                Static_AppMethods.ToastEncryptionError(this);
            }
        }
    }

    public void addnewBclassicDevice() {
        // search for paired devices
        queryPairedBclassicDevices();

        // Register broadcast when a device is discovered
        registerReceiver(bluetoothBroadcastReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
        // Register broadcast when device finished discovering new devices
        registerReceiver(bluetoothBroadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
        // Register broadcast when pairing with a new device
        registerReceiver(bluetoothBroadcastReceiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));

        // discover devices so that the helmets and bikes can be paired
        doDiscovery();

        // show the paired devices and the ones just discovered
        displayListBclassicDevices();
    }

    private void queryPairedBclassicDevices() {
        arrayBclassicNamesMACs.clear();
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        // If there are paired devices
        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {
                // Add the name and address to array list to show in AlertDialog
                String deviceNameMAC = device.getName()+";"+device.getAddress();
                if (arrayBclassicNamesMACs.indexOf( deviceNameMAC ) < 0 && device.getName() != null &&
                        ( device.getName().equals("V6") || device.getName().equals("Vansky") ) ) { //TODO: remove or change names allowed for intercomms
                    Log.d(TAG, "New --> Name=" + device.getName() + " MAC=" + device.getAddress());
                    arrayBclassicNamesMACs.add(deviceNameMAC); // list in alertdialog updates automatically
                }
//                arrayBclassicNamesMACs.add(device.getName()+";"+device.getAddress());
            }
            Log.d(TAG, "Bluetooth:" + arrayBclassicNamesMACs.toString());
        }
    }

    private void doDiscovery() {
        // If we're already discovering, stop it
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        // Request discover fromActivity BluetoothAdapter
        bluetoothAdapter.startDiscovery();
    }

    // The BroadcastReceiver that listens for discovered devices
    private final BroadcastReceiver bluetoothBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String intentAction = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(intentAction)) {
                // When a device is found
                // Get the BluetoothDevice object fromActivity the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // If it's already listed, skip it.
                String deviceNameMAC = device.getName()+";"+device.getAddress();
                if (arrayBclassicNamesMACs.indexOf( deviceNameMAC ) < 0 && device.getName() != null &&
                        ( device.getName().equals("V6") || device.getName().equals("Vansky") ) ) { //TODO: remove or change names allowed for intercomms
                    Log.d(TAG, "New --> Name=" + device.getName() + " MAC=" + device.getAddress());
                    arrayBclassicNamesMACs.add(deviceNameMAC); // list in alertdialog updates automatically
                    adapterBclassicNames.notifyDataSetChanged();
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intentAction)) {
                // When discovery is finished, change the Activity title
                bClassicDevicesDialog.setDialogMessage(R.string.chooseBTaudio);
                bClassicDevicesDialog.setDialogPositiveButton(getResources().getString(R.string.retry), new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        bClassicDevicesDialog.dismissAlert();
                        addnewBclassicDevice();
                    }
                });
                Log.d(TAG, "Bluetooth Discovery Finished");
            }
            if (intent.getAction().equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                BluetoothDevice mBluetooth2Pair = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                switch (mBluetooth2Pair.getBondState()) {
                    case BluetoothDevice.BOND_BONDED:
                        helmyAudioMAC = mBluetooth2Pair.getAddress();
                        pbHelmet.setVisibility(View.GONE);
                        saveHelmetInServer();
                        break;
                    case BluetoothDevice.BOND_BONDING:
                        Log.d(TAG, "Bluetooth pairing BONDING");
                        pbHelmet.setVisibility(View.VISIBLE);
                        break;
                    case BluetoothDevice.BOND_NONE:
                        Log.d(TAG, "Bluetooth pairing NONE");
                        Toast.makeText(ActivityRegisterHelmet.this, getResources().getString(R.string.errorTryAgain),
                                Toast.LENGTH_SHORT).show();
                        addnewBclassicDevice(); // start all over because there was an error pairing
                        break;
                }
            }
        }
    };

    private void displayListBclassicDevices() {
        adapterBclassicNames = new AdapterListDevices(arrayBclassicNamesMACs, this);

        bClassicDevicesDialog = new AlertList(this);
        bClassicDevicesDialog.setRecyclerView(adapterBclassicNames);
        bClassicDevicesDialog.setDialogMessage(getResources().getString(R.string.searchingBTaudio));
        bClassicDevicesDialog.hideCancelButton();
        final RecyclerView recyclerView = bClassicDevicesDialog.getRecyclerView();
        adapterBclassicNames.setOnItemClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int position = recyclerView.getChildAdapterPosition(view);
                bluetoothAdapter.cancelDiscovery();
                String[] separated = arrayBclassicNamesMACs.get(position).split(";");
                BluetoothDevice deviceSelected = bluetoothAdapter.getRemoteDevice( separated[1] );
                if (deviceSelected.getBondState() == BluetoothDevice.BOND_NONE) {
                    pbHelmet.setVisibility(View.VISIBLE);
                    pairDevice(deviceSelected);
                } else {
                    helmyAudioMAC = deviceSelected.getAddress();
                    saveHelmetInServer();
                }
                bClassicDevicesDialog.dismissAlert();
            }
        });
        bClassicDevicesDialog.showAlert();
    }

    private void pairDevice(BluetoothDevice device) {
        // Response to pairing will handled by the bluetoothBroadcastReceiver
        try {
            Log.d(TAG, "pairDevice() Start Pairing...");
            Method m = device.getClass().getMethod("createBond", (Class[]) null);
            m.invoke(device, (Object[]) null);
            Log.d(TAG, "pairDevice() Pairing finished.");
        } catch (Exception e) {
            Log.e(TAG, e.toString() );
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // check discovery is off, otherwise cancel
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();

        try{
            unregisterReceiver(bluetoothBroadcastReceiver);
            unregisterReceiver(blueDeviceConnectionReceiver);
            unregisterReceiver(blueWriteColorReceiver);
        } catch (Exception ignored){        }

        bluetoothHelmet.finishConnection();
    }

    public void deleteHelmet(View view) {
        if(!TextUtils.isEmpty(edit_MAC)){
            lauchAlertDeleteHelmet();
        }
    }

    private void lauchAlertDeleteHelmet(){
        final AlertMessageButton alert = new AlertMessageButton(this);
        alert.setDialogMessage(getResources().getString(R.string.areYouSureToDelete));
        alert.setDialogPositiveButton(getResources().getString(R.string.Yes), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                StringRequest joRequest = requestDelete(primaryHelmetSelectedMAC);
                requestQueue.add(joRequest);
                pbHelmet.setVisibility(View.VISIBLE);
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

    public void goBack(View view) {
        onBackPressed();
    }

    public void btn_launchManual(View view) {
        Toast.makeText(this, "En desarrollo", Toast.LENGTH_SHORT).show();
    }

    private StringRequest request(final String alias, final String size, final String brand,
                                  final String color, final String mac, final String intercomm_mac){

        String url;
        if(preferences.get_isPrimaryServerDown()){
            // primary server is down, so we will use the secondary
            url = Static_AppVariables.url_helmet2;
        } else {
            url = Static_AppVariables.url_helmet;
        }

        StringRequest strRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response)
                    {
                        Log.d(TAG+"Volley", "save response: " + response);
                        saving = false;
                        pbHelmet.setVisibility(View.INVISIBLE);
                        if( !TextUtils.isEmpty(response) ){
                            if(response.contains("1")){
                                Toast.makeText(getApplicationContext(), R.string.dataSaved, Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(getApplicationContext(), R.string.dataUpdated, Toast.LENGTH_LONG).show();
                            }

                            //New BLE devices will be assigned as a Helmet
                            preferences.saveHelmetPreferences(primaryHelmetSelectedMAC);
                            preferences.savePrimaryHelmetPreferences(primaryHelmetSelectedMAC);
                            preferences.setHelmetAssociatedBluetoothClassic( primaryHelmetSelectedMAC, helmyAudioMAC ); // Associate bluetooth classic
                            preferences.setHelmetNickname(primaryHelmetSelectedMAC, etHelmetNickname.getText().toString() );
                            preferences.setHelmetBrand_encrypted(primaryHelmetSelectedMAC, brand ); // save data previously encrypted
                            preferences.setHelmetSize_encrypted(primaryHelmetSelectedMAC, size ); // save data previously encrypted
                            preferences.setHelmetColor(primaryHelmetSelectedMAC, helmetColor );

                            Intent intent;
                            if( preferences.didUserRegister_data_devices() ){
                                intent = new Intent(ActivityRegisterHelmet.this, ActivityChooseDevices.class);
                            } else {
                                intent = new Intent(ActivityRegisterHelmet.this, ActivityProgress.class);
                            }
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                        } else {
                            // most likely the user has mobile data on but without internet service. Worst case, something went wrong with the server
                            Static_AppMethods.ToastCheckYourInternet(ActivityRegisterHelmet.this);
                        }
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                        saving = false;
                        pbHelmet.setVisibility(View.INVISIBLE);
                        Log.e(TAG+"Volley", error.toString());
                        Static_AppMethods.ToastCheckYourInternet(ActivityRegisterHelmet.this);
                        Static_AppMethods.checkResponseCode(error, preferences);
                    }
                })
        {
            @Override
            protected Map<String, String> getParams()
            {
                Log.d(TAG+"Volley", "userId= " + preferences.get_lastUser_Id_logged() +
                        "alias= " + alias + " size=" +size + " brand=" + brand + " color=" + color + " mac=" + mac
                        + " intercom=" + intercomm_mac);
                Map<String, String> params = new HashMap<>();
                params.put("userId", preferences.get_lastUser_Id_logged());
                params.put("alias", alias);
                params.put("size", size);
                params.put("brand", brand);
                params.put("customColor", color);
                params.put("mac", mac);
                params.put("intercom_mac", intercomm_mac);
                return params;
            }
        };

        return strRequest;
    }

    private StringRequest requestDelete(final String mac){

        String url;
        if(preferences.get_isPrimaryServerDown()){
            // primary server is down, so we will use the secondary
            url = Static_AppVariables.url_helmet2;
        } else {
            url = Static_AppVariables.url_helmet;
        }

        StringRequest strRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response)
                    {
                        Log.d(TAG+"Volley", "HC delete response: " + response);
                        saving = false;
                        pbHelmet.setVisibility(View.INVISIBLE);
                        if( !TextUtils.isEmpty(response) ){
                            if(response.contains("3")){
                                Toast.makeText(getApplicationContext(), R.string.dataDeleted, Toast.LENGTH_LONG).show();
                                //delete helmet
                                preferences.deleteHelmetFromPreferences(primaryHelmetSelectedMAC);
                            } else {
                                Toast.makeText(getApplicationContext(), R.string.errorTryAgain, Toast.LENGTH_LONG).show();
                            }

                            Intent intent;
                            if( preferences.didUserRegister_data_devices() ){
                                intent = new Intent(ActivityRegisterHelmet.this, ActivityChooseDevices.class);
                            } else {
                                intent = new Intent(ActivityRegisterHelmet.this, ActivityProgress.class);
                            }
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                        } else {
                            // most likely the user has mobile data on but without internet service. Worst case, something went wrong with the server
                            Static_AppMethods.ToastCheckYourInternet(ActivityRegisterHelmet.this);
                        }
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                        saving = false;
                        pbHelmet.setVisibility(View.INVISIBLE);
                        Log.e(TAG+"Volley", error.toString());
                        Static_AppMethods.ToastCheckYourInternet(ActivityRegisterHelmet.this);
                        Static_AppMethods.checkResponseCode(error, preferences);
                    }
                })
        {
            @Override
            protected Map<String, String> getParams()
            {
                Map<String, String> params = new HashMap<>();
                params.put("userId", preferences.get_lastUser_Id_logged());
                params.put("mac", mac);
                return params;
            }
        };

        return strRequest;
    }

    public void click_whatIsAlias(View view) {
        Static_AppMethods.launchAlertMessage(getResources().getString(R.string.whatIsAliasC), this);
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
}