package lmsierra.gtuner;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.TarsosDSPAudioInputStream;
import be.tarsos.dsp.io.android.AndroidAudioInputStream;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;


public class GTuner extends ActionBarActivity {

    public static final int NUM_ARRAYS_FREQUENCY = 8;
    public static final int NUM_NOTES = 12;
    public static final float INITIAL_FREQUENCY = 4.0899982f;

    private TextView textNota;
    private TextView textFreq;

    private AdView adView;

    private ImageView right_indicator_1;
    private ImageView right_indicator_2;
    private ImageView right_indicator_3;
    private ImageView right_indicator_4;
    private ImageView right_indicator_5;

    private ImageView left_indicator_1;
    private ImageView left_indicator_2;
    private ImageView left_indicator_3;
    private ImageView left_indicator_4;
    private ImageView left_indicator_5;

    private float [] [] frequenciesTab;

    private String [] notas;

    private boolean notacion;
    private boolean sostenidos;
    private boolean recording = false;

    private SharedPreferences prefs;

    private float frecuenciaAnterior = -9999;

    private int count = 0;

    private Thread thread;
    private AudioRecord audioInputStream;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gtuner);

        prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE);

        adView = (AdView) findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().addTestDevice("TEST_DEVICE_ID").build();
        adView.loadAd(adRequest);

        notacion = prefs.getBoolean("notacion", false);
        sostenidos = prefs.getBoolean("sostenidos_bemoles", false);

        getNotation(notacion, sostenidos);

        initializeFrecuenciesTab();

        textNota = (TextView) findViewById(R.id.nota);
        textFreq = (TextView) findViewById(R.id.freq);

        textNota.setOnLongClickListener(new View.OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {

                changeBemol_Sostenido();
                return true;
            }
        });

        right_indicator_1 = (ImageView) findViewById(R.id.indicador_derecha_1);
        right_indicator_2 = (ImageView) findViewById(R.id.indicador_derecha_2);
        right_indicator_3 = (ImageView) findViewById(R.id.indicador_derecha_3);
        right_indicator_4 = (ImageView) findViewById(R.id.indicador_derecha_4);
        right_indicator_5 = (ImageView) findViewById(R.id.indicador_derecha_5);

        left_indicator_1 = (ImageView) findViewById(R.id.indicador_izquierda_1);
        left_indicator_2 = (ImageView) findViewById(R.id.indicador_izquierda_2);
        left_indicator_3 = (ImageView) findViewById(R.id.indicador_izquierda_3);
        left_indicator_4 = (ImageView) findViewById(R.id.indicador_izquierda_4);
        left_indicator_5 = (ImageView) findViewById(R.id.indicador_izquierda_5);

        textNota.setText(getResources().getString(R.string.empty));

        listenToMicrophone();
    }

    /*
     *  INITIALIZE A BI-DIMENSIONAL ARRAY WITH THE FREQUENCY OF ALL THE NOTES BETWEEN
     *  A THIRD OCTAVE LOWER AND A FORTH OCTAVE HIGHER
     */

    private void initializeFrecuenciesTab(){

        frequenciesTab = new float[NUM_ARRAYS_FREQUENCY] [NUM_NOTES];

        float previousFRQ = 0;

        for(int i = 0; i < NUM_ARRAYS_FREQUENCY; i++){
            for(int j = 0; j < NUM_NOTES; j++){

                if(i == 0 && j == 0){
                    frequenciesTab [i] [j] = INITIAL_FREQUENCY;
                }else{
                    frequenciesTab [i] [j] = previousFRQ * ((float) Math.pow(2, 0.083333333333));
                }

                previousFRQ = frequenciesTab [i] [j];
            }
        }


        float prev = 0;

        for(int i = 0; i < NUM_NOTES; i++){
            if(i == 0){
                prev = INITIAL_FREQUENCY / ((float) Math.pow(2, 0.083333333333));
            }else{
                prev = prev / ((float) Math.pow(2, 0.083333333333));
            }
        }
    }

    public void changeNotation(View v){

        if(notacion){
            notacion = false;

        }else{
            notacion = true;
        }

        SharedPreferences.Editor edit = prefs.edit();
        edit.putBoolean("notacion", notacion);
        edit.apply();

        getNotation(notacion, sostenidos);

        clearText();
    }

    public void changeBemol_Sostenido(){

        if (sostenidos) {
            sostenidos = false;
        } else {
            sostenidos = true;
        }

        SharedPreferences.Editor edit = prefs.edit();
        edit.putBoolean("sostenidos_bemoles", sostenidos);
        edit.apply();

        getNotation(notacion, sostenidos);

        clearText();
    }

    private void getNotation(boolean notacion_europea, boolean sostenidos){

        if(sostenidos) {
            if (notacion_europea) {
                notas = getResources().getStringArray(R.array.array_notacion_europea_sostenido);
            } else {
                notas = getResources().getStringArray(R.array.array_notacion_americana_sostenido);
            }
        }else{
            if (notacion_europea) {
                notas = getResources().getStringArray(R.array.array_notacion_europea_bemol);
            } else {
                notas = getResources().getStringArray(R.array.array_notacion_americana_bemol);
            }
        }
    }

    private void listenToMicrophone() {

        audioInputStream = new AudioRecord(
                MediaRecorder.AudioSource.MIC, 22050,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT,
                1024 * 2);

        TarsosDSPAudioFormat format = new TarsosDSPAudioFormat(22050, 16, 1, true, false);

        TarsosDSPAudioInputStream audioStream = new AndroidAudioInputStream(audioInputStream, format);
        audioInputStream.startRecording();
        recording = true;

        AudioDispatcher dispatcher = new AudioDispatcher(audioStream, 1024, 0);

        if (dispatcher != null) {

            PitchDetectionHandler pdh = new PitchDetectionHandler() {
                @Override
                public void handlePitch(PitchDetectionResult result, AudioEvent e) {
                    final float pitchInHz = result.getPitch();
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {


                            if(frecuenciaAnterior > pitchInHz - 10 && frecuenciaAnterior < pitchInHz + 10) {
                                count++;

                                if (count > 2) {
                                    if (pitchInHz < 0) {

                                        clearText();
                                        clearLeftIndicators();
                                        clearRightIndicators();
                                        textNota.setBackground(getResources().getDrawable(R.drawable.nota_background));

                                    } else {

                                        updateUI(pitchInHz);
                                    }
                                }
                            }else{
                                count = 0;
                            }

                            frecuenciaAnterior = pitchInHz;
                        }
                    });
                }
            };

            AudioProcessor p = new PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.FFT_YIN, 22050, 1024, pdh);
            dispatcher.addAudioProcessor(p);
            thread = new Thread(dispatcher, "Audio Dispatcher");
            thread.start();
        }
    }


    private void updateUI(float pitchInHz){

        int nota = getNota(pitchInHz);

        float previousFrequency = 0;
        float nextFrequency = 0;
        float perfectFrequency = 0;

        textNota.setText(notas[getNota(pitchInHz) % 12]);
        textFreq.setText("" + (String.format("%.02f", pitchInHz)) + " Hz");

        for (int i = 0; i < NUM_ARRAYS_FREQUENCY; i++) {

            if(nota % NUM_NOTES == 0){
                if( i  > 0) {
                    previousFrequency = frequenciesTab[i - 1][NUM_NOTES - 1];
                }
            }else{
                previousFrequency = frequenciesTab[i][(nota - 1) % NUM_NOTES];
            }

            perfectFrequency = frequenciesTab[i][nota % NUM_NOTES];

            if(nota % NUM_NOTES == 11){
                if(i < NUM_ARRAYS_FREQUENCY) {
                    nextFrequency = frequenciesTab[i + 1][0];
                }
            }else{
                nextFrequency = frequenciesTab[i][(nota + 1) % NUM_NOTES];
            }


            if (pitchInHz > previousFrequency && pitchInHz < nextFrequency) {

                float distance;

                if (pitchInHz > perfectFrequency) {

                    distance = (nextFrequency - perfectFrequency) / 2;

                    float interval = distance / 6;
                    float top = perfectFrequency + distance;

                    clearLeftIndicators();

                    if(pitchInHz > top - interval){
                        right_indicator_1.setVisibility(View.VISIBLE);
                        right_indicator_2.setVisibility(View.GONE);
                        right_indicator_3.setVisibility(View.GONE);
                        right_indicator_4.setVisibility(View.GONE);
                        right_indicator_5.setVisibility(View.GONE);
                        textNota.setBackground(getResources().getDrawable(R.drawable.nota_background));

                    }else if(pitchInHz > top - 2*interval){
                        right_indicator_1.setVisibility(View.VISIBLE);
                        right_indicator_2.setVisibility(View.VISIBLE);
                        right_indicator_3.setVisibility(View.GONE);
                        right_indicator_4.setVisibility(View.GONE);
                        right_indicator_5.setVisibility(View.GONE);
                        textNota.setBackground(getResources().getDrawable(R.drawable.nota_background));

                    }else if(pitchInHz > top - 3*interval) {
                        right_indicator_1.setVisibility(View.VISIBLE);
                        right_indicator_2.setVisibility(View.VISIBLE);
                        right_indicator_3.setVisibility(View.VISIBLE);
                        right_indicator_4.setVisibility(View.GONE);
                        right_indicator_5.setVisibility(View.GONE);
                        textNota.setBackground(getResources().getDrawable(R.drawable.nota_background));

                    }else if(pitchInHz > top - 4*interval){
                        right_indicator_1.setVisibility(View.VISIBLE);
                        right_indicator_2.setVisibility(View.VISIBLE);
                        right_indicator_3.setVisibility(View.VISIBLE);
                        right_indicator_4.setVisibility(View.VISIBLE);
                        right_indicator_5.setVisibility(View.GONE);
                        textNota.setBackground(getResources().getDrawable(R.drawable.nota_background));

                    }else if (pitchInHz > top - 4*interval) {
                        right_indicator_1.setVisibility(View.VISIBLE);
                        right_indicator_2.setVisibility(View.VISIBLE);
                        right_indicator_3.setVisibility(View.VISIBLE);
                        right_indicator_4.setVisibility(View.VISIBLE);
                        right_indicator_5.setVisibility(View.VISIBLE);
                        textNota.setBackground(getResources().getDrawable(R.drawable.nota_background));

                    }else {
                        textNota.setBackground(getResources().getDrawable(R.drawable.nota_background_green));
                        clearLeftIndicators();
                        clearRightIndicators();
                    }
                }else if(pitchInHz < perfectFrequency){

                    distance = (perfectFrequency - previousFrequency) / 2;

                    float interval = distance / 6;

                    float bottom = perfectFrequency - distance;

                    clearRightIndicators();

                    if(pitchInHz > bottom + 5*interval){
                        left_indicator_1.setVisibility(View.VISIBLE);
                        left_indicator_2.setVisibility(View.VISIBLE);
                        left_indicator_3.setVisibility(View.VISIBLE);
                        left_indicator_4.setVisibility(View.VISIBLE);
                        left_indicator_5.setVisibility(View.VISIBLE);
                        textNota.setBackground(getResources().getDrawable(R.drawable.nota_background));

                    }else if(pitchInHz > bottom + 4*interval){
                        left_indicator_1.setVisibility(View.VISIBLE);
                        left_indicator_2.setVisibility(View.VISIBLE);
                        left_indicator_3.setVisibility(View.VISIBLE);
                        left_indicator_4.setVisibility(View.VISIBLE);
                        left_indicator_5.setVisibility(View.GONE);
                        textNota.setBackground(getResources().getDrawable(R.drawable.nota_background));

                    }else if(pitchInHz > bottom + 3*interval){
                        left_indicator_1.setVisibility(View.VISIBLE);
                        left_indicator_2.setVisibility(View.VISIBLE);
                        left_indicator_3.setVisibility(View.VISIBLE);
                        left_indicator_4.setVisibility(View.GONE);
                        left_indicator_5.setVisibility(View.GONE);
                        textNota.setBackground(getResources().getDrawable(R.drawable.nota_background));

                    }else if(pitchInHz > bottom + 2*interval){
                        left_indicator_1.setVisibility(View.VISIBLE);
                        left_indicator_2.setVisibility(View.VISIBLE);
                        left_indicator_3.setVisibility(View.GONE);
                        left_indicator_4.setVisibility(View.GONE);
                        left_indicator_5.setVisibility(View.GONE);
                        textNota.setBackground(getResources().getDrawable(R.drawable.nota_background));

                    }else if(pitchInHz > bottom + interval){
                        left_indicator_1.setVisibility(View.VISIBLE);
                        left_indicator_2.setVisibility(View.GONE);
                        left_indicator_3.setVisibility(View.GONE);
                        left_indicator_4.setVisibility(View.GONE);
                        left_indicator_5.setVisibility(View.GONE);
                        textNota.setBackground(getResources().getDrawable(R.drawable.nota_background));
                    }else{
                        textNota.setBackground(getResources().getDrawable(R.drawable.nota_background_green));
                        clearLeftIndicators();
                        clearRightIndicators();
                    }
                }else{

                    textNota.setBackground(getResources().getDrawable(R.drawable.nota_background_green));
                    clearLeftIndicators();
                    clearRightIndicators();
                }
                break;
            }
        }
    }


    private void clearLeftIndicators(){
        left_indicator_1.setVisibility(View.GONE);
        left_indicator_2.setVisibility(View.GONE);
        left_indicator_3.setVisibility(View.GONE);
        left_indicator_4.setVisibility(View.GONE);
        left_indicator_5.setVisibility(View.GONE);
    }

    private void clearRightIndicators(){
        right_indicator_1.setVisibility(View.GONE);
        right_indicator_2.setVisibility(View.GONE);
        right_indicator_3.setVisibility(View.GONE);
        right_indicator_4.setVisibility(View.GONE);
        right_indicator_5.setVisibility(View.GONE);
    }

    private int getNota(float pitch){


        float frecuenciaLA = 440;

        double value = (12*(Math.log10(pitch/frecuenciaLA)/Math.log10(2)) + 57) + 0.5;
        int notaDetectada = (int)value;

        return notaDetectada;
    }

    private void clearText(){
        textNota.setText(getResources().getString(R.string.empty));
        textFreq.setText("");
    }

    @Override
    protected void onPause() {

        super.onPause();

        if(thread != null){
            thread.interrupt();
            thread = null;
        }

        if(audioInputStream != null && recording == true) {
            audioInputStream.stop();
            audioInputStream.release();
            recording = false;
        }

        if (adView != null) {
            adView.pause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        adView.resume();
    }

    @Override
    public void onDestroy() {
        adView.destroy();
        super.onDestroy();
    }
}