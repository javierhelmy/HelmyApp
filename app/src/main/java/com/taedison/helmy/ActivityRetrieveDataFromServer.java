package com.taedison.helmy;

import androidx.appcompat.app.AppCompatActivity;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/***
 * Activity for retriving all data from the server
 * All data is received encrypted, and we store it encrypted in the preferences
 * If any of the retrieving data requests fails, the user cannot continue
 * If user closed the activity before downloading and saving the data, then we should comeback
 * to this activity
 */
public class ActivityRetrieveDataFromServer extends AppCompatActivity {
    private final String TAG = "VolleyRetrieve";

    //Views
    ImageView imageCircle;
    TextView tvMessage, tvBtnRetry;

    //Volley
    private RequestQueue requestQueue;
    // preferences
    SingletonSharedPreferences preferences;

    private HashMap<String, JSONObject> url_JSON_hashMap = new HashMap<>(); // maps the URL to the JSON response
    private ArrayList<String> urlsArrayList = new ArrayList<>();
    private int contRequest = 0;
    private AnimatorSet circleRotationAnim;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_retrieve_data_from_server);

        imageCircle = findViewById(R.id.imgCircle);
        tvMessage = findViewById(R.id.tvDownloading);
        tvBtnRetry = findViewById(R.id.tvBtnRetry);

        // animation
        ObjectAnimator rotation = ObjectAnimator.ofFloat(imageCircle, "rotation", 360);
        rotation.setDuration(2000);
        rotation.setRepeatCount(Animation.INFINITE);
        circleRotationAnim = new AnimatorSet();
        circleRotationAnim.play(rotation);
        circleRotationAnim.start();

        // volley
        requestQueue = SingletonVolley.getInstance(this.getApplicationContext()).getRequestQueue();
        // preferences
        preferences = SingletonSharedPreferences.getInstance(this.getApplicationContext());

        // array for requesting in sequence
        if(preferences.get_isPrimaryServerDown()){
            // primary server is down, so we will use the secondary
            urlsArrayList.add(Static_AppVariables.url_personOne2);
            urlsArrayList.add(Static_AppVariables.url_personTwo2);
            urlsArrayList.add(Static_AppVariables.url_personThree2);
            urlsArrayList.add(Static_AppVariables.url_personFour2);
            urlsArrayList.add(Static_AppVariables.url_personFive2);
            urlsArrayList.add(Static_AppVariables.url_helmet2);
            urlsArrayList.add(Static_AppVariables.url_motorcycle2);
        } else {
            urlsArrayList.add(Static_AppVariables.url_personOne);
            urlsArrayList.add(Static_AppVariables.url_personTwo);
            urlsArrayList.add(Static_AppVariables.url_personThree);
            urlsArrayList.add(Static_AppVariables.url_personFour);
            urlsArrayList.add(Static_AppVariables.url_personFive);
            urlsArrayList.add(Static_AppVariables.url_helmet);
            urlsArrayList.add(Static_AppVariables.url_motorcycle);
        }


        StringRequest joRequest = request(preferences.get_lastUser_Id_logged(), urlsArrayList.get(contRequest));
        requestQueue.add(joRequest);
    }

    private StringRequest request(final String userId_fromServer, final String url){

        return new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response)
                    {
                        Log.d(TAG, url + " " + response);
                        if(!TextUtils.isEmpty(response)){
                            // response is status=0 if there is no data saved in the server for the current user, but never empty.
                            // in case it is empty, then it is like that it was an internet connection error
                            try {
                                JSONObject jsonArray = new JSONObject(response);
                                url_JSON_hashMap.put(url, jsonArray);
                                if(urlsArrayList.size() > contRequest +1){
                                    contRequest++;
                                    requestQueue.add(request(preferences.get_lastUser_Id_logged(), urlsArrayList.get(contRequest)));
                                } else {
                                    saveDataInPreferences();
                                }
                            } catch (JSONException e) {
                                Log.e(TAG, e.toString());
                                Toast.makeText(ActivityRetrieveDataFromServer.this, R.string.errorWithServer, Toast.LENGTH_SHORT).show();
                                imageCircle.setVisibility(View.INVISIBLE);
                                tvBtnRetry.setVisibility(View.VISIBLE);
                            }
                        } else {
                            // most likely the user has mobile data on but without internet service. Worst case, something went wrong with the server
                            Static_AppMethods.ToastCheckYourInternet(ActivityRetrieveDataFromServer.this);
                            imageCircle.setVisibility(View.INVISIBLE);
                            tvBtnRetry.setVisibility(View.VISIBLE);
                            tvMessage.setText(getResources().getString(R.string.checkInternetConnection));
                        }
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                        Log.d(TAG, error.toString());
                        Static_AppMethods.ToastCheckYourInternet(ActivityRetrieveDataFromServer.this);
                        imageCircle.setVisibility(View.INVISIBLE);
                        tvBtnRetry.setVisibility(View.VISIBLE);
                        tvMessage.setText(getResources().getString(R.string.checkInternetConnection));
                        Static_AppMethods.checkResponseCode(error, preferences);
                    }
                })
        {
            @Override
            protected Map<String, String> getParams()
            {
                Log.d(TAG, "userId= " + userId_fromServer);
                Map<String, String> params = new HashMap<>();
                params.put("userId", userId_fromServer);
                return params;
            }
        };
    }

//    private void saveDataInPreferences(){
//        // the data stored in the server is the same encrypted data stored in preferences,
//        // reason why we stored the retrieved data in preferences as it comes from the server
//        for(Map.Entry<String, JSONObject> entry : url_JSON_hashMap.entrySet()){
//            switch (entry.getKey()){
//                case Static_AppVariables.url_personOne:
//                    try {
//                        JSONObject jsonArray = entry.getValue();
//                        String nationality = jsonArray.getString("nationality");
//                        String documentType = jsonArray.getString("documentType");
//                        String documentNumber = jsonArray.getString("documentNumber");
//                        String colombianLicense = jsonArray.getString("colombianLicense");
//
//                        if( !TextUtils.isEmpty(nationality) ){
//                            preferences.setUserNationality_encrypted(nationality);
//                            preferences.setUserIDType_encrypted(documentType);
//                            preferences.setUserIDnum_encrypted(documentNumber);
//                            preferences.setUser_isColLicense_encrypted(colombianLicense);
//                        }
//                        Log.d(TAG, "success person one" + preferences.getUserIDType()
//                                + "nat= " + nationality + "type= " + documentType);
//                    } catch (Exception e) {
//                        // JSON contains status=0, or error saving into preferences
//                        Log.e(TAG, e.toString());
//                    }
//                    break;
//                case Static_AppVariables.url_personTwo:
//                    try {
//                        JSONObject jsonArray = entry.getValue();
//                        String licenseNumber = jsonArray.getString("licenseNumber");
//                        String names = jsonArray.getString("names");
//                        String surnames = jsonArray.getString("surnames");
//                        String age = jsonArray.getString("age");
//                        String rh = jsonArray.getString("rh");
//                        if( !TextUtils.isEmpty(licenseNumber) ){
//                            preferences.setUserLicenseNum_encrypted(licenseNumber);
//                            preferences.setUserNames_encrypted(names);
//                            preferences.setUserSurnames_encrypted(surnames);
//                            preferences.setUserAge_encrypted(age);
//                            preferences.setUserRH_encrypted(rh);
//                        }
//                        Log.d(TAG, "success person two");
//                    } catch (Exception e) {
//                        // JSON contains status=0, or error saving into preferences
//                        Log.e(TAG, e.toString());
//                    }
//                    break;
//                case Static_AppVariables.url_personThree:
//                    try {
//                        JSONObject jsonArray = entry.getValue();
//                        String sex = jsonArray.getString("sex");
//                        String eps = jsonArray.getString("eps");
//                        String arl = jsonArray.getString("arl");
//                        String phone = jsonArray.getString("phone");
//
//                        if( !TextUtils.isEmpty(sex) ){
//                            preferences.setUserSex_encrypted(sex);
//                            preferences.setUserEPS_encrypted(eps);
//                            preferences.setUserARL_encrypted(arl);
//                            preferences.setUserPhone_encrypted(phone);
//                        }
//                        Log.d(TAG, "success person three");
//                    } catch (Exception e) {
//                        // JSON contains status=0, or error saving into preferences
//                        Log.e(TAG, e.toString());
//                    }
//                    break;
//                case Static_AppVariables.url_personFour:
//                    try {
//                        JSONObject jsonArray = entry.getValue();
//                        String firstEmergencyContactNames = jsonArray.getString("firstEmergencyContactNames");
//                        String surnamesFirstEmergencyContact = jsonArray.getString("surnamesFirstEmergencyContact");
//                        String firstEmergencyContactNumber = jsonArray.getString("firstEmergencyContactNumber");
//
//                        if( !TextUtils.isEmpty(firstEmergencyContactNames) ){
//                            preferences.setUserEmergencyNames_encrypted(firstEmergencyContactNames);
//                            preferences.setUserEmergencySurnames_encrypted(surnamesFirstEmergencyContact);
//                            preferences.setUserEmergencyPhone_encrypted(firstEmergencyContactNumber);
//                        }
//                        Log.d(TAG, "success person four");
//                    } catch (Exception e) {
//                        // JSON contains status=0, or error saving into preferences
//                        Log.e(TAG, e.toString());
//                    }
//                    break;
//                case Static_AppVariables.url_personFive:
//                    try {
//                        JSONObject jsonArray = entry.getValue();
//                        String namesSecondEmergencyContact = jsonArray.getString("namesSecondEmergencyContact");
//                        String surnamesSecondEmergencyContact = jsonArray.getString("surnamesSecondEmergencyContact");
//                        String numberSecondEmergencyContact = jsonArray.getString("numberSecondEmergencyContact");
//
//                        if( !TextUtils.isEmpty(namesSecondEmergencyContact) ){
//                            preferences.setUserEmergencyNames2_encrypted(namesSecondEmergencyContact);
//                            preferences.setUserEmergencySurnames2_encrypted(surnamesSecondEmergencyContact);
//                            preferences.setUserEmergencyPhone2_encrypted(numberSecondEmergencyContact);
//                        }
//                        Log.d(TAG, "success person five");
//                    } catch (Exception e) {
//                        // JSON contains status=0, or error saving into preferences
//                        Log.e(TAG, e.toString());
//                        preferences.deleteSecondContact();
//                    }
//                    break;
//                case Static_AppVariables.url_helmet:
//                    // the server stores brand and size of the helmet with encryption, the rest is not sensitive data to the user
//                    String helmetMac = "";
//                    try {
//                        JSONObject jsonArray = entry.getValue();
//                        int numHelmets = jsonArray.getInt("numHelmets");
//                        if( numHelmets > 0 ){
//                            for(int idx = 0; idx < numHelmets; idx++){
//                                String alias = jsonArray.getString("alias" + idx);
//                                String brand = jsonArray.getString("brand" + idx);
//                                String size = jsonArray.getString("size" + idx);
//                                String customColor = jsonArray.getString("customColor" + idx);
//                                String intercom_mac = jsonArray.getString("intercom_mac" + idx);
//                                helmetMac = jsonArray.getString("mac" + idx);
//                                preferences.saveHelmetPreferences(helmetMac);
//                                preferences.setHelmetAssociatedBluetoothClassic( helmetMac, intercom_mac ); // Associate bluetooth classic
//                                preferences.setHelmetNickname(helmetMac, alias );
//                                preferences.setHelmetBrand_encrypted(helmetMac, brand );
//                                preferences.setHelmetSize_encrypted(helmetMac, size );
//                                preferences.setHelmetColor(helmetMac, Integer.parseInt(customColor) );
//                            }
//                        }
//                        if( numHelmets == 1 ){
//                            // if there is only one helmet, then set it as the primary
//                            preferences.savePrimaryHelmetPreferences(helmetMac);
//                        }
//                        Log.d(TAG, "success helmet");
//                    } catch (Exception e){
//                        // JSON contains status=0, or there was an error saving data into prefereces. Delete helmet
//                        Log.e(TAG, e.toString());
//                        preferences.deleteHelmetFromPreferences(helmetMac);
//                    }
//                    break;
//                case Static_AppVariables.url_motorcycle:
//                    String bikeMac = "";
//                    try {
//                        JSONObject jsonArray = entry.getValue();
//                        int numMotorcycles = jsonArray.getInt("numMotorcycle");
//                        if( numMotorcycles > 0 ){
//                            for(int idx = 0; idx < numMotorcycles; idx++){
//                                String alias = jsonArray.getString("alias" + idx);
//                                String policySoat = jsonArray.getString("policySoat" + idx);
//                                String noPolicyTwo = jsonArray.getString("noPolicyTwo" + idx);
//                                String policyTelephoneTwo = jsonArray.getString("policyTelephoneTwo" + idx);
//                                String brand = jsonArray.getString("brand" + idx);
//                                String chassis = jsonArray.getString("chassis" + idx);
//                                String codeM = jsonArray.getString("codeM" + idx);
//                                bikeMac = jsonArray.getString("mac" + idx);
//                                String plate = jsonArray.getString("plate" + idx);
//                                String threeDigitsWheelReference = jsonArray.getString("threeDigitsWheelReference" + idx);
//                                String threeDigitsBackupPowerKey = jsonArray.getString("threeDigitsBackupPowerKey" + idx);
//
//                                try {
//                                    // decrypt wheelref and password so that we can seperate the digits
//                                    String wheelRefDecrypted = Static_AppMethods.decryptAES_toUTF8(this, threeDigitsWheelReference, preferences.get_lastUser_email_logged(), preferences);
//                                    String passwordDecrypted = Static_AppMethods.decryptAES_toUTF8(this, threeDigitsBackupPowerKey, preferences.get_lastUser_email_logged(), preferences);
//
//                                    String[] reference = wheelRefDecrypted.split(";");
//                                    String[] password = passwordDecrypted.split(";");
//                                    if(reference.length == 3 &&  password.length == 3){
//                                        // now encrypt again
//                                        String email = preferences.get_lastUser_email_logged();
//                                        String widthEncrypted = Static_AppMethods.encryptAES_toString64(ActivityRetrieveDataFromServer.this,
//                                                reference[0], email, preferences);
//                                        String percentageEncrypted = Static_AppMethods.encryptAES_toString64(ActivityRetrieveDataFromServer.this,
//                                                reference[1], email, preferences);
//                                        String diameterEncrypted = Static_AppMethods.encryptAES_toString64(ActivityRetrieveDataFromServer.this,
//                                                reference[2], email, preferences);
//                                        String pass1Encrypted = Static_AppMethods.encryptAES_toString64(ActivityRetrieveDataFromServer.this,
//                                                password[0], email, preferences);
//                                        String pass2Encrypted = Static_AppMethods.encryptAES_toString64(ActivityRetrieveDataFromServer.this,
//                                                password[1], email, preferences);
//                                        String pass3Encrypted = Static_AppMethods.encryptAES_toString64(ActivityRetrieveDataFromServer.this,
//                                                password[2], email, preferences);
//
//                                        preferences.saveBikePreferences(bikeMac);
//                                        preferences.setBikeNickname(bikeMac, alias);
//                                        preferences.saveBikeSOAT_encrypted(bikeMac, policySoat);
//                                        preferences.saveBike2ndPolicy_encrypted(bikeMac, noPolicyTwo);
//                                        preferences.saveBike2ndPolicyPhone_encrypted(bikeMac, policyTelephoneTwo);
//                                        preferences.saveBikeBrand_encrypted(bikeMac, brand);
//                                        preferences.saveBikeChasis_encrypted(bikeMac, chassis);
//                                        preferences.saveBikePlate_encrypted(bikeMac, plate);
//                                        preferences.saveBikePreferences(bikeMac);
//                                        preferences.saveBikeId_encrypted(bikeMac, codeM);
//
//                                        preferences.saveBikeTireWidth_encrypted( bikeMac, widthEncrypted );
//                                        preferences.saveBikeTirePercentage_encrypted( bikeMac, percentageEncrypted );
//                                        preferences.saveBikeWheelDiameter_encrypted( bikeMac, diameterEncrypted );
//
//                                        preferences.saveBikePass1_encrypted(bikeMac, pass1Encrypted );
//                                        preferences.saveBikePass2_encrypted(bikeMac, pass2Encrypted);
//                                        preferences.saveBikePass3_encrypted(bikeMac, pass3Encrypted );
//                                    }
//                                } catch (Exception e) {
//                                    Log.e(TAG+"Encrypt", "Error= " + e.getMessage());
//                                }
//                            }
//                        }
//                        if(numMotorcycles == 1){
//                            // if there is only one bike, then set it as the primary bike
//                            preferences.savePrimaryBikePreferences(bikeMac);
//                        }
//                        Log.d(TAG, "success bike");
//                    }  catch (Exception e){
//                        // JSON contains status=0, or there was an error saving data into prefereces. Delete bike
//                        Log.e(TAG, e.toString());
//                        preferences.deleteBikeFromPreferences(bikeMac);
//                    }
//                    break;
//                default:
//                    break;
//            }
//        }
//
//        Intent intent;
//        if( !preferences.allPersonalInfoWasSaved() || TextUtils.isEmpty(preferences.getUserEmergencyPhone())
//                || !(preferences.get_helmets_saved_MACs().size() > 0) ){
//            // if user has not entered personal information, emergency contacts or registered one HelmyC
//            intent = new Intent(this, ActivityProgress.class);
//        } else {
//            preferences.userRegistered_data_devices();
//            intent = new Intent(this, ActivityGoAs.class);
//        }
//        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
//        startActivity(intent);
//    }

    private void saveDataInPreferences(){
        // the data stored in the server is the same encrypted data stored in preferences,
        // reason why we stored the retrieved data in preferences as it comes from the server

        try {
            JSONObject jsonArray = url_JSON_hashMap.get(urlsArrayList.get(0)); // personOne
            String nationality = jsonArray.getString("nationality");
            String documentType = jsonArray.getString("documentType");
            String documentNumber = jsonArray.getString("documentNumber");
            String colombianLicense = jsonArray.getString("colombianLicense");

            if( !TextUtils.isEmpty(nationality) ){
                preferences.setUserNationality_encrypted(nationality);
                preferences.setUserIDType_encrypted(documentType);
                preferences.setUserIDnum_encrypted(documentNumber);
                preferences.setUser_isColLicense_encrypted(colombianLicense);
            }
            Log.d(TAG, "success person one" + preferences.getUserIDType()
                    + "nat= " + nationality + "type= " + documentType);
        } catch (Exception e) {
            // JSON contains status=0, or error saving into preferences
            Log.e(TAG, e.toString());
        }

        try {
            JSONObject jsonArray = url_JSON_hashMap.get(urlsArrayList.get(1)); // personTwo
            String licenseNumber = jsonArray.getString("licenseNumber");
            String names = jsonArray.getString("names");
            String surnames = jsonArray.getString("surnames");
            String age = jsonArray.getString("age");
            String rh = jsonArray.getString("rh");
            if( !TextUtils.isEmpty(licenseNumber) ){
                preferences.setUserLicenseNum_encrypted(licenseNumber);
                preferences.setUserNames_encrypted(names);
                preferences.setUserSurnames_encrypted(surnames);
                preferences.setUserAge_encrypted(age);
                preferences.setUserRH_encrypted(rh);
            }
            Log.d(TAG, "success person two");
        } catch (Exception e) {
            // JSON contains status=0, or error saving into preferences
            Log.e(TAG, e.toString());
        }

        try {
            JSONObject jsonArray = url_JSON_hashMap.get(urlsArrayList.get(2)); // personThree
            String sex = jsonArray.getString("sex");
            String eps = jsonArray.getString("eps");
            String arl = jsonArray.getString("arl");
            String phone = jsonArray.getString("phone");

            if( !TextUtils.isEmpty(sex) ){
                preferences.setUserSex_encrypted(sex);
                preferences.setUserEPS_encrypted(eps);
                preferences.setUserARL_encrypted(arl);
                preferences.setUserPhone_encrypted(phone);
            }
            Log.d(TAG, "success person three");
        } catch (Exception e) {
            // JSON contains status=0, or error saving into preferences
            Log.e(TAG, e.toString());
        }

        try {
            JSONObject jsonArray = url_JSON_hashMap.get(urlsArrayList.get(3)); // personFour
            String firstEmergencyContactNames = jsonArray.getString("firstEmergencyContactNames");
            String surnamesFirstEmergencyContact = jsonArray.getString("surnamesFirstEmergencyContact");
            String firstEmergencyContactNumber = jsonArray.getString("firstEmergencyContactNumber");

            if( !TextUtils.isEmpty(firstEmergencyContactNames) ){
                preferences.setUserEmergencyNames_encrypted(firstEmergencyContactNames);
                preferences.setUserEmergencySurnames_encrypted(surnamesFirstEmergencyContact);
                preferences.setUserEmergencyPhone_encrypted(firstEmergencyContactNumber);
            }
            Log.d(TAG, "success person four");
        } catch (Exception e) {
            // JSON contains status=0, or error saving into preferences
            Log.e(TAG, e.toString());
        }

        try {
            JSONObject jsonArray = url_JSON_hashMap.get(urlsArrayList.get(4)); // personFive
            String namesSecondEmergencyContact = jsonArray.getString("namesSecondEmergencyContact");
            String surnamesSecondEmergencyContact = jsonArray.getString("surnamesSecondEmergencyContact");
            String numberSecondEmergencyContact = jsonArray.getString("numberSecondEmergencyContact");

            if( !TextUtils.isEmpty(namesSecondEmergencyContact) ){
                preferences.setUserEmergencyNames2_encrypted(namesSecondEmergencyContact);
                preferences.setUserEmergencySurnames2_encrypted(surnamesSecondEmergencyContact);
                preferences.setUserEmergencyPhone2_encrypted(numberSecondEmergencyContact);
            }
            Log.d(TAG, "success person five");
        } catch (Exception e) {
            // JSON contains status=0, or error saving into preferences
            Log.e(TAG, e.toString());
            preferences.deleteSecondContact();
        }

        // the server stores brand and size of the helmet with encryption, the rest is not sensitive data to the user
        String helmetMac = "";
        try {
            JSONObject jsonArray = url_JSON_hashMap.get(urlsArrayList.get(5)); // helmet
            int numHelmets = jsonArray.getInt("numHelmets");
            if( numHelmets > 0 ){
                for(int idx = 0; idx < numHelmets; idx++){
                    String alias = jsonArray.getString("alias" + idx);
                    String brand = jsonArray.getString("brand" + idx);
                    String size = jsonArray.getString("size" + idx);
                    String customColor = jsonArray.getString("customColor" + idx);
                    String intercom_mac = jsonArray.getString("intercom_mac" + idx);
                    helmetMac = jsonArray.getString("mac" + idx);
                    preferences.saveHelmetPreferences(helmetMac);
                    preferences.setHelmetAssociatedBluetoothClassic( helmetMac, intercom_mac ); // Associate bluetooth classic
                    preferences.setHelmetNickname(helmetMac, alias );
                    preferences.setHelmetBrand_encrypted(helmetMac, brand );
                    preferences.setHelmetSize_encrypted(helmetMac, size );
                    preferences.setHelmetColor(helmetMac, Integer.parseInt(customColor) );
                }
            }
            if( numHelmets == 1 ){
                // if there is only one helmet, then set it as the primary
                preferences.savePrimaryHelmetPreferences(helmetMac);
            }
            Log.d(TAG, "success helmet");
        } catch (Exception e){
            // JSON contains status=0, or there was an error saving data into prefereces. Delete helmet
            Log.e(TAG, e.toString());
            preferences.deleteHelmetFromPreferences(helmetMac);
        }

        String bikeMac = "";
        try {
            JSONObject jsonArray = url_JSON_hashMap.get(urlsArrayList.get(6)); // motorcycle
            int numMotorcycles = jsonArray.getInt("numMotorcycle");
            if( numMotorcycles > 0 ){
                for(int idx = 0; idx < numMotorcycles; idx++){
                    String alias = jsonArray.getString("alias" + idx);
                    String policySoat = jsonArray.getString("policySoat" + idx);
                    String noPolicyTwo = jsonArray.getString("noPolicyTwo" + idx);
                    String policyTelephoneTwo = jsonArray.getString("policyTelephoneTwo" + idx);
                    String brand = jsonArray.getString("brand" + idx);
                    String chassis = jsonArray.getString("chassis" + idx);
                    String codeM = jsonArray.getString("codeM" + idx);
                    bikeMac = jsonArray.getString("mac" + idx);
                    String plate = jsonArray.getString("plate" + idx);
                    String threeDigitsWheelReference = jsonArray.getString("threeDigitsWheelReference" + idx);
                    String threeDigitsBackupPowerKey = jsonArray.getString("threeDigitsBackupPowerKey" + idx);

                    try {
                        // decrypt wheelref and password so that we can seperate the digits
                        String wheelRefDecrypted = Static_AppMethods.decryptAES_toUTF8(this, threeDigitsWheelReference, preferences.get_lastUser_email_logged(), preferences);
                        String passwordDecrypted = Static_AppMethods.decryptAES_toUTF8(this, threeDigitsBackupPowerKey, preferences.get_lastUser_email_logged(), preferences);

                        String[] reference = wheelRefDecrypted.split(";");
                        String[] password = passwordDecrypted.split(";");
                        if(reference.length == 3 &&  password.length == 3){
                            // now encrypt again
                            String email = preferences.get_lastUser_email_logged();
                            String widthEncrypted = Static_AppMethods.encryptAES_toString64(ActivityRetrieveDataFromServer.this,
                                    reference[0], email, preferences);
                            String percentageEncrypted = Static_AppMethods.encryptAES_toString64(ActivityRetrieveDataFromServer.this,
                                    reference[1], email, preferences);
                            String diameterEncrypted = Static_AppMethods.encryptAES_toString64(ActivityRetrieveDataFromServer.this,
                                    reference[2], email, preferences);
                            String pass1Encrypted = Static_AppMethods.encryptAES_toString64(ActivityRetrieveDataFromServer.this,
                                    password[0], email, preferences);
                            String pass2Encrypted = Static_AppMethods.encryptAES_toString64(ActivityRetrieveDataFromServer.this,
                                    password[1], email, preferences);
                            String pass3Encrypted = Static_AppMethods.encryptAES_toString64(ActivityRetrieveDataFromServer.this,
                                    password[2], email, preferences);

                            preferences.saveBikePreferences(bikeMac);
                            preferences.setBikeNickname(bikeMac, alias);
                            preferences.saveBikeSOAT_encrypted(bikeMac, policySoat);
                            preferences.saveBike2ndPolicy_encrypted(bikeMac, noPolicyTwo);
                            preferences.saveBike2ndPolicyPhone_encrypted(bikeMac, policyTelephoneTwo);
                            preferences.saveBikeBrand_encrypted(bikeMac, brand);
                            preferences.saveBikeChasis_encrypted(bikeMac, chassis);
                            preferences.saveBikePlate_encrypted(bikeMac, plate);
                            preferences.saveBikePreferences(bikeMac);
                            preferences.saveBikeId_encrypted(bikeMac, codeM);

                            preferences.saveBikeTireWidth_encrypted( bikeMac, widthEncrypted );
                            preferences.saveBikeTirePercentage_encrypted( bikeMac, percentageEncrypted );
                            preferences.saveBikeWheelDiameter_encrypted( bikeMac, diameterEncrypted );

                            preferences.saveBikePass1_encrypted(bikeMac, pass1Encrypted );
                            preferences.saveBikePass2_encrypted(bikeMac, pass2Encrypted);
                            preferences.saveBikePass3_encrypted(bikeMac, pass3Encrypted );
                        }
                    } catch (Exception e) {
                        Log.e(TAG+"Encrypt", "Error= " + e.getMessage());
                    }
                }
            }
            if(numMotorcycles == 1){
                // if there is only one bike, then set it as the primary bike
                preferences.savePrimaryBikePreferences(bikeMac);
            }
            Log.d(TAG, "success bike");
        }  catch (Exception e){
            // JSON contains status=0, or there was an error saving data into prefereces. Delete bike
            Log.e(TAG, e.toString());
            preferences.deleteBikeFromPreferences(bikeMac);
        }

        Intent intent;
        if( !preferences.allPersonalInfoWasSaved() || TextUtils.isEmpty(preferences.getUserEmergencyPhone())
                || !(preferences.get_helmets_saved_MACs().size() > 0) ){
            // if user has not entered personal information, emergency contacts or registered one HelmyC
            intent = new Intent(this, ActivityProgress.class);
        } else {
            preferences.userRegistered_data_devices();
            intent = new Intent(this, ActivityGoAs.class);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        circleRotationAnim.cancel();
    }

    public void click_tryAgain(View view) {
        imageCircle.setVisibility(View.VISIBLE);
        tvBtnRetry.setVisibility(View.INVISIBLE);
        tvMessage.setText(getResources().getString(R.string.retrievingDataFromServer));
        // continue downloading where it failed
        StringRequest joRequest = request(preferences.get_lastUser_Id_logged(), urlsArrayList.get(contRequest));
        requestQueue.add(joRequest);
    }
}

