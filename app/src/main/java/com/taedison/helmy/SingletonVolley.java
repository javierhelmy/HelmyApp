package com.taedison.helmy;

import android.content.Context;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.Volley;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/***
 * Singleton Volley instance, which is used throughout the app
 */
public class SingletonVolley {

    private static SingletonVolley instance;
    private static RequestQueue requestQueue;
    private static Context ctx;

    private SingletonVolley(Context context) {
        ctx = context;
        requestQueue = getRequestQueue();
    }

    public static synchronized SingletonVolley getInstance(Context context) {
        if (instance == null) {
            instance = new SingletonVolley(context);
        }
        return instance;
    }

    public RequestQueue getRequestQueue() {
        if (requestQueue == null) {
            // getApplicationContext() is key, it prevents you from setting the contact from
            // Activity or BroadcastReceivers.
            try {
                setSSLcert_setQueue();
            } catch (Exception e) {
//                Log.e("singletonVolley", "Error loading SSL: " + e.getMessage());
            }
        }
        return requestQueue;
    }

    private static void setSSLcert_setQueue() throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
        // load ssl certificate in .crt format. In some android old versions (e.g., API 21) does not
        // read android documentation: https://developer.android.com/training/articles/security-ssl.html
        // to convert .pfx ssl certificates to .crt, follow: https://www.ibm.com/support/knowledgecenter/SSVP8U_9.7.0/com.ibm.drlive.doc/topics/r_extratsslcert.html

        // trust the SSL certificate in our smarter server
//        Log.d("singletonVolley", "Started SSL");
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        InputStream caInput = ctx.getAssets().open("smarter_ssl.crt");
        Certificate ca;
        try {
            ca = cf.generateCertificate(caInput);
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

//        Log.d("singletonVolley", "context SSL");

        // Tell volley to use a SocketFactory from our SSLContext
        requestQueue = Volley.newRequestQueue(ctx.getApplicationContext(), new HurlStack(null, context.getSocketFactory()));
    }
}
