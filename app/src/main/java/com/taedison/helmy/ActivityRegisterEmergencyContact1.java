package com.taedison.helmy;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;

import java.util.HashMap;
import java.util.Map;

/***
 * User enters the first emergency contact, it is mandatory.
 * Data is sent to the server with encryption
 */
public class ActivityRegisterEmergencyContact1 extends AppCompatActivity {

    final String TAG = "volleyContact1";

    EditText etEmergencyName, etEmergencySurname;
    TextView tvEmergencyPhone, tvContact1;
    ProgressBar pbInfoEmer;

    // preferences
    SingletonSharedPreferences preferences;

    ConstraintLayout logo;
    boolean isKeyboardShowing = false;

    //Volley
    private RequestQueue requestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_emergency_contact1);

        etEmergencyName = findViewById(R.id.etEmergencyName);
        etEmergencySurname = findViewById(R.id.etEmergencySurnames);
        tvEmergencyPhone = findViewById(R.id.etEmergencyPhone);
        tvContact1 = findViewById(R.id.tvContact1);
        pbInfoEmer = findViewById(R.id.pbInfoEmer);
        pbInfoEmer.setVisibility(View.INVISIBLE);

        //preferences
        preferences = SingletonSharedPreferences.getInstance(this.getApplicationContext());

        if( !TextUtils.isEmpty(preferences.getUserEmergencyPhone2()) ){
            tvContact1.setText(getResources().getString(R.string.editSecondContact));
        }

        if( !TextUtils.isEmpty(preferences.getUserEmergencyPhone()) ) {
            etEmergencyName.setText(preferences.getUserEmergencyNames());
            etEmergencySurname.setText(preferences.getUserEmergencySurnames());
            tvEmergencyPhone.setText(preferences.getUserEmergencyPhone());
        }

        final LinearLayout LLtitle = findViewById(R.id.LLtitle_contact1);
        final ConstraintLayout contentView = findViewById(R.id.CL_contact1);
        logo = findViewById(R.id.CL_logo_contact1);

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
                                logo.setVisibility(View.GONE);
                                LLtitle.setVisibility(View.GONE);
                            }
                        }
                        else {
                            // keyboard is closed
                            if (isKeyboardShowing) {
                                isKeyboardShowing = false;
                                logo.setVisibility(View.VISIBLE);
                                LLtitle.setVisibility(View.VISIBLE);
                            }
                        }
                    }
                });

        //volley
        requestQueue = SingletonVolley.getInstance(this.getApplicationContext()).getRequestQueue();
    }

    boolean saving;
    boolean addSecondContact = false; // indicates if user will add a second contact or not. It will redirect the next activity
    public void click_saveContact1(View view) {
        Static_AppMethods.checkField(etEmergencyName);
        Static_AppMethods.checkField(etEmergencySurname);
        Static_AppMethods.checkField(tvEmergencyPhone);
        if( TextUtils.isEmpty(etEmergencyName.getText())
                || TextUtils.isEmpty(etEmergencySurname.getText())
                || TextUtils.isEmpty(tvEmergencyPhone.getText())){
            Toast.makeText(this, R.string.AllFieldsAreRequired, Toast.LENGTH_SHORT).show();
        } else if( etEmergencyName.getText().toString().equals(preferences.getUserEmergencyNames()) &&
                etEmergencySurname.getText().toString().equals(preferences.getUserEmergencySurnames()) &&
                tvEmergencyPhone.getText().toString().equals(preferences.getUserEmergencyPhone()) ){
            Intent intent;
            if( preferences.didUserRegister_data_devices() ){
                // User was editting data, then go back to ActivityGoAs
                intent = new Intent(ActivityRegisterEmergencyContact1.this, ActivityGoAs.class);
            } else {
                intent = new Intent(ActivityRegisterEmergencyContact1.this, ActivityProgress.class);
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        } else {
            if(!saving){
                send2server();

                saving = true; //prevents fromActivity entering again
                addSecondContact = false;
            }
        }
    }

    public void click_addSecondContact(View view) {
        if( TextUtils.isEmpty(etEmergencyName.getText())
                || TextUtils.isEmpty(etEmergencySurname.getText())
                || TextUtils.isEmpty(tvEmergencyPhone.getText())){
            Toast.makeText(this, R.string.AllFieldsAreRequired, Toast.LENGTH_SHORT).show();

            Static_AppMethods.checkField(etEmergencyName);
            Static_AppMethods.checkField(etEmergencySurname);
            Static_AppMethods.checkField(tvEmergencyPhone);
        } else if( etEmergencyName.getText().toString().equals(preferences.getUserEmergencyNames()) &&
                etEmergencySurname.getText().toString().equals(preferences.getUserEmergencySurnames()) &&
                tvEmergencyPhone.getText().toString().equals(preferences.getUserEmergencyPhone()) ){
            Intent intent = new Intent(ActivityRegisterEmergencyContact1.this, ActivityRegisterEmergencyContact2.class);
//            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        } else {
            if( !saving ){
                send2server();

                saving = true; //prevents fromActivity entering again
                addSecondContact = true;
            }
        }
    }

    public void tvWhyThisInfo_click(View view) {
        Static_AppMethods.launchAlertReasonForAskingPersonalInfo(this);
    }

    private final int contactRequestCode = 234;
    public void launchContactsApp(View view) {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            // not null means that there is an activity to handle this intent
            startActivityForResult(intent, contactRequestCode);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == contactRequestCode && resultCode == RESULT_OK) {
            // Get the URI and query the content provider for the phone number
            Uri contactUri = data.getData();
            Cursor cursor;
            if (contactUri != null) {
                cursor = getContentResolver().query(contactUri, null,
                        null, null, null);

                // If the cursor returned is valid, get the phone number
                if (cursor != null && cursor.moveToFirst()) {
                    int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                    String number = cursor.getString(numberIndex);

                    String hasPhone = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER));
                    if(hasPhone.equals("1")){
                        if(number.charAt(0) == "+".charAt(0) || number.charAt(0) == "3".charAt(0)) {
                            tvEmergencyPhone.setText(number);
                        } else {
                            Toast.makeText(this, getResources().getString(R.string.landlinePhoneError),
                                    Toast.LENGTH_SHORT).show();
                            tvEmergencyPhone.setText("");
                        }
                    } else {
                        Toast.makeText(this, getResources().getString(R.string.errorTryAgain),
                                Toast.LENGTH_SHORT).show();
                    }
                }
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
    }

    public void goBack(View view) {
        onBackPressed();
    }

    private void send2server(){
        try {
            String email = preferences.get_lastUser_email_logged();
            String nameEncrypted = Static_AppMethods.encryptAES_toString64(this,
                    etEmergencyName.getText().toString(), email, preferences);
            String surnamesEncrypted = Static_AppMethods.encryptAES_toString64(this,
                    etEmergencySurname.getText().toString(), email, preferences);
            String phoneEncrypted = Static_AppMethods.encryptAES_toString64(this,
                    tvEmergencyPhone.getText().toString(), email, preferences);

            StringRequest joRequest = request(nameEncrypted, surnamesEncrypted, phoneEncrypted);
            requestQueue.add(joRequest);

            pbInfoEmer.setVisibility(View.VISIBLE);
        } catch (Exception e) {
//            Log.e(TAG+"Encrypt", "Encryption Error= " + e.getMessage());
            Static_AppMethods.ToastEncryptionError(this);
        }
    }

    private StringRequest request(final String names, final String surnames, final String phone){
        String url;
        if(preferences.get_isPrimaryServerDown()){
            // primary server is down, so we will use the secondary
            url = Static_AppVariables.url_personFour2;
        } else {
            url = Static_AppVariables.url_personFour;
        }

        return new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response)
                    {
//                        Log.d(TAG, response);
                        saving = false;
                        pbInfoEmer.setVisibility(View.INVISIBLE);

                        if( !TextUtils.isEmpty(response) ){
                            // save personal data
                            preferences.setUserEmergencyNames_encrypted(names);
                            preferences.setUserEmergencySurnames_encrypted(surnames);
                            preferences.setUserEmergencyPhone_encrypted(phone);

                            if(response.contains("1")){
                                Toast.makeText(getApplicationContext(), R.string.dataSaved, Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(getApplicationContext(), R.string.dataUpdated, Toast.LENGTH_LONG).show();
                            }

                            Intent intent;
                            if(addSecondContact){
                                intent = new Intent(ActivityRegisterEmergencyContact1.this, ActivityRegisterEmergencyContact2.class);
                            } else {
                                if( preferences.didUserRegister_data_devices() ){
                                    // User was editting data, then go back to ActivityGoAs
                                    intent = new Intent(ActivityRegisterEmergencyContact1.this, ActivityGoAs.class);
                                } else {
                                    intent = new Intent(ActivityRegisterEmergencyContact1.this, ActivityProgress.class);
                                }
                            }
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                        } else {
                            // most likely the user has mobile data on but without internet service. Worst case, something went wrong with the server
                            Static_AppMethods.ToastCheckYourInternet(ActivityRegisterEmergencyContact1.this);
                        }
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                        saving = false;
                        pbInfoEmer.setVisibility(View.INVISIBLE);
//                        Log.d(TAG, error.toString());
                        Static_AppMethods.ToastCheckYourInternet(ActivityRegisterEmergencyContact1.this);
                        Static_AppMethods.checkResponseCode(error, preferences);
                    }
                })
        {
            @Override
            protected Map<String, String> getParams()
            {
//                Log.d(TAG, "userId= " + preferences.get_lastUser_Id_logged());
                Map<String, String> params = new HashMap<>();
                params.put("userId", preferences.get_lastUser_Id_logged());
                params.put("firstEmergencyContactNames", names);
                params.put("surnamesFirstEmergencyContact", surnames);
                params.put("firstEmergencyContactNumber", phone);
                return params;
            }
        };
    }
}
