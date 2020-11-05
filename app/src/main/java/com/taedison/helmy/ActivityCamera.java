package com.taedison.helmy;

import androidx.appcompat.app.AppCompatActivity;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/***
 * this activity uses camera to scan and process the image on every frame. The text that is recognized
 * is analyzed in a seperate thread to not block the camera preview
 * */
public class ActivityCamera extends AppCompatActivity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    final String TAG = "actCamera";

    View vHorizontal, vVertical; // scanning lines
    ObjectAnimator animationH, animationV;

    private SurfaceHolder mHolder;
    private Camera mCamera;
    TextRecognizer textRecognizer;
    int previewWidth, previewHeight;

    SurfaceView surfaceView;

    boolean alertLaunched = false;
    int fromActivity;

    String fullName="", names="", surnames="", rh="", idnum="", ageS = "";
    String poliza="", placa = "", marca="", chasis="";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        Intent intent = getIntent();
        fromActivity = intent.getIntExtra("fromActivity", 0); // 1: activity was started from personalInfo2_OCR, 2: started from bikeOCR

        vHorizontal = findViewById(R.id.vHorizontalScan);
        vVertical = findViewById(R.id.vVerticalScan);


        checkCameraHardware(this);

        surfaceView = findViewById(R.id.surfaceViewCamera);

        textRecognizer = new TextRecognizer.Builder(this).build(); // uses mobile vision
    }

    /** Check if this device has a camera */
    private void checkCameraHardware(Context context) {
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            // no camera on this device
            Toast.makeText(this, getResources().getString(R.string.deviceWithoutCamera),
                    Toast.LENGTH_LONG).show();
            finish(); // finish the activity
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // this method executes only at the start. If user send the app to the background, it will exit
        if (!alertLaunched) {
            mCamera = getCameraInstance();

            previewWidth = mCamera.getParameters().getPreviewSize().width;
            previewHeight = mCamera.getParameters().getPreviewSize().height;

            // Install a SurfaceHolder.Callback so we get notified when the
            // underlying surface is created and destroyed.
            mHolder = surfaceView.getHolder();
            mHolder.addCallback(ActivityCamera.this);
        }
    }

    @Override
    protected void onPause(){
        super.onPause();
        if(threadProcessOCR != null){
            threadProcessOCR.interrupt();
        }
        finish(); // exits the camera if user sends the app to the background
    }

    View.OnTouchListener onTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (mCamera != null) {
                // set autofocus, although this method doesnt not work for some devices, that is why the touch triggers autofocus as well
                mCamera.cancelAutoFocus();
                Rect focusRect = new Rect(previewWidth/4, previewHeight/4,
                        previewWidth - previewWidth/4, previewHeight - previewHeight/4);

                Camera.Parameters parameters = mCamera.getParameters();
                if (parameters.getFocusMode().equals(
                        Camera.Parameters.FOCUS_MODE_AUTO) ) {
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                }

                if (parameters.getMaxNumFocusAreas() > 0) {
                    List<Camera.Area> mylist = new ArrayList<>();
                    mylist.add(new Camera.Area(focusRect, 1000));
                    parameters.setFocusAreas(mylist);
                }

                try {
                    mCamera.cancelAutoFocus();
                    mCamera.setParameters(parameters);
                    mCamera.startPreview();
                    mCamera.autoFocus(new Camera.AutoFocusCallback() {
                        @Override
                        public void onAutoFocus(boolean success, Camera camera) {
                            if (!camera.getParameters().getFocusMode().equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                                Camera.Parameters parameters = camera.getParameters();
                                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                                if (parameters.getMaxNumFocusAreas() > 0) {
                                    parameters.setFocusAreas(null);
                                }
                                camera.setParameters(parameters);
                                camera.startPreview();
                            }
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return true;
        }
    };

    /** A safe way to get an instance of the Camera object. */
    public Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open();
            c.setDisplayOrientation(90); // so that camera orientation is vertical. Activity is set to portrait mode in the manifest
            Camera.Parameters params = c.getParameters();
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);

            Camera.Size bestSize;

            List<Camera.Size> sizeList = c.getParameters().getSupportedPreviewSizes();
            bestSize = sizeList.get(0);

            // choose the camera with the highest number of pixels
            for(int i = 1; i < sizeList.size(); i++){
                if((sizeList.get(i).width * sizeList.get(i).height) >
                        (bestSize.width * bestSize.height)){
                    bestSize = sizeList.get(i);
                }
            }

            params.setPreviewSize(bestSize.width, bestSize.height);

            c.setParameters(params);

        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
            Toast.makeText(this, getResources().getString(R.string.otherAppUsingCamera) + e,
                    Toast.LENGTH_LONG ).show();
            finish();
        }
        return c; // returns null if camera is unavailable
    }

    public void surfaceCreated(SurfaceHolder holder) {
        // once created start animating the vertical and horizontal lines
        if( animationH == null || !animationH.isRunning() ){
            animationH = ObjectAnimator.ofFloat(vVertical, "translationX", surfaceView.getWidth());
            animationH.setDuration(2000);
            animationH.setRepeatCount(Animation.INFINITE);
            animationH.setRepeatMode(ValueAnimator.REVERSE);
            animationH.start();

            animationV = ObjectAnimator.ofFloat(vHorizontal, "translationY", surfaceView.getHeight());
            animationV.setDuration(3000);
            animationV.setRepeatCount(Animation.INFINITE);
            animationV.setRepeatMode(ValueAnimator.REVERSE);
            animationV.start();
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        surfaceView.setOnTouchListener(onTouchListener);
        // start the preview of the camera
        try {
            mCamera.setPreviewCallback(this);

            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();

        } catch (IOException e) {
            Log.d(TAG+"PreviewCamera", "Error setting camera preview: " + e.getMessage());
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // empty. Take care of releasing the Camera preview
        releaseCameraAndPreview();
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (mHolder.getSurface() == null){
            // preview surface does not exist
            return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception ignored){
            // ignore: tried to stop a non-existent preview
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here

        // start preview with new settings
        try {
            mCamera.setPreviewCallback(this);

            mCamera.setPreviewDisplay(mHolder);
            mCamera.startPreview();

        } catch (Exception e){
            Log.d(TAG+"PreviewCamera", "Error starting camera preview: " + e.getMessage());
        }
    }

    private void releaseCameraAndPreview() {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.setAutoFocusMoveCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    private Thread threadProcessOCR;

    @Override
    public void onPreviewFrame(final byte[] bytes, Camera camera) {

        if ( fromActivity == 1 ){
            // scanning license
            if ( TextUtils.isEmpty(idnum) || TextUtils.isEmpty(names) || TextUtils.isEmpty(surnames)
                    || TextUtils.isEmpty(rh) ){
                // these four are in the old license card, first check if any of them is empty and in
                // the next if statement check if age is empty in case it is a new license card
                Log.d(TAG+"ocrtext", "NOT COMPLETE " + names + " " + surnames + " " + ageS + " " + rh + " " + idnum);
                clearFields_License(); // all fields must be detected in the same frame
            } else if( !isOldLicenseCard && TextUtils.isEmpty(ageS)){
                // ageS is checked only if it is a new license card
                Log.d(TAG+"ocrtext", "NOT COMPLETE " + names + " " + surnames + " " + ageS + " " + rh + " " + idnum);
                clearFields_License(); // all fields must be detected in the same frame
            } else {
                Log.d(TAG+"ocrtext", "COMPLETE " + names + " " + surnames + " " + ageS + " " + rh + " " + idnum);

                launchAlertConfirmLicenseData();
            }
        } else if ( fromActivity == 2 ) {
            // scanning soat
            if ( TextUtils.isEmpty(poliza) || TextUtils.isEmpty(placa) || TextUtils.isEmpty(marca) || TextUtils.isEmpty(chasis) ){
                Log.d(TAG+"ocrtext", "NOT COMPLETE " + poliza + " " + marca + " " + chasis );
                clearFields_SOAT(); // all fields must be detected in the same frame
            } else {
                Log.d(TAG+"ocrtext", "COMPLETE " + poliza + " " + marca + " " + chasis );

                launchAlertConfirmSOATData();
            }
        }

        if(threadProcessOCR == null || !threadProcessOCR.isAlive()) {
            threadProcessOCR = new Thread() {
                @Override
                public void run() {
                    // we process the image in 3 orientations
                    ByteBuffer buffer = ByteBuffer.wrap(bytes);
                    Frame imageFramePortrait = new Frame.Builder()
                            .setImageData(buffer, previewWidth, previewHeight, ImageFormat.NV21)
                            .setRotation(Frame.ROTATION_90)
                            .build();

                    Frame imageFrameLand_0 = new Frame.Builder()
                            .setImageData(buffer, previewWidth, previewHeight, ImageFormat.NV21)
                            .build();

                    Frame imageFrameLand_180 = new Frame.Builder()
                            .setImageData(buffer, previewWidth, previewHeight, ImageFormat.NV21)
                            .setRotation(Frame.ROTATION_180)
                            .build();

                    SparseArray<TextBlock> textBlocks_portrait = textRecognizer.detect(imageFramePortrait);
                    SparseArray<TextBlock> textBlocks_land_0 = textRecognizer.detect(imageFrameLand_0);
                    SparseArray<TextBlock> textBlocks_land_180 = textRecognizer.detect(imageFrameLand_180);

                    SparseArray<TextBlock> textBlocks; // used to extract the data
                    // we process the orientation with the highest number of pieces of text detected
                    if(textBlocks_portrait.size() >= textBlocks_land_0.size() &&
                            textBlocks_portrait.size() >= textBlocks_land_180.size()){
                        textBlocks = textBlocks_portrait;
                    } else if( textBlocks_land_0.size() >= textBlocks_land_180.size() ){
                        textBlocks = textBlocks_land_0;
                    } else {
                        textBlocks = textBlocks_land_180;
                    }

                    Log.d(TAG+"ocrtext", "Textblocks size="+textBlocks.size());
                    if(textBlocks.size() > 3){
                        // if more than 3 blocks were detected then process the info, otherwise no text was identified
                        if(fromActivity == 1){
                            synchronized(this) {
                                processRecognizedText_License(textBlocks);
                            }
                        } else if(fromActivity == 2){
                            synchronized(this) {
                                processRecognizedText_SOAT(textBlocks);
                            }
                        }
                    }
                }
            };

            threadProcessOCR.start();
        }
    }

    private void launchAlertConfirmLicenseData() {
        // once all fields are detected, we confirm with the user that the data is correct, otherwise the user can continue scanning
        threadProcessOCR.interrupt();
        releaseCameraAndPreview();

        final AlertMessageButton alert = new AlertMessageButton(ActivityCamera.this);
        String msg;
        if(isOldLicenseCard){
            // old cards do not contain the birthday/age
            msg = getResources().getString(R.string.registerPersonalData) + "\n" + "\n"
                    + getResources().getString(R.string.userLastName) + ": " + surnames + "\n"
                    + getResources().getString(R.string.userGivenNames) + ": " + names + "\n"
                    + getResources().getString(R.string.userRH) + ": " + rh + "\n"
                    + getResources().getString(R.string.userLicense) + ": " + idnum;
        } else {
            msg = getResources().getString(R.string.registerPersonalData) + "\n" + "\n"
                    + getResources().getString(R.string.userIdNumber) + ": " + idnum + "\n"
                    + getResources().getString(R.string.userGivenNames) + ": " + names + "\n"
                    + getResources().getString(R.string.userLastName) + ": " + surnames + "\n"
                    + getResources().getString(R.string.userAge) + ": " + ageS + "\n"
                    + getResources().getString(R.string.userRH) + ": " + rh;
        }

        alert.setDialogMessage(msg);
        alert.setDialogPositiveButton(getResources().getString(R.string.Ok),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Log.d(TAG+"ocrtext", "COMPLETE " + names + " " + surnames + " " + ageS + " " + rh + " " + idnum);

                        alertLaunched = false;
                        Intent intent = new Intent(ActivityCamera.this, ActivityRegisterUser_PersonalInfo2.class);
                        intent.putExtra("idNum", idnum);
                        intent.putExtra("names", names);
                        intent.putExtra("surnames", surnames);
                        intent.putExtra("age", ageS);
                        intent.putExtra("rh", rh);
                        startActivity(intent);
                        clearFields_License();
                        alert.dismissAlert();
                    }
                });
        alert.setDialogNegativeButton(getResources().getString(R.string.retry),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        alertLaunched = false;
                        onResume();
                        surfaceCreated(mHolder);
                        clearFields_License();
                        alert.dismissAlert();
                    }
                });
        alert.setCancelButtonClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alertLaunched = false;
                onResume();
                surfaceCreated(mHolder);
                clearFields_License();
                alert.dismissAlert();
            }
        });
        alert.setDialogCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                alertLaunched = false;
                onResume();
                surfaceCreated(mHolder);
                clearFields_License();
            }
        });
        alert.showAlert();
        alertLaunched = true;
    }

    private void launchAlertConfirmSOATData() {
        // once all fields are detected, we confirm with the user that the data is correct, otherwise the user can continue scanning
        threadProcessOCR.interrupt();
        releaseCameraAndPreview();

        final AlertMessageButton alert = new AlertMessageButton(ActivityCamera.this);
        final String msg = getResources().getString(R.string.SOAT_DATA) + "\n" + "\n"
                + getResources().getString(R.string.bikeSOAT) + ": " + poliza + "\n"
                + getResources().getString(R.string.plate) + ": " + placa + "\n"
                + getResources().getString(R.string.bikeBrand) + ": " + marca + "\n"
                + getResources().getString(R.string.bikeChassisID) + ": " + chasis;

        alert.setDialogMessage(msg);
        alert.setDialogPositiveButton(getResources().getString(R.string.Ok),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Log.d(TAG+"ocrtext", "COMPLETE " + poliza + " " + marca + " " + chasis );

                        alertLaunched = false;
                        Intent intent = new Intent(ActivityCamera.this, ActivityRegisterBike.class);
                        intent.putExtra("soat", poliza);
                        intent.putExtra("placa", placa);
                        intent.putExtra("marca", marca);
                        intent.putExtra("chasis", chasis);
                        startActivity(intent);
                        clearFields_SOAT();
                        alert.dismissAlert();
                    }
                });
        alert.setDialogNegativeButton(getResources().getString(R.string.retry),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        alertLaunched = false;
                        onResume();
                        surfaceCreated(mHolder);
                        clearFields_SOAT();
                        alert.dismissAlert();
                    }
                });
        alert.setCancelButtonClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alertLaunched = false;
                onResume();
                surfaceCreated(mHolder);
                clearFields_SOAT();
                alert.dismissAlert();
            }
        });
        alert.setDialogCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                alertLaunched = false;
                onResume();
                surfaceCreated(mHolder);
                clearFields_SOAT();
            }
        });
        alert.showAlert();
        alertLaunched = true;
    }

    boolean isOldLicenseCard;
    private void processRecognizedText_License(SparseArray<TextBlock> textBlocks){
        // we first have to determine if it is an old or new license card
        isOldLicenseCard = false;
        for (int i = 0; i < textBlocks.size(); i++) {
            TextBlock textBlock = textBlocks.get(textBlocks.keyAt(i));
            String tbString =  textBlock.getValue();
            Log.d(TAG+"ocrtext", "Textblock ="+tbString + " i=" + i);

            if(tbString.contains("C.C.") &&
                tbString.replaceAll("\\D", "").length() > 7 // deletes all non-digits
                ){
                // if C.C. is detected and there are at least 7 numbers, then it is an old license card
                isOldLicenseCard = true;
                break;
            }
        }

        if(isOldLicenseCard){
            processOldLicense(textBlocks);
        } else {
            processNewLicense(textBlocks);
        }
    }

    /*** the way the recognized text is processed was done by trial and error and my not work in all phones */
    private void processOldLicense(SparseArray<TextBlock> textBlocks){
        boolean apellidoIsComing = false;
        boolean nombreIsComing = false;
        boolean licenceNumIsComing = false;
        for (int i = 0; i < textBlocks.size(); i++) {
            TextBlock textBlock = textBlocks.get(textBlocks.keyAt(i));
            String tbString = textBlock.getValue();
            Log.d(TAG+"ocrtext", "Textblock ="+tbString + " i=" + i);

            /*
             * PLEASE HAVE AN OLD LICENSE CARD TO UNDERTAND THE CODE
             * Data should be extracted in the following order, as that is how it appears in the licenses:
             * 1. the apellido and nombre
             * 2. once apellido and nombre are detected, we expect the RH
             * 3. the license number
             * NO AGE in the old license cards
             * */
            if (tbString.contains("APELLID")) {
                // the workd "APELLIDO" was detected, therefore the apellido should be in the next textblock
                apellidoIsComing = true;
            } else if(TextUtils.isEmpty(surnames) && apellidoIsComing){
                // the workd "APELLIDO" was detected in the previous textblock, therefore this textblock should be the apellido
                surnames = tbString.toUpperCase();
                Log.d(TAG+"ocrtext", "SURNAMES: " + surnames);
            } else if (tbString.contains("NOMBRE")) {
                // the workd "NOMBRE" was detected, therefore the nombre should be in the next textblock
                nombreIsComing = true;
            } else if(TextUtils.isEmpty(names) && nombreIsComing){
                // the workd "NOMBRE" was detected in the previous textblock, therefore this textblock should be the nombre
                names = tbString.toUpperCase();
                Log.d(TAG+"ocrtext", "NAMES: " + names);
            } else if(!TextUtils.isEmpty(surnames) && !TextUtils.isEmpty(names)
                    && TextUtils.isEmpty(rh) && TextUtils.isEmpty(idnum)){
                // If apellidos and nombres were already detected, then the inmidiate textblock after it is the one that contains the RH
                for(String type : Static_AppVariables.arrayRHs){
                    if(tbString.contains(type)){
                        rh = type;
                        Log.d(TAG+"ocrtext", "RH: " + rh);
                        break;
                    }
                }
            } else if(tbString.contains("DE LICENCIA")
                    && tbString.replaceAll("\\D", "").length() > 7 ) {
                // the number of the license is contained in this textblock
                String[] seperated = tbString.split(" ");
                if(seperated.length > 3){
                    // contains at least three words and the number
                    idnum = seperated[seperated.length - 1]; // the last in the array is the license number
                    Log.d(TAG+"ocrtext", "LICENSE: " + idnum);
                }
            } else if(tbString.contains("DE LICENCIA")
                    && tbString.replaceAll("\\D", "").length() < 7 ) {
                // the number of the license is not in this textblock, but the next textblock contains it
                licenceNumIsComing = true;
            } else if(TextUtils.isEmpty(idnum) && licenceNumIsComing){
                idnum = tbString;
                Log.d(TAG+"ocrtext", "LICENSE: " + idnum);
            }
        }
    }

    /*** the way the recognized text is processed was done by trial and error and my not work in all phones */
    private void processNewLicense(SparseArray<TextBlock> textBlocks){
        boolean nameIsComing = false;
        for (int i = 0; i < textBlocks.size(); i++) {
            TextBlock textBlock = textBlocks.get(textBlocks.keyAt(i));
            String tbString =  textBlock.getValue();
            Log.d(TAG+"ocrtext", "Textblock ="+tbString + " i=" + i);

            /*
             PLEASE HAVE A NEW LICENSE CARD TO UNDERTAND THE CODE
             Data should be extracted in the following order, as that is how it appears in the licenses:
             1. the license number
             2. names and surnames
             3. rh
             4. age (birthday)
             */
            if (tbString.contains("No.") && tbString.length() > 6) {
                // If contains "No.", we extract the string onwards
                String temp = tbString.substring(4);
                Log.d(TAG+"ocrtext", "Id = " + temp);
                try {
                    temp = temp.replaceAll("\\s+", ""); // \\s+ finds all white spaces and remove them
                    Integer.parseInt(temp); // if exception is thrown, then it was not a number
                    idnum = temp;
                } catch (Exception e) {
                    Log.d(TAG+"ocrtext", "Id error: " + e);
                    // if it is not a number, then it wont be assigned to etLicenseNum
                }
            } else if (tbString.contains("NOMBRE")) {
                // the workd "NOMBRE" was detected, therefore the names and surnames should be in the next textblock
                names = "";
                surnames = "";
                nameIsComing = true;
            } else if (nameIsComing) {
                nameIsComing = false; // so that it does not enter again in this section of the code
                // name is usally after the textblock "NOMBRE"
                fullName = tbString.toUpperCase();
                String[] separatedFullName = fullName.split(" ");
                if (separatedFullName.length >= 3) {
                    // at least one name, and two last names
                    for (int idx = separatedFullName.length - 1; idx >= 0; idx--) {
                        if (idx >= separatedFullName.length - 2) {
                            // the last two are surnames
                            if (TextUtils.isEmpty(surnames)) {
                                surnames = separatedFullName[idx];
                            } else {
                                surnames = separatedFullName[idx] + " " + surnames;
                            }
                        } else {
                            // the rest are names and middle names
                            if (TextUtils.isEmpty(names)) {
                                names = separatedFullName[idx];
                            } else {
                                names = separatedFullName[idx] + " " + names;
                            }
                        }
                    }
                }

                if (fullName.matches(".*\\d.*")) {
                    // if there are number in the names, then clear them
                    names = "";
                    surnames = "";
                }
                Log.d(TAG+"ocrtext", "Length=" + separatedFullName.length + " Full name = " + fullName + " name:" + names + " surnames:" + surnames);
            } else if (Static_AppVariables.arrayRHs.contains(tbString)) {
                // check if the strings detected match any of the blood groups
                rh = tbString;
                Log.d(TAG+"ocrtext", "Rh= " + rh);
            } else if (tbString.contains("-19") || tbString.contains("-20")) {
                //if contains years starting -19 (e.g. 1993) or -20 (e.g. 2001), then this should be the birthday
                int age;
                if (TextUtils.isEmpty(ageS)) {
                    //if empty or null, it will add the first date only
                    Calendar todayCal = Calendar.getInstance(), birthdayCal = Calendar.getInstance();

                    String[] separatedAge = tbString.split("-"); // year="+separatedAge[2] +" month="+separatedAge[1] +" day="+separatedAge[0]
                    Log.d(TAG+"ocrtext", "Age length= " + separatedAge.length);
                    if (separatedAge.length == 3) {
                        //must be length 3, day-month-year
                        try {
                            int year = Integer.parseInt(separatedAge[2]);
                            int month = Integer.parseInt(separatedAge[1]);
                            int day = Integer.parseInt(separatedAge[0]);
                            birthdayCal.set(year, month, day);
                            // calculate the age in years
                            age = todayCal.get(Calendar.YEAR) - birthdayCal.get(Calendar.YEAR);
                            if (todayCal.get(Calendar.DAY_OF_YEAR) < birthdayCal.get(Calendar.DAY_OF_YEAR)) {
                                // if user's birthday still has not passed the present year
                                age--;
                            }

                            ageS = String.valueOf(age);

                            Log.d(TAG+"ocrtext", "year= " + year + " month=" + month + " day=" + day + " Age= " + ageS);
                        } catch (Exception e) {
                            Log.e(TAG+"ocrtext", e.toString());
                        }
                    }
                }
            }
        }
    }

    /*** the way the recognized text is processed was done by trial and error and my not work in all phones */
    private void processRecognizedText_SOAT(SparseArray<TextBlock> textBlocks){
        boolean marcaIsComing = false;
        boolean polizaIsComing = false;

        for (int i = 0; i < textBlocks.size(); i++) {
            TextBlock textBlock = textBlocks.get(textBlocks.keyAt(i));
            String tbString = textBlock.getValue();

            Log.d(TAG+"ocrtext", "Textblock ="+tbString + " i=" + i);
            /*
             * PLEASE HAVE AN PRINTED COPY OF SOAT TO UNDERTAND THE CODE
             * Data should be extracted in the following order, as that is how it appears in the SOATs:
             * 1. policy number
             * 2. placa
             * 3. brand
             * 4. chasis number (how chasis serial works: https://www.pruebaderuta.com/numero-identificacion-vehicular.php)
             * */
            if( TextUtils.isEmpty(poliza) || TextUtils.isEmpty(marca) || TextUtils.isEmpty(chasis) ){
                if( tbString.contains("POL") && TextUtils.isEmpty(poliza) ){
                    // the word "POL" was detected, the policy number may be in this textblock
                    Log.d(TAG+"ocrtext_soat", "POL" + tbString);
                    String[] separated = tbString.split("\n");
                    if (separated.length == 3){
                        // if textblock contains three line then the policy number is all together in this textblock.
                        // Sometimes the policy number is devided in two textblocks, we do not address that case.
                        poliza = separated[1] + "-" + separated[2];
                        Log.d(TAG+"ocrtext_soat", "POLIZA= " + poliza);
                    } else {
                        // this textblock does not contain the policy number, but the inmidiate next does
                        polizaIsComing = true;
                    }
                } else if( polizaIsComing && tbString.split("\r\n|\r|\n").length == 2
                            && tbString.replaceAll("\\D","").length() > 8
                        && TextUtils.isEmpty(poliza) ){
                    // the word "POL" was detected in the previous textblock.
                    // If there are two lines and more than 8 numbers is the poliza number
                    Log.d(TAG+"ocrtext_soat", "POL" + tbString);
                    String[] separated = tbString.split("\n");
                    if (separated.length == 2){
                        poliza = separated[0] + "-" + separated[1];
                        Log.d(TAG+"ocrtext_soat", "POLIZA= " + poliza);
                    }
                } else if( tbString.replaceAll("\\D", "").length() == 3 && // replaces all non-digits and there should be 3 numbers remaining in the plate number
                        tbString.replaceAll("\\d", "").length() == 3 && // replaces all digits (0-9) and there should be 3 letter remaining in the plate number
                        TextUtils.isEmpty(placa)){
                    // poliza should have been found first
                    Log.d(TAG+"ocrtext_soat", "PLACA= " + tbString);
                    placa = tbString;
                } else if ( tbString.contains("MARCA") && TextUtils.isEmpty(marca) ){
                    // sometime two or three words are detected for marca
                    Log.d(TAG+"ocrtext_soat", "MARCA" + tbString + " length= " + tbString.split(" ").length );
                    String[] separated = tbString.split(" ");
                    if (separated.length == 3){
                        // marca label is in the middle of the 3 words detected, the last is the marca name
                        if(separated[1].equals("MARCA")){
                            marca = separated[2].toUpperCase();
                            Log.d(TAG+"ocrtext_soat", "MARCA= " + marca);
                        } else {
                            // marca label and the marca name are not in the same block, therefore it will be next (coming)
                            marcaIsComing = true;
                        }
                    } else if (separated.length == 2){
                        // marca label is the first of the two words detected, the last is the marca name
                        if(separated[0].equals("MARCA")){
                            marca = separated[1].toUpperCase();
                            Log.d(TAG+"ocrtext_soat", "MARCA= " + marca);
                        } else {
                            // marca label and the marca name are not in the same block, therefore it will be next (coming)
                            marcaIsComing = true;
                        }
                    } else {
                        // marca label and the marca name are not in the same block, therefore it will be next (coming)
                        marcaIsComing = true;
                    }
                } else if (marcaIsComing && TextUtils.isEmpty(marca) ){
                    // the word "MARCA" was detected in the previous block, then this textblock contains the brand
                    Log.d(TAG+"ocrtext_soat", "MARCA" + tbString);
                    marca = tbString.toUpperCase();
                    Log.d(TAG+"ocrtext_soat", "MARCA= " + marca);
                } else if (tbString.length() == 17 && TextUtils.isEmpty(chasis) ){
                    // if the textblock contains 17 characters, which is the length of chassis numbers
                    Log.d(TAG+"ocrtext_soat", "CHASIS" + tbString);
                    // how chasis serial works: https://www.pruebaderuta.com/numero-identificacion-vehicular.php
                    try{
                        Integer.parseInt(tbString.substring(11, 16));
                        // if an exception is not thrown, then this corresponds to the chasis
                        chasis = tbString;
                        Log.d(TAG+"ocrtext_soat", "CHASIS= " + chasis);
                    } catch (Exception e){
                        // it wasnt the chasis
                    }
                }
            } else {
                // poliza, marca, and chasis were found
                break;
            }

        }
    }

    private void clearFields_License(){
        names = "";
        surnames = "";
        ageS = "";
        rh = "";
        idnum = "";
    }

    private void clearFields_SOAT(){
        poliza = "";
        placa = "";
        marca = "";
        chasis = "";
    }

}
