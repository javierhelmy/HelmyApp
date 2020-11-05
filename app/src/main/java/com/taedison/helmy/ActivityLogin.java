package com.taedison.helmy;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static com.taedison.helmy.Static_AppMethods.checkField;

/***
 * Activity allows users to log in with email and password
 * Server responds with the userId and AES encryption key, which must be stored for throughout the app
 */
public class ActivityLogin extends AppCompatActivity {

    final String TAG = "actLogin";

    EditText etUserNameLogin, etPasswordLogin;
    TextView btnLogin;
    Button btnVisibilityPasswordLogin;
    ProgressBar pbLogin;

    //Volley
    private RequestQueue requestQueue;

    SingletonSharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etUserNameLogin = findViewById(R.id.etUserName);
        etPasswordLogin = findViewById(R.id.etPasswordLogin);
        btnVisibilityPasswordLogin = findViewById(R.id.btnVisibilityLoginPassword);
        btnLogin = findViewById(R.id.btnLogin);

        pbLogin = findViewById(R.id.pbLogin);
        pbLogin.setVisibility(View.INVISIBLE);

        //preferences
        preferences = SingletonSharedPreferences.getInstance(this.getApplicationContext());

        //volley
        requestQueue = SingletonVolley.getInstance(this.getApplicationContext()).getRequestQueue();

        // if registered, then autofill email and password
        Intent intent = getIntent();
        String email = intent.getStringExtra("email");
        if(!TextUtils.isEmpty(email)){
            etUserNameLogin.setText(email);
            etPasswordLogin.setText( intent.getStringExtra("password") );
        }

    }

    public void btn_Login(View view) {
        if(!TextUtils.isEmpty(etUserNameLogin.getText()) && !TextUtils.isEmpty(etPasswordLogin.getText())){
//            Toast.makeText(this,"Correct", Toast.LENGTH_SHORT).show();
            StringRequest joRequest = request(etUserNameLogin.getText().toString(),
                    etPasswordLogin.getText().toString());

            requestQueue.add(joRequest);

            pbLogin.setVisibility(View.VISIBLE);

        } else {
            Toast.makeText(this, R.string.emailPasswordEmpty, Toast.LENGTH_SHORT).show();

            checkField(etUserNameLogin);
            checkField(etPasswordLogin);
        }
    }

    private StringRequest request(final String email, final String password){
        String url;
        if(preferences.get_isPrimaryServerDown()){
            // primary server is down, so we will use the secondary
            url = Static_AppVariables.url_login2;
        } else {
            url = Static_AppVariables.url_login;
        }

        StringRequest strRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response)
                    {
                        Log.d("RequestVolleyLogin", "response: " + response);
                        pbLogin.setVisibility(View.INVISIBLE);
                        if( !TextUtils.isEmpty(response) ){
                            try {
                                JSONObject jsonArray = new JSONObject(response);
                                String status = jsonArray.getString("status");
                                if( status.equals("0") ){
                                    // user already register but has not validated his/her email
                                    preferences.save_LastUser_logged(etUserNameLogin.getText().toString(), "0"); // 0 because user still needs to validate his/her email
                                    preferences.reset(getApplicationContext());
                                    Intent i = new Intent(ActivityLogin.this, ActivityConfirmEmail.class);
                                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    i.putExtra("email", etUserNameLogin.getText().toString());
                                    i.putExtra("password", etPasswordLogin.getText().toString());
                                    startActivity(i);
                                } else if( status.equals("1") ) {
                                    // succesful login so now check if user already accepted terms and conditions
                                    String TyC = jsonArray.getString("tyc");
                                    String AESkey64_fromServer = jsonArray.getString("userK");
                                    String userId_fromServer = jsonArray.getString("userId");
                                    if(TyC.equals("1")){
                                        // already accepted terms and conditions. Now store userId and userK (AES encryption key)
                                        AESkey64_fromServer = AESkey64_fromServer.replace("\n", "");
                                        Log.d(TAG+"Volley", "userId: " + userId_fromServer + " AESkey_base64: " + AESkey64_fromServer);
                                        try{
                                            byte[] aesKeyBytes = Static_AppMethods.stringBase64toBytes(AESkey64_fromServer);
                                            SecretKey keyAES = new SecretKeySpec(aesKeyBytes, 0, aesKeyBytes.length, "AES");
                                            Static_AppMethods.saveAESkeyInPrefs(ActivityLogin.this,
                                                keyAES, email, preferences);

                                            preferences.save_LastUser_logged(etUserNameLogin.getText().toString(), userId_fromServer);
                                            preferences.reset(getApplicationContext());
                                            preferences.save_userLoginForm(0); // 0 means user logged in with email. Useful for optimizing logging out
                                            Go2ActivityRetrieve();

                                        } catch (Exception e){
                                            Log.e("Encryptionn", "Error: " + e.getMessage());
                                            Static_AppMethods.ToastEncryptionError(ActivityLogin.this);
                                        }
                                    } else {
                                        // user has not accepted terms and conditions. We send TyC = 1 to the server right a way because during signup he/she already accepted. This logic may change though.
                                        StringRequest joRequest = requestSendTyC(email, userId_fromServer, "1", AESkey64_fromServer.replace("\n", ""));
                                        requestQueue.add(joRequest);
                                        pbLogin.setVisibility(View.VISIBLE);
                                    }
                                } else {
                                    Toast.makeText(getApplicationContext(), R.string.email_password_wrong, Toast.LENGTH_SHORT).show();
                                }

                            } catch (JSONException e) {
                                e.printStackTrace();
                                Toast.makeText(getApplicationContext(), R.string.errorTryAgain, Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            // most likely the user has mobile data on but without internet service. Worst case, something went wrong with the server
                            Static_AppMethods.ToastCheckYourInternet(ActivityLogin.this);
                        }
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                        pbLogin.setVisibility(View.INVISIBLE);
                        Log.d(TAG+"Volley", error.toString());
                        Static_AppMethods.ToastCheckYourInternet(ActivityLogin.this);
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
                return params;
            }
        };

        strRequest.setRetryPolicy(new DefaultRetryPolicy(
                DefaultRetryPolicy.DEFAULT_TIMEOUT_MS * 2,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        return strRequest;
    }

    private void Go2ActivityRetrieve() {
        Intent intent = new Intent(this, ActivityRetrieveDataFromServer.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    boolean loginPasswordVisible = false;
    public void showLoginPassword(View view) {
        if(loginPasswordVisible){
            loginPasswordVisible = false;
            etPasswordLogin.setTransformationMethod(new PasswordTransformationMethod());
            btnVisibilityPasswordLogin.setBackgroundResource(R.drawable.ic_visibility_black_24dp);
        } else {
            loginPasswordVisible = true;
            etPasswordLogin.setTransformationMethod(null);
            btnVisibilityPasswordLogin.setBackgroundResource(R.drawable.ic_visibility_off_black_24dp);
        }
        etPasswordLogin.setSelection(etPasswordLogin.getText().length());
    }

    public void goBack(View view) {
        onBackPressed();
    }

    public void click_forgotPassword(View view) {
        if( TextUtils.isEmpty(etUserNameLogin.getText()) ) {
            checkField(etUserNameLogin);
            Toast.makeText(this, R.string.emailEmpty, Toast.LENGTH_SHORT).show();
        } else {
            StringRequest joRequest = requestForgotPassword( etUserNameLogin.getText().toString() );
            requestQueue.add(joRequest);
            pbLogin.setVisibility(View.VISIBLE);
        }
    }

    private StringRequest requestForgotPassword(final String email){
        String url;
        if(preferences.get_isPrimaryServerDown()){
            // primary server is down, so we will use the secondary
            url = Static_AppVariables.url_forgotPassword2;
        } else {
            url = Static_AppVariables.url_forgotPassword;
        }

        StringRequest strRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response)
                    {
                        Log.d(TAG+"Volley", response);
                        if( !TextUtils.isEmpty(response) ){
                            try {
                                JSONObject jsonArray = new JSONObject(response);
                                String status = jsonArray.getString("status");
                                if(status.equals("0")){
                                    Toast.makeText(getApplicationContext(), R.string.emailNotRegistered, Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(getApplicationContext(), R.string.go2email, Toast.LENGTH_SHORT).show();
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                                Toast.makeText(getApplicationContext(), R.string.errorTryAgain, Toast.LENGTH_SHORT).show();

                            }
                        } else {
                            // most likely the user has mobile data on but without internet service. Worst case, something went wrong with the server
                            Static_AppMethods.ToastCheckYourInternet(ActivityLogin.this);
                        }
                        pbLogin.setVisibility(View.INVISIBLE);
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                        pbLogin.setVisibility(View.INVISIBLE);
                        Log.d(TAG+"Volley", error.toString());
                        Static_AppMethods.ToastCheckYourInternet(ActivityLogin.this);
                        Static_AppMethods.checkResponseCode(error, preferences);
                    }
                })
        {
            @Override
            protected Map<String, String> getParams()
            {
                Map<String, String> params = new HashMap<>();
                params.put("email", email);
                return params;
            }
        };

        return strRequest;
    }

    private StringRequest requestSendTyC(final String email, final String userId_fromServer, final String TyC,
                                         final String AESkey64_fromServer){
        // save user's response to terms and conditions
        String url;
        if(preferences.get_isPrimaryServerDown()){
            // primary server is down, so we will use the secondary
            url = Static_AppVariables.url_newAccount2;
        } else {
            url = Static_AppVariables.url_newAccount;
        }

        return new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response)
                    {
                        if( !TextUtils.isEmpty(response) ){
                            try {
                                JSONObject jsonArray = new JSONObject(response);
                                Log.d(TAG+"Volley", "Jason: " + jsonArray.toString());
                                String status = jsonArray.getString("status");

                                if( status.equals("2") ) {
                                    // user acceptance was stored successfully in server
                                    Log.d(TAG+"Volley", "userId: " + userId_fromServer + " AESkey_base64: " + AESkey64_fromServer);
                                    try{
                                        byte[] aesKeyBytes = Static_AppMethods.stringBase64toBytes(AESkey64_fromServer);
                                        SecretKey keyAES = new SecretKeySpec(aesKeyBytes, 0, aesKeyBytes.length, "AES");
                                        Static_AppMethods.saveAESkeyInPrefs(ActivityLogin.this,
                                                keyAES, email, preferences);

                                        preferences.save_LastUser_logged(etUserNameLogin.getText().toString(), userId_fromServer);
                                        preferences.reset(getApplicationContext());
                                        preferences.save_userLoginForm(0); // 0 means user logged in with email. Useful for optimizing logging out
                                        Go2ActivityRetrieve();

                                    } catch (Exception e){
                                        Log.d(TAG+"Encrypt", "Error: " + e.getMessage());
                                        Toast.makeText(ActivityLogin.this, getResources().getString(R.string.errorWithEncryption)
                                                + "\n" + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    Log.d(TAG+"Volley", "Response does not contains 2. " + response);
                                    Toast.makeText(getApplicationContext(), R.string.errorTryAgain, Toast.LENGTH_SHORT).show();
                                }

                            } catch (JSONException e) {
                                e.printStackTrace();
                                Toast.makeText(getApplicationContext(), R.string.errorWithServer, Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                Log.d(TAG+"Encrypt", "Error: " + e.getMessage());
                                Toast.makeText(ActivityLogin.this, getResources().getString(R.string.errorTryAgain)
                                        + "\n" + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            // most likely the user has mobile data on but without internet service. Worst case, something went wrong with the server
                            Static_AppMethods.ToastCheckYourInternet(ActivityLogin.this);
                        }
                        pbLogin.setVisibility(View.INVISIBLE);
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                        pbLogin.setVisibility(View.INVISIBLE);
                        Log.e(TAG+"Volley", error.toString());
                        Static_AppMethods.ToastCheckYourInternet(ActivityLogin.this);
                        Static_AppMethods.checkResponseCode(error, preferences);
                    }
                })
        {
            @Override
            protected Map<String, String> getParams()
            {
                Map<String, String> params = new HashMap<>();
                params.put("userId", userId_fromServer);
                params.put("tyc", TyC);
                return params;
            }
        };
    }
}
