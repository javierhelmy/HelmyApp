package com.taedison.helmy;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/***
 * This class generates a singleton instance of preferences
 * We save persistently in preferences, and data that is sensitive is encrypted. AES returns bytes and they are converted to string in base64, and those strings are stored in preferences
 * Encrypted data includes: personal data, emergency contacts, and information about helmet and motorcycle
 * Excludes: email (we cant encrypt it, this is used to store RSA keys), MAC addresses (they are always visible to every bluetooth device), nicknames, helmet color
 */

public class SingletonSharedPreferences {
    private final String TAG = "SingletonPrefs";

    private static SingletonSharedPreferences instance;

    private static Context context;
    //    private static String lastUserLogged;
    private static SharedPreferences prefs;
    private static SharedPreferences.Editor editor;

    private SingletonSharedPreferences(Context context) {
        this.context = null;
        prefs = null;
        editor = null;

        this.context = context;
        prefs = context.getSharedPreferences("HELMY", Context.MODE_PRIVATE);
        editor = prefs.edit();

//        Log.d("SharedPrefs", "LastUser=" + get_lastUser_Id_logged() + "   prefs" + prefs);
    }

    public static synchronized SingletonSharedPreferences getInstance(Context context) {
        if (instance == null) {
            instance = new SingletonSharedPreferences(context);
        }
        return instance;
    }

    synchronized void reset(Context context) {
        instance = null;
        instance = new SingletonSharedPreferences(context);
//        Log.d("SharedPrefs", "LastUser Reset");
    }

    String get_lastUser_email_logged() {
        return prefs.getString("LAST_USER_LOGGED", null);
    }

    String get_lastUser_Id_logged() {
        String encryptedInBase64 = prefs.getString("USER_ID", null);
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                String userIddecrypted = Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
//                Log.d(TAG+"Encrypt", "userIddecrypted= " + userIddecrypted);
                return userIddecrypted;
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get UserId Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    void delete_lastUser_logged() {
        // delete all preferences
        editor.clear().commit();
    }

    void save_LastUser_logged(String username, String userId) {
        editor.putString("LAST_USER_LOGGED", username); // we must not encrypted, it is the reference for login and for the rsa encryption keys
        editor.commit();
        try {
            String userIdEncrypted = Static_AppMethods.encryptAES_toString64(context, userId, username, instance);
//            Log.d(TAG+"Encrypt", "userIdEncrypted= " + userIdEncrypted);
            editor.putString("USER_ID", userIdEncrypted); // encrypted
            editor.commit();
        } catch (Exception e) {
//            Log.e(TAG+"Encrypt", "Set UserId Error= " + e.getMessage());
        }
    }

    // save the login form to know where to logout from
    void save_userLoginForm(int email_google_face){
        // user logged in with
        // 0: email
        // 1: google
        // 2: facebook
        editor.putInt("LOGIN_FORM", email_google_face);
        editor.commit();
    }

    int get_userLoginForm(){
        // user logged in with
        // 0: email
        // 1: google
        // 2: facebook
        return prefs.getInt("LOGIN_FORM", -1);
    }

    // AESkey is passed encypted by RSA and as string base64
    void save_AESkey_rsaEncrypted(String AESkey){
//        Log.d(TAG+"Encrypt", "AESkey= " + AESkey);
        editor.putString("AESkey", AESkey);
        editor.commit();
    }

    // AESkey is returned encypted by RSA and as string base64
    String get_AESkey_rsaEncrypted(){
        return prefs.getString("AESkey", "");
    }


    void androidGO_wasChecked(){
        editor.putBoolean("androidGO", true);
        editor.commit(); // save the data synchronously
    }

    boolean was_androidGO_checked(){
//        Log.d("SharedPrefs", "was androidGO checked = " + prefs.getBoolean("androidGO", false));
        return prefs.getBoolean("androidGO", false);
    }

    void set_isAndroidGo(boolean isAndroidGo){
        editor.putBoolean("isAndroidGo", isAndroidGo);
        editor.commit(); // save the data synchronously
    }

    boolean get_isAndroidGO(){
        return prefs.getBoolean("isAndroidGo", false);
    }

    void userRegistered_data_devices(){
        editor.putBoolean("register", true);
        editor.commit(); // save the data synchronously
//        Log.d("SharedPrefs", "register yes");
    }

    boolean didUserRegister_data_devices(){
//        Log.d("SharedPrefs", "registered data = " + prefs.getBoolean("register", false));
        return prefs.getBoolean("register", false);
    }

    /*** HELMY-Cs */

    ArrayList<String> get_helmets_saved_MACs() {
        ArrayList<String> items = new ArrayList<>();
        // Check if helmets that have been saved already
        Set<String> helmetsSet = prefs.getStringSet("HELMETS", null); //  AndroidDoc: you must not modify the set instance returned by this call
        if (helmetsSet != null && helmetsSet.size() != 0) {
            items.addAll(helmetsSet);
        }
        return items;
    }

    ArrayList<String> get_helmets_saved_nicknames() {
        ArrayList<String> items = new ArrayList<>();
        // Check if helmets that have been saved already
        Set<String> helmetsSet = prefs.getStringSet("HELMETS", null); //  AndroidDoc: you must not modify the set instance returned by this call
        if (helmetsSet != null && helmetsSet.size() != 0) {
            for (String s : helmetsSet) {
                items.add(getHelmetNickname(s));
            }
        }
        return items;
    }

    String get_helmet_MAC_from_nickname(String nickname) {
        // Check if helmets that have been saved already
        Set<String> helmetsSet = prefs.getStringSet("HELMETS", null);
        if (helmetsSet != null && helmetsSet.size() != 0) {
            for (String MAC : helmetsSet) {
//                Log.d("prefsSingle", "MAC= " + MAC);
                if (nickname.equals(getHelmetNickname(MAC))) {
                    return MAC;
                }
            }
        }
        return null;
    }

    String get_primary_helmet_nickname() {
        // Check if helmets that have been saved already
        String mainMAC = prefs.getString("PRIMARY_HELMET", null);
        if (mainMAC != null) {
            return getHelmetNickname(mainMAC);
        }
        return null;
    }

    String get_primaryHelmet_MAC() {
        // Check if helmets that have been saved already
        return prefs.getString("PRIMARY_HELMET", null); // this will contain the MAC (name:mac)
    }

    void deleteHelmetFromPreferences(String helmetMAC) {
        Set<String> temp = prefs.getStringSet("HELMETS", new HashSet<String>()); // assign the returned set to a new one, as Android does not recommend to modify the returning set
        Set<String> helmetsSet = new HashSet<>(temp);

        if (helmetsSet.contains(helmetMAC)) {
            helmetsSet.remove(helmetMAC);
            editor.putStringSet("HELMETS", helmetsSet);
            editor.commit(); // save the data synchronously
            if( helmetMAC.equals( get_primaryHelmet_MAC() ) ){
                deletePrimaryHelmetFromPreferences();
            }
            deleteHelmetAssociatedBluetoothClassic(helmetMAC);
            deleteHelmetNickname(helmetMAC);
            deleteHelmetBrand(helmetMAC);
            deleteHelmetSize(helmetMAC);
            deleteHelmetColor(helmetMAC);
            deleteHelmet_isCovid(helmetMAC);
        }
    }

    private void deleteHelmetNickname(String helmetMAC) {
        editor.remove(helmetMAC + "Nickname");
        editor.commit();
    }

    void saveHelmetPreferences(String helmetMAC) {
        Set<String> temp = prefs.getStringSet("HELMETS", new HashSet<String>()); // assign the returned set to a new one, as Android does not recommend to modify the returning set
        Set<String> helmetsSet = new HashSet<>(temp);

        helmetsSet.add(helmetMAC);
        editor.putStringSet("HELMETS", helmetsSet);
        editor.commit(); // save the data synchronously
//        Log.d("redHelmet","Saved helmet");

//        Log.d("redHelmet", "saved= " + get_helmets_saved_MACs().size() );
    }

    void savePrimaryHelmetPreferences(String primary) {
        editor.putString("PRIMARY_HELMET", primary);
        editor.commit(); // save the data synchronously
//        Log.d("redHelmet","Saved primary");
    }

    private void deletePrimaryHelmetFromPreferences() {
        editor.remove("PRIMARY_HELMET");
        editor.commit(); // save the data synchronously
    }

    // classic bluetooth
    void setHelmetAssociatedBluetoothClassic(String bLE_MAC, String classic_MAC) {
        editor.putString(bLE_MAC + "BT", classic_MAC);
        editor.commit(); // save the data synchronously
//        Log.d("redHelmet","Saved bt");
    }

    String getHelmetAssociatedBluetoothClassic(String bLE_MAC) {
        return prefs.getString(bLE_MAC+ "BT", null);
    }

    private void deleteHelmetAssociatedBluetoothClassic(String bLE_MAC) {
        editor.remove(bLE_MAC + "BT");
        editor.commit();
    }

    void setHelmetNickname(String bLE_MAC, String nickname) {
        editor.putString(bLE_MAC + "Nickname", nickname);
        editor.commit(); // save the data synchronously
//        Log.d("redHelmet","Saved nickname");
    }

    String getHelmetNickname(String bLE_MAC) {
        return prefs.getString(bLE_MAC + "Nickname", null);
    }

    /*** HELMY-Ms */

    ArrayList<String> get_bikes_saved_MACs() {
        ArrayList<String> items = new ArrayList<>();
        // Check if bikes that have been saved already
        Set<String> bikesSet = prefs.getStringSet("BIKES", null); //  AndroidDoc: you must not modify the set instance returned by this call
        if (bikesSet != null && bikesSet.size() != 0) {
            items.addAll(bikesSet);
        }
        return items;
    }

    ArrayList<String> get_bikes_saved_nicknames() {
        ArrayList<String> items = new ArrayList<>();
        // Check if bikes that have been saved already
        Set<String> bikesSet = prefs.getStringSet("BIKES", null); //  AndroidDoc: you must not modify the set instance returned by this call
        if (bikesSet != null && bikesSet.size() != 0) {
            for (String s : bikesSet) {
                items.add(getBikeNickname(s));
            }
        }
        return items;
    }

    String get_bike_MAC_from_nickname(String nickname) {
        // Check if helmets that have been saved already
        Set<String> bikesSet = prefs.getStringSet("BIKES", null);
        if (bikesSet != null && bikesSet.size() != 0) {
            for (String MAC : bikesSet) {
//                Log.d("prefsSingle", MAC);
                if (nickname.equals(getBikeNickname(MAC))) {
                    return MAC;
                }
            }
        }
        return null;
    }

    String get_primary_bike_nickname() {
        // Check if bikes that have been saved already
        String mainMAC = prefs.getString("PRIMARY_BIKE", null);
        if (mainMAC != null) {
            return getBikeNickname(mainMAC);
        }
        return null;
    }

    void deleteBikeFromPreferences(String bikeMAC) {
        Set<String> temp = prefs.getStringSet("BIKES", new HashSet<String>()); // assign the returned set to a new one, as Android does not recommend to modify the returning set
        Set<String> bikesSet = new HashSet<>(temp);

        if (bikesSet.contains(bikeMAC)) {
            bikesSet.remove(bikeMAC);
            editor.putStringSet("BIKES", bikesSet);
            editor.commit(); // save the data synchronously

            deletePrimaryBikeFromPreferences();
            deleteBikeNickname(bikeMAC);
            deleteBikeSOAT(bikeMAC);
            deleteBike2ndPolicy(bikeMAC);
            deleteBike2ndPolicyPhone(bikeMAC);
            deleteBikeBrand(bikeMAC);
            deleteBikeChasis(bikeMAC);
            deleteBikePlate(bikeMAC);
            deleteBikeTireWidth(bikeMAC);
            deleteBikeTirePercentage(bikeMAC);
            deleteBikeWheelDiameter(bikeMAC);
            deleteBikePass1(bikeMAC);
            deleteBikePass2(bikeMAC);
            deleteBikePass3(bikeMAC);
            deleteBikeId(bikeMAC);
            deleteBikeBondId(bikeMAC);
        }
    }

    private void deleteBikeNickname(String bikeMAC) {
        editor.remove(bikeMAC + "Nickname");
        editor.commit();
    }

    void saveBikePreferences(String bikeMAC) {
        Set<String> temp = prefs.getStringSet("BIKES", new HashSet<String>()); // assign the returned set to a new one, as Android does not recommend to modify the returning set
        Set<String> bikesSet = new HashSet<>(temp);
        bikesSet.add(bikeMAC);
        editor.putStringSet("BIKES", bikesSet);
        editor.commit(); // save the data synchronously
    }

    void savePrimaryBikePreferences(String primary) {
        editor.putString("PRIMARY_BIKE", primary);
        editor.commit(); // save the data synchronously
    }

    private void deletePrimaryBikeFromPreferences() {
        editor.remove("PRIMARY_BIKE");
        editor.commit(); // save the data synchronously
    }

    String get_primaryBike_MAC() {
        // Check if helmets that have been saved already
        String mainMAC = prefs.getString("PRIMARY_BIKE", null);
        if (mainMAC != null) {
            return mainMAC; // this will contain the MAC (name:mac)
        }
        return null;
    }

    String get_primaryBike_bikeId() {
        return getBikeId(get_primaryBike_MAC());
    }

    void setBikeNickname(String bLE_MAC, String nickname) {
        editor.putString(bLE_MAC + "Nickname", nickname);
        editor.commit(); // save the data synchronously
    }

    String getBikeNickname(String bLE_MAC) {
        return prefs.getString(bLE_MAC + "Nickname", null);
    }

    /*** EMERGENCY ALERT DEMO */

    boolean wasDemoAlreadyLaunched() {
        return prefs.getBoolean("DEMO", false); //default it will return false until set_demoWasLaunched is called
    }

    void set_demoWasLaunched() {
        editor.putBoolean("DEMO", true);
        editor.commit(); // save the data synchronously
    }

    /*** PERSONAL DATA */

    void setUserNationality_encrypted(String natiolatity) {
        editor.putString("natiolatity", natiolatity);
        editor.commit();
    }

    String getUserNatiolatity() {
        String encryptedInBase64 = prefs.getString("natiolatity", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                String nationalityDecrypted = Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
//                Log.d(TAG+"Encrypt", "nationalityDecrypted= " + nationalityDecrypted);
                return nationalityDecrypted;
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get Natiolatity Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    void setUserIDType_encrypted(String id_type) {
        editor.putString("id_type", id_type);
        editor.commit();
    }

    String getUserIDType() {
        String encryptedInBase64 = prefs.getString("id_type", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                String idTypeDecrypted = Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
//                Log.d(TAG+"Encrypt", "idTypeDecrypted= " + idTypeDecrypted);
                return idTypeDecrypted;
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get idType Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    void setUserIDnum_encrypted(String idNum) {
        editor.putString("idNum", idNum);
        editor.commit();
    }

    String getUserIDnum() {
        String encryptedInBase64 = prefs.getString("idNum", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                String idNumDecrypted = Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
//                Log.d(TAG+"Encrypt", "idNumDecrypted= " + idNumDecrypted);
                return idNumDecrypted;
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get idNum Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }


    void setUser_isColLicense_encrypted(String colLicense) {
        editor.putString("colLicense", colLicense);
        editor.commit();
    }

    boolean getUser_isColLicense() {
        String encryptedInBase64 = prefs.getString("colLicense", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                String isColLicenseDecrypted = Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
//                Log.d(TAG+"Encrypt", "isColLicenseDecrypted= " + isColLicenseDecrypted);
                if(isColLicenseDecrypted.equals("1")){
                    return true;
                } else {
                    return false;
                }
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get isColLicenseDecrypted Error= " + e.getMessage());
                return true; // return true as default
            }
        }
        return true; // return true as default
    }

    void setUserLicenseNum_encrypted(String licenseNum) {
        editor.putString("licenseNum", licenseNum);
        editor.commit();
    }

    String getUserLicenseNum() {
        String encryptedInBase64 = prefs.getString("licenseNum", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get licenseNum Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    void setUserNames_encrypted(String names) {
        editor.putString("names", names);
        editor.commit();
    }

    String getUserNames() {
        String encryptedInBase64 = prefs.getString("names", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get names Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    void setUserSurnames_encrypted(String surnames) {
        editor.putString("surnames", surnames);
        editor.commit();
    }

    String getUserSurnames() {
        String encryptedInBase64 = prefs.getString("surnames", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get surnames Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    void setUserAge_encrypted(String age) {
        editor.putString("age", age);
        editor.commit();
    }

    String getUserAge() {
        String encryptedInBase64 = prefs.getString("age", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get age Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    void setUserRH_encrypted(String rh) {
        editor.putString("rh", rh);
        editor.commit();
    }

    String getUserRH() {
        String encryptedInBase64 = prefs.getString("rh", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get rh Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    void setUserSex_encrypted(String sex) {
        // "0": female, "1": male, "2": rather not say
        editor.putString("sex", sex);
        editor.commit();
    }

    String getUserSex() {
        // "0": female, "1": male, "2": rather not say
        String encryptedInBase64 = prefs.getString("sex", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get sex Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    void setUserEPS_encrypted(String eps) {
        editor.putString("eps", eps);
        editor.commit();
    }

    String getUserEPS() {
        String encryptedInBase64 = prefs.getString("eps", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get eps Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    void setUserARL_encrypted(String arl) {
        editor.putString("arl", arl);
        editor.commit();
    }

    String getUserARL() {
        String encryptedInBase64 = prefs.getString("arl", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get arl Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    void setUserPhone_encrypted(String phone) {
        editor.putString("phone", phone);
        editor.commit();
    }

    String getUserPhone() {
        String encryptedInBase64 = prefs.getString("phone", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get phone Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    boolean allPersonalInfoWasSaved(){
        return !TextUtils.isEmpty(getUserNatiolatity()) && !TextUtils.isEmpty(getUserIDType())
                && !TextUtils.isEmpty(getUserIDnum()) && !TextUtils.isEmpty(getUserLicenseNum())
                && !TextUtils.isEmpty(getUserNames()) && !TextUtils.isEmpty(getUserSurnames())
                && !TextUtils.isEmpty(getUserAge()) && !TextUtils.isEmpty(getUserRH()) && !TextUtils.isEmpty(getUserSex())
                && !TextUtils.isEmpty(getUserEPS()) && !TextUtils.isEmpty(getUserARL())
                && !TextUtils.isEmpty(getUserPhone());
    }

    /*** EMERGENCY CONTACTS */
    void setUserEmergencyNames_encrypted(String emergencyNames) {
        editor.putString("emergencyNames", emergencyNames);
        editor.commit();
    }

    String getUserEmergencyNames(){
        String encryptedInBase64 = prefs.getString("emergencyNames", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get emergencyNames Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    void setUserEmergencySurnames_encrypted(String emergencySurnames){
        editor.putString("emergencySurnames", emergencySurnames);
        editor.commit();
    }

    String getUserEmergencySurnames(){
        String encryptedInBase64 = prefs.getString("emergencySurnames", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get emergencySurnames Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    void setUserEmergencyPhone_encrypted(String emergencyPhone){
        editor.putString("emergencyPhone", emergencyPhone);
        editor.commit();
    }

    String getUserEmergencyPhone(){
        String encryptedInBase64 = prefs.getString("emergencyPhone", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get emergencyPhone Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    void setUserEmergencyNames2_encrypted(String emergencyNames2){
        editor.putString("emergencyNames2", emergencyNames2);
        editor.commit();
    }

    String getUserEmergencyNames2(){
        String encryptedInBase64 = prefs.getString("emergencyNames2", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get emergencyNames2 Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    String getUserEmergencyNames2_encrypted(){
        return prefs.getString("emergencyNames2", "");
    }

    void setUserEmergencySurnames2_encrypted(String emergencySurnames2){
        editor.putString("emergencySurnames2", emergencySurnames2);
        editor.commit();
    }

    String getUserEmergencySurnames2(){
        String encryptedInBase64 = prefs.getString("emergencySurnames2", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get emergencySurnames2 Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    void setUserEmergencyPhone2_encrypted(String emergencyPhone2){
        editor.putString("emergencyPhone2", emergencyPhone2);
        editor.commit();
    }

    String getUserEmergencyPhone2(){
        String encryptedInBase64 = prefs.getString("emergencyPhone2", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get emergencyPhone2 Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    void deleteSecondContact(){
        editor.remove("emergencyNames2");
        editor.remove("emergencySurnames2");
        editor.remove("emergencyPhone2");
        editor.commit();
    }


    /*** HELMET data */

    void setHelmetBrand_encrypted (String bLE_MAC, String brand){
        editor.putString(bLE_MAC+"brand", brand);
        editor.commit(); // save the data synchronously
    }

    String getHelmetBrand (String bLE_MAC){
        String encryptedInBase64 = prefs.getString(bLE_MAC+"brand", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get brand Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    private void deleteHelmetBrand(String bLE_MAC){
        editor.remove(bLE_MAC + "brand");
        editor.commit();
    }

    void setHelmetSize_encrypted (String bLE_MAC, String size){
        editor.putString(bLE_MAC+"size", size);
        editor.commit(); // save the data synchronously
    }

    String getHelmetSize (String bLE_MAC){
        String encryptedInBase64 = prefs.getString(bLE_MAC+"size", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get brand Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    private void deleteHelmetSize(String bLE_MAC){
        editor.remove(bLE_MAC + "size");
        editor.commit();
    }

    void setHelmetColor (String bLE_MAC, int color){
        editor.putInt(bLE_MAC+"color", color);
        editor.commit(); // save the data synchronously
//        Log.d("redHelmet","Saved color");
    }

    int getHelmetColor (String bLE_MAC){
        return prefs.getInt(bLE_MAC+"color", context.getResources().getColor(R.color.error) );
    }

    private void deleteHelmetColor(String bLE_MAC){
        editor.remove(bLE_MAC + "color");
        editor.commit();
    }

    void setHelmet_isCovid (String bLE_MAC, boolean isCovid){
        editor.putBoolean(bLE_MAC+"isCovid", isCovid);
        editor.commit(); // save the data synchronously
    }

    boolean getHelmet_isCovid (String bLE_MAC){
        return prefs.getBoolean(bLE_MAC+"isCovid", false );
    }

    private void deleteHelmet_isCovid(String bLE_MAC){
        editor.remove(bLE_MAC + "isCovid");
        editor.commit();
    }

    /*** BIKE data */

    void saveBikeSOAT_encrypted(String bikeMAC, String numSOAT){
        editor.putString(bikeMAC+"soat", numSOAT);
        editor.commit();
    }

    String getBikeSOAT(String bikeMAC){
        String encryptedInBase64 = prefs.getString(bikeMAC+"soat", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get soat Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    private void deleteBikeSOAT(String bLE_MAC){
        editor.remove(bLE_MAC + "soat");
        editor.commit();
    }

    void saveBike2ndPolicy_encrypted(String bikeMAC, String numPolicy){
        editor.putString(bikeMAC+"2nd_policy", numPolicy);
        editor.commit();
    }

    String getBike2ndPolicy(String bikeMAC){
        String encryptedInBase64 = prefs.getString(bikeMAC+"2nd_policy", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get 2nd_policy Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    private void deleteBike2ndPolicy(String bLE_MAC){
        editor.remove(bLE_MAC + "2nd_policy");
        editor.commit();
    }

    void saveBike2ndPolicyPhone_encrypted(String bikeMAC, String phone){
        editor.putString(bikeMAC+"2nd_policy_phone", phone);
        editor.commit();
    }

    String getBike2ndPolicyPhone(String bikeMAC){
        String encryptedInBase64 = prefs.getString(bikeMAC+"2nd_policy_phone", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get 2nd_policy_phone Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    private void deleteBike2ndPolicyPhone(String bLE_MAC){
        editor.remove(bLE_MAC + "2nd_policy_phone");
        editor.commit();
    }

    void saveBikeBrand_encrypted(String bikeMAC, String brand){
        editor.putString(bikeMAC+"brand", brand);
        editor.commit();
    }

    String getBikeBrand(String bikeMAC){
        String encryptedInBase64 = prefs.getString(bikeMAC+"brand", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get brand Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    private void deleteBikeBrand(String bLE_MAC){
        editor.remove(bLE_MAC + "brand");
        editor.commit();
    }

    void saveBikeChasis_encrypted(String bikeMAC, String chasis){
        editor.putString(bikeMAC+"chasis", chasis);
        editor.commit();
    }

    String getBikeChasis(String bikeMAC){
        String encryptedInBase64 = prefs.getString(bikeMAC+"chasis", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get chasis Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    private void deleteBikeChasis(String bLE_MAC){
        editor.remove(bLE_MAC + "chasis");
        editor.commit();
    }

    void saveBikePlate_encrypted(String bikeMAC, String plate){
        editor.putString(bikeMAC+"plate", plate);
        editor.commit();
    }

    String getBikePlate(String bikeMAC){
        String encryptedInBase64 = prefs.getString(bikeMAC+"plate", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get plate Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    private void deleteBikePlate(String bLE_MAC){
        editor.remove(bLE_MAC + "plate");
        editor.commit();
    }

    void saveBikeTireWidth_encrypted(String bikeMAC, String width){
        editor.putString(bikeMAC+"width", width);
        editor.commit();
    }

    String getBikeTireWidth(String bikeMAC){
        String encryptedInBase64 = prefs.getString(bikeMAC+"width", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get width Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    private void deleteBikeTireWidth(String bLE_MAC){
        editor.remove(bLE_MAC + "width");
        editor.commit();
    }

    void saveBikeTirePercentage_encrypted(String bikeMAC, String percentage){
        editor.putString(bikeMAC+"percentage", percentage);
        editor.commit();
    }

    String getBikeTirePercentage(String bikeMAC){
        String encryptedInBase64 = prefs.getString(bikeMAC+"percentage", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get percentage Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    private void deleteBikeTirePercentage(String bLE_MAC){
        editor.remove(bLE_MAC + "percentage");
        editor.commit();
    }

    void saveBikeWheelDiameter_encrypted(String bikeMAC, String diameter){
        editor.putString(bikeMAC+"diameter", diameter);
        editor.commit();
    }

    String getBikeWheelDiameter(String bikeMAC){
        String encryptedInBase64 = prefs.getString(bikeMAC+"diameter", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get diameter Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    private void deleteBikeWheelDiameter(String bLE_MAC){
        editor.remove(bLE_MAC + "diameter");
        editor.commit();
    }

    void saveBikePass1_encrypted(String bikeMAC, String pass1){
        editor.putString(bikeMAC+"pass1", pass1);
        editor.commit();
    }

    String getBikePass1(String bikeMAC){
        String encryptedInBase64 = prefs.getString(bikeMAC+"pass1", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get pass1 Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    private void deleteBikePass1(String bLE_MAC){
        editor.remove(bLE_MAC+"pass1");
        editor.commit();
    }

    void saveBikePass2_encrypted(String bikeMAC, String pass2){
        editor.putString(bikeMAC+"pass2", pass2);
        editor.commit();
    }

    String getBikePass2(String bikeMAC){
        String encryptedInBase64 = prefs.getString(bikeMAC+"pass2", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get pass2 Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    private void deleteBikePass2(String bLE_MAC){
        editor.remove(bLE_MAC+"pass2");
        editor.commit();
    }

    void saveBikePass3_encrypted(String bikeMAC, String pass3){
        editor.putString(bikeMAC+"pass3", pass3);
        editor.commit();
    }

    String getBikePass3(String bikeMAC){
        String encryptedInBase64 = prefs.getString(bikeMAC+"pass3", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get pass3 Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    private void deleteBikePass3(String bLE_MAC){
        editor.remove(bLE_MAC+"pass3");
        editor.commit();
    }

    void saveBikeId_encrypted(String bikeMAC, String bikeId){
        editor.putString(bikeMAC+"bikeId", bikeId);
        editor.commit();
    }

    String getBikeId(String bikeMAC){
        String encryptedInBase64 = prefs.getString(bikeMAC+"bikeId", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get bikeId Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    private void deleteBikeId(String bikeMAC){
        editor.remove(bikeMAC+"bikeId");
        editor.commit();
    }

    void saveBikeBondId_encrypted(String bikeMAC, String bondId){
        editor.putString(bikeMAC+"bondId", bondId);
        editor.commit();
    }

    String getBikeBondId(String bikeMAC){
        String encryptedInBase64 = prefs.getString(bikeMAC+"bondId", "");
        if( !TextUtils.isEmpty(encryptedInBase64) ){
            try {
                return Static_AppMethods.decryptAES_toUTF8(context, encryptedInBase64, get_lastUser_email_logged(), instance);
            } catch (Exception e) {
//                Log.e(TAG+"Encrypt", "Get bondId Error= " + e.getMessage());
                return "";
            }
        }
        return encryptedInBase64;
    }

    private void deleteBikeBondId(String bLE_MAC){
        editor.remove(bLE_MAC + "bondId");
        editor.commit();
    }

    /*** ActivityRetrieveDataFromServer ***/

    void set_downloadWasCompleteAfterLogin(){
        editor.putBoolean("DOWNLOAD_COMPLETE", true);
        editor.commit();
    }

    boolean get_downloadWasCompleteAfterLogin(){
        return prefs.getBoolean("DOWNLOAD_COMPLETE", false);
    }

    /*** VELOCITY
     * We have to ensure that the velocity files are uploaded. If a user logs out and a new one logs in,
     * the txt files will be uploaded regarless of the current user logged in. That is why
     * a new SharedPreferences instance is created
     * ***/

    void addVelocityFilePendingToUpload(String fileName, String userId, String bikeMAC){
        SharedPreferences general_prefs = context.getSharedPreferences("FILES_TXT", Context.MODE_PRIVATE);
        SharedPreferences.Editor g_editor = general_prefs.edit();

        Set<String> temp = general_prefs.getStringSet("files_to_upload", new HashSet<String>()); // assign the returned set to a new one, as Android does not recommend to modify the returning set
        Set<String> filesTxt = new HashSet<>(temp);

        String file = fileName+";"+userId+";"+bikeMAC;
        filesTxt.add(file);

        g_editor.putStringSet("files_to_upload", filesTxt);
        g_editor.putString("mostRecentTxt", file);
        g_editor.commit(); // save the data synchronously
    }

    Set<String> getSetFilesPendingToUpload_name_userId_MAC (){
        SharedPreferences general_prefs = context.getSharedPreferences("FILES_TXT", Context.MODE_PRIVATE);
        return general_prefs.getStringSet("files_to_upload", new HashSet<String>() );
    }

    String getMostRecentFilePendingToUpload_name_userId_MAC (){
        SharedPreferences general_prefs = context.getSharedPreferences("FILES_TXT", Context.MODE_PRIVATE);
        return general_prefs.getString("mostRecentTxt", "");
    }

    void removeFileAlreadyUploaded(String fileName, String userId, String bikeMAC){
        SharedPreferences general_prefs = context.getSharedPreferences("FILES_TXT", Context.MODE_PRIVATE);
        SharedPreferences.Editor g_editor = general_prefs.edit();

        Set<String> temp = general_prefs.getStringSet("files_to_upload", new HashSet<String>()); // assign the returned set to a new one, as Android does not recommend to modify the returning set
        Set<String> filesTxt = new HashSet<>(temp);

        String file = fileName+";"+userId+";"+bikeMAC;
        if(filesTxt.contains(file)){
            filesTxt.remove(file);
            g_editor.putStringSet("files_to_upload", filesTxt);

            if(file.equals(getMostRecentFilePendingToUpload_name_userId_MAC())){
                g_editor.remove("mostRecentTxt");
            }
            g_editor.commit(); // save the data synchronously

//            Log.d("ServiceUploadTxt", "txt deleted from preferences");
        }
    }

    /*** ALERTS
     * We have to ensure that the alert information is uploaded. If a user logs out and a new one logs in,
     * the information will be uploaded regarless of the current user logged in. That is why
     * a new SharedPreferences instance is created
     * ***/
    void addAlertRegistryPendingToUpload(String dateOfThetrip_userId_bikeMAC_wasSent){
        SharedPreferences general_prefs = context.getSharedPreferences("ALERTS", Context.MODE_PRIVATE);
        SharedPreferences.Editor g_editor = general_prefs.edit();

//        g_editor.clear();
//        g_editor.commit();

        Set<String> temp = general_prefs.getStringSet("alerts_sms", new HashSet<String>()); // assign the returned set to a new one, as Android does not recommend to modify the returning set
        Set<String> alertsCanceled = new HashSet<>(temp);

        alertsCanceled.add(dateOfThetrip_userId_bikeMAC_wasSent);

        g_editor.putStringSet("alerts_sms", alertsCanceled);
        g_editor.putString("mostRecentAlert", dateOfThetrip_userId_bikeMAC_wasSent);
        g_editor.commit(); // save the data synchronously
    }

    void removeAlertRegistryAlreadyUploaded(String dateOfThetrip_userId_bikeMAC_smsSent){
        SharedPreferences general_prefs = context.getSharedPreferences("ALERTS", Context.MODE_PRIVATE);
        SharedPreferences.Editor g_editor = general_prefs.edit();

        Set<String> temp = general_prefs.getStringSet("alerts_sms", new HashSet<String>()); // assign the returned set to a new one, as Android does not recommend to modify the returning set
        Set<String> alertsCanceled = new HashSet<>(temp);

//        String trip = dateOfThetrip+";"+userId+";"+bikeMAC;
        if(alertsCanceled.contains(dateOfThetrip_userId_bikeMAC_smsSent)){
            alertsCanceled.remove(dateOfThetrip_userId_bikeMAC_smsSent);
            g_editor.putStringSet("alerts_sms", alertsCanceled);

            if(dateOfThetrip_userId_bikeMAC_smsSent.equals(getMostRecentAlert())){
                g_editor.remove("mostRecentAlert");
            }

            g_editor.commit(); // save the data synchronously
        }
    }

    Set<String> getSetAlertsPendingToUpload(){
        SharedPreferences general_prefs = context.getSharedPreferences("ALERTS", Context.MODE_PRIVATE);
        return general_prefs.getStringSet("alerts_sms", new HashSet<String>() ); // dateImpact;userId;MAC;dateSMS;dateCanceled
    }

    String getMostRecentAlert(){
        SharedPreferences general_prefs = context.getSharedPreferences("ALERTS", Context.MODE_PRIVATE);
        return general_prefs.getString("mostRecentAlert", "");
    }

    /*** Threshols to send to Helmy-C */
    void saveImpactThreshold(float impact){
        editor.putFloat("impactTH", impact);
        editor.commit();
    }

    float getImpactThreshold(){
        return prefs.getFloat("impactTH", 0);
    }

    void saveAngleSuperiorThreshold(float angle){
        editor.putFloat("angleSup", angle);
        editor.commit();
    }

    float getAngleSuperiorThreshold(){
        return prefs.getFloat("angleSup", 0);
    }

    void saveAngleInferiorThreshold(float angle){
        editor.putFloat("angleInf", angle);
        editor.commit();
    }

    float getAngleInferiorThreshold(){
        return prefs.getFloat("angleInf", 0);
    }

    // TOUR will be launched everytime the user logs in

    void setTourWasShown(){
        editor.putBoolean("tour", true);
        editor.commit();
    }

    boolean wasTourShown(){
        return prefs.getBoolean("tour", false); // false if tour has not been shown, user has not reached the Go As activity
    }

    // save alert message (or reason for not sending) which is show when user presses on the notification and comes back to the emergencyActivity
    void setMessageAboutAlert(String msg, int id){
        editor.putString("alertMsg", msg);
        editor.putInt("alertMsgId", id);
        editor.commit();
    }

    String getMessageAboutAlert(){
        String msg = prefs.getString("alertMsg", "");
        return msg;
    }

    int getMessageId_AboutAlert(){
        int id = prefs.getInt("alertMsgId", -1);
        return id;
    }

    void set_dataUpdatedInServer(boolean dataWasChanged){
        editor.putBoolean("dataUpdated", dataWasChanged);
        editor.commit();
    }

    boolean get_wasDataUpdated(){
        return prefs.getBoolean("dataUpdated", false);
    }

    void set_passwordUpdatedInServer(boolean dataWasChanged){
        editor.putBoolean("passwordUpdated", dataWasChanged);
        editor.commit();
    }

    boolean get_wasPasswordUpdated(){
        return prefs.getBoolean("passwordUpdated", false);
    }

    void set_primaryServerIsDown(boolean serverIsDown){
        editor.putBoolean("serverDown", serverIsDown);
        editor.commit();
    }

    boolean get_isPrimaryServerDown(){
        return prefs.getBoolean("serverDown", false);
    }
}

