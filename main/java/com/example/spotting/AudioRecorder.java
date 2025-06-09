package com.example.spotting;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioRecorder {
    private static final String TAG = "AudioRecorder";

    // Configurazioni audio per il modello speech_commands.tflite aggiornato
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    // NUOVO: Buffer size aggiornato per 44032 campioni (~2.75 secondi)
    private static final int BUFFER_SIZE_IN_SAMPLES = 44032;
    private static final int MIN_BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private ExecutorService executorService;
    private AudioRecorderListener listener;

    // Buffer circolare per la gestione dell'audio continuo
    private short[] audioBuffer;
    private int bufferPosition = 0;
    private boolean bufferFull = false; // Flag per tracciare se il buffer è stato riempito almeno una volta

    public interface AudioRecorderListener {
        void onAudioDataReceived(short[] audioData);
        void onSilenceDetected();
        void onSpeechDetected();
        void onError(String error);
        void onRecordingStopped();
    }

    public AudioRecorder(AudioRecorderListener listener) {
        this.listener = listener;
        this.executorService = Executors.newSingleThreadExecutor();
        this.audioBuffer = new short[BUFFER_SIZE_IN_SAMPLES];
        initAudioRecord();
    }

    private void initAudioRecord() {
        try {
            // Buffer più grande per gestire 44032 campioni
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

            Log.d(TAG, "AudioRecord inizializzato - Sample Rate: " + SAMPLE_RATE + "Hz");
            Log.d(TAG, "Buffer size: " + BUFFER_SIZE_IN_SAMPLES + " campioni (" +
                    (BUFFER_SIZE_IN_SAMPLES / (float) SAMPLE_RATE) + " secondi)");

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
            bufferPosition = 0;
            bufferFull = false; // Reset del flag

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

            if (listener != null) {
                listener.onRecordingStopped();
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
        short[] readBuffer = new short[1024]; // Buffer di lettura più piccolo per lettura continua

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
        for (int i = 0; i < length; i++) {
            audioBuffer[bufferPosition] = newData[i];
            bufferPosition++;

            // Quando il buffer è pieno
            if (bufferPosition >= BUFFER_SIZE_IN_SAMPLES) {
                bufferFull = true;
                bufferPosition = 0; // Ricomincia da capo (buffer circolare)

                // Crea una copia del buffer per l'elaborazione
                short[] bufferCopy = new short[BUFFER_SIZE_IN_SAMPLES];
                System.arraycopy(audioBuffer, 0, bufferCopy, 0, BUFFER_SIZE_IN_SAMPLES);

                // Rileva speech/silenzio
                detectSpeechOrSilence(bufferCopy);

                // Invia i dati al listener
                if (listener != null) {
                    listener.onAudioDataReceived(bufferCopy);
                }

                Log.v(TAG, "Buffer completo inviato: " + BUFFER_SIZE_IN_SAMPLES + " campioni");
            }
        }

        // Log del progresso del riempimento del buffer (solo per debug)
        if (!bufferFull && bufferPosition % 8000 == 0) { // Log ogni mezzo secondo circa
            float progress = (bufferPosition / (float) BUFFER_SIZE_IN_SAMPLES) * 100;
            Log.v(TAG, "Buffer riempimento: " + String.format("%.1f%%", progress) +
                    " (" + bufferPosition + "/" + BUFFER_SIZE_IN_SAMPLES + ")");
        }
    }

    private void detectSpeechOrSilence(short[] audioData) {
        long energy = 0;
        for (short sample : audioData) {
            energy += (long) sample * sample;
        }

        double rms = Math.sqrt((double) energy / audioData.length);

        // Soglia adattata per il buffer più lungo
        double speechThreshold = 500.0; // Ridotta leggermente per buffer più lungo

        if (listener != null) {
            if (rms > speechThreshold) {
                listener.onSpeechDetected();
                Log.v(TAG, "Speech detected - RMS: " + String.format("%.1f", rms));
            } else {
                listener.onSilenceDetected();
                Log.v(TAG, "Silence detected - RMS: " + String.format("%.1f", rms));
            }
        }
    }

    /**
     * Forza l'invio del buffer corrente anche se non completamente pieno
     * Utile quando si ferma la registrazione
     */
    public void flushBuffer() {
        if (bufferPosition > 0) {
            // Crea un buffer della dimensione corretta con padding di zeri
            short[] paddedBuffer = new short[BUFFER_SIZE_IN_SAMPLES];
            System.arraycopy(audioBuffer, 0, paddedBuffer, 0, bufferPosition);
            // Il resto rimane a zero (padding automatico)

            Log.d(TAG, "Buffer flush: " + bufferPosition + " campioni + " +
                    (BUFFER_SIZE_IN_SAMPLES - bufferPosition) + " zeri di padding");

            if (listener != null) {
                listener.onAudioDataReceived(paddedBuffer);
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

    // Metodi getter
    public int getSampleRate() {
        return SAMPLE_RATE;
    }

    public int getBufferSizeInSamples() {
        return BUFFER_SIZE_IN_SAMPLES;
    }

    public boolean isRecording() {
        return isRecording;
    }

    public boolean isBufferFull() {
        return bufferFull;
    }

    public int getCurrentBufferPosition() {
        return bufferPosition;
    }

    public float getBufferDurationSeconds() {
        return BUFFER_SIZE_IN_SAMPLES / (float) SAMPLE_RATE;
    }
}