package com.taedison.helmy;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/***
 * User enters part of his/her personal information
 * Data is encrypted and sent to the server
 */
public class ActivityRegisterUser_PersonalInfo2 extends AppCompatActivity {

    final String TAG = "personalInfo2";

    EditText etNames, etSurnames, etAge, etLicenseNum;
    ProgressBar pbInfo1;

    Spinner spinnerRH;
    ArrayList<String> arrayRHs;

    // preferences
    SingletonSharedPreferences preferences;

    //Volley
    private RequestQueue requestQueue;

    ConstraintLayout CL_progress_logo;
    boolean isKeyboardShowing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_user_personal_info2);

        ImageView circle = findViewById(R.id.circle);
        Static_AppMethods.animateProgressCircle(circle);

        etNames = findViewById(R.id.etUserNameReg);
        etSurnames = findViewById(R.id.etSurnamesReg);
        etAge = findViewById(R.id.etAge);
        etLicenseNum = findViewById(R.id.etIdNumOCR);

        pbInfo1 = findViewById(R.id.pbInfo2);
        pbInfo1.setVisibility(View.INVISIBLE);

        spinnerRH = findViewById(R.id.spinnerRH);

        arrayRHs = Static_AppVariables.arrayRHs;
        arrayRHs.add(0, getResources().getString(R.string.userRH));
        spinnerRH.setAdapter(new ClassSpinnerAdapter(this, R.layout.textview_template_spinner, arrayRHs, spinnerRH));

        //preferences
        preferences = SingletonSharedPreferences.getInstance(this.getApplicationContext());

        if( !TextUtils.isEmpty(preferences.getUserLicenseNum()) ) {
            etLicenseNum.setText(preferences.getUserLicenseNum());
            etNames.setText(preferences.getUserNames());
            etSurnames.setText(preferences.getUserSurnames());
            etAge.setText( String.valueOf(preferences.getUserAge()) );
            spinnerRH.setSelection(arrayRHs.indexOf(preferences.getUserRH()));
        }

        final LinearLayout LLtitle = findViewById(R.id.LLtitle2);
        final ConstraintLayout contentView = findViewById(R.id.CL_Info2);
        CL_progress_logo = findViewById(R.id.CL_progress_logo);

        contentView.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        // this is used to hide/show views when the keyboard is hidden/shown.
                        // AdjustPan does not work in activities with no action bar
                        Rect r = new Rect();
                        contentView.getWindowVisibleDisplayFrame(r);
                        int screenHeight = contentView.getRootView().getHeight();

                        // r.bottom is the position above soft keypad or device button.
                        // if keypad is shown, the r.bottom is smaller than that before.
                        int keypadHeight = screenHeight - r.bottom;

                        if (keypadHeight > screenHeight * 0.15) { // 0.15 ratio is perhaps enough to determine keypad height.
                            // keyboard is opened
                            if (!isKeyboardShowing) {
                                isKeyboardShowing = true;
                                CL_progress_logo.setVisibility(View.GONE);
                                LLtitle.setVisibility(View.GONE);
                            }
                        }
                        else {
                            // keyboard is closed
                            if (isKeyboardShowing) {
                                isKeyboardShowing = false;
                                CL_progress_logo.setVisibility(View.VISIBLE);
                                LLtitle.setVisibility(View.VISIBLE);
                            }
                        }
                    }
                });

        // get data that was extracted using the camera
        Intent i = getIntent();
        if( !TextUtils.isEmpty(i.getStringExtra("idNum")) ){
            etLicenseNum.setText(i.getStringExtra("idNum"));
            etNames.setText(i.getStringExtra("names"));
            etSurnames.setText(i.getStringExtra("surnames"));
            etAge.setText(i.getStringExtra("age"));
            spinnerRH.setSelection( arrayRHs.indexOf(i.getStringExtra("rh")) );
        }

        //volley
        requestQueue = SingletonVolley.getInstance(this.getApplicationContext()).getRequestQueue();
    }

    boolean saving;
    public void savePersonalInfo1(View view) {
        Static_AppMethods.checkField(etNames);
        Static_AppMethods.checkField(etSurnames);
        Static_AppMethods.checkField(etAge);
        Static_AppMethods.checkField(etLicenseNum);
        Static_AppMethods.checkField(spinnerRH);
        if(TextUtils.isEmpty(etNames.getText()) || TextUtils.isEmpty(etSurnames.getText())
                || TextUtils.isEmpty(etAge.getText()) || TextUtils.isEmpty(etLicenseNum.getText())
                || spinnerRH.getSelectedItemPosition() == 0 ){
            Toast.makeText(this, R.string.AllFieldsAreRequired, Toast.LENGTH_SHORT).show();
        } else if(etNames.getText().toString().equals(preferences.getUserNames())
                && etSurnames.getText().toString().equals(preferences.getUserSurnames())
                && etAge.getText().toString().equals(preferences.getUserAge())
                && etLicenseNum.getText().toString().equals(preferences.getUserLicenseNum())
                && spinnerRH.getSelectedItem().equals(preferences.getUserRH())){
            Intent intent = new Intent(ActivityRegisterUser_PersonalInfo2.this, ActivityRegisterUser_PersonalInfo3.class);
            startActivity(intent);
        } else if(Integer.parseInt(etAge.getText().toString()) < 16){
            etAge.setBackgroundResource(R.drawable.redcontour_rounded);
            Toast.makeText(this, getResources().getString(R.string.minimumAge), Toast.LENGTH_SHORT).show();
        } else {
            if(!saving){
                try {
                    String email = preferences.get_lastUser_email_logged();
                    String licenseNumEncrypted = Static_AppMethods.encryptAES_toString64(this,
                            etLicenseNum.getText().toString(), email, preferences);
                    String namesEncrypted = Static_AppMethods.encryptAES_toString64(this,
                            etNames.getText().toString(), email, preferences);
                    String surnamesEncrypted = Static_AppMethods.encryptAES_toString64(this,
                            etSurnames.getText().toString(), email, preferences);
                    String ageEncrypted = Static_AppMethods.encryptAES_toString64(this,
                            etAge.getText().toString(), email, preferences);
                    String rhEncrypted = Static_AppMethods.encryptAES_toString64(this,
                            spinnerRH.getSelectedItem().toString(), email, preferences);


                    saving = true; //prevents fromActivity entering again

                    StringRequest joRequest = request(licenseNumEncrypted, namesEncrypted,
                            surnamesEncrypted, ageEncrypted, rhEncrypted);

                    requestQueue.add(joRequest);

                    pbInfo1.setVisibility(View.VISIBLE);
                } catch (Exception e) {
//                    Log.e(TAG+"Encrypt", "Encryption Error= " + e.getMessage());
                    Static_AppMethods.ToastEncryptionError(this);
                }
            }
        }
    }

    public void tvWhyThisInfo_click(View view) {
        Static_AppMethods.launchAlertReasonForAskingPersonalInfo(this);
    }

    public void goBack(View view) {
        onBackPressed();
    }

    private StringRequest request(final String licenseNum, final String names, final String surnames,
                                  final String age, final String rh){
        String url;
        if(preferences.get_isPrimaryServerDown()){
            // primary server is down, so we will use the secondary
            url = Static_AppVariables.url_personTwo2;
        } else {
            url = Static_AppVariables.url_personTwo;
        }

        return new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response)
                    {
                        saving = false;
                        pbInfo1.setVisibility(View.INVISIBLE);
//                        Log.d("RequestVolleyReg2", "response= " + response);

                        if( !TextUtils.isEmpty(response) ){
                            // save personal data in preferences
                            preferences.setUserLicenseNum_encrypted(licenseNum);
                            preferences.setUserNames_encrypted(names);
                            preferences.setUserSurnames_encrypted(surnames);
                            preferences.setUserAge_encrypted(age);
                            preferences.setUserRH_encrypted(rh);
                            if(response.contains("1")){
                                Toast.makeText(getApplicationContext(), R.string.dataSaved, Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(getApplicationContext(), R.string.dataUpdated, Toast.LENGTH_LONG).show();
                            }
                            Intent intent = new Intent(ActivityRegisterUser_PersonalInfo2.this, ActivityRegisterUser_PersonalInfo3.class);
                            startActivity(intent);
                        } else {
                            // most likely the user has mobile data on but without internet service. Worst case, something went wrong with the server
                            Static_AppMethods.ToastCheckYourInternet(ActivityRegisterUser_PersonalInfo2.this);
                        }
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                        saving = false;
                        pbInfo1.setVisibility(View.INVISIBLE);
//                        Log.d("RequestVolleyReg1", error.toString());
                        Static_AppMethods.ToastCheckYourInternet(ActivityRegisterUser_PersonalInfo2.this);
                        Static_AppMethods.checkResponseCode(error, preferences);
                    }
                })
        {
            @Override
            protected Map<String, String> getParams()
            {
//                Log.d("RequestVolleyReg1", "userId= " + preferences.get_lastUser_Id_logged());
                Map<String, String> params = new HashMap<>();
                params.put("userId", preferences.get_lastUser_Id_logged());
                params.put("licenseNumber", licenseNum);
                params.put("names", names);
                params.put("surnames", surnames);
                params.put("age", age);
                params.put("rh", rh);
                return params;
            }
        };
    }

}


