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
import java.util.HashMap;
import java.util.Map;

/***
 * User enters part of his/her personal information
 * Data is encrypted and sent to the server
 */
public class ActivityRegisterUser_PersonalInfo3 extends AppCompatActivity {

    private final String TAG = "personalInfo3";

    EditText etPhone;
    ProgressBar pbInfo3;

    Spinner spinnerSex, spinnerEPS, spinnerARL;
    ArrayList<String> arraySex, arrayEPSs, arrayARLs;
    String spinnerHintSex, spinnerHintEPS, spinnerHintARL;

    // preferences
    SingletonSharedPreferences preferences;

    //Volley
    private RequestQueue requestQueue;

    ConstraintLayout CL_progress_logo;
    boolean isKeyboardShowing = false;
    boolean saving = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_user_personal_info3);

        ImageView circle = findViewById(R.id.circle);
        Static_AppMethods.animateProgressCircle(circle);

        etPhone = findViewById(R.id.etPhoneReg);
        pbInfo3 = findViewById(R.id.pbInfo3);
        pbInfo3.setVisibility(View.INVISIBLE);
        spinnerSex = findViewById(R.id.spinnerSex);
        spinnerEPS = findViewById(R.id.spinnerEPS);
        spinnerARL = findViewById(R.id.spinnerARL);

        //preferences
        preferences = SingletonSharedPreferences.getInstance(this.getApplicationContext());

        //spinners
        spinnerHintSex = getResources().getString(R.string.userSex);
        arraySex = new ArrayList<>( Arrays.asList(spinnerHintSex, getResources().getString(R.string.female),
                getResources().getString(R.string.male), getResources().getString(R.string.ratherNotSaySex)) );
        spinnerSex.setAdapter(new ClassSpinnerAdapter(this, R.layout.textview_template_spinner, arraySex, spinnerSex));

        spinnerHintEPS = getResources().getString(R.string.UserEPS);
        arrayEPSs = new ArrayList<>( Arrays.asList(spinnerHintEPS, getResources().getString(R.string.none), "Nueva EPS", "EPS Sura",
                "Salud Total", "Sanitas", "Medimás", "Coosalud", "Mutual Ser", "Emsanar",
                "Asmet Salud", getResources().getString(R.string.otherBrand)) );
        if( !TextUtils.isEmpty(preferences.getUserSex()) && arrayEPSs.indexOf(preferences.getUserEPS()) == -1 ){
            // spinner does not contain the EPS, perhaps it was added as new entry "other"
            arrayEPSs.add(arrayEPSs.size()-1, preferences.getUserEPS() );
        }
        spinnerEPS.setAdapter(new ClassSpinnerAdapter(this, R.layout.textview_template_spinner, arrayEPSs, spinnerEPS));

        spinnerHintARL = getResources().getString(R.string.UserARL);
        arrayARLs = new ArrayList<>( Arrays.asList(spinnerHintARL, getResources().getString(R.string.none), "Sura", "Positiva", "Axa colpatria",
                "Colmena", "Seguros Bolivar", "Liberty Seguros", "Equidad Seguros", "Seguros ALFA",
                "Mapfre", "Vida Aurora",  getResources().getString(R.string.otherBrand)) );
        if( !TextUtils.isEmpty(preferences.getUserSex()) && arrayARLs.indexOf(preferences.getUserARL()) == -1 ){
            // spinner does not contain the EPS, perhaps it was added as new entry "other"
            arrayARLs.add(arrayARLs.size()-1, preferences.getUserARL() );
        }
        spinnerARL.setAdapter(new ClassSpinnerAdapter(this, R.layout.textview_template_spinner, arrayARLs, spinnerARL));

        if( !TextUtils.isEmpty(preferences.getUserSex()) ) {
            try{
                spinnerSex.setSelection(Integer.parseInt(preferences.getUserSex()) + 1); // "0": female, "1": male, "2": rather not say
                spinnerEPS.setSelection(arrayEPSs.indexOf(preferences.getUserEPS()));
                spinnerARL.setSelection(arrayARLs.indexOf(preferences.getUserARL()));
                etPhone.setText(preferences.getUserPhone());
            } catch (Exception ignored){
                //error possibly parsing to integer
            }
        }

        final LinearLayout LLtitle = findViewById(R.id.LLtitle_3);
        final ConstraintLayout contentView = findViewById(R.id.CL_Info3);
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

        //volley
        requestQueue = SingletonVolley.getInstance(this.getApplicationContext()).getRequestQueue();
    }

    public void savePersonalInfo3(View view) throws Exception {
        // throws exception something fails, for example, parsing to integer
        Static_AppMethods.checkField(etPhone);
        Static_AppMethods.checkField(spinnerEPS);
        Static_AppMethods.checkField(spinnerARL);
        Static_AppMethods.checkField(spinnerSex);
        if(TextUtils.isEmpty(etPhone.getText())
                || spinnerEPS.getSelectedItemPosition() == 0
                || spinnerARL.getSelectedItemPosition() == 0
                || spinnerSex.getSelectedItemPosition() == 0){
            Toast.makeText(this, R.string.AllFieldsAreRequired, Toast.LENGTH_SHORT).show();
        } else if(etPhone.getText().toString().equals(preferences.getUserPhone())
                && spinnerEPS.getSelectedItem().equals(preferences.getUserEPS())
                && spinnerARL.getSelectedItem().equals(preferences.getUserARL())
                && spinnerSex.getSelectedItemPosition()-1 == Integer.parseInt(preferences.getUserSex()) ){
            Intent intent;
            if( preferences.didUserRegister_data_devices() ){
                //User was editing personal data, go back to ActivityGoAs
                intent = new Intent(ActivityRegisterUser_PersonalInfo3.this, ActivityGoAs.class);
            } else {
                intent = new Intent(ActivityRegisterUser_PersonalInfo3.this, ActivityProgress.class);
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        } else {
            if( !saving ){
                try {
                    String email = preferences.get_lastUser_email_logged();
                    String sexEncrypted;
                    if(spinnerSex.getSelectedItem().equals(getResources().getString(R.string.female))){
                        sexEncrypted = Static_AppMethods.encryptAES_toString64(this,
                                "0", email, preferences);
                    } else if (spinnerSex.getSelectedItem().equals(getResources().getString(R.string.male))){
                        sexEncrypted = Static_AppMethods.encryptAES_toString64(this,
                                "1", email, preferences);
                    } else {
                        sexEncrypted = Static_AppMethods.encryptAES_toString64(this,
                                "2", email, preferences);
                    }
                    String epsEncrypted = Static_AppMethods.encryptAES_toString64(this,
                            spinnerEPS.getSelectedItem().toString(), email, preferences);
                    String arlEncrypted = Static_AppMethods.encryptAES_toString64(this,
                            spinnerARL.getSelectedItem().toString(), email, preferences);
                    String phoneEncrypted = Static_AppMethods.encryptAES_toString64(this,
                            etPhone.getText().toString(), email, preferences);


                    saving = true; //prevents fromActivity entering again

                    StringRequest joRequest = request(sexEncrypted, epsEncrypted, arlEncrypted, phoneEncrypted);

                    requestQueue.add(joRequest);

                    pbInfo3.setVisibility(View.VISIBLE);
                } catch (Exception e) {
                    Log.e(TAG+"Encrypt", "Encryption Error= " + e.getMessage());
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

    private StringRequest request(final String sex, final String eps, final String arl,
                                  final String phone){
        String url;
        if(preferences.get_isPrimaryServerDown()){
            // primary server is down, so we will use the secondary
            url = Static_AppVariables.url_personThree2;
        } else {
            url = Static_AppVariables.url_personThree;
        }

        return new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response)
                    {
                        saving = false;
                        pbInfo3.setVisibility(View.INVISIBLE);
                        if( !TextUtils.isEmpty(response) ){
                            // save personal data
                            preferences.setUserSex_encrypted(sex);
                            preferences.setUserEPS_encrypted(eps);
                            preferences.setUserARL_encrypted(arl);
                            preferences.setUserPhone_encrypted(phone);

                            Intent intent;
                            if( preferences.didUserRegister_data_devices() ){
                                //User was editing personal data, go back to ActivityGoAs
                                intent = new Intent(ActivityRegisterUser_PersonalInfo3.this, ActivityGoAs.class);
                            } else {
                                intent = new Intent(ActivityRegisterUser_PersonalInfo3.this, ActivityProgress.class);
                            }

                            Log.d("RequestVolleyReg3", "response= " + response);
                            if(response.contains("1")){
                                Toast.makeText(getApplicationContext(), R.string.dataSaved, Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(getApplicationContext(), R.string.dataUpdated, Toast.LENGTH_LONG).show();
                            }

                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                        } else {
                            // most likely the user has mobile data on but without internet service. Worst case, something went wrong with the server
                            Static_AppMethods.ToastCheckYourInternet(ActivityRegisterUser_PersonalInfo3.this);
                        }
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                        saving = false;
                        pbInfo3.setVisibility(View.INVISIBLE);
                        Log.d("RequestVolleyReg1", error.toString());
                        Static_AppMethods.ToastCheckYourInternet(ActivityRegisterUser_PersonalInfo3.this);
                        Static_AppMethods.checkResponseCode(error, preferences);
                    }
                })
        {
            @Override
            protected Map<String, String> getParams()
            {
                Log.d("RequestVolleyReg1", "userId= " + preferences.get_lastUser_Id_logged());
                Map<String, String> params = new HashMap<>();
                params.put("userId", preferences.get_lastUser_Id_logged());
                params.put("sex", sex);
                params.put("eps", eps);
                params.put("arl", arl);
                params.put("phone", phone);
                return params;
            }
        };
    }
}