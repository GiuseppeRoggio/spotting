package com.example.spotting;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioRecorder {
    private static final String TAG = "AudioRecorder";

    // Configurazioni audio per il modello speech_commands.tflite
    private static final int SAMPLE_RATE = 16000; // 16kHz richiesto dal modello
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    // Buffer per 1 secondo di audio (come richiesto dal modello)
    private static final int BUFFER_SIZE_IN_SAMPLES = SAMPLE_RATE; // 16000 campioni = 1 secondo
    private static final int MIN_BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

    // Sliding window per cattura continua
    private static final int OVERLAP_SIZE = SAMPLE_RATE / 4; // Overlap di 0.25 secondi

    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private ExecutorService executorService;
    private AudioRecorderListener listener;

    // Buffer circolare per la gestione dell'audio continuo
    private short[] audioBuffer;
    private int bufferPosition = 0;

    public interface AudioRecorderListener {
        void onAudioDataReceived(short[] audioData);
        void onSilenceDetected();
        void onSpeechDetected();
        void onError(String error);
    }

    public AudioRecorder(AudioRecorderListener listener) {
        this.listener = listener;
        this.executorService = Executors.newSingleThreadExecutor();
        this.audioBuffer = new short[BUFFER_SIZE_IN_SAMPLES];

        initAudioRecord();
    }

    private void initAudioRecord() {
        try {
            // Calcola la dimensione del buffer ottimale
            int bufferSize = Math.max(MIN_BUFFER_SIZE, BUFFER_SIZE_IN_SAMPLES * 2);

            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new RuntimeException("AudioRecord non inizializzato correttamente");
            }

            Log.d(TAG, "AudioRecord inizializzato - Sample Rate: " + SAMPLE_RATE + "Hz, Buffer Size: " + bufferSize);

        } catch (Exception e) {
            Log.e(TAG, "Errore nell'inizializzazione di AudioRecord", e);
            if (listener != null) {
                listener.onError("Errore inizializzazione AudioRecord: " + e.getMessage());
            }
        }
    }

    public void startRecording() {
        if (audioRecord == null) {
            if (listener != null) {
                listener.onError("AudioRecord non inizializzato");
            }
            return;
        }

        if (isRecording) {
            Log.w(TAG, "Registrazione già in corso");
            return;
        }

        try {
            audioRecord.startRecording();
            isRecording = true;

            // Reset del buffer
            bufferPosition = 0;

            // Avvia il thread di registrazione
            executorService.submit(this::recordingLoop);

            Log.d(TAG, "Registrazione avviata");

        } catch (Exception e) {
            Log.e(TAG, "Errore nell'avvio della registrazione", e);
            if (listener != null) {
                listener.onError("Errore avvio registrazione: " + e.getMessage());
            }
        }
    }

    public void stopRecording() {
        if (!isRecording) {
            return;
        }

        isRecording = false;

        try {
            if (audioRecord != null) {
                audioRecord.stop();
            }
            Log.d(TAG, "Registrazione fermata");
        } catch (Exception e) {
            Log.e(TAG, "Errore nella chiusura della registrazione", e);
            if (listener != null) {
                listener.onError("Errore chiusura registrazione: " + e.getMessage());
            }
        }
    }

    private void recordingLoop() {
        short[] readBuffer = new short[1024]; // Buffer di lettura piccolo per bassa latenza

        while (isRecording) {
            try {
                int bytesRead = audioRecord.read(readBuffer, 0, readBuffer.length);

                if (bytesRead > 0) {
                    processAudioData(readBuffer, bytesRead);
                } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                    if (listener != null) {
                        listener.onError("Operazione AudioRecord non valida");
                    }
                    break;
                } else if (bytesRead == AudioRecord.ERROR_BAD_VALUE) {
                    if (listener != null) {
                        listener.onError("Valore AudioRecord non valido");
                    }
                    break;
                }

            } catch (Exception e) {
                Log.e(TAG, "Errore nel loop di registrazione", e);
                if (listener != null) {
                    listener.onError("Errore nel loop di registrazione: " + e.getMessage());
                }
                break;
            }
        }
    }

    private void processAudioData(short[] newData, int length) {
        // Aggiungi i nuovi dati al buffer circolare
        for (int i = 0; i < length; i++) {
            audioBuffer[bufferPosition] = newData[i];
            bufferPosition = (bufferPosition + 1) % BUFFER_SIZE_IN_SAMPLES;

            // Quando il buffer è pieno, invia i dati per la classificazione
            if (bufferPosition == 0) {
                // Crea una copia del buffer per evitare modifiche durante la classificazione
                short[] bufferCopy = new short[BUFFER_SIZE_IN_SAMPLES];
                System.arraycopy(audioBuffer, 0, bufferCopy, 0, BUFFER_SIZE_IN_SAMPLES);

                // Rileva se c'è parlato o silenzio
                detectSpeechOrSilence(bufferCopy);

                // Invia i dati al listener
                if (listener != null) {
                    listener.onAudioDataReceived(bufferCopy);
                }
            }
        }
    }

    private void detectSpeechOrSilence(short[] audioData) {
        // Calcola il livello medio di energia
        long energy = 0;
        for (short sample : audioData) {
            energy += (long) sample * sample;
        }

        double rms = Math.sqrt((double) energy / audioData.length);

        // Soglia per distinguere tra parlato e silenzio
        // Valori tipici: silenzio < 500, parlato > 1000
        double speechThreshold = 800.0;

        if (listener != null) {
            if (rms > speechThreshold) {
                listener.onSpeechDetected();
            } else {
                listener.onSilenceDetected();
            }
        }
    }

    public void release() {
        stopRecording();

        if (executorService != null) {
            executorService.shutdownNow();
        }

        if (audioRecord != null) {
            try {
                audioRecord.release();
                audioRecord = null;
            } catch (Exception e) {
                Log.e(TAG, "Errore nel rilascio di AudioRecord", e);
            }
        }

        Log.d(TAG, "AudioRecorder rilasciato");
    }

    // Metodi getter per informazioni
    public int getSampleRate() {
        return SAMPLE_RATE;
    }

    public int getBufferSizeInSamples() {
        return BUFFER_SIZE_IN_SAMPLES;
    }

    public boolean isRecording() {
        return isRecording;
    }
}