package com.taedison.helmy;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.CheckBox;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/***
 * User enters part of his/her personal information
 * Data is encrypted and sent to the server
 */
public class ActivityRegisterUser_PersonalInfo extends AppCompatActivity {

    final String TAG = "personalInfo";

    Spinner spinnerNationality;
    ArrayList<String> arrayCountries;
    String spinnerHintNationality;

    Spinner spinnerIDtype;
    ArrayList<String> arrayIDtypes;
    String spinnerHintIDtype;

    EditText etIdNumber;
    CheckBox checkLicense;
    ProgressBar pbInfo2;

    boolean isKeyboardShowing = false;

    // preferences
    SingletonSharedPreferences preferences;

    //Volley
    private RequestQueue requestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_user_personal_info);

        ImageView circle = findViewById(R.id.circle);
        Static_AppMethods.animateProgressCircle(circle);

        spinnerNationality = findViewById(R.id.spinnerNationality);
        spinnerIDtype = findViewById(R.id.spinnerIDtype);
        etIdNumber = findViewById(R.id.etIdNum);
        checkLicense = findViewById(R.id.checkLicense);

        pbInfo2 = findViewById(R.id.pbInfo);
        pbInfo2.setVisibility(View.INVISIBLE);

        // Get a list of all countries
        Locale[] locale = Locale.getAvailableLocales();
        arrayCountries = new ArrayList<>();
        String country;
        for( Locale loc : locale ){
            country = loc.getDisplayCountry();
            if( country.length() > 0 && !arrayCountries.contains(country) ){
                arrayCountries.add( country );
            }
        }
        Collections.sort(arrayCountries, String.CASE_INSENSITIVE_ORDER);
        arrayCountries.remove("Colombia");
        arrayCountries.add(0, "Colombia");

        //spinners
        spinnerHintNationality = getResources().getString(R.string.userNationality);
        arrayCountries.add(0, spinnerHintNationality);
        spinnerNationality.setAdapter(new ClassSpinnerAdapter(this,
                R.layout.textview_template_spinner, arrayCountries, spinnerNationality));

        spinnerHintIDtype = getResources().getString(R.string.userIDtype);
        arrayIDtypes = new ArrayList<>( Arrays.asList(spinnerHintIDtype,
                getResources().getString(R.string.userCedula),
                getResources().getString(R.string.userTI),
                getResources().getString(R.string.userCedulaForeigners),
                getResources().getString(R.string.userPassport),
                getResources().getString(R.string.otherIDtype) ));
        spinnerIDtype.setAdapter(new ClassSpinnerAdapter(this,
                R.layout.textview_template_spinner, arrayIDtypes, spinnerIDtype));

        //preferences
        preferences = SingletonSharedPreferences.getInstance(this.getApplicationContext());

        if( !TextUtils.isEmpty(preferences.getUserNatiolatity()) ){
            spinnerNationality.setSelection(arrayCountries.indexOf(preferences.getUserNatiolatity()));
            spinnerIDtype.setSelection(arrayIDtypes.indexOf(preferences.getUserIDType()));
            etIdNumber.setText(preferences.getUserIDnum());
            checkLicense.setChecked(preferences.getUser_isColLicense());
        }


        final LinearLayout LLtitle = findViewById(R.id.LLtitle2);
        final ConstraintLayout contentView = findViewById(R.id.CL_Info2);
        final ConstraintLayout CL_progress_logo = findViewById(R.id.CL_progress_logo);

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

        //volley
        requestQueue = SingletonVolley.getInstance(this.getApplicationContext()).getRequestQueue();
    }


    public void tvWhyThisInfo_click(View view) {
        Static_AppMethods.launchAlertReasonForAskingPersonalInfo(this);
    }

    boolean saving;
    public void savePersonalInfo2(View view) {
        Static_AppMethods.checkField(spinnerNationality);
        Static_AppMethods.checkField(spinnerIDtype);
        Static_AppMethods.checkField(etIdNumber);
        if( spinnerNationality.getSelectedItemPosition() == 0
                || spinnerIDtype.getSelectedItemPosition() == 0
                || TextUtils.isEmpty(etIdNumber.getText()) ){
            Toast.makeText(this, R.string.AllFieldsAreRequired, Toast.LENGTH_SHORT).show();
        } else if (spinnerNationality.getSelectedItem().equals(preferences.getUserNatiolatity())
                && spinnerIDtype.getSelectedItem().equals(preferences.getUserIDType())
                && etIdNumber.getText().toString().equals(preferences.getUserIDnum())) {
            Intent intent;
            if(checkLicense.isChecked() && TextUtils.isEmpty(preferences.getUserNames()) ){
                // user's license is colombian and user has not entered data in personalinfo2
                intent = new Intent(ActivityRegisterUser_PersonalInfo.this, ActivityRegisterUser_PersonalInfo2_OCR.class);
            } else {
                intent = new Intent(ActivityRegisterUser_PersonalInfo.this, ActivityRegisterUser_PersonalInfo2.class);
            }
            startActivity(intent);
        } else {
            if(!saving){
                try {
                    String email = preferences.get_lastUser_email_logged();
                    String natiolatityEncrypted = Static_AppMethods.encryptAES_toString64(this,
                            spinnerNationality.getSelectedItem().toString(), email, preferences);
                    String idTypeEncrypted = Static_AppMethods.encryptAES_toString64(this,
                            spinnerIDtype.getSelectedItem().toString(), email, preferences);
                    String idNumberEncrypted = Static_AppMethods.encryptAES_toString64(this,
                            etIdNumber.getText().toString(), email, preferences);
                    String isColLicenseEncrypted;
                    if(checkLicense.isChecked()){
                        isColLicenseEncrypted = Static_AppMethods.encryptAES_toString64(this,
                                "1", email, preferences);
                    } else {
                        isColLicenseEncrypted = Static_AppMethods.encryptAES_toString64(this,
                                "0", email, preferences);
                    }

                    Log.d("RequestVolleyReg1", "userId= " + preferences.get_lastUser_Id_logged()
                            + "\nNationality: " + natiolatityEncrypted + "\nidType: " + idTypeEncrypted + "\nidNum: " + idNumberEncrypted
                            + "\nidNum: " + isColLicenseEncrypted);

                    saving = true; //prevents fromActivity entering again

                    StringRequest joRequest = request(natiolatityEncrypted, idTypeEncrypted, idNumberEncrypted, isColLicenseEncrypted);
                    requestQueue.add(joRequest);

                    pbInfo2.setVisibility(View.VISIBLE);
                } catch (Exception e) {
                    Log.e(TAG+"Encrypt", "Encryption Error= " + e.getMessage());
                    Static_AppMethods.ToastEncryptionError(this);
                }
            }
        }
    }

    public void goBack(View view) {
        onBackPressed();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
//        moveTaskToBack(true);
    }

    private StringRequest request(final String nationality, final String idType, final String idNum, final String isColLicense){
        String url;
        if(preferences.get_isPrimaryServerDown()){
            // primary server is down, so we will use the secondary
            url = Static_AppVariables.url_personOne2;
        } else {
            url = Static_AppVariables.url_personOne;
        }

        return new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response)
                    {
                        saving = false;
                        pbInfo2.setVisibility(View.INVISIBLE);
                        Log.d("RequestVolleyReg1", "response= " + response);

                        if( !TextUtils.isEmpty(response) ){
                            // save personal data
                            preferences.setUserNationality_encrypted(nationality);
                            preferences.setUserIDType_encrypted(idType);
                            preferences.setUserIDnum_encrypted(idNum);
                            preferences.setUser_isColLicense_encrypted(isColLicense);

                            if(response.contains("1")){
                                Toast.makeText(getApplicationContext(), R.string.dataSaved, Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(getApplicationContext(), R.string.dataUpdated, Toast.LENGTH_LONG).show();
                            }

                            Intent intent;
//                        if( preferences.didUserRegister_data_devices() ){
//                            // User is editing personal data coming from ActivityGoAs
//                            intent = new Intent(ActivityRegisterUser_PersonalInfo.this, ActivityRegisterUser_PersonalInfo2.class);
//                        } else
                            if(checkLicense.isChecked() && TextUtils.isEmpty(preferences.getUserNames()) ){
                                // user's license is colombian and user has not entered data in personalinfo2
                                intent = new Intent(ActivityRegisterUser_PersonalInfo.this, ActivityRegisterUser_PersonalInfo2_OCR.class);
                            } else {
                                intent = new Intent(ActivityRegisterUser_PersonalInfo.this, ActivityRegisterUser_PersonalInfo2.class);
                            }
                            startActivity(intent);
                        } else {
                            // most likely the user has mobile data on but without internet service. Worst case, something went wrong with the server
                            Static_AppMethods.ToastCheckYourInternet(ActivityRegisterUser_PersonalInfo.this);
                        }
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                        saving = false;
                        pbInfo2.setVisibility(View.INVISIBLE);
                        Log.d("RequestVolleyReg1", error.toString());
                        Static_AppMethods.ToastCheckYourInternet(ActivityRegisterUser_PersonalInfo.this);
                        Static_AppMethods.checkResponseCode(error, preferences);
                    }
                })
        {
            @Override
            protected Map<String, String> getParams()
            {
                // data already encrypted
                Log.d("RequestVolleyReg1", "userId= " + preferences.get_lastUser_Id_logged() );
                Map<String, String> params = new HashMap<>();
                params.put("userId", preferences.get_lastUser_Id_logged());
                params.put("nationality", nationality);
                params.put("documentType", idType);
                params.put("documentNumber", idNum);
                params.put("colombianLicense", isColLicense);
                return params;
            }
        };
    }

}
