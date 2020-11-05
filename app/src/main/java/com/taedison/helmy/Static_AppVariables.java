package com.taedison.helmy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

/***
 * Variable used in different places across the app
 */
public class Static_AppVariables {

    // GATT Actions for broadcast receivers
    final static String ACTIONFILTER_GATT_CONNECTION =
            "ACTIONFILTER_GATT_CONNECTION";
    final static String ACTIONFILTER_DATA_AVAILABLE =
            "ACTIONFILTER_DATA_AVAILABLE";
    final static String ACTIONFILTER_BIKE_ONOFF =
            "ACTIONFILTER_BIKE_ONOFF";
    final static String ACTIONFILTER_BIKE_PASSWORD =
            "ACTIONFILTER_BIKE_PASSWORD";
    final static String ACTIONFILTER_BIKE_ENABLE =
            "ACTIONFILTER_BIKE_ENABLE";
    final static String ACTIONFILTER_BIKE_ID =
            "ACTIONFILTER_BIKE_ID";


    final static String ACTIONFILTER_HELMET_COLOR =
            "ACTIONFILTER_HELMET_COLOR";
    final static String ACTIONFILTER_HELMET_PHONE_LOWBATTERY =
            "ACTIONFILTER_HELMET_PHONE_LOWBATTERY"; //used for disconnecting phone from HelmyC, thus HelmyC connects directly to HelmyM

    // String identifiers of extras in intents
    final static String INTENTEXTRA_HELMET_DATA = "HelmetData";
    final static String INTENTEXTRA_BLE_CONNECTION = "Connected";
    final static String INTENTEXTRA_BLE_MAC = "MAC";
    final static String INTENTEXTRA_BIKE_ONOFF_WRITE = "Bike_OnOff";
    final static String INTENTEXTRA_BIKE_PASSWORD_WRITE = "Bike_password";
    final static String INTENTEXTRA_BIKE_ENABLE_WRITE = "Bike_enable";
    final static String INTENTEXTRA_BIKE_ID_WRITE = "Bike_Id";

    final static String INTENTEXTRA_HELMET_COLOR_WRITE = "Helmet_color";
    final static String INTENTEXTRA_HELMET_PHONE_LOWBATTERY = "Helmet_Phone_BatteryLow";

    final static String INTENTEXTRA_BLUETOOTHOFF = "bluetoothOff";


    // GATT ServiceHelmyM and characteristics - Bike
    final static UUID UUID_HELMYM_SERVICE =
            UUID.fromString("008E1343-0000-0000-0000-000000000000");

    final static UUID UUID_HELMYM_CAR_TURN_ON_OFF =
            UUID.fromString("E4AEA42C-5124-496D-9E18-31B86AB32DF5");

    final static UUID UUID_HELMYM_CAR_RPM =
            UUID.fromString("E66ACF09-559C-45BB-A6B2-99DEBD3B84B4");

    final static UUID UUID_HELMYM_CAR_PASSWORD_1 =
            UUID.fromString("C90075B5-E0A7-4B28-89EE-719A04E976BD");

    final static UUID UUID_HELMYM_CAR_PASSWORD_2 =
            UUID.fromString("000075B0-0000-1000-8000-00805F9B34F0");

    final static UUID UUID_HELMYM_CAR_PASSWORD_3 =
            UUID.fromString("000075B5-0000-1000-8000-00805F9B34F1");

    final static UUID UUID_HELMYM_CAR_ENABLE =
            UUID.fromString("1D2B3249-0D90-4A27-B275-19CF8C7E18F1");

    final static UUID UUID_HELMYM_ID =
            UUID.fromString("55A259B0-CA46-4882-891B-E3CDDD1503AE");






    // GATT ServiceHelmyC and characteristics - Helmet driver
    final static UUID UUID_HELMYC_SERVICE =
            UUID.fromString("008E134C-0000-0000-0000-000000000000");

    final static UUID UUID_HELMYC_CAR_HELMETONHEAD =
            UUID.fromString("E4AEA42C-5124-496D-9E18-31B86AB32DF4");//

    final static UUID UUID_HELMYC_CAR_HELMET_FASTEN =
            UUID.fromString("E4AEA42C-5124-496D-9E18-31B86AB32DF3");//

    final static UUID UUID_HELMYC_CAR_HELMET_IMPACT =
            UUID.fromString("E4AEA42C-5124-496D-9E18-31B86AB32DF2");//

    final static UUID UUID_HELMYC_CAR_HELMET_BATTERY =
            UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB");//

    final static UUID UUID_HELMYC_COLOR_RED =
            UUID.fromString("0000949B-0000-1000-8000-00805F9B34FB");//

    final static UUID UUID_HELMYC_COLOR_BLUE =
            UUID.fromString("000060F4-0000-1000-8000-00805F9B34FB");//

    final static UUID UUID_HELMYC_COLOR_GREEN =
            UUID.fromString("0000B1D3-0000-1000-8000-00805F9B34FB");//

    final static UUID UUID_HELMYC_MAC_SEG1 =
            UUID.fromString("7B4DEB30-8609-48E7-9B41-0A4CAAE8ACBA");//

    final static UUID UUID_HELMYC_MAC_SEG2 =
            UUID.fromString("60EF56FF-E869-49F3-BAD2-C3E0C53E4922");//

    final static UUID UUID_HELMYC_MAC_SEG3 =
            UUID.fromString("A2EA81DE-8AD1-4037-855F-00556E627088");//

    final static UUID UUID_HELMYC_MAC_SEG4 =
            UUID.fromString("C21F72BE-87E4-4241-9B1D-5A43A7541B22");//

    final static UUID UUID_HELMYC_MAC_SEG5 =
            UUID.fromString("A5CE5C43-F3AF-45FC-BB92-DB178B5059C9");//

    final static UUID UUID_HELMYC_MAC_SEG6 =
            UUID.fromString("38584E7A-1B66-4763-8193-0A04835E526B");//

    final static UUID UUID_HELMYC_PHONE_LOWBATTERY =
            UUID.fromString("127BC4AA-BDBA-43CB-B6B5-10C1CB6B080B");//

    final static UUID UUID_HELMYC_TEMPERATURE =
            UUID.fromString("2D4391AF-C1EB-40F2-9B26-5CA1171515C3");//

    final static UUID UUID_HELMYC_THRESHOLDS =
            UUID.fromString("238B6198-6EB4-4175-9ECF-56638CD46A99");//

    final static UUID UUID_HELMYC_INTERCOMM_PAIRINGMODE =
            UUID.fromString("DC12F725-291F-4F3F-8BA4-D20B4117A38C");//



    // EMERGENCY
    final static String ACTIONFILTER_EMERGENCY =
            "ACTIONFILTER_EMERGENCY";


    // Notification IDs
    static int notifID_Emergency = 11;
    static int notifCancelBtn_requestCode_Emergency = 1;
    static int notifID_BikeDisconnected = 22;
    static int notifCancelBtn_requestCode_BikeDisconnected = 2;

    // Array lists for spinners
    static ArrayList<String> arrayRHs = new ArrayList<>( Arrays.asList("O+", "O-", "A+", "A-", "B+", "B-", "AB+", "AB-"));

    // INTENT EXTRAS BETWEEN ACTIVITIES

    // Emergency
    static String INTENTEXTRA_ALERT_SENT = "alert_sent";
    static String ACTIONFILTER_ALERT_SENT = "ACTIONFILTER_ALERT";

    // Velocity
    static String FILENAME_VELOCITY_FILE_NAMES = "velocityFileNames";

    // REQUEST CODE
    // ActivitySplash
    static final int REQUESTCODE_PERMISSIONS = 100;
    static final int REQUESTCODE_TTS_CHECK = 200;
    //ActivityGoAs
    static final int REQUESTCODE_BT_ENABLE_HELMY = 300;
    static final int REQUESTCODE_TURNON_BLUETOOTH = 400;
    static final int REQUESTCODE_TURNON_BLUETOOTH_FROM_PILLION = 450;
    static final int REQUESTCODE_TURNON_BLUETOOTH_FROM_INTERCOM = 475;
    static final int REQUESTCODE_TURNON_GPS = 500;

    //PRIMARY SERVER URLs
    static final String url_login = "https://www.helmy.com.co/models/model-login.php";
    static final String url_forgotPassword = "https://www.helmy.com.co/models/model-requestPassword.php";
    static final String url_social = "https://www.helmy.com.co/models/model-social.php";
    static final String url_newAccount = "https://www.helmy.com.co/models/model-signup.php";
    static final String url_emailAgain = "https://www.helmy.com.co/models/model-requestAccount.php";
    static final String url_personOne = "https://www.helmy.com.co/models/model-personOne.php";
    static final String url_personTwo = "https://www.helmy.com.co/models/model-personTwo.php";
    static final String url_personThree = "https://www.helmy.com.co/models/model-personThree.php";
    static final String url_personFour = "https://www.helmy.com.co/models/model-personFour.php";
    static final String url_personFive = "https://www.helmy.com.co/models/model-personFive.php";
    static final String url_motorcycle = "https://www.helmy.com.co/models/model-motorcycle.php";
    static final String url_helmet = "https://www.helmy.com.co/models/model-helmet.php";
    static final String url_txtVelocity = "https://www.helmy.com.co/models/model-txt.php";
    static final String url_impact = "https://www.helmy.com.co/models/model-detection.php";
    static final String url_thresholds = "https://www.helmy.com.co/models/model-impact.php";
    static final String url_dataLoginUpdates = "https://www.helmy.com.co/models/model-update.php";
    // Blockchain
    static final String url_blockchainRegister = "https://blockchain.helmy.com.co/register";
    static final String url_blockchainDelete_fromBikeID = "https://blockchain.helmy.com.co/deleteFromBikeId";
    static final String url_blockchainGetBondIDfromBikeID = "https://blockchain.helmy.com.co/queryBondId_fromBikeId";
    static final String url_blockchainGetUserIDfromBikeID = "https://blockchain.helmy.com.co/queryUserId_fromBikeId";

    //SECONDARY SERVER URLs
    static final String url_login2 = "http://www.helmy2.com.co/models/model-login.php";
    static final String url_forgotPassword2 = "http://www.helmy2.com.co/models/model-requestPassword.php";
    static final String url_social2 = "http://www.helmy2.com.co/models/model-social.php";
    static final String url_newAccount2 = "http://www.helmy2.com.co/models/model-signup.php";
    static final String url_emailAgain2 = "http://www.helmy2.com.co/models/model-requestAccount.php";
    static final String url_personOne2 = "http://www.helmy2.com.co/models/model-personOne.php";
    static final String url_personTwo2 = "http://www.helmy2.com.co/models/model-personTwo.php";
    static final String url_personThree2 = "http://www.helmy2.com.co/models/model-personThree.php";
    static final String url_personFour2 = "http://www.helmy2.com.co/models/model-personFour.php";
    static final String url_personFive2 = "http://www.helmy2.com.co/models/model-personFive.php";
    static final String url_motorcycle2 = "http://www.helmy2.com.co/models/model-motorcycle.php";
    static final String url_helmet2 = "http://www.helmy2.com.co/models/model-helmet.php";
    static final String url_txtVelocity2 = "http://www.helmy2.com.co/models/model-txt.php";
    static final String url_impact2 = "http://www.helmy2.com.co/models/model-detection.php";
    static final String url_thresholds2 = "http://www.helmy2.com.co/models/model-impact.php";
    static final String url_dataLoginUpdates2 = "http://www.helmy2.com.co/models/model-update.php";
    // Blockchain
    static final String url_blockchainRegister2 = "http://blockchain.helmy2.com.co/register";
    static final String url_blockchainDelete_fromBikeID2 = "http://blockchain.helmy2.com.co/deleteFromBikeId";
    static final String url_blockchainGetBondIDfromBikeID2 = "http://blockchain.helmy2.com.co/queryBondId_fromBikeId";
    static final String url_blockchainGetUserIDfromBikeID2 = "http://blockchain.helmy2.com.co/queryUserId_fromBikeId";

}
