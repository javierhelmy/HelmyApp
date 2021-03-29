package com.taedison.helmy;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.content.Intent;
import android.graphics.Rect;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.taedison.helmy.Static_AppMethods.checkField;

/***
 * First information about the motorcycle and SOAT
 */
public class ActivityRegisterBike extends AppCompatActivity {
    final String TAG = "registerBike";

    EditText etBikeNickname, etBikeChasis, etBikeSoat, et2ndPolicy, et2ndPolicyPhone;
    CheckBox check2ndPolicy;
    ConstraintLayout pbBike;
    ImageButton imgBtnDelete;
    TextView tvBtnManual;

    Spinner spinnerBikeBrand;
    ArrayList<String> arrayBikeBrands;
    String spinnerHintBrand;

    String placaFromOCR;

    boolean isKeyboardShowing = false;

    private String edit_MAC;

    private SingletonSharedPreferences preferences;

    //Volley
    private RequestQueue requestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_bike);

        etBikeNickname = findViewById(R.id.etNicknameBike);
        etBikeChasis = findViewById(R.id.etChasisBike);
        spinnerBikeBrand = findViewById(R.id.spinnerBikeBrand);
        etBikeSoat = findViewById(R.id.etSOATbike);
        et2ndPolicy = findViewById(R.id.et2ndPolicy); // starts with visibility GONE
        et2ndPolicyPhone = findViewById(R.id.et2ndPolicyPhone); // starts with visibility GONE
        check2ndPolicy = findViewById(R.id.check2ndPolicy);

        imgBtnDelete = findViewById(R.id.imgBtnDeleteBike);
        tvBtnManual = findViewById(R.id.btnManual);

        pbBike = findViewById(R.id.pbRegBike);
        pbBike.setVisibility(View.INVISIBLE);

        ImageView circle = findViewById(R.id.circle);
        Static_AppMethods.animateProgressCircle(circle);

        //spinner
        spinnerHintBrand = getResources().getString(R.string.bikeBrand);
        arrayBikeBrands = new ArrayList<>(Arrays.asList(spinnerHintBrand, "HONDA", "AKT", "HERO",
                "BAJAJ", "YAMAHA", "SUZUKI", "TVS", "PULSAR", getResources().getString(R.string.otherBrand)));
        spinnerBikeBrand.setAdapter(new ClassSpinnerAdapter(this,
                R.layout.textview_template_spinner, arrayBikeBrands, spinnerBikeBrand, false));

        preferences = SingletonSharedPreferences.getInstance(this.getApplicationContext());

        final LinearLayout LLtitle = findViewById(R.id.LLtitle_bike);
        final ConstraintLayout contentView = findViewById(R.id.CL_bike);
        final ConstraintLayout CL_progress_logo = findViewById(R.id.CL_progress_logo_b1);

        contentView.getViewTreeObserver().addOnGlobalLayoutListener(
                // this is used to hide/show views when the keyboard is hidden/shown. AdjustPan does not work in activities with no action bar
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {

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
                                tvBtnManual.setVisibility(View.GONE);
                            }
                        } else {
                            // keyboard is closed
                            if (isKeyboardShowing) {
                                isKeyboardShowing = false;
                                CL_progress_logo.setVisibility(View.VISIBLE);
                                LLtitle.setVisibility(View.VISIBLE);
                                tvBtnManual.setVisibility(View.VISIBLE);
                            }
                        }
                    }
                });

        check2ndPolicy.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean value) {
                // hide/show the private policy
                if (value) {
                    et2ndPolicy.setVisibility(View.VISIBLE);
                    et2ndPolicyPhone.setVisibility(View.VISIBLE);
                } else {
                    et2ndPolicy.setVisibility(View.GONE);
                    et2ndPolicyPhone.setVisibility(View.GONE);
                }
            }
        });

        // if data was extracted using the camera
        Intent i = getIntent();
        if (!TextUtils.isEmpty(i.getStringExtra("soat"))) {
            etBikeSoat.setText(i.getStringExtra("soat"));
            etBikeChasis.setText(i.getStringExtra("chasis"));
            String brand = i.getStringExtra("marca");
            int sel = arrayBikeBrands.indexOf(brand);
            if (sel > 0) {
                spinnerBikeBrand.setSelection(sel);
            } else {
                ((ClassSpinnerAdapter) spinnerBikeBrand.getAdapter()).addOther(brand);
            }
            placaFromOCR = i.getStringExtra("placa");
        }

        edit_MAC = i.getStringExtra("edit_MAC_bike");
//        Log.d(TAG, "editMAC= " + edit_MAC);
        if (TextUtils.isEmpty(edit_MAC)) {
            imgBtnDelete.setVisibility(View.GONE); // hide delete button
        } else {
            // user is editing a HelmyM
            if(!TextUtils.isEmpty(preferences.getBikeNickname(edit_MAC))){
                try{
                    etBikeNickname.setText( preferences.getBikeNickname(edit_MAC) );
                    etBikeSoat.setText( preferences.getBikeSOAT(edit_MAC) );
                    if( !TextUtils.isEmpty(preferences.getBike2ndPolicy(edit_MAC)) ){
                        check2ndPolicy.setChecked(true);
                        et2ndPolicy.setText( preferences.getBike2ndPolicy(edit_MAC) );
                        et2ndPolicyPhone.setText( preferences.getBike2ndPolicyPhone(edit_MAC) );
                    }
                    int sel = arrayBikeBrands.indexOf( preferences.getBikeBrand(edit_MAC) );
//                    Log.d(TAG, "brand= " + preferences.getBikeBrand(edit_MAC));
                    if (sel > 0) {
                        spinnerBikeBrand.setSelection(sel);
                    } else {
                        if( !TextUtils.isEmpty(preferences.getBikeBrand(edit_MAC)) ) {
                            ((ClassSpinnerAdapter) spinnerBikeBrand.getAdapter()).addOther(preferences.getBikeBrand(edit_MAC));
                        }
                    }
                    etBikeChasis.setText( preferences.getBikeChasis(edit_MAC) );
                } catch (Exception ignored){}

            }
        }

        //volley
        requestQueue = SingletonVolley.getInstance(this.getApplicationContext()).getRequestQueue();
    }

    public void goBack(View view) {
        onBackPressed();
    }

    public void clickNext_Bike(View view) {
        // highlight fields if errors
        checkField(etBikeNickname);
        checkField(etBikeChasis);
        checkField(etBikeSoat);
        checkField(spinnerBikeBrand);
        if( check2ndPolicy.isChecked() ){
            checkField(et2ndPolicy);
            checkField(et2ndPolicyPhone);
        }

        ArrayList<String> bikesNicknames = SingletonSharedPreferences.getInstance(this).get_bikes_saved_nicknames();
        if( bikesNicknames.indexOf( etBikeNickname.getText().toString() ) >= 0 && TextUtils.isEmpty(edit_MAC) ){
            Toast.makeText(this, R.string.nicknameAlreadyExists, Toast.LENGTH_SHORT).show();
        } else if ( TextUtils.isEmpty(etBikeNickname.getText()) || TextUtils.isEmpty(etBikeChasis.getText())
                    || TextUtils.isEmpty(etBikeSoat.getText()) || spinnerBikeBrand.getSelectedItemPosition() == 0
                    || (check2ndPolicy.isChecked() && (
                            TextUtils.isEmpty(et2ndPolicy.getText()) || TextUtils.isEmpty(et2ndPolicyPhone.getText()) ))){
            Toast.makeText(this, R.string.AllFieldsAreRequired, Toast.LENGTH_SHORT).show();
        } else {
            // all fields entered correctly, pass them to the next activity so that they are sent to the server
            Intent i = new Intent(this, ActivityRegisterBike2.class);
            i.putExtra("nickname", etBikeNickname.getText().toString() );
            if(!TextUtils.isEmpty(edit_MAC)){
                i.putExtra("edit_MAC_bike", edit_MAC);
            }
            i.putExtra("soat", etBikeSoat.getText().toString() );
            if(check2ndPolicy.isChecked()){
                i.putExtra("2nd_policy", et2ndPolicy.getText().toString());
                i.putExtra("2nd_policy_phone", et2ndPolicyPhone.getText().toString());
            }
            i.putExtra("brand", spinnerBikeBrand.getSelectedItem().toString());
            i.putExtra("chasis", etBikeChasis.getText().toString());
            if( placaFromOCR != null ){
                i.putExtra("placa", placaFromOCR);
            }
            startActivity(i);
        }
    }

    public void click_deleteBike(View view) {
        if(!TextUtils.isEmpty(edit_MAC)){
            lauchAlertDeleteBike();
        }
    }

    private void lauchAlertDeleteBike(){
        final AlertMessageButton alert = new AlertMessageButton(this);
        alert.setDialogMessage(getResources().getString(R.string.areYouSureToDelete));
        alert.setDialogPositiveButton(getResources().getString(R.string.Yes), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pbBike.setVisibility(View.VISIBLE);
                if( !TextUtils.isEmpty( preferences.getBikeId(edit_MAC)) ){
                    deleteBikeFromBlockchain();
                }

                alert.dismissAlert();
            }
        });
        alert.setDialogNegativeButton(getResources().getString(R.string.No), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alert.dismissAlert();
            }
        });
        alert.showAlert();
    }

    private void requestDeleteBike(){
        String url;
        if(preferences.get_isPrimaryServerDown()){
            // primary server is down, so we will use the secondary
            url = Static_AppVariables.url_motorcycle2;
        } else {
            url = Static_AppVariables.url_motorcycle;
        }

        StringRequest request = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response)
                    {
                        // end of the process
                        pbBike.setVisibility(View.INVISIBLE);
                        if( !TextUtils.isEmpty(response) ){
                            preferences.deleteBikeFromPreferences(edit_MAC);
                            Intent intent;
                            if( preferences.didUserRegister_data_devices() ){
                                // go to ChooseDevice
                                intent = new Intent(ActivityRegisterBike.this, ActivityChooseDevices.class);
                            } else {
                                // go to Progress
                                intent = new Intent(ActivityRegisterBike.this, ActivityProgress.class);
                            }
                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK|Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        } else {
                            // most likely the user has mobile data on but without internet service. Worst case, something went wrong with the server
                            Static_AppMethods.ToastCheckYourInternet(ActivityRegisterBike.this);
                        }
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
                        pbBike.setVisibility(View.INVISIBLE);
//                        Log.e(TAG+"volley", error.toString());
                        Static_AppMethods.ToastCheckYourInternet(ActivityRegisterBike.this);
                        Static_AppMethods.checkResponseCode(error, preferences);
                    }
                })
        {
            @Override
            protected Map<String, String> getParams()
            {
//                Log.e(TAG+"volley", "userId= " + preferences.get_lastUser_Id_logged());
                Map<String, String> params = new HashMap<>();
                params.put("userId", preferences.get_lastUser_Id_logged());
                params.put("mac", edit_MAC);
                return params;
            }
        };

        requestQueue.add(request);
    }

    final String TAG_blockchain = TAG + "Blockchain";
    public void deleteBikeFromBlockchain() {
        String url;
        if(preferences.get_isPrimaryServerDown()){
            // primary server is down, so we will use the secondary
            url = Static_AppVariables.url_blockchainDelete_fromBikeID2;
        } else {
            url = Static_AppVariables.url_blockchainDelete_fromBikeID;
        }

        StringRequest request = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response) {
//                        Log.d(TAG_blockchain, "Deletion response: " + response);
                        if( !TextUtils.isEmpty(response) ){
                            try {
                                JSONObject jsonArray = new JSONObject(response);
                                String status = jsonArray.getString("status");
                                if(status.equals("1")){
                                    // bike is being deleted in blockchain, now delete from server
                                    final AlertMessageButton alert = new AlertMessageButton(ActivityRegisterBike.this);
                                    alert.setDialogMessage(getResources().getString(R.string.deletingFromBlockchain));
                                    alert.setDialogPositiveButton(getResources().getString(R.string.Ok),
                                            new View.OnClickListener() {
                                                @Override
                                                public void onClick(View view) {
                                                    requestDeleteBike();
                                                    alert.dismissAlert();
                                                }
                                            });
                                    alert.setCancellable(false);
                                    alert.hideCancelButton();
                                    alert.showAlert();

                                } else if(status.equals("2")) {
                                    Toast.makeText(ActivityRegisterBike.this, getResources().
                                            getString(R.string.bikeNotRegisteredInBlockchain), Toast.LENGTH_SHORT).show();
                                    pbBike.setVisibility(View.GONE);
                                    requestDeleteBike();
                                } else {
                                    Toast.makeText(ActivityRegisterBike.this, getResources().
                                            getString(R.string.errorTryAgain), Toast.LENGTH_SHORT).show();
                                    pbBike.setVisibility(View.GONE);
                                }
                            } catch (Exception ignored){
                                Static_AppMethods.ToastTryAgain(ActivityRegisterBike.this);
                            }
                        } else {
                            // most likely the user has mobile data on but without internet service. Worst case, something went wrong with the server
                            Static_AppMethods.ToastCheckYourInternet(ActivityRegisterBike.this);
                            pbBike.setVisibility(View.GONE);
                        }
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error)
                    {
//                        Log.e(TAG_blockchain, error.toString());
                        Static_AppMethods.ToastCheckYourInternet(ActivityRegisterBike.this);
                        pbBike.setVisibility(View.GONE);
                        Static_AppMethods.checkResponseCode(error, preferences);
                    }
                })
        {
            @Override
            protected Map<String, String> getParams()
            {
                Map<String, String> params = new HashMap<>();
                params.put("bikeId", preferences.getBikeId(edit_MAC));
//                Log.e(TAG_blockchain, "params delete" + params.toString());
                return params;
            }
        };

        requestQueue.add(request);
    }

    public void click_btnLaunchManual(View view) {
        Toast.makeText(this, "En desarrollo", Toast.LENGTH_SHORT).show();
    }

    public void click_whatIsAlias(View view) {
        Static_AppMethods.launchAlertMessage(getResources().getString(R.string.whatIsAliasM), this);
    }

    public void click_whatIsChassis(View view) {
        Static_AppMethods.launchAlertMessage(getResources().getString(R.string.whatIsChassis), this);
    }
}
