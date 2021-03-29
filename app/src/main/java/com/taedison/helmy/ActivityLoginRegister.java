package com.taedison.helmy;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.tasks.Task;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/***
 * Activity allows users to log in with Google or facebook
 * If user is registering then we must generate a new AES encryption key and send it to the server.
 * Server responds with the userId and AES encryption key, which must be stored for throughout the app
 */
public class ActivityLoginRegister extends AppCompatActivity {
    final String TAG = "actGoogleFB";

    ImageButton btnGoogle, btnFacebook;
    ProgressBar pbRegister;

    //preferences
    SingletonSharedPreferences preferences;

    //volley singleton
    private RequestQueue requestQueue;

    //Facebook
    CallbackManager callbackManager;
    private static final String EMAIL = "email";
    String emailFacebook = null;

    //Google
    GoogleSignInClient mGoogleSignInClient;
    int RC_SIGN_IN = 123;

    //Encryption
    SecretKey keyAES;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_register);

        btnGoogle = findViewById(R.id.btnGoogle);
        btnFacebook = findViewById(R.id.btnFacebook);
        pbRegister = findViewById(R.id.pbLoginReg);

        pbRegister.setVisibility(View.GONE);

        //preferences
        preferences = SingletonSharedPreferences.getInstance(this.getApplicationContext());
        //volley
        requestQueue = SingletonVolley.getInstance(this.getApplicationContext()).getRequestQueue();

        //Facebook
        callbackManager = CallbackManager.Factory.create();

        LoginManager.getInstance().registerCallback(callbackManager,
                new FacebookCallback<LoginResult>() {
                    @Override
                    public void onSuccess(final LoginResult loginResult) {
                        GraphRequest request = GraphRequest.newMeRequest(
                                loginResult.getAccessToken(),
                                new GraphRequest.GraphJSONObjectCallback() {
                                    @Override
                                    public void onCompleted(JSONObject object, GraphResponse response) {
//                                        Log.d(TAG, response.toString());
                                        try {
                                            emailFacebook = object.getString("email");
                                        } catch (JSONException e) {
                                            e.printStackTrace();
                                        }
                                        if(emailFacebook != null){
                                            String token = loginResult.getAccessToken().getToken(); //todo send to server
//                                            Log.d(TAG, "token: " + token);
                                            generateKeyAES_sendServer(emailFacebook, "FACEBOOK", token);
                                        }
                                    }
                                });
                        Bundle parameters = new Bundle();
                        parameters.putString("fields", "email");
                        request.setParameters(parameters);
                        request.executeAsync();
                    }

                    @Override
                    public void onCancel() {
                        // nothing
                    }

                    @Override
                    public void onError(FacebookException exception) {
                        // App code
//                        Log.e(TAG, exception.toString());
                        Static_AppMethods.ToastCheckYourInternet(ActivityLoginRegister.this);
                    }

                });

        //Google
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("47959277174-5lsh2ldbgvo1dqrbhrt8i87ujnsb25lo.apps.googleusercontent.com") // gotten from google developers console
                .requestEmail()
                .build();
        // Build a GoogleSignInClient with the options specified by gso.
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

    }

    private void generateKeyAES_sendServer(String email, String password, String token){
        try{
            // generate an AESkey that will be saved in preferences after server confirms the creation of the account
            KeyGenerator keygen = KeyGenerator.getInstance("AES");
            keygen.init(128);
            keyAES = keygen.generateKey();

            StringRequest joRequest = requestRegisterFacebookGoogle(email,
                    password, Static_AppMethods.bytesToStringBase64(keyAES.getEncoded()), token );
            requestQueue.add(joRequest);
            pbRegister.setVisibility(View.VISIBLE);

        } catch (Exception e){
//            Log.e(TAG+"Encrypt", "Error: " + e.getMessage());
            Static_AppMethods.ToastEncryptionError(this);
        }
    }

    public void click_Go2Login(View view) {
        Intent i = new Intent(this, ActivityLogin.class);
        startActivity(i);
    }

    public void click_Go2Register(View view) {
        Intent i = new Intent(this, ActivityRegisterAccount.class);
        startActivity(i);
    }

    public void click_Google(View view) {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    public void click_Facebook(View view) {
        LoginManager.getInstance().logInWithReadPermissions(this, Collections.singletonList(EMAIL));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        callbackManager.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
//                Log.d(TAG, "signing");
                GoogleSignInAccount account = task.getResult(ApiException.class);

                // Signed in was successful
                if(account != null){
                    String tokenId = account.getIdToken();
//                    Log.d(TAG, "tokenId: " + tokenId);
                    generateKeyAES_sendServer(account.getEmail(),
                            "GOOGLE", tokenId);
                }
            } catch (ApiException e) {
                // The ApiException status code indicates the detailed failure reason.
                // Please refer to the GoogleSignInStatusCodes class reference for more information.
//                Log.e(TAG, "signInResult:failed code=" + e.getStatusCode());
                if(e.getStatusCode() == CommonStatusCodes.NETWORK_ERROR){
                    Static_AppMethods.ToastCheckYourInternet(this);
                }
            } catch (Exception ignored){}
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void launchAlertTermsConditions(final String emailFacebook_Google, final String userId,
                                            final String AESkey64_fromServer, final String password){
        // user must accept terms and conditions, otherwise he/she cannot continue
        final AlertMessageButton alert = new AlertMessageButton(this);
        alert.setDialogMessage( R.string.termsConditions );
        alert.setDialogPositiveButton(getResources().getString(R.string.Yes),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        StringRequest joRequest = requestSendTyC(userId, "1", emailFacebook_Google, AESkey64_fromServer, password);
                        requestQueue.add(joRequest);
                        pbRegister.setVisibility(View.VISIBLE);
                        alert.dismissAlert();
                    }
                });
        alert.setDialogNegativeButton(getResources().getString(R.string.No), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alert.dismissAlert();
            }
        });
        alert.setupHyperlinks();
        alert.showAlert();
    }

    private StringRequest requestRegisterFacebookGoogle(final String emailFacebook_Google, final String password,
                                                        final String AESkey_string64, final String token){
        String url;
        if(preferences.get_isPrimaryServerDown()){
            // primary server is down, so we will use the secondary
            url = Static_AppVariables.url_social2;
        } else {
            url = Static_AppVariables.url_social;
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
//                                Log.d(TAG+"Volley", "Jason: " + jsonArray.toString());
                                String status = jsonArray.getString("status");
                                String userID = jsonArray.getString("userId");

                                if( status.equals("1") ){
                                    String AESkey64_fromServer = jsonArray.getString("userK");
                                    Toast.makeText(getApplicationContext(), R.string.accountRegisteredSuccesfully, Toast.LENGTH_SHORT).show();
                                    launchAlertTermsConditions(emailFacebook_Google, userID, AESkey64_fromServer, password);
                                } else if( status.equals("2") ) {
                                    // Facebook email was already registered
                                    // check if user accepted Terms and conditions
                                    String tyc = jsonArray.getString("tyc");
                                    String AESkey64_fromServer = jsonArray.getString("userK");
                                    if(tyc.equals("0")){
                                        // user did not accept Terms and conditions
                                        launchAlertTermsConditions(emailFacebook_Google, userID, AESkey64_fromServer, password);
                                    } else {
                                        // user already accepted TyC
                                        byte[] aesKeyBytes = Static_AppMethods.stringBase64toBytes(AESkey64_fromServer);
                                        SecretKey keyAES = new SecretKeySpec(aesKeyBytes, 0, aesKeyBytes.length, "AES");
                                        Static_AppMethods.saveAESkeyInPrefs(ActivityLoginRegister.this,
                                                keyAES, emailFacebook_Google, preferences);

                                        preferences.save_LastUser_logged(emailFacebook_Google, userID);
                                        preferences.reset(getApplicationContext());
                                        // save the login form, regardless if the user does not accept the terms and conditions. This is only used for logging out
                                        if(password.equals("GOOGLE")){
                                            preferences.save_userLoginForm(1); // 1 means user logged in with GOOGLE. Useful for optimizing logging out
                                        } else {
                                            preferences.save_userLoginForm(2); // 0 means user logged in with FACEBOOK. Useful for optimizing logging out
                                        }

                                        // Go to retrive data from server
                                        Intent intent = new Intent(ActivityLoginRegister.this, ActivityRetrieveDataFromServer.class);
                                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                        startActivity(intent);
                                    }
                                } else {
//                                    Log.d(TAG+"Volley", "Response does not contains 1 or 2. " + response);
                                    Toast.makeText(getApplicationContext(), R.string.errorWithServer, Toast.LENGTH_SHORT).show();
                                }

                            } catch (JSONException e) {
                                e.printStackTrace();
                                Toast.makeText(getApplicationContext(), R.string.errorWithServer, Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
//                                Log.e(TAG+"Encrypt", "Error: " + e.getMessage());
                                Toast.makeText(ActivityLoginRegister.this, getResources().getString(R.string.errorWithEncryption)
                                        + "\n" + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            // most likely the user has mobile data on but without internet service. Worst case, something went wrong with the server
                            Static_AppMethods.ToastCheckYourInternet(ActivityLoginRegister.this);
                        }
                        pbRegister.setVisibility(View.INVISIBLE);
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                        pbRegister.setVisibility(View.INVISIBLE);
//                        Log.e(TAG+"Volley", error.toString());
                        Static_AppMethods.ToastCheckYourInternet(ActivityLoginRegister.this);
                        Static_AppMethods.checkResponseCode(error, preferences);
                    }
                })
        {
            @Override
            protected Map<String, String> getParams()
            {
//                Log.d(TAG+"Volley", "email: " + emailFacebook_Google + " password: " + password + " userK: " + AESkey_string64 );
                Map<String, String> params = new HashMap<>();
                params.put("email", emailFacebook_Google);
                params.put("password", password);
                params.put("userK", AESkey_string64);
                params.put("token", token);
                return params;
            }
        };
    }

    private StringRequest requestSendTyC(final String userID, final String TyC, final String emailFacebook_Google,
                                         final String AESkey64_fromServer, final String password){
        // save user's response to terms and conditions
        String url;
        if(preferences.get_isPrimaryServerDown()){
            // primary server is down, so we will use the secondary
            url = Static_AppVariables.url_social2;
        } else {
            url = Static_AppVariables.url_social;
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
//                                Log.d(TAG+"Volley", "Jason: " + jsonArray.toString());
                                String status = jsonArray.getString("status");

                                if( status.equals("4") ) {
                                    // user acceptance was stored successfully in server
                                    byte[] aesKeyBytes = Static_AppMethods.stringBase64toBytes(AESkey64_fromServer);
                                    SecretKey keyAES = new SecretKeySpec(aesKeyBytes, 0, aesKeyBytes.length, "AES");
                                    Static_AppMethods.saveAESkeyInPrefs(ActivityLoginRegister.this,
                                            keyAES, emailFacebook_Google, preferences);

                                    preferences.save_LastUser_logged(emailFacebook_Google, userID);
                                    preferences.reset(getApplicationContext());
                                    // save the login form, regardless if the user does not accept the terms and conditions. This is only used for logging out
                                    if(password.equals("GOOGLE")){
                                        preferences.save_userLoginForm(1); // 1 means user logged in with GOOGLE. Useful for optimizing logging out
                                    } else {
                                        preferences.save_userLoginForm(2); // 0 means user logged in with FACEBOOK. Useful for optimizing logging out
                                    }

                                    Intent intent = new Intent(ActivityLoginRegister.this, ActivityProgress.class);
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                } else {
//                                    Log.d(TAG+"Volley", "Response does not contains 4. " + response);
                                    Toast.makeText(getApplicationContext(), R.string.errorTryAgain, Toast.LENGTH_SHORT).show();
                                }

                            } catch (JSONException e) {
                                e.printStackTrace();
                                Toast.makeText(getApplicationContext(), R.string.errorWithServer, Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
//                                Log.e(TAG+"Encrypt", "Error: " + e.getMessage());
                                Toast.makeText(ActivityLoginRegister.this, getResources().getString(R.string.errorTryAgain)
                                        + "\n" + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            // most likely the user has mobile data on but without internet service. Worst case, something went wrong with the server
                            Static_AppMethods.ToastCheckYourInternet(ActivityLoginRegister.this);
                        }
                        pbRegister.setVisibility(View.INVISIBLE);
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                        pbRegister.setVisibility(View.INVISIBLE);
//                        Log.e(TAG+"Volley", error.toString());
                        Static_AppMethods.ToastCheckYourInternet(ActivityLoginRegister.this);
                        Static_AppMethods.checkResponseCode(error, preferences);
                    }
                })
        {
            @Override
            protected Map<String, String> getParams()
            {
                Map<String, String> params = new HashMap<>();
                params.put("userId", userID);
                params.put("tyc", TyC);
                return params;
            }
        };
    }
}
