package com.taedison.helmy;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/***
 * Service that sends velocity txt files from every time that HelmyM connected with Helmy App.
 * The velocity file is created in BLE_HelmyM, and here it is uploaded. If at the time of upload to
 * the server the user does not have internet service, then it will be uploaded next time that user
 * opens the app and has internet service.
 * This service is executed when the app opens and uploads all pending files to the server
 */
public class ServiceUploadTxt extends Service {

    public static boolean running = false;
    private String TAG = "ServiceUploadTxt";
    private SingletonSharedPreferences preferences;

    public ServiceUploadTxt() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

//        Log.d(TAG, "onStartCommand");

        running = true;

        preferences = SingletonSharedPreferences.getInstance(getApplicationContext());

        uploadNextFileTxt();

        // If we get killed because of insuficient memory, START NOT STICKY do not restart when memory is released
        return START_STICKY;
    }

    private void uploadNextFileTxt(){
        String mostRecentFile = preferences.getMostRecentFilePendingToUpload_name_userId_MAC();

        if( !TextUtils.isEmpty(mostRecentFile) ){
            // start sending the most recent file
            String[] segments = mostRecentFile.split(";");
            if(segments.length == 3){
                uploadTxt(segments[0], segments[1], segments[2]);
            } else {
                stopSelf();
            }
        } else {
            // if there is no recent file, then continue with the remaining files
            ArrayList<String> listFiles = new ArrayList<>( preferences.getSetFilesPendingToUpload_name_userId_MAC() );
            if(listFiles.size() > 0){
                String[] segments = listFiles.get(0).split(";");
                if(segments.length == 3){
                    uploadTxt(segments[0], segments[1], segments[2]);
                } else {
                    stopSelf();
                }
            } else {
                stopSelf();
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    void uploadTxt(final String fileName, final String userId, final String MAC) {

        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                String content_type = "text/plain";
                File file = new File(getFilesDir(), fileName+".txt");

                OkHttpClient client = new OkHttpClient();
                // load ssl certificate in .crt format. In some android old versions (e.g., API 21) does not
                // read android documentation: https://developer.android.com/training/articles/security-ssl.html
                // to convert .pfx ssl certificates to .crt, follow: https://www.ibm.com/support/knowledgecenter/SSVP8U_9.7.0/com.ibm.drlive.doc/topics/r_extratsslcert.html

                try{
                    // trust the SSL certificate in our smarter server
//                    Log.d("singletonVolley", "Started SSL");
                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                    InputStream caInput = getAssets().open("smarter_ssl.crt");
                    Certificate ca;
                    try {
                        ca = cf.generateCertificate(caInput);
                        System.out.println("ca=" + ((X509Certificate) ca).getSubjectDN());
                    } finally {
                        caInput.close();
                    }
//                    Log.d("singletonVolley", ".crt loaded");
                    // Create a KeyStore containing our trusted CAs
                    String keyStoreType = KeyStore.getDefaultType();
                    KeyStore keyStore = KeyStore.getInstance(keyStoreType);
                    keyStore.load(null, null);
                    keyStore.setCertificateEntry("ca", ca);

//                    Log.d("singletonVolley", "setCertificateEntry SSL");
                    // Create a TrustManager that trusts the CAs in our KeyStore
                    String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
                    TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
                    tmf.init(keyStore);
//                    Log.d("singletonVolley", "trust CA SSL");
                    // Create an SSLContext that uses our TrustManager
                    SSLContext ssl_context = SSLContext.getInstance("TLS");
                    ssl_context.init(null, tmf.getTrustManagers(), null);

                    // set the CA so that it can trust the SSL certificate
                    X509TrustManager trustManager = (X509TrustManager) tmf.getTrustManagers()[0];
                    client = new OkHttpClient.Builder().sslSocketFactory(ssl_context.getSocketFactory(), trustManager).build();
                } catch (Exception ignored){

                }



                RequestBody file_body = RequestBody.create(file, MediaType.parse(content_type));

                RequestBody request_body = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("type",content_type)
                        .addFormDataPart("txt", fileName, file_body)
                        .addFormDataPart("mac", MAC)
                        .addFormDataPart("userId", userId)
                        .build();

                String url;
                if(preferences.get_isPrimaryServerDown()){
                    // primary server is down, so we will use the secondary
                    url = Static_AppVariables.url_txtVelocity2;
                } else {
                    url = Static_AppVariables.url_txtVelocity;
                }

                Request request = new Request.Builder()
                        .url(url)
                        .post(request_body)
                        .build();

                try {
                    final Response response = client.newCall(request).execute();

                    if(!response.isSuccessful()){
//                        Log.e(TAG, "Error : "+response + "\nuserId= " + userId);
                        // check if server is down, and if it is, then user has to try again and this
                        // time using the secondary server
                        if(response.code() == 503){
                            // server is down
                            preferences.set_primaryServerIsDown(true);
                        }
                    } else {
                        try {
                            JSONObject jsonArray = new JSONObject(response.body().string());
//                            Log.d(TAG, "Jason: " + jsonArray.toString());
                            String status = jsonArray.getString("status");

                            if( status.equals("1") || status.equals("0") ) {
                                //1: file saved successfully in server, 0: file was already in server
                                preferences.removeFileAlreadyUploaded(fileName, userId, MAC);
                                // we do not delete the txt file because it may be used in the future to display trips and velocities
                            }

                        } catch (JSONException ignored) {
//                            Log.e(TAG, "error txt upload:" + ignored.getMessage());
                        }

                    }
                    uploadNextFileTxt();


                } catch (final IOException e) {
//                    Log.e(TAG, "Exception : "+e.getMessage() + "\nuserId= " + userId);
                    stopSelf();
                }
            }
        });
        thread.start();
    }

    private SSLSocketFactory getSSLfactory() throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
        // load ssl certificate in .crt format. In some android old versions (e.g., API 21) does not
        // read android documentation: https://developer.android.com/training/articles/security-ssl.html
        // to convert .pfx ssl certificates to .crt, follow: https://www.ibm.com/support/knowledgecenter/SSVP8U_9.7.0/com.ibm.drlive.doc/topics/r_extratsslcert.html

        // trust the SSL certificate in our smarter server
//        Log.d("singletonVolley", "Started SSL");
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        InputStream caInput = getAssets().open("smarter_ssl.crt");
        Certificate ca;
        try {
            ca = cf.generateCertificate(caInput);
            System.out.println("ca=" + ((X509Certificate) ca).getSubjectDN());
        } finally {
            caInput.close();
        }
//        Log.d("singletonVolley", ".crt loaded");
        // Create a KeyStore containing our trusted CAs
        String keyStoreType = KeyStore.getDefaultType();
        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        keyStore.load(null, null);
        keyStore.setCertificateEntry("ca", ca);

//        Log.d("singletonVolley", "setCertificateEntry SSL");
        // Create a TrustManager that trusts the CAs in our KeyStore
        String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
        tmf.init(keyStore);
//        Log.d("singletonVolley", "trust CA SSL");
        // Create an SSLContext that uses our TrustManager
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, tmf.getTrustManagers(), null);

        return context.getSocketFactory();
    }

    @Override
    public void onDestroy() {
//        Log.d(TAG, "Service txt destroyed");
        running = false;
        super.onDestroy();
    }
}
