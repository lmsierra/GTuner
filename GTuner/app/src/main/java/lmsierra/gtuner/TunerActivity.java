package lmsierra.gtuner;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.TarsosDSPAudioInputStream;
import be.tarsos.dsp.io.android.AndroidAudioInputStream;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;

public class TunerActivity extends Activity {

    public static final int NUM_ARRAYS_FRECUENCY = 6;

    private TextView textNota;

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

    private String [] [] frecuenciesTab;

    private boolean notacion;

    private boolean sostenidos;

    private WatchViewStub stub;

    private String [] notas;

    private SharedPreferences prefs;

    private int notaActual;

    private float frecuenciaAnterior = -9999;

    private int count = 0;

    private Thread thread;
    private AudioRecord audioInputStream;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tuner);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);


        prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE);

        notacion = prefs.getBoolean("notacion", false);
        sostenidos = prefs.getBoolean("sostenidos_bemoles", false);

        getNotation(notacion, sostenidos);

        initializeFrecuenciesTab();

        stub = (WatchViewStub) findViewById(R.id.watch_view_stub);

        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {

            @Override
            public void onLayoutInflated(WatchViewStub stub) {

                textNota = (TextView) stub.findViewById(R.id.nota);

                textNota.setOnLongClickListener(new View.OnLongClickListener() {

                    @Override
                    public boolean onLongClick(View v) {

                        changeBemol_Sostenido();
                        return true;
                    }
                });


                right_indicator_1 = (ImageView) stub.findViewById(R.id.indicador_derecha_1);
                right_indicator_2 = (ImageView) stub.findViewById(R.id.indicador_derecha_2);
                right_indicator_3 = (ImageView) stub.findViewById(R.id.indicador_derecha_3);
                right_indicator_4 = (ImageView) stub.findViewById(R.id.indicador_derecha_4);
                right_indicator_5 = (ImageView) stub.findViewById(R.id.indicador_derecha_5);

                left_indicator_1 = (ImageView) stub.findViewById(R.id.indicador_izquierda_1);
                left_indicator_2 = (ImageView) stub.findViewById(R.id.indicador_izquierda_2);
                left_indicator_3 = (ImageView) stub.findViewById(R.id.indicador_izquierda_3);
                left_indicator_4 = (ImageView) stub.findViewById(R.id.indicador_izquierda_4);
                left_indicator_5 = (ImageView) stub.findViewById(R.id.indicador_izquierda_5);

                textNota.setText(getResources().getString(R.string.empty));

                listenToMicrophone();
            }
        });
    }


    private void initializeFrecuenciesTab(){
        frecuenciesTab = new String[6][12];
        frecuenciesTab [0] = getResources().getStringArray(R.array.octava_2);
        frecuenciesTab [1] = getResources().getStringArray(R.array.octava_1);
        frecuenciesTab [2] = getResources().getStringArray(R.array.octava0);
        frecuenciesTab [3] = getResources().getStringArray(R.array.octava1);
        frecuenciesTab [4] = getResources().getStringArray(R.array.octava2);
        frecuenciesTab [5] = getResources().getStringArray(R.array.octava3);
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

        textNota.setText(notas[notaActual]);
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

        textNota.setText(notas[notaActual]);
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
                                        
                                    } else {

                                        int nota = getNota(pitchInHz);

                                        textNota.setText(notas[getNota(pitchInHz) % 12]);

                                        float previousFrecuency;
                                        float nextFrecuency;
                                        float perfectFrecuency;

                                        Log.e("ANTES DE BUCLE", "ANTES DEL BUCLE");

                                        for (int i = 0; i < NUM_ARRAYS_FRECUENCY; i++) {
                                            Log.e("LOOP I: ", String.valueOf(i));
                                            previousFrecuency = Float.parseFloat(frecuenciesTab[i][(nota - 1) % 12]);
                                            perfectFrecuency = Float.parseFloat(frecuenciesTab[i][nota%12]);
                                            nextFrecuency = Float.parseFloat(frecuenciesTab[i][(nota + 1) % 12]);

                                            if (pitchInHz > previousFrecuency && pitchInHz < nextFrecuency) {

                                                Log.e("DENTRO DE AQUI", "DENTRO DE AQUI");
                                                float distance;

                                                if (pitchInHz > perfectFrecuency) {
                                                    Log.e("POR LA DERECHA", "POR LA DERECHA");

                                                    distance = (nextFrecuency - perfectFrecuency) / 6;

                                                    clearLeftIndicators();

                                                    if (pitchInHz > nextFrecuency - distance) {
                                                        right_indicator_1.setVisibility(View.VISIBLE);
                                                        right_indicator_2.setVisibility(View.GONE);
                                                        right_indicator_3.setVisibility(View.GONE);
                                                        right_indicator_4.setVisibility(View.GONE);
                                                        right_indicator_5.setVisibility(View.GONE);
                                                    }else if(pitchInHz > nextFrecuency - 2*distance){
                                                        right_indicator_1.setVisibility(View.VISIBLE);
                                                        right_indicator_2.setVisibility(View.VISIBLE);
                                                        right_indicator_3.setVisibility(View.GONE);
                                                        right_indicator_4.setVisibility(View.GONE);
                                                        right_indicator_5.setVisibility(View.GONE);
                                                    }else if(pitchInHz > nextFrecuency - 3*distance){
                                                        right_indicator_1.setVisibility(View.VISIBLE);
                                                        right_indicator_2.setVisibility(View.VISIBLE);
                                                        right_indicator_3.setVisibility(View.VISIBLE);
                                                        right_indicator_4.setVisibility(View.GONE);
                                                        right_indicator_5.setVisibility(View.GONE);
                                                    }else if(pitchInHz > nextFrecuency - 4*distance){
                                                        right_indicator_1.setVisibility(View.VISIBLE);
                                                        right_indicator_2.setVisibility(View.VISIBLE);
                                                        right_indicator_3.setVisibility(View.VISIBLE);
                                                        right_indicator_4.setVisibility(View.VISIBLE);
                                                        right_indicator_5.setVisibility(View.GONE);
                                                    }else if(pitchInHz > nextFrecuency - 5*distance){
                                                        right_indicator_1.setVisibility(View.VISIBLE);
                                                        right_indicator_2.setVisibility(View.VISIBLE);
                                                        right_indicator_3.setVisibility(View.VISIBLE);
                                                        right_indicator_4.setVisibility(View.VISIBLE);
                                                        right_indicator_5.setVisibility(View.VISIBLE);
                                                    }else {
                                                        textNota.setBackground(getDrawable(R.drawable.round_background_green));
                                                    }

                                                }else{
                                                    Log.e("POR LA IZQUIERDA", "POR LA IZQUIERDA");

                                                    distance = (perfectFrecuency - previousFrecuency) / 6;

                                                    clearRightIndicators();

                                                    if(pitchInHz > previousFrecuency + distance){
                                                        left_indicator_1.setVisibility(View.VISIBLE);
                                                        left_indicator_2.setVisibility(View.GONE);
                                                        left_indicator_3.setVisibility(View.GONE);
                                                        left_indicator_4.setVisibility(View.GONE);
                                                        left_indicator_5.setVisibility(View.GONE);
                                                    }else if(pitchInHz > previousFrecuency + 2*distance){
                                                        left_indicator_1.setVisibility(View.VISIBLE);
                                                        left_indicator_2.setVisibility(View.VISIBLE);
                                                        left_indicator_3.setVisibility(View.GONE);
                                                        left_indicator_4.setVisibility(View.GONE);
                                                        left_indicator_5.setVisibility(View.GONE);
                                                    }else if(pitchInHz > previousFrecuency + 3*distance){
                                                        left_indicator_1.setVisibility(View.VISIBLE);
                                                        left_indicator_2.setVisibility(View.VISIBLE);
                                                        left_indicator_3.setVisibility(View.VISIBLE);
                                                        left_indicator_4.setVisibility(View.GONE);
                                                        left_indicator_5.setVisibility(View.GONE);
                                                    }else if(pitchInHz > previousFrecuency + 4*distance){
                                                        left_indicator_1.setVisibility(View.VISIBLE);
                                                        left_indicator_2.setVisibility(View.VISIBLE);
                                                        left_indicator_3.setVisibility(View.VISIBLE);
                                                        left_indicator_4.setVisibility(View.VISIBLE);
                                                        left_indicator_5.setVisibility(View.GONE);
                                                    }else if(pitchInHz > previousFrecuency + 5*distance){
                                                        left_indicator_1.setVisibility(View.VISIBLE);
                                                        left_indicator_2.setVisibility(View.VISIBLE);
                                                        left_indicator_3.setVisibility(View.VISIBLE);
                                                        left_indicator_4.setVisibility(View.VISIBLE);
                                                        left_indicator_5.setVisibility(View.VISIBLE);
                                                    }else{
                                                        textNota.setBackground(getDrawable(R.drawable.round_background_green));
                                                    }
                                                }
                                                break;
                                            }
                                        }
                                    }
                                }
                            }else{
                                count = 0;
                            }

                            Log.e("FRECUENCY", "" + pitchInHz);

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
    }

    @Override
    protected void onPause() {
        super.onPause();

        if(thread != null){
            thread.interrupt();
            thread = null;
        }

        audioInputStream.stop();
        audioInputStream.release();
    }
}