package com.taedison.helmy;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.google.android.material.textfield.TextInputLayout;

import java.util.HashMap;
import java.util.Map;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
/***
 * Activity allows users to register with email and password
 * We must generate a new AES encryption key and send it to the server.
 * Server responds with the userId and the same AES encryption key. which must be stored for throughout the app
 */
public class ActivityRegisterAccount extends AppCompatActivity {

    final String TAG = "actRegAccount";


    EditText etUserNameReg, etPasswordReg, etConfirmPassword;
    TextInputLayout tilUserName, tilPasswordReg, tilConfirmPassword;
    TextView btnVisibilityRegPassword, btnVisibilityConfirmPassword;
    TextView btnRegisterAccount;
    ProgressBar pbRegister;

    private RequestQueue requestQueue;
    //preferences
    SingletonSharedPreferences preferences;

    SecretKey keyAES;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_account);

        etUserNameReg = findViewById(R.id.etUserNameRegister);
        etPasswordReg = findViewById(R.id.etPasswordRegister);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);

        tilUserName = findViewById(R.id.tilUserName);
        tilPasswordReg = findViewById(R.id.tilPasswordRegister);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);

        btnVisibilityRegPassword = findViewById(R.id.btnVisibilityRegPassword);
        btnVisibilityConfirmPassword = findViewById(R.id.btnVisibilityConfirmPassword);
        btnRegisterAccount = findViewById(R.id.btnRegisterAccount);

        pbRegister = findViewById(R.id.pbRegister);
        pbRegister.setVisibility(View.INVISIBLE);

        //preferences
        preferences = SingletonSharedPreferences.getInstance(this.getApplicationContext());
        //volley
        requestQueue = SingletonVolley.getInstance(this.getApplicationContext()).getRequestQueue();

    }

    private StringRequest request(final String email, final String password, final String AESkey_string64){
        String url;
        if(preferences.get_isPrimaryServerDown()){
            // primary server is down, so we will use the secondary
            url = Static_AppVariables.url_newAccount2;
        } else {
            url = Static_AppVariables.url_newAccount;
        }

        StringRequest strRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response)
                    {
                        pbRegister.setVisibility(View.INVISIBLE);
                        if( !TextUtils.isEmpty(response) ){
                            if(response.contains("1")){
                                Log.d(TAG+"volley", "Response contains 1. " + response);
                                Go2NextActivity();
                            } else {
                                Log.d(TAG+"volley", "Response does not contains 1. " + response);
                                Toast.makeText(getApplicationContext(), R.string.accountAlreadyExists, Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            // most likely the user has mobile data on but without internet service. Worst case, something went wrong with the server
                            Static_AppMethods.ToastCheckYourInternet(ActivityRegisterAccount.this);
                        }
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
//                        error.networkResponse.statusCode;
                        pbRegister.setVisibility(View.INVISIBLE);
                        Log.e(TAG+"volley", "response error: " + error.toString());
                        Static_AppMethods.ToastCheckYourInternet(ActivityRegisterAccount.this);
                        Static_AppMethods.checkResponseCode(error, preferences);
                    }
                })
        {
            @Override
            protected Map<String, String> getParams()
            {
                Map<String, String> params = new HashMap<>();
                params.put("email", email);
                params.put("password", password);
                params.put("userK", AESkey_string64);
                return params;
            }
        };

        // increase the timeout period because for some reason this URL end point is throwing: BasicNetwork.logSlowRequests
        strRequest.setRetryPolicy(new DefaultRetryPolicy(
                DefaultRetryPolicy.DEFAULT_TIMEOUT_MS*4,
                0,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        return strRequest;
    }



    public void registerNewAccount(View view) {
        if (verifyFields()){
            launchAlertTermsConditions();
        }
    }

    private void launchAlertTermsConditions(){
        // user must accept terms and conditions in order to continue
        final AlertMessageButton alert = new AlertMessageButton(this);
        alert.setDialogMessage(R.string.termsConditions);
        alert.setDialogPositiveButton(getResources().getString(R.string.Yes),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        try{
                            // generate an AESkey that will be saved in preferences after server confirms the creation of the account
                            KeyGenerator keygen = KeyGenerator.getInstance("AES");
                            keygen.init(128);
                            keyAES = keygen.generateKey();

                            StringRequest joRequest = request(etUserNameReg.getText().toString(),
                                    etPasswordReg.getText().toString(), Static_AppMethods.bytesToStringBase64(keyAES.getEncoded()) );

                            requestQueue.add(joRequest);
                            pbRegister.setVisibility(View.VISIBLE);
                            alert.dismissAlert();
                        } catch (Exception e){
                            Log.e(TAG+"Encrypt", "Error: " + e.getMessage());
                            Toast.makeText(ActivityRegisterAccount.this, getResources().
                                    getString(R.string.errorWithEncryption)
                                    + "\n" + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
        alert.setDialogNegativeButton(getResources().getString(R.string.No), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alert.dismissAlert();
                AlertMessageButton alert2 = new AlertMessageButton(ActivityRegisterAccount.this);
                alert2.setDialogMessage(getResources().getString(R.string.didntAcceptTermsConditions));
                alert2.showAlert();
            }
        });

        alert.setupHyperlinks();
        alert.showAlert();

    }

    private boolean verifyFields() {
        boolean verified = true;
        String email = etUserNameReg.getText().toString();
        String password = etPasswordReg.getText().toString();
        //hide errors
        tilUserName.setErrorEnabled(false);
        tilPasswordReg.setErrorEnabled(false);
        tilConfirmPassword.setErrorEnabled(false);
        Static_AppMethods.checkField(etUserNameReg);
        Static_AppMethods.checkField(etPasswordReg);
        Static_AppMethods.checkField(etConfirmPassword);
        if (email.isEmpty()) {
            tilUserName.setError(getResources().getString(R.string.emailEmpty));
            Static_AppMethods.checkField(etUserNameReg);
            verified = false;
        } else if (!email.matches(".+@.+[.].+")) {
            tilUserName.setError(getResources().getString(R.string.emailInvalid));
            verified = false;
        }
        if (password.isEmpty()) {
            tilPasswordReg.setError(getResources().getString(R.string.passwordEmpty));
            Static_AppMethods.checkField(etPasswordReg);
            verified = false;
        } else if (password.length()<6) {
            tilPasswordReg.setError(getResources().getString(R.string.passwordMin6characters));
            verified = false;
        }
        if (!password.equals(etConfirmPassword.getText().toString())) {
            tilConfirmPassword.setError(getResources().getString(R.string.passwordsDoNotMatch));
            verified = false;
        }

        return verified;
    }

    private void Go2NextActivity() {
        preferences.save_LastUser_logged(etUserNameReg.getText().toString(), "0"); // 0 because user still needs to validate his/her email
        preferences.reset(getApplicationContext());
        Intent i = new Intent(this, ActivityConfirmEmail.class);
        i.putExtra("email", etUserNameReg.getText().toString());
        i.putExtra("password", etPasswordReg.getText().toString());
        startActivity(i);
    }

    boolean regPasswordVisible = false;
    public void click_showRegPassword(View view) {
        if(regPasswordVisible){
            regPasswordVisible = false;
            etPasswordReg.setTransformationMethod(new PasswordTransformationMethod());
            btnVisibilityRegPassword.setBackgroundResource(R.drawable.ic_visibility_black_24dp);
        } else {
            regPasswordVisible = true;
            etPasswordReg.setTransformationMethod(null);
            btnVisibilityRegPassword.setBackgroundResource(R.drawable.ic_visibility_off_black_24dp);
        }
        etPasswordReg.setSelection(etPasswordReg.getText().length());
    }

    boolean confirmPasswordVisible = false;
    public void click_showConfirmPassword(View view) {
        if(confirmPasswordVisible){
            confirmPasswordVisible = false;
            etConfirmPassword.setTransformationMethod(new PasswordTransformationMethod());
            btnVisibilityConfirmPassword.setBackgroundResource(R.drawable.ic_visibility_black_24dp);
        } else {
            confirmPasswordVisible = true;
            etConfirmPassword.setTransformationMethod(null);
            btnVisibilityConfirmPassword.setBackgroundResource(R.drawable.ic_visibility_off_black_24dp);
        }
        etConfirmPassword.setSelection(etConfirmPassword.getText().length());
    }

    public void goBack(View view) {
        onBackPressed(); // sends app to the background
    }
}
