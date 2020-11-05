package com.taedison.helmy;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.common.util.Hex;

import java.util.Arrays;
import java.util.Calendar;
import java.util.UUID;

/***
 * This class is for connecting, sending and receiving data from HelmyM
 * Actions of reading or writing on Ble will be handled by callbacks
 */
public class BLE_HelmyM {

    private final static String TAG = "class_bike";

    private Context context;
    public BluetoothGatt bluetoothGatt;

    private BluetoothGattCharacteristic characteristicBikeOnOff, characteristicBikeRPM,
            characteristicBikePassword_1, characteristicBikePassword_2, characteristicBikePassword_3,
            characteristicBikeEnable, characteristicBikeID;

    BluetoothDevice device;

    SingletonSharedPreferences preferences;
    private String fileName_dateTrip;
    private double wheelPerimeterInKM;
    private String velocityData = "";
    private int contId = 0;
    private byte[] encryptedBytes;
    private String password;
    int lastVelocityValue = 0;

    BLE_HelmyM(Context context){
        Log.i(TAG, "constructor 0");
        this.context = context;

        preferences = SingletonSharedPreferences.getInstance(context.getApplicationContext());
    }

    void initiateBluetoothConexion(BluetoothDevice device) {
        this.device = device;

        Log.i(TAG, "Initiated. Device mac: " + this.device.getAddress() );
        bluetoothGatt = device.connectGatt(context, false, gattCallback); // onConnectionStateChange will handle that connection state

        String bikeMAC = device.getAddress();

        if ( !TextUtils.isEmpty(preferences.getBikeTireWidth(bikeMAC))) {
            // for calculating velocity. TODO: still neccesary to test in a real trip
            int tireWidth = Integer.parseInt(preferences.getBikeTireWidth(bikeMAC)); // in millimeters
            int tirePercentage = Integer.parseInt(preferences.getBikeTirePercentage(bikeMAC)); // percentage %
            int wheelDiameter = Integer.parseInt(preferences.getBikeWheelDiameter(bikeMAC)); // in inches

            // how to calculate perimeter (in spanish) https://www.calculartodo.com/automovil/dimension-neumatico.php
            double diameterIn_mm = (wheelDiameter * 25.4d + 2 * (tireWidth * tirePercentage / 100d)); //in cm
            wheelPerimeterInKM = (2 * Math.PI * (diameterIn_mm / 2)) / 1000000d;
        }
    }

    // Various callback methods defined by the BLE API.
    private final BluetoothGattCallback gattCallback =
            new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                                    int newState) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.i(TAG, "Connected to GATT server.");
                        password = "";
                        encryptedBytes = new byte[16];
                        bluetoothGatt.discoverServices(); // onServicesDiscovered callback will handle the action
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        bluetoothGatt.disconnect();
                        bluetoothGatt = null;
                        sendDeviceConnectionToActivity(device.getAddress(), false);
                        Log.i(TAG, "Disonnected fromActivity GATT server.");
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    super.onServicesDiscovered(gatt, status);

                    Log.i(TAG, "Services discovered");
                    BluetoothGattService serviceHelmyM = bluetoothGatt.getService(Static_AppVariables.UUID_HELMYM_SERVICE);

                    // get the instances of the predefined characteristics
                    characteristicBikeOnOff = serviceHelmyM
                            .getCharacteristic(Static_AppVariables.UUID_HELMYM_CAR_TURN_ON_OFF);

                    characteristicBikeRPM = serviceHelmyM
                            .getCharacteristic(Static_AppVariables.UUID_HELMYM_CAR_RPM);

                    characteristicBikePassword_1 = serviceHelmyM
                            .getCharacteristic(Static_AppVariables.UUID_HELMYM_CAR_PASSWORD_1);

                    characteristicBikePassword_2 = serviceHelmyM
                            .getCharacteristic(Static_AppVariables.UUID_HELMYM_CAR_PASSWORD_2);

                    characteristicBikePassword_3 = serviceHelmyM
                            .getCharacteristic(Static_AppVariables.UUID_HELMYM_CAR_PASSWORD_3);

                    characteristicBikeEnable = serviceHelmyM
                            .getCharacteristic(Static_AppVariables.UUID_HELMYM_CAR_ENABLE);

                    characteristicBikeID = serviceHelmyM
                            .getCharacteristic(Static_AppVariables.UUID_HELMYM_ID);

                    contId = 0;
                    readBikeId(characteristicBikeID); // read Id before informing to the activity that Bike is connected.
                }

                @Override
                // Result of a characteristic read operation
                public void onCharacteristicRead(BluetoothGatt gatt,
                                                 BluetoothGattCharacteristic characteristic,
                                                 int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        UUID charUUID = characteristic.getUuid();
                        if( charUUID == characteristicBikeRPM.getUuid()){
                            characteristicRPMRead(characteristic);
                        } else if( charUUID == characteristicBikeID.getUuid()){
                            // it is one od the Id characteristics
                            characteristicReadBikeID(characteristic);
                            if(contId < 16){
                                // if all 16 bytes have not been received, continue reading
                                readBikeId(characteristicBikeID);
                            } else {
                                Log.d("RequestVolleyBike2", "Id in HelmyM= " + Arrays.toString(getBikeIDbytes()));
                                readBikePassword(characteristicBikePassword_1);
                            }
                        } else if( charUUID == characteristicBikePassword_1.getUuid()){
                            characteristicReadBikePassword(characteristicBikePassword_1);
                            readBikePassword(characteristicBikePassword_2);
                        } else if( charUUID == characteristicBikePassword_2.getUuid()){
                            characteristicReadBikePassword(characteristicBikePassword_2);
                            readBikePassword(characteristicBikePassword_3);
                        } else if( charUUID == characteristicBikePassword_3.getUuid()){
                            characteristicReadBikePassword(characteristicBikePassword_3);
                            // once password has been read, then notify the activity of the connection
                            sendDeviceConnectionToActivity(device.getAddress(), true);

                            Log.d(TAG, "code M= " + Static_AppMethods.bytesToStringBase64(getBikeIDbytes())
                                    + " passWord= " + getBikePassword());
                        }
                    }
                }

                @Override
                // Result of a characteristic write operation
                public void onCharacteristicWrite(BluetoothGatt gatt,
                                                  BluetoothGattCharacteristic characteristic,
                                                  int status) {
                    Log.d(TAG, "Write Status = " + status + "CarUUID = " +
                            characteristic.getUuid() );
                    UUID charUUID = characteristic.getUuid();
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        if(charUUID == characteristicBikeOnOff.getUuid()){
                            Log.e(TAG, "on off wrote. Returned = " + true);
                            sendActivityWriteOnOffStatus(true);
                        } else if(charUUID == characteristicBikePassword_1.getUuid()
                                || charUUID == characteristicBikePassword_2.getUuid()
                                || charUUID == characteristicBikePassword_3.getUuid()){
                            sendActivityWritePasswordStatus(true);
                        } else if(charUUID == characteristicBikeEnable.getUuid()){
                            sendActivityWriteEnableStatus(true);
                        } else {
                            // it was one of the ID characteristics
                            sendActivityWriteIdStatus(true);
                        }
                    } else {
                        // an error ocurred
                        if(charUUID == characteristicBikeOnOff.getUuid()){
                            Log.e(TAG, "on off wrote. Returned = " + false);
                            sendActivityWriteOnOffStatus(false);
                        } else if(charUUID == characteristicBikePassword_1.getUuid()
                                || charUUID == characteristicBikePassword_2.getUuid()
                                || charUUID == characteristicBikePassword_3.getUuid()){
                            sendActivityWritePasswordStatus(false);
                        } else if(charUUID == characteristicBikeEnable.getUuid()){
                            sendActivityWriteEnableStatus(false);
                        } else {
                            // it was one of the ID characteristics
                            sendActivityWriteIdStatus(false);
                        }
                    }
                }
            };

    private void characteristicReadBikeID(BluetoothGattCharacteristic characteristic) {
        // processes the data received
        final byte[] data = characteristic.getValue();
        if (data != null && data.length == 1) {
            Log.d("RequestVolleyBike2", "Id seg byte= " + Arrays.toString(data) );
            encryptedBytes[contId] = data[0];
            contId ++;
        }
    }

    byte[] getBikeIDbytes(){
        Log.d("RequestVolleyBike2", "Bytes saved in HelmyM= " + Arrays.toString(encryptedBytes));
        return encryptedBytes;
    }

    private void characteristicReadBikePassword(BluetoothGattCharacteristic characteristic) {
        // For profiles that the data is formatted in HEX.
        final byte[] data = characteristic.getValue();
        Log.d(TAG, "characteristicReadBikePassword. Data length= " + data.length );
        if (data != null && data.length == 1) {
            StringBuilder stringBuilder = new StringBuilder(data.length);
            for(byte byteChar : data){
//                    stringBuilder.append(String.format("%02X ", byteChar));
                stringBuilder.append(byteChar & 0xFF);
            }
            if(TextUtils.isEmpty(password)){
                password =  stringBuilder.toString();
            } else {
                password =  password + ";" + stringBuilder.toString();
            }
            Log.d(TAG, "password= " + password );
        }
    }

    String getBikePassword(){
        return password;
    }

    public void finishConnection() {

        if( !TextUtils.isEmpty(fileName_dateTrip) ){
            saveFile();
        }

        if (bluetoothGatt == null) {
            return;
        }
        bluetoothGatt.disconnect();
        bluetoothGatt.close();
        bluetoothGatt = null;
    }

    /***  /////////////// VELOCITY //////////////// ***/

    void startReadVelocityRPM(String fileName_date){
        Log.d(TAG+"RPM", "start RPMs");
        fileName_dateTrip = fileName_date;
        readBikeRPMCharacteristic(); // start reading RPMs
    }

    private void readBikeRPMCharacteristic(){

        Log.d(TAG+"RPM", "read char RPM");

        //check mBluetoothGatt is available
        if (bluetoothGatt == null) {
            Log.e(TAG, "lost connection");
            sendDeviceConnectionToActivity(device.getAddress(), false);
            return;
        }

        bluetoothGatt.readCharacteristic(characteristicBikeRPM);

    }

    final int millisRPM = 1000;
    private void characteristicRPMRead(final BluetoothGattCharacteristic characteristic) {
        double velH=0, vH=0, velL=0, vL=0;
        // For profiles that the data is formatted in HEX.
        final byte[] data = characteristic.getValue();
        if (data != null && data.length >= 4) {
            Log.d(TAG+"RPM", "N bytes --> " + data.length);
            final StringBuilder stringBuilder = new StringBuilder(data.length);
            int cont = 0;
            for(byte byteChar : data){
                stringBuilder.append(byteChar & 0xFF);
                if(cont == 0){
                    velH = byteChar & 0xFF;
                } else if(cont == 1){
                    vH = byteChar & 0xFF;
                } else if(cont == 2){
                    velL = byteChar & 0xFF;
                } else if(cont == 3){
                    vL = byteChar & 0xFF;
                }
                cont++;
            }

            double a = velH*256;
            double b = a + vH;
            double c = velL*256;
            double d = c + vL;

            double timeInHrs = b+d; // in millis
            timeInHrs = timeInHrs/(1000d); // conversion to seconds
            timeInHrs = timeInHrs/3600; // conversion to hours


            String speed4nums = stringBuilder.toString();

            int linVelocity;
            if(timeInHrs != 0) {
                // to prevent from dividing by 0
                linVelocity = 0;
            } else {
                linVelocity = (int) ((int) wheelPerimeterInKM / timeInHrs); //round down the number
            }

            Log.d(TAG+"RPM", "Bytes = " + stringBuilder.toString() + "time=" +
                    timeInHrs + " Vel= " + linVelocity + " wheelPer= " + wheelPerimeterInKM);

            Calendar calendar = Calendar.getInstance();
            String timeStamp = calendar.get(Calendar.HOUR_OF_DAY)
                    +"-"+ calendar.get(Calendar.MINUTE)+"-"+ calendar.get(Calendar.SECOND);

            velocityData = velocityData + linVelocity + "," + timeStamp + "\n";

            lastVelocityValue = linVelocity;

            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    readBikeRPMCharacteristic(); // continue reading RPM
                }
            }, millisRPM);

        }
    }

    void saveFile(){
        Static_AppMethods.writeToFile(fileName_dateTrip, velocityData, context); //TITLE OF THE FILE: YEAR-MONTH-DAY-HOUR-MINUTE-SECOND

        String velocityFileNames = Static_AppMethods.readFromFile(
                Static_AppVariables.FILENAME_VELOCITY_FILE_NAMES, context);
        velocityFileNames = velocityFileNames + "," + fileName_dateTrip; // save title to keep track of all velocity files saved
        Static_AppMethods.writeToFile(Static_AppVariables.FILENAME_VELOCITY_FILE_NAMES,
                velocityFileNames, context);

        // save name of the file in prefereces, relating it to userId and bikeMAC
        preferences.addVelocityFilePendingToUpload(fileName_dateTrip, preferences.get_lastUser_Id_logged(), device.getAddress());

        // upload txt to the server
        if( !ServiceUploadTxt.running ){
            // prevent from running twice the service. Anyways, this new file will be uploaded from service
            Intent intent = new Intent(context, ServiceUploadTxt.class);
            context.startService(intent);
        }

        Log.d(TAG+"RPM", fileName_dateTrip + "\n" + velocityData);

        fileName_dateTrip = "";
        velocityData = "";
    }

    /***  /////////////// Bike ID //////////////// ***/

    private void readBikeId(BluetoothGattCharacteristic characteristic ){

        Log.d(TAG+"Id", "read Bike ID");

        //check mBluetoothGatt is available
        if (bluetoothGatt == null) {
            Log.e(TAG, "lost connection");
            sendDeviceConnectionToActivity(device.getAddress(), false);
            return;
        }
        bluetoothGatt.readCharacteristic(characteristic);
    }

    /***  /////////////// PASSWORD //////////////// ***/

    private void readBikePassword(BluetoothGattCharacteristic characteristic ){

        Log.d(TAG+"password", "read Bike password");

        //check mBluetoothGatt is available
        if (bluetoothGatt == null) {
            Log.e(TAG, "lost connection");
            sendDeviceConnectionToActivity(device.getAddress(), false);
            return;
        }

        bluetoothGatt.readCharacteristic(characteristic);
    }

    public void bikePassword_1_WriteCharacteristic(int password1){

        //check mBluetoothGatt is available
        if (bluetoothGatt == null) {
            Log.e(TAG, "lost connection");
            sendDeviceConnectionToActivity(device.getAddress(), false);
            return;
        }

        // convert to hexa
        String pass1 = Integer.toString(password1, 16);
        String value;
        if(password1 < 16){
            // if password1 has just one digit, Hex.stringToBytes() will throw an error
            value = "0" + pass1;
        } else {
            value = pass1;
        }

        Log.d("Hexadecc", "hexa = " +  pass1 + " value = " + value  );

        characteristicBikePassword_1.setValue( Hex.stringToBytes(value) );
        bluetoothGatt.writeCharacteristic(characteristicBikePassword_1);


    }

    public void bikePassword_2_WriteCharacteristic(int password1){

        //check mBluetoothGatt is available
        if (bluetoothGatt == null) {
            Log.e(TAG, "lost connection");
            sendDeviceConnectionToActivity(device.getAddress(), false);
            return;
        }

        // convert to hexa
        String pass1 = Integer.toString(password1, 16);
        String value;
        if(password1 < 16){
            // if password1 has just one digit, Hex.stringToBytes() will throw an error
            value = "0" + pass1;
        } else {
            value = pass1;
        }

        Log.d("Hexadecc", "hexa = " +  pass1 + " value = " + value  );

        characteristicBikePassword_2.setValue( Hex.stringToBytes(value) );
        bluetoothGatt.writeCharacteristic(characteristicBikePassword_2);


    }

    public void bikePassword_3_WriteCharacteristic(int password1){

        //check mBluetoothGatt is available
        if (bluetoothGatt == null) {
            Log.e(TAG, "lost connection");
            sendDeviceConnectionToActivity(device.getAddress(), false);
            return;
        }

        // convert to hexa
        String pass1 = Integer.toString(password1, 16);
        String value;
        if(password1 < 16){
            // if password1 has just one digit, Hex.stringToBytes() will throw an error
            value = "0" + pass1;
        } else {
            value = pass1;
        }

        Log.d("Hexadecc", "hexa = " +  pass1 + " value = " + value  );

        characteristicBikePassword_3.setValue( Hex.stringToBytes(value) );
        bluetoothGatt.writeCharacteristic(characteristicBikePassword_3);

    }

    /***  /////////////// turnOnOFF //////////////// ***/

    public void turnOnOFF_WriteCharacteristic(boolean bikeOn){
        //check mBluetoothGatt is available
        if (bluetoothGatt == null) {
            Log.e(TAG, "lost connection");
            sendDeviceConnectionToActivity(device.getAddress(), false);
            return;
        }

        // convert to hexa
        boolean status;
        if(bikeOn){
            byte[] value = new byte[1];
            value[0] = (byte) (0x00);
            characteristicBikeOnOff.setValue(value);

        } else {
            byte[] value = new byte[1];
            value[0] = (byte) (0x01);
            characteristicBikeOnOff.setValue(value);
        }
        status = bluetoothGatt.writeCharacteristic(characteristicBikeOnOff);
        Log.e(TAG, "on off wrote. Status = " + status);
    }

    public void enable_WriteCharacteristic(boolean bikeEnable){

        //check mBluetoothGatt is available
        if (bluetoothGatt == null) {
            Log.e(TAG, "lost connection");
            sendDeviceConnectionToActivity(device.getAddress(), false);
            return;
        }

        // convert to hexa
        boolean status;
        if(bikeEnable){
            // Helmy M always on
            byte[] value = new byte[1];
            value[0] = (byte) (0x00);
            characteristicBikeEnable.setValue(value);
        } else {
            // Helmy M working with the app
            byte[] value = new byte[1];
            value[0] = (byte) (0x01);
            characteristicBikeEnable.setValue(value);
        }

        status = bluetoothGatt.writeCharacteristic(characteristicBikeEnable);
        Log.e(TAG, "enable wrote. Status = " + status);
    }

    public void bikeId_write(byte[] segment, int contId){
        //check mBluetoothGatt is available
        if (bluetoothGatt == null) {
            Log.e(TAG, "lost connection");
            sendDeviceConnectionToActivity(device.getAddress(), false);
            return;
        }

        Log.d("RequestVolleyBike2", "byte id segment sent= " + Arrays.toString(segment) );

        characteristicBikeID.setValue( segment );
        bluetoothGatt.writeCharacteristic(characteristicBikeID);

        encryptedBytes[contId] = segment[0];
    }

    // methods to communicate with activities using broadcast receivers
    private void sendDataToActivity(String data) {
        Intent intent = new Intent(Static_AppVariables.ACTIONFILTER_DATA_AVAILABLE);
        intent.putExtra(Static_AppVariables.INTENTEXTRA_HELMET_DATA, data);
        context.sendBroadcast(intent);
    }

    private void sendDeviceConnectionToActivity(String BlueMAC, boolean connected) {
        Intent intent = new Intent(Static_AppVariables.ACTIONFILTER_GATT_CONNECTION);
        intent.putExtra(Static_AppVariables.INTENTEXTRA_BLE_CONNECTION, connected);
        intent.putExtra(Static_AppVariables.INTENTEXTRA_BLE_MAC, BlueMAC);
        context.sendBroadcast(intent);

        if( !TextUtils.isEmpty(fileName_dateTrip) && !connected){
            //if it was disconnected then we save the velocity txt file for later send it to the server
            saveFile();
        }
    }

    private void sendActivityWritePasswordStatus(boolean writeWasSuccessful) {
        Intent intent = new Intent(Static_AppVariables.ACTIONFILTER_BIKE_PASSWORD);
        intent.putExtra(Static_AppVariables.INTENTEXTRA_BIKE_PASSWORD_WRITE, writeWasSuccessful);
        context.sendBroadcast(intent);
    }

    private void sendActivityWriteOnOffStatus(boolean writeWasSuccessful) {
        Intent intent = new Intent(Static_AppVariables.ACTIONFILTER_BIKE_ONOFF);
        intent.putExtra(Static_AppVariables.INTENTEXTRA_BIKE_ONOFF_WRITE, writeWasSuccessful);
        context.sendBroadcast(intent);
    }

    private void sendActivityWriteEnableStatus(boolean writeWasSuccessful) {
        Intent intent = new Intent(Static_AppVariables.ACTIONFILTER_BIKE_ENABLE);
        intent.putExtra(Static_AppVariables.INTENTEXTRA_BIKE_ENABLE_WRITE, writeWasSuccessful);
        context.sendBroadcast(intent);
    }

    private void sendActivityWriteIdStatus(boolean writeWasSuccessful) {
        Intent intent = new Intent(Static_AppVariables.ACTIONFILTER_BIKE_ID);
        intent.putExtra(Static_AppVariables.INTENTEXTRA_BIKE_ID_WRITE, writeWasSuccessful);
        context.sendBroadcast(intent);
    }

}