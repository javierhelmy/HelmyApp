package com.taedison.helmy;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;

import java.util.HashMap;
import java.util.Map;

/***
 * This activity is shown when the user registered using email and password and still has to verify/confirm he/she owns the email account
 * */
public class ActivityConfirmEmail extends AppCompatActivity {

    final String TAG = "confirmEmail";

    //Volley
    private RequestQueue requestQueue;
    // Preferences
    private SingletonSharedPreferences preferences;
    String email, password;
    private ProgressBar pbConfirm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirm_email);

        pbConfirm = findViewById(R.id.pbConfirm);
        pbConfirm.setVisibility(View.INVISIBLE);

        //preferences
        preferences = SingletonSharedPreferences.getInstance(this.getApplicationContext());

        //volley
        requestQueue = SingletonVolley.getInstance(this.getApplicationContext()).getRequestQueue();

        Intent intent = getIntent();
        if(intent.hasExtra("email")){
            // we keep the email and password to send to the login activity to save the user sometime.
            // if the user had closed the app previously, then the user will have to enter them again in the login activity
            email = intent.getStringExtra("email");
            password = intent.getStringExtra("password");
        }

//        Log.d(TAG, "email: " + preferences.get_lastUser_email_logged() );
    }

    public void click_BackToLoginRegister(View view) {
        pbConfirm.setVisibility(View.INVISIBLE);
        Static_AppMethods.logOut(this, pbConfirm);
        Intent intent = new Intent(this, ActivityLoginRegister.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK|Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    public void click_goToLogin(View view) {
        // method registered in layout
        pbConfirm.setVisibility(View.INVISIBLE);
        Intent intent = new Intent(this, ActivityLogin.class);
        if( !TextUtils.isEmpty(email)){
            intent.putExtra("email", email);
            intent.putExtra("password", password);
        }
        startActivity(intent);
    }

    public void click_sendEmailAgain(View view) {
        // method registered in layout
        pbConfirm.setVisibility(View.VISIBLE);
        requestQueue.add(requestEmailAgain(preferences.get_lastUser_email_logged()));
    }

    private StringRequest requestEmailAgain(final String email){
        String url;
        if(preferences.get_isPrimaryServerDown()){
            // primary server is down, so we will use the secondary
            url = Static_AppVariables.url_emailAgain2;
        } else {
            url = Static_AppVariables.url_emailAgain;
        }

        return new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response)
                    {
                        pbConfirm.setVisibility(View.INVISIBLE);
//                        Log.d(TAG, "response: " + response);
                        if( !TextUtils.isEmpty(response) ){
                            if(response.contains("1")) {
                                // user has not validated his/her email account
                                Toast.makeText(ActivityConfirmEmail.this, getResources().
                                        getString(R.string.validateEmail), Toast.LENGTH_SHORT).show();
                            } else if(response.contains("2")){
                                // user already validated the email, go to login
                                Toast.makeText(ActivityConfirmEmail.this, getResources().
                                        getString(R.string.emailWasVavlidated), Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(ActivityConfirmEmail.this, ActivityLogin.class);
                                intent.putExtra("email", email);
                                intent.putExtra("password", "");
                                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK|Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            } else {
                                // email not registered. This should never occur though, unless there is an error in the server, e.g. email was deleted
                                Toast.makeText(ActivityConfirmEmail.this, getResources().
                                        getString(R.string.emailNotRegistered) + "\nemail= " + email +
                                        "\nresponse= " + response, Toast.LENGTH_SHORT).show();

                                Intent intent = new Intent(ActivityConfirmEmail.this, ActivityRegisterAccount.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK|Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            }
                        } else {
                            // most likely the user has mobile data on but without internet service. Worst case, something went wrong with the server
                            Static_AppMethods.ToastCheckYourInternet(ActivityConfirmEmail.this);
                        }
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                        pbConfirm.setVisibility(View.INVISIBLE);
//                        Log.d(TAG, error.toString());
                        Static_AppMethods.ToastCheckYourInternet(ActivityConfirmEmail.this);
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
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true); // if user presses back, it will just send the app to the background
    }

}
