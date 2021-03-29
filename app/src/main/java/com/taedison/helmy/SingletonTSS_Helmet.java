package com.taedison.helmy;

import android.content.Context;
import android.media.AudioManager;
import android.speech.tts.TextToSpeech;

import java.util.HashMap;
import java.util.Locale;

/***
 * Singleton TTS instance, which is used throughout the app
 */
public class SingletonTSS_Helmet {

    private static SingletonTSS_Helmet instance;
    private static Context ctx;
    private static TextToSpeech mTTS;
    private static boolean TTSready = false;
    private HashMap<String, String> hashMusicStream;

    private SingletonTSS_Helmet(final Context context) {
        ctx = context;
//        Log.d("TTS_singleton", "instance created");
        initializeTTS();

        hashMusicStream = new HashMap<>();
        hashMusicStream.put(TextToSpeech.Engine.KEY_PARAM_STREAM,
                String.valueOf(AudioManager.STREAM_MUSIC)); // configured to as music stream
    }

    public static synchronized SingletonTSS_Helmet getInstance(Context context) {
        if (instance == null) {
            instance = new SingletonTSS_Helmet(context);
        }
        return instance;
    }

    private void initializeTTS(){
        mTTS = new TextToSpeech(ctx, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int response) {
                if(TextToSpeech.SUCCESS == response){
                    TTSready = true;
                    configTTS();
//                    Log.d("TTS_singleton", "success");
                } else {
//                    Log.d("TTS_singleton", "error");
                }
            }
        });
    }

    boolean isTTSready(){
        return TTSready;
    }

    private void configTTS() {
        // set the default language for TTS, if it does not have a TTS package installed,
        // ActivitySplash will request to install one
        int available = mTTS.isLanguageAvailable(Locale.getDefault());
        if( available != TextToSpeech.LANG_MISSING_DATA
                && available != TextToSpeech.LANG_NOT_SUPPORTED ){
            mTTS.setLanguage(new Locale(Locale.getDefault().getLanguage()) );
        }
    }

    void speakSentence(String sentence){
        // it plays the most recent sentence and clears out the previous one
//        Log.d("TTS_singleton", "Speaking");
        mTTS.speak(sentence, TextToSpeech.QUEUE_FLUSH, hashMusicStream);
    }
}
