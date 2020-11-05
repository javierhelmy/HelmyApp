package com.taedison.helmy;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;

public class ActivityRegisterUser_PersonalInfo2_OCR extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_user_personal_info2_ocr);

        ImageView circle = findViewById(R.id.circle);
        Static_AppMethods.animateProgressCircle(circle);
    }

    public void tvWhyThisInfo_click(View view) {
        Static_AppMethods.launchAlertReasonForAskingPersonalInfo(this);
    }

    final int PERMISSION_CAMERA = 202;
    public void btnOCR(View view) {
        boolean showRationale = true;
        if (Build.VERSION.SDK_INT >= 23) {
            showRationale = shouldShowRequestPermissionRationale(Manifest.permission.CAMERA);
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchAlertHowToTakePhoto();
        } else if (!showRationale) {
            // user denied permission and CHECKED "never ask again"
            launchAlertEnablePermissionManually();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    PERMISSION_CAMERA);
        }
    }

    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_CAMERA && permissions.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchAlertHowToTakePhoto();
        }
    }

    private void launchAlertHowToTakePhoto(){
        final AlertImage alert = new AlertImage(this);
        alert.setDialogMessage(getResources().getString(R.string.howToScanLicense));
        alert.setDialogImage(getResources().getDrawable(R.drawable.photo_instructions_license));
        alert.setDialogPositiveButton(getResources().getString(R.string.Ok), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ActivityRegisterUser_PersonalInfo2_OCR.this, ActivityCamera.class);
                intent.putExtra("fromActivity", 1);
                startActivity(intent);

                alert.dismissAlert();
            }
        });
        alert.showAlert();
    }

    private void launchAlertEnablePermissionManually(){
        final AlertMessageButton alert = new AlertMessageButton(this);
        alert.setDialogMessage(getResources().getString(R.string.enable_Camera_PermissionsManually));
        alert.setDialogPositiveButton(getResources().getString(R.string.AlreadyEnabledPermissions), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alert.dismissAlert();
            }
        });
        alert.setDialogNegativeButton(getResources().getString(R.string.Go2Settings), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
                alert.dismissAlert();
            }
        });
        alert.showAlert();
    }

    public void insertPersonalInfo1Manually(View view) {
        Intent intent = new Intent(this, ActivityRegisterUser_PersonalInfo2.class);
        startActivity(intent);
    }

//    @Override
//    public void onBackPressed() {
//        Intent intent = new Intent(this, ActivityRegisterUser_PersonalInfo.class);
//        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
//        startActivity(intent);
//    }

    public void goBack(View view) {
        onBackPressed();
    }

}
