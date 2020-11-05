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

/***
 * User can choose to enter data manually or by scanning the SOAT printed document
 */
public class ActivityRegisterBikeOCR extends AppCompatActivity {

    final int PERMISSION_CAMERA = 203;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_bike_ocr);

        ImageView circle = findViewById(R.id.circle);
        Static_AppMethods.animateProgressCircle(circle);
    }

    public void goBack(View view) {
        onBackPressed();
    }

    public void btnOCR(View view) {
        boolean showRationale = true;
        if (Build.VERSION.SDK_INT >= 23) {
            // in version above 23 user can reject permission and request not be asked again
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
        alert.setDialogMessage(getResources().getString(R.string.howToScanSOAT));
        alert.setDialogImage(getResources().getDrawable(R.drawable.photo_instructions_soat));
        alert.setDialogPositiveButton(getResources().getString(R.string.Ok), new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ActivityRegisterBikeOCR.this, ActivityCamera.class);
                intent.putExtra("fromActivity", 2);
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
        Intent intent = new Intent(this, ActivityRegisterBike.class);
        startActivity(intent);
    }
}
