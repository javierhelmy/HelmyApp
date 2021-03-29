package com.taedison.helmy;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.text.TextUtils;

import com.google.android.gms.common.util.Hex;

import java.util.UUID;

/***
 * This class is for connecting, sending and receiving data from HelmyC
 * Actions of reading or writing on Ble will be handled by callbacks
 */
class BLE_HelmyC {
    private final static String TAG = "class_helmet";

    private Context context;
    private String MAC_1, MAC_2, MAC_3, MAC_4, MAC_5, MAC_6;
    BluetoothGatt bluetoothGatt;

    // READ characteristics
    private BluetoothGattCharacteristic characteristicHelmetOnHead, characteristicHelmetFasten,
            characteristicHelmetImpact, characteristicHelmetBattery,
            characteristicPhoneLowBattery,
            characteristicHelmet_MAC1, characteristicHelmet_MAC2, characteristicHelmet_MAC3,
            characteristicHelmet_MAC4, characteristicHelmet_MAC5, characteristicHelmet_MAC6,
            characteristicHelmetTemperature, characteristicHelmetThresholds,
            characteristicHelmetParing;

    private boolean hasTemperatureSensor = false;

    // WRITE characteristics
    BluetoothGattCharacteristic characteristicHelmetColorRed,
            characteristicHelmetColorGreen, characteristicHelmetColorBlue;

    private boolean readVariables; // onHead and FastenVariables

    BluetoothDevice device;

    private long timer=0, startMillis=0, readTemperatureFreq = 3000;

    BLE_HelmyC(Context context){
        this.context = context;
    }

    void initiateBluetoothConexion(BluetoothDevice device, boolean readVariables, String bikeMAC) {
        this.device = device;
        this.readVariables = readVariables; // if true, user will start a trip. If false, user is registering helmet (no need for reading variables)

//        Log.i(TAG, "Initiated");
        bluetoothGatt = device.connectGatt(context, false, gattCallback); // onConnectionStateChange will handle that connection state

        if(!TextUtils.isEmpty(bikeMAC)){
            // when initiating as driver. The MAC address is sent to HelmyC so that it can connect to
            // HelmyM as master. This will be used in case the phone's battery goes lower than 15%.
            // characteristicPhoneLowBattery will be used nto notify HelmyC of the event
            String[] MACs = bikeMAC.split(":");
//            Log.d("BikeMAC", "MAC= " + bikeMAC + " segments= " + MACs.length);
            if(MACs.length == 6){
                MAC_1 = MACs[0];
                MAC_2 = MACs[1];
                MAC_3 = MACs[2];
                MAC_4 = MACs[3];
                MAC_5 = MACs[4];
                MAC_6 = MACs[5];
            }
        }
    }

    private final BluetoothGattCallback gattCallback =
            new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                                    int newState) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
//                        Log.d(TAG, "Connected to GATT server.");
                        bluetoothGatt.discoverServices(); // onServicesDiscovered callback will handle the action
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        bluetoothGatt.disconnect();
                        bluetoothGatt.close();
                        bluetoothGatt = null;
                        sendDeviceConnectionToActivity(device.getAddress(), false);
//                        Log.d(TAG, "Disonnected fromActivity GATT server.");
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
//                        Log.i(TAG, "Services discovered");
                        // get the instance of the service
                        BluetoothGattService serviceHelmyC = bluetoothGatt.getService(Static_AppVariables.UUID_HELMYC_SERVICE);

                        // get the instances of the predefined characteristics
                        characteristicHelmetOnHead = serviceHelmyC
                                .getCharacteristic(Static_AppVariables.UUID_HELMYC_CAR_HELMETONHEAD);

                        characteristicHelmetFasten = serviceHelmyC
                                .getCharacteristic(Static_AppVariables.UUID_HELMYC_CAR_HELMET_FASTEN);

                        characteristicHelmetImpact = serviceHelmyC
                                .getCharacteristic(Static_AppVariables.UUID_HELMYC_CAR_HELMET_IMPACT);

                        characteristicHelmetBattery = serviceHelmyC.getCharacteristic(
                                Static_AppVariables.UUID_HELMYC_CAR_HELMET_BATTERY);

                        characteristicHelmetColorRed = serviceHelmyC.getCharacteristic(
                                Static_AppVariables.UUID_HELMYC_COLOR_RED);

                        characteristicHelmetColorGreen = serviceHelmyC.getCharacteristic(
                                Static_AppVariables.UUID_HELMYC_COLOR_GREEN);

                        characteristicHelmetColorBlue = serviceHelmyC.getCharacteristic(
                                Static_AppVariables.UUID_HELMYC_COLOR_BLUE);

                        characteristicHelmet_MAC1 = serviceHelmyC.getCharacteristic(
                                Static_AppVariables.UUID_HELMYC_MAC_SEG1);

                        characteristicHelmet_MAC2 = serviceHelmyC.getCharacteristic(
                                Static_AppVariables.UUID_HELMYC_MAC_SEG2);

                        characteristicHelmet_MAC3 = serviceHelmyC.getCharacteristic(
                                Static_AppVariables.UUID_HELMYC_MAC_SEG3);

                        characteristicHelmet_MAC4 = serviceHelmyC.getCharacteristic(
                                Static_AppVariables.UUID_HELMYC_MAC_SEG4);

                        characteristicHelmet_MAC5 = serviceHelmyC.getCharacteristic(
                                Static_AppVariables.UUID_HELMYC_MAC_SEG5);

                        characteristicHelmet_MAC6 = serviceHelmyC.getCharacteristic(
                                Static_AppVariables.UUID_HELMYC_MAC_SEG6);

                        characteristicPhoneLowBattery = serviceHelmyC.getCharacteristic(
                                Static_AppVariables.UUID_HELMYC_PHONE_LOWBATTERY);

                        characteristicHelmetTemperature = serviceHelmyC.getCharacteristic(
                                Static_AppVariables.UUID_HELMYC_TEMPERATURE);

                        characteristicHelmetThresholds = serviceHelmyC.getCharacteristic(
                                Static_AppVariables.UUID_HELMYC_THRESHOLDS);

                        characteristicHelmetParing = serviceHelmyC.getCharacteristic(
                                Static_AppVariables.UUID_HELMYC_INTERCOMM_PAIRINGMODE);

                        // in case covid version is launch to market
                        if(characteristicHelmetTemperature == null) {
                            hasTemperatureSensor = false;
                        } else {
                            hasTemperatureSensor = true;
                        }

                        sendDeviceConnectionToActivity(device.getAddress(), true); // notify activity

                        if(readVariables){
                            // if true, user will start a trip. If false, user is registering helmet
                            // (no need for reading variables, setting notifications, writingMAC)
                            // 1. setup notifications
                            // 2. write BikeMAC to helmyC
                            // 3. read variables: battery, onHead, fasten, temperature
                            setHelmetImpactNotification();

//                            Log.d(TAG, "Notif set");

                            if(TextUtils.isEmpty(MAC_1)){
                                // Bike was not registered, or user entered in the pillion activity
                                readHelmetBatteryCharacteristic();
                            } else {
                                bikeMAC_Write(MAC_1, characteristicHelmet_MAC1);
                            }
                        }
                    } else {
//                        Log.w(TAG, "onServicesDiscovered received: " + status);
                    }
                }

                @Override
                // Result of a characteristic read operation
                public void onCharacteristicRead(BluetoothGatt gatt,
                                                 BluetoothGattCharacteristic characteristic,
                                                 int status) {
//                    Log.d(TAG+"ReadChar", "Read " + characteristic.getUuid());
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        if(characteristicHelmetTemperature !=null &&
                                characteristic.getUuid() == characteristicHelmetTemperature.getUuid()){
                            characteristicReadTemperature(characteristic);
                        } else {
                            characteristicRead(characteristic);
                        }
                    } else {
                        // try again
                        UUID charUUID = characteristic.getUuid();
                        nextCharacteristic_If_GATT_FAILURE(charUUID);
                    }
                }

                @Override
                // when setting up the notification
                public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                    super.onDescriptorWrite(gatt, descriptor, status);

                    UUID charUUID = descriptor.getCharacteristic().getUuid();
                    if (status == BluetoothGatt.GATT_SUCCESS) {
//                        Log.d(TAG+"Descriptor", "Callback success" + status);
                        nextCharacteristic_If_GATT_SUCCESS(charUUID);
                    } else {
//                        Log.d(TAG+"Descriptor", "Callback error" + status);
                        nextCharacteristic_If_GATT_FAILURE(charUUID);
                    }
                }

                @Override
                // Characteristic notification
                public void onCharacteristicChanged(BluetoothGatt gatt,
                                                    BluetoothGattCharacteristic characteristic) {
                    characteristicRead(characteristic);
//                    Log.d(TAG, "Impact notification received");
                }

                @Override
                // calback for handling write operations
                public void onCharacteristicWrite(BluetoothGatt gatt,
                                                  BluetoothGattCharacteristic characteristic,
                                                  int status) {
//                    Log.d("class_helmet", "Write Status Success= " + (status==BluetoothGatt.GATT_SUCCESS) );
                    UUID charUUID = characteristic.getUuid();
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        nextCharacteristic_If_GATT_SUCCESS(charUUID);
                    } else {
                        nextCharacteristic_If_GATT_FAILURE(charUUID);
                    }
                }
            };

    private void nextCharacteristic_If_GATT_SUCCESS(UUID charUUID){
        // method executes the next read/write operation after a successful read/write operation
//        Log.d("HelmetColor", "nextCharacteristic_If_GATT_SUCCESS " + charUUID );
        if( charUUID == characteristicHelmetImpact.getUuid()){
            // impact notification was set
            bikeMAC_Write(MAC_1, characteristicHelmet_MAC1);
        } else if(charUUID == characteristicHelmetColorRed.getUuid() ||
                charUUID == characteristicHelmetColorGreen.getUuid() ||
                charUUID == characteristicHelmetColorBlue.getUuid() ){
//            Log.e("HelmetColor", "Color wrote. Returned = " + true);
            sendActivityWriteColorStatus(true); // notify the activity
        } else if (charUUID == characteristicPhoneLowBattery.getUuid()){
//            Log.e("PhoneLowBattery", "PhoneLowBattery wrote. Returned = " + true);
            sendActivityWritePhoneLowBatteryStatus(true);
        } else if (charUUID == characteristicHelmet_MAC1.getUuid()){
            // segment 1 was set. Write segment 2
            bikeMAC_Write(MAC_2, characteristicHelmet_MAC2);
        } else if (charUUID == characteristicHelmet_MAC2.getUuid()){
            // segment 2 was set. Write segment 3
            bikeMAC_Write(MAC_3, characteristicHelmet_MAC3);
        } else if (charUUID == characteristicHelmet_MAC3.getUuid()){
            // segment 3 was set. Write segment 4
            bikeMAC_Write(MAC_4, characteristicHelmet_MAC4);
        } else if (charUUID == characteristicHelmet_MAC4.getUuid()){
            // segment 4 was set. Write segment 5
            bikeMAC_Write(MAC_5, characteristicHelmet_MAC5);
        } else if (charUUID == characteristicHelmet_MAC5.getUuid()){
            // segment 5 was set. Write segment 6
            bikeMAC_Write(MAC_6, characteristicHelmet_MAC6);
        } else if (charUUID == characteristicHelmet_MAC6.getUuid()){
            // all segments of the MAC were set succesfully. Now start reading variables
            readHelmetBatteryCharacteristic();
        } else if (charUUID == characteristicHelmetThresholds.getUuid()){
            // thresholds were written successfully, then continue reading or writing the next characteric
            nextCharacteristic_ReadWriteVariables(null);
        } else if (charUUID == characteristicHelmetParing.getUuid()){
            // HelmyC started or stopped the pairing mode successfully
            if(paring){
                // now start timer to turnoff pairing mode in 6s
                new Handler().postDelayed(new Runnable(){
                    public void run(){
//                        Log.d(TAG, "pairing mode off");
                        pairingMode_writeChar(false);
                    }
                }, 6000);
            }
            nextCharacteristic_ReadWriteVariables(null);
        }
    }

    private void nextCharacteristic_If_GATT_FAILURE(UUID charUUID) {
        // method executes the next read/write operation after a failed read/write operation
//        Log.d("HelmetColor", "nextCharacteristic_If_GATT_FAILURE " + charUUID );
        if( charUUID == characteristicHelmetImpact.getUuid()){
            // try again
            setHelmetImpactNotification();
        } else if( charUUID == characteristicHelmetColorRed.getUuid() ||
                charUUID == characteristicHelmetColorGreen.getUuid() ||
                charUUID == characteristicHelmetColorBlue.getUuid() ){
//            Log.e("HelmetColor", "Color wrote. Returned = " + false);
            sendActivityWriteColorStatus(false);
        } else if (charUUID == characteristicPhoneLowBattery.getUuid()){
//            Log.e("PhoneLowBattery", "PhoneLowBattery wrote. Returned = " + false);
            sendActivityWritePhoneLowBatteryStatus(false);
        } else if (charUUID == characteristicHelmet_MAC1.getUuid()){
            // try again
            bikeMAC_Write(MAC_1, characteristicHelmet_MAC1);
        } else if (charUUID == characteristicHelmet_MAC2.getUuid()){
            // try again
            bikeMAC_Write(MAC_2, characteristicHelmet_MAC2);
        } else if (charUUID == characteristicHelmet_MAC3.getUuid()){
            // try again
            bikeMAC_Write(MAC_3, characteristicHelmet_MAC3);
        } else if (charUUID == characteristicHelmet_MAC4.getUuid()){
            // try again
            bikeMAC_Write(MAC_4, characteristicHelmet_MAC4);
        } else if (charUUID == characteristicHelmet_MAC5.getUuid()){
            // try again
            bikeMAC_Write(MAC_5, characteristicHelmet_MAC5);
        } else if (charUUID == characteristicHelmet_MAC6.getUuid()){
            // try again
            bikeMAC_Write(MAC_6, characteristicHelmet_MAC6);
        } else if (charUUID == characteristicHelmetBattery.getUuid()){
            // try again
            readHelmetBatteryCharacteristic();
        } else if (charUUID == characteristicHelmetOnHead.getUuid()){
            // try again
            readHelmetOnHeadCharacteristic();
        } else if (charUUID == characteristicHelmetFasten.getUuid()){
            // try again
            readHelmetFastenCharacteristic();
        } else if (charUUID == characteristicHelmetTemperature.getUuid()){
            // try again
            readHelmetTemperatureCharacteristic();
        } else if (charUUID == characteristicHelmetThresholds.getUuid()){
            // try again
            thresholds_WriteChar();
        } else if (charUUID == characteristicHelmetParing.getUuid()){
            // try again
            pairingMode_writeChar(paring);
        }
    }


    private void nextCharacteristic_ReadWriteVariables(UUID charUUID) {
        // this method is called after reading or writing successfully on the BLE
        // it loops the read/write operations so that all are executed in a ordered manner.
        // This method is to prevent from overriding operations, since the Ble can only handle one operation at a time
        // if then else ordered according to priority
        if(sendTresdholds){
            thresholds_WriteChar(); // sendThresholds is set to false once it is sent
        } else if (phoneLowBattery){
            phoneBatteryLow_WriteChar();
        } else if(readBattery) {
            readHelmetBatteryCharacteristic();
            readBattery = false; // set to false so that the battery is not read again and continues reading the other characteristics periodically
        } else if(readVariables){
            boolean timeElapsed = false;
            if(hasTemperatureSensor) {
                timer = System.currentTimeMillis();
//                Log.d(TAG, "timer= " + timer + " startMillis= " + startMillis);
                long timeElapsedSincePreviousRead = timer - startMillis; //startMillis was set in readHelmetTemperatureCharacteristic()
                timeElapsed = startMillis == 0 || timeElapsedSincePreviousRead > readTemperatureFreq; // if startMillis = 0, then read for it must read for the first time
            }
            // read temperature if has the sensor and readTemperatureFreq millis have elapsed, otherwise continue reading onFasten and onHead
            if ( hasTemperatureSensor && timeElapsed ) {
                // if startmillis == 0 then it had not read temperature for the first time
                readHelmetTemperatureCharacteristic();
            } else if(charUUID == characteristicHelmetOnHead.getUuid()){
                readHelmetFastenCharacteristic();
            } else {
                readHelmetOnHeadCharacteristic();
            }
        }
    }

    private void characteristicRead(final BluetoothGattCharacteristic characteristic) {
        // It processes the data received
        final byte[] data = characteristic.getValue();
        if (data != null && data.length > 0) {
            final StringBuilder stringBuilder = new StringBuilder(data.length);
            for(byte byteChar : data){
                stringBuilder.append(byteChar & 0xFF);
//                stringBuilder.append(String.format("%02x", byteChar));
            }
//            Log.d(TAG, "Datareceived " + characteristic.getUuid() + " = " + stringBuilder);
            sendDataToActivity(stringBuilder.toString());
        }

        nextCharacteristic_ReadWriteVariables(characteristic.getUuid());
    }

    private void characteristicReadTemperature(final BluetoothGattCharacteristic characteristic) {
        // It processes the temperature data received
        final byte[] data = characteristic.getValue();
        if (data != null && data.length > 0) {
            int cont = 0;
            double mostSig = 0, leastSig = 0;
            for(byte byteChar : data){
                if(cont == 0){
                    mostSig = byteChar & 0xFF;
                } else if(cont == 1){
                    leastSig = byteChar & 0xFF;
                }
                cont++;
            }
            double St = mostSig*256 + leastSig;

            double temperature = -45 + 175*(St/(Math.pow(2,16)-1));
//            Log.d(TAG, "Temperature= " + temperature + " MSB= " + mostSig + " LSB= " + leastSig);
            sendDataToActivity("T=" + temperature);
        }

        nextCharacteristic_ReadWriteVariables(characteristic.getUuid());
    }

    void finishConnection() {
        // to terminate the ble connection
        if (bluetoothGatt == null) {
            return;
        }
        bluetoothGatt.disconnect();
        bluetoothGatt.close();
        bluetoothGatt = null;
    }

    private void readHelmetOnHeadCharacteristic(){
//        Log.d(TAG+"OnHead", "Started");

        //check mBluetoothGatt is available
        if (bluetoothGatt == null) {
//            Log.e(TAG+"OnHead", "lost connection");
            sendDeviceConnectionToActivity(device.getAddress(), false);
            return;
        }

        bluetoothGatt.readCharacteristic(characteristicHelmetOnHead);

        readVariables = true;
    }

    private void readHelmetFastenCharacteristic(){
//        Log.d(TAG+"Fasten", "Started");

        //check mBluetoothGatt is available
        if (bluetoothGatt == null) {
//            Log.e(TAG+"Fasten", "lost connection");
            sendDeviceConnectionToActivity(device.getAddress(), false);
            return;
        }

        bluetoothGatt.readCharacteristic(characteristicHelmetFasten);

        readVariables = true;
    }

    private void readHelmetTemperatureCharacteristic(){
//        Log.d(TAG+"Temperature", "Started");

        //check mBluetoothGatt is available
        if (bluetoothGatt == null) {
//            Log.e(TAG+"Fasten", "lost connection");
            sendDeviceConnectionToActivity(device.getAddress(), false);
            return;
        }

        bluetoothGatt.readCharacteristic(characteristicHelmetTemperature);

        readVariables = true;
        startMillis=System.currentTimeMillis(); // time marker
    }

    boolean hasTemperatureSensor(){
        return hasTemperatureSensor;
    }

    private boolean readBattery = false;
    void readBattery(){
        readBattery = true; // so that readsbattery but does not break the cycle in nextCharacteristic_ReadWriteVariables, if still running
        if(!readVariables){
            // if readvariable==false, we are not longer reading OnHead and OnFasten characteristic
            // periodically.
            nextCharacteristic_ReadWriteVariables(null); // it will read the battery level as readBattery was set to true
        }
    }

    private void readHelmetBatteryCharacteristic(){
//        Log.d(TAG+"Battery", "Started");

        //check mBluetoothGatt is available
        if (bluetoothGatt == null) {
//            Log.e(TAG+"Battery", "lost connection");
            sendDeviceConnectionToActivity(device.getAddress(), false);
            return;
        }

        bluetoothGatt.readCharacteristic(characteristicHelmetBattery);
    }

    private void setHelmetImpactNotification(){

        //check mBluetoothGatt is available
        if (bluetoothGatt == null) {
//            Log.e(TAG+"Impact", "lost connection");
            sendDeviceConnectionToActivity(device.getAddress(), false);
            return;
        }

        boolean status = bluetoothGatt.setCharacteristicNotification(characteristicHelmetImpact, true);
//        Log.d(TAG+"Notif", "Notif="+status);
    }

    public void helmetColorWrite(int color, BluetoothGattCharacteristic characteristic){
        //check mBluetoothGatt is available
        if (bluetoothGatt == null) {
//            Log.e(TAG, "lost connection");
            sendDeviceConnectionToActivity(device.getAddress(), false);
            return;
        }

        // convert to hexadecimal
        String pass1 = Integer.toString(color, 16);
        String value;
        if(color < 16){
            // if password1 has just one digit, Hex.stringToBytes() will throw an error
            value = "0" + pass1;
        } else {
            value = pass1;
        }

//        Log.d(TAG, "HelmetColor hexa = " +  pass1 + " value = " + value  );

        characteristic.setValue( Hex.stringToBytes(value) );
        bluetoothGatt.writeCharacteristic(characteristic);
    }

    private void bikeMAC_Write(String segment, BluetoothGattCharacteristic characteristic){
        //check mBluetoothGatt is available
        if (bluetoothGatt == null) {
//            Log.e(TAG, "lost connection");
            sendDeviceConnectionToActivity(device.getAddress(), false);
            return;
        }

//        Log.d(TAG, "BikeMAC hexa = " +  Hex.stringToBytes(segment) + " segment = " + segment  );

        characteristic.setValue( Hex.stringToBytes(segment) );
        bluetoothGatt.writeCharacteristic(characteristic);
    }

    private boolean phoneLowBattery = false;
    void phoneBatteryLow_Write(){
        phoneLowBattery = true;
        if(readVariables){
            // it is still reading variables
            readVariables = false; // stop reading variables, this will stop the cycle in nextCharacteristic_ReadWriteVariables
        } else {
            // it is no longer reading variables
            nextCharacteristic_ReadWriteVariables(null);
        }
    }

    private void phoneBatteryLow_WriteChar(){
        // in case phone is on low battery, HelmyC will establish the connection directly to HelmyM

        //check mBluetoothGatt is available
        if (bluetoothGatt == null) {
//            Log.e(TAG, "lost connection");
            sendDeviceConnectionToActivity(device.getAddress(), false);
            return;
        }

        byte[] value = new byte[1];
        value[0] = (byte) (0x01);
        characteristicPhoneLowBattery.setValue(value);

        bluetoothGatt.writeCharacteristic(characteristicPhoneLowBattery);
    }

    private boolean sendTresdholds = false;
    private float impact = 0, ang_inferior = 0, ang_superior = 0;
    void scheduleSendThredsholds(float impactTH, float ang_inferiorTH, float ang_superiorTH){
        impact = impactTH;
        ang_inferior = ang_inferiorTH;
        ang_superior = ang_superiorTH;
        if(characteristicHelmetThresholds != null){
            sendTresdholds = true; // nextCharacteristic_ReadWriteVariables will send them
        }
//        Log.d("RequestThresholds", "write threshold scheduled");
    }

    private void thresholds_WriteChar(){
        // in case phone is on low battery, HelmyC will establish the connection directly to HelmyM

        //check mBluetoothGatt is available
        if (bluetoothGatt == null) {
//            Log.e(TAG, "lost connection");
            sendDeviceConnectionToActivity(device.getAddress(), false);
            return;
        }

        // convert to hexa
        String th1 = Integer.toString((int) impact, 16);
        String value;
        if(impact < 16){
            // if password1 has just one digit, Hex.stringToBytes() will throw an error
            value = "0" + th1;
        } else {
            value = th1;
        }
        String th2 = Integer.toString((int) ang_inferior, 16);
        if(ang_inferior < 16){
            // if password1 has just one digit, Hex.stringToBytes() will throw an error
            value += "0" + th2;
        } else {
            value += th2;
        }
        String th3 = Integer.toString((int) ang_superior, 16);
        if(ang_superior < 16){
            // if password1 has just one digit, Hex.stringToBytes() will throw an error
            value += "0" + th3;
        } else {
            value += th3;
        }

//        Log.d("RequestThresholds", "value to send= " + value);

        characteristicHelmetThresholds.setValue(value);
        bluetoothGatt.writeCharacteristic(characteristicHelmetThresholds);

        sendTresdholds = false; // so that it does not keep sending the thresholds to HelmyC
    }

    private boolean paring = false;

    void pairingMode_writeChar(boolean pair){
        //check mBluetoothGatt is available
        if (bluetoothGatt == null) {
//            Log.e(TAG, "lost connection");
            sendDeviceConnectionToActivity(device.getAddress(), false);
            return;
        }

        byte[] value = new byte[1]; // 0 by default (pair == false)
        if(pair){
            value[0] = (byte) (0x01);
        }

        if(characteristicHelmetParing != null){
            characteristicHelmetParing.setValue(value);

            bluetoothGatt.writeCharacteristic(characteristicHelmetParing);
            paring = pair;
        }


    }

    // methods to send data to activities using broadcast receivers
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
    }

    private void sendActivityWriteColorStatus(boolean writeWasSuccessful) {
//        Log.d("HelmetColor", "Send Write Status: " + writeWasSuccessful);
        Intent intent = new Intent(Static_AppVariables.ACTIONFILTER_HELMET_COLOR);
        intent.putExtra(Static_AppVariables.INTENTEXTRA_HELMET_COLOR_WRITE, writeWasSuccessful);
        context.sendBroadcast(intent);
    }

    private void sendActivityWritePhoneLowBatteryStatus(boolean writeWasSuccessful) {
        Intent intent = new Intent(Static_AppVariables.ACTIONFILTER_HELMET_PHONE_LOWBATTERY);
        intent.putExtra(Static_AppVariables.INTENTEXTRA_HELMET_PHONE_LOWBATTERY, writeWasSuccessful);
        context.sendBroadcast(intent);
    }

}
