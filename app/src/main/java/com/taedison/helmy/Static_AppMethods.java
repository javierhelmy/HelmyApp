package com.taedison.helmy;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.security.KeyPairGeneratorSpec;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.VolleyError;
import com.facebook.login.LoginManager;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.x500.X500Principal;

/***
 * Methods that are used in at least two different places
 */
public class Static_AppMethods {

    static void logOut(Context context, ProgressBar progressBar){
        progressBar.setVisibility(View.VISIBLE);
        SingletonSharedPreferences preferences = SingletonSharedPreferences.getInstance(context.getApplicationContext());
        int loginForm = preferences.get_userLoginForm();
        Log.d("logout_helmy", "started");
        preferences.delete_lastUser_logged(); // deletes all preferences
        Log.d("logout_helmy", "prefs");
        if(loginForm == 1){
            //Google logout
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .build();
            GoogleSignInClient mGoogleSignInClient = GoogleSignIn.getClient(context, gso);
            mGoogleSignInClient.signOut();
            Log.d("logout_helmy", "google");
        } else if (loginForm == 2){
            //facebook logout
            LoginManager.getInstance().logOut();
            Log.d("logout_helmy", "facebook");
        }

        //Go to ActivityLogin
        Intent va = new Intent(context, ActivityLoginRegister.class);
        va.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(va);
        Log.d("logout_helmy", "done");
        progressBar.setVisibility(View.GONE);
    }

    static void checkResponseCode(VolleyError error, SingletonSharedPreferences preferences){
        // check if server is down, and if it is, then user has to try again and this
        // time using the secondary server
        if(error.networkResponse.statusCode == 503){
            // server is down
            preferences.set_primaryServerIsDown(true);
        }
    }

    static void ToastCheckYourInternet(Context context){
        Toast.makeText(context, R.string.checkInternetConnection, Toast.LENGTH_SHORT).show();
    }

    static void ToastTryAgain(Context context){
        Toast.makeText(context, R.string.errorTryAgain, Toast.LENGTH_SHORT).show();
    }

    static void ToastEncryptionError(Context context){
        Toast.makeText(context, R.string.errorWithEncryption, Toast.LENGTH_SHORT).show();
    }

    static void launchAlertReasonForAskingPersonalInfo(Context context) {
        final AlertMessageButton alert = new AlertMessageButton(context);
        alert.setDialogMessage(R.string.ReasonForAskingPersonalInformation);
        alert.setDialogPositiveButton(context.getResources().getString(android.R.string.ok), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alert.dismissAlert();
            }
        });
        alert.showAlert();
    }

    static void launchAlertGoBack2MainMenu(final Context context){
        final AlertMessageButton alert = new AlertMessageButton(context);
        alert.setDialogMessage( context.getResources().getString(R.string.areYouSureGoBack) );
        alert.setDialogPositiveButton( context.getResources().getString(R.string.Yes), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(context, ActivityGoAs.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                context.startActivity(intent);
                alert.dismissAlert();
            }
        });
        alert.setDialogNegativeButton( context.getResources().getString(R.string.No), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alert.dismissAlert();
            }
        });
        alert.showAlert();
    }

    // check fields: if they are empty, then they are highlighted with red
    public static void checkField(View view){
        if(view instanceof EditText){
            EditText et = (EditText) view;
            if( TextUtils.isEmpty( et.getText()) ){
                et.setBackgroundResource(R.drawable.redcontour_rounded);
            } else {
                et.setBackgroundResource(R.drawable.whitecontour_rounded);
            }
        } else if(view instanceof Spinner){
            Spinner spinner = (Spinner) view;
            if( spinner.getSelectedItemPosition() == 0 ){
                ( (ClassSpinnerAdapter) spinner.getAdapter() ).changeAdapterBaseOnErrorNotSelected(true);
            } else {
                ( (ClassSpinnerAdapter) spinner.getAdapter() ).changeAdapterBaseOnErrorNotSelected(false);
            }
        } else if(view instanceof TextView){
            TextView tv = (TextView) view;
            if( TextUtils.isEmpty( tv.getText()) ){
                tv.setBackgroundResource(R.drawable.redcontour_rounded);
            } else {
                tv.setBackgroundResource(R.drawable.whitecontour_rounded);
            }

        }
    }

    public static void animateProgressCircle(final ImageView circle){
        // animates circles at the bottom of the screen that show the progress of filling information
        long duration = 1500;

        ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(circle, "scaleX", 1f);
        ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(circle, "scaleY", 1f);
        final AnimatorSet scaleDownAnimator = new AnimatorSet();
        scaleDownAnimator.play(scaleUpX).with(scaleUpY);
        scaleDownAnimator.setDuration(duration);
        scaleDownAnimator.start();

        new Handler().postDelayed(new Runnable(){
            public void run(){
                scaleDownAnimator.cancel();
            }
        }, duration);
    }

    static void writeToFile(String fileName, String data, Context context) {
        // used for creating the velocity txt files
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput(fileName+".txt", Context.MODE_PRIVATE));
            outputStreamWriter.write(data);
            outputStreamWriter.close();
        }
        catch (IOException e) {
            Log.e("SplashAct", "File write failed: " + e.toString());
        }
    }

    static String readFromFile(String fileName, Context context) {
        // used for creating the velocity txt files
        String ret = "";

        try {
            InputStream inputStream = context.openFileInput(fileName + ".txt");

            if ( inputStream != null ) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString = "";
                StringBuilder stringBuilder = new StringBuilder();

                while ( (receiveString = bufferedReader.readLine()) != null ) {
                    stringBuilder.append(receiveString).append("\n");
                }

                inputStream.close();
                ret = stringBuilder.toString();
                Log.d("SplashAct", "data" + fileName + "\n" + ret);
            }
        }
        catch (FileNotFoundException e) {
            Log.e("SplashAct", "File not found: " + e.toString());
        } catch (IOException e) {
            Log.e("SplashAct", "Can not read file: " + e.toString());
        }

        return ret;
    }

    static void launchAlertBluetooth(Context context, final Activity activity, final int requestCode) {
        final AlertMessageButton alert = new AlertMessageButton(context);
        alert.setDialogMessage(context.getResources().getString(R.string.userDidnotTurnOnBluetooth));
        alert.setDialogPositiveButton(context.getResources().getString(R.string.btnEnableBluetooth), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE); // in the calling activity the method onActivityResult must be overriden
                activity.startActivityForResult(enableBtIntent, requestCode);
                alert.dismissAlert();
            }
        });
        alert.setDialogNegativeButton(context.getResources().getString(R.string.No), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alert.dismissAlert();
            }
        });
        alert.showAlert();
    }

    static void launchAlertMessage(String msg, Context context){
        final AlertMessageButton alert = new AlertMessageButton(context);
        alert.setDialogMessage(msg);
        alert.setDialogPositiveButton(context.getResources().getString(R.string.Ok),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        alert.dismissAlert();
                    }
                });
        alert.showAlert();
    }

    /*** ENCRYPTION
     * We use RSA to encrypt AES keys and save them in preferences. RSA keys are stored using
     * AndroidKeyStore, which is very safe and implemented by Android.
     * We do not use RSA as the encryption method because the encrypted strings are longer than
     * with AES. If at some point with want to send data encrypted to HelmyC or M, it is easier and
     * less computational expensive. In addition, cypress ble microcontrollers can do AES encryption
     * AES is sent raw to the server and has to be kept safe
     * ***/

    static String bytesToStringBase64(byte[] encryptedBytes){
        return Base64.encodeToString(encryptedBytes, Base64.DEFAULT);
    }

    static byte[] stringBase64toBytes(String stringBase64){
        return Base64.decode(stringBase64, Base64.DEFAULT);
    }

    // aes without gcm or cbc, we do not use them because of the difficulty to store the keys and
    // allow a user to sign in in different phones
    private static SecretKey global_keyAES = null;
    public static String encryptAES_toString64(Context context, String stringToEncrypt, String KEY_ALIAS,
                                               SingletonSharedPreferences preferences) throws Exception {
        byte[] plaintext = stringToEncrypt.getBytes();
        Cipher cipher = Cipher.getInstance("AES");

        if(global_keyAES == null){
            // get the key that was generated/retrieved at the time of login or registration
            Log.e("Encryptionn", "key is null" );
            getAESkeyFromPrefs(context, KEY_ALIAS, preferences);
        } else {
            Log.e("Encryptionn", "AES key bytes: " + Arrays.toString(global_keyAES.getEncoded()) );
        }

        cipher.init(Cipher.ENCRYPT_MODE, global_keyAES);
        byte[] encrypted = cipher.doFinal(plaintext);
        return bytesToStringBase64(encrypted);
    }

    public static String decryptAES_toUTF8(Context context, String string64ToDecrypt, String KEY_ALIAS,
                                           SingletonSharedPreferences preferences) throws Exception {
        Cipher cipher = Cipher.getInstance("AES");

        if(global_keyAES == null){
            // get the key that was generated/retrieved at the time of login or registration
            Log.e("Encryptionn", "key is null" );
            getAESkeyFromPrefs(context, KEY_ALIAS, preferences);
        }else {
            Log.e("Encryptionn", "AES key bytes: " + Arrays.toString(global_keyAES.getEncoded()) );
        }

        cipher.init(Cipher.DECRYPT_MODE, global_keyAES);
        byte[] decrypted = cipher.doFinal( stringBase64toBytes(string64ToDecrypt) );
        return new String(decrypted, "UTF-8");
    }

    private static final String RSA_MODE =  "RSA/ECB/PKCS1Padding";
    // this method is used to encrypt the bytes of the AES key
    private static byte[] rsaEncrypt(Context context, byte[] bytesToEncrypt, String KEY_ALIAS) throws Exception{

        // if RSA keys have not been created for the user, then generate them first
        check_generateRSA(context, KEY_ALIAS);

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(KEY_ALIAS, null);

        Cipher inputCipher = Cipher.getInstance(RSA_MODE, "AndroidOpenSSL");
        inputCipher.init(Cipher.ENCRYPT_MODE, privateKeyEntry.getCertificate().getPublicKey());

        // Encrypt bytes
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        CipherOutputStream cipherOutputStream = new CipherOutputStream(outputStream, inputCipher);
        cipherOutputStream.write(bytesToEncrypt);
        cipherOutputStream.close();

        byte[] vals = outputStream.toByteArray();

        return vals;
    }

    // this method is used to decrypt the bytes of the AES key
    private static byte[]  rsaDecrypt(Context context, byte[] encrypted, String KEY_ALIAS) throws Exception {
        // if RSA keys have not been created for the user, then generate them first
        check_generateRSA(context, KEY_ALIAS);

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(KEY_ALIAS, null);
        Cipher output = Cipher.getInstance(RSA_MODE);
        output.init(Cipher.DECRYPT_MODE, privateKeyEntry.getPrivateKey());

        CipherInputStream cipherInputStream = new CipherInputStream(
                new ByteArrayInputStream(encrypted), output);
        ArrayList<Byte> values = new ArrayList<>();
        int nextByte;
        while ((nextByte = cipherInputStream.read()) != -1) {
            values.add((byte)nextByte);
        }

        byte[] bytes = new byte[values.size()];
        for(int i = 0; i < bytes.length; i++) {
            bytes[i] = values.get(i);
        }

        return bytes;
    }

    private static void check_generateRSA(Context context, String KEY_ALIAS) throws Exception {
        // If there are not RSA keys saved on this phone for the current users, then we must generate them
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        // Generate the RSA key pairs
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            Log.d("Encryptionn",  "NO RSA KEY");
            // Generate a key pair for encryption
            Calendar start = Calendar.getInstance();
            Calendar end = Calendar.getInstance();
            end.add(Calendar.YEAR, 30);
            KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(context)
                    .setAlias(KEY_ALIAS)
                    .setSubject(new X500Principal("CN=" + KEY_ALIAS))
                    .setSerialNumber(BigInteger.TEN)
                    .setStartDate(start.getTime())
                    .setEndDate(end.getTime())
                    .build();
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
            kpg.initialize(spec);
            kpg.generateKeyPair(); // privateKeys are not accessible in the app or outside

            Log.d("Encryptionn",  "NOW YES RSA KEY");
        }
    }

    static void saveAESkeyInPrefs(Context context, SecretKey keyAES, String KEY_ALIAS, SingletonSharedPreferences preferences){
        try{
            // RSAkeys will be created under an alias, the user's email. If a user registers with
            // an email and also with social media, and both emails turn out to be the same, then
            // RSA will be the same for both but AES will be different, which the one used for encrypting data
            // that is saved in the prefs and in the server
            byte[] aesEncryptedBytes = rsaEncrypt(context,
                    keyAES.getEncoded(), KEY_ALIAS );

            // save the AES key as a string in base 64
            preferences.save_AESkey_rsaEncrypted(Static_AppMethods.bytesToStringBase64(aesEncryptedBytes));

            global_keyAES = keyAES;

            Log.d("Encryptionn",  "keyAES string prefs= " +  Static_AppMethods.bytesToStringBase64(aesEncryptedBytes) );

            Log.d("Encryptionn",  "email= " + preferences.get_lastUser_email_logged() +" keyAES string prefs= " +  preferences.get_AESkey_rsaEncrypted() );
        } catch (Exception e){
            Log.d("Encryptionn", "Error: " + e.getMessage());
            Toast.makeText(context, context.getResources().getString(R.string.errorWithEncryption)
                    + "\n" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static void getAESkeyFromPrefs (Context context, String KEY_ALIAS, SingletonSharedPreferences preferences) throws Exception{
        String AESkey64_rsaEncrypted = preferences.get_AESkey_rsaEncrypted(); // get the AES key as a string in base 64
        Log.e("Encryptionn", "Prefs AESkey64_rsaEncrypted: " + AESkey64_rsaEncrypted );
        //convert it to bytes. Remember that the AES key was encrypted using RSA
        byte[] AESkey_rsaEncrypted_bytes = Static_AppMethods.stringBase64toBytes(AESkey64_rsaEncrypted);
        // Now decrypt the AES key
        byte[] AESkey_decrypted_bytes = rsaDecrypt(context, AESkey_rsaEncrypted_bytes, KEY_ALIAS );
        // generate the AES key from the decrypted bytes
        global_keyAES = new SecretKeySpec(AESkey_decrypted_bytes, 0, AESkey_decrypted_bytes.length, "AES");
    }
}






