package com.example.spotting;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioRecorder {

    public interface AudioRecorderListener {
        void onAudioDataReceived(short[] audioData);
        void onSilenceDetected();
        void onSpeechDetected();
        void onError(String error);
    }

    // Configurazione audio compatibile con il modello TensorFlow Lite Speech Commands
    private static final int SAMPLE_RATE = 16000; // 16kHz
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE_FACTOR = 2;

    // Configurazione per la detection del speech - ottimizzata per speech_commands.tflite
    private static final int WINDOW_SIZE_MS = ModelConfig.AUDIO_LENGTH_MS;
    private static final int WINDOW_SIZE_SAMPLES = ModelConfig.AUDIO_LENGTH_SAMPLES;
    private static final double SILENCE_THRESHOLD = ModelConfig.SILENCE_THRESHOLD;
    private static final int SILENCE_DURATION_MS = ModelConfig.SILENCE_DURATION_MS;

    private AudioRecord audioRecord;
    private ExecutorService executorService;
    private AudioRecorderListener listener;

    private boolean isRecording = false;
    private int bufferSize;

    // Stati per la detection del speech
    private boolean wasSpeaking = false;
    private long lastSpeechTime = 0;

    public AudioRecorder(AudioRecorderListener listener) {
        this.listener = listener;
        this.executorService = Executors.newSingleThreadExecutor();
        initAudioRecord();
    }

    private void initAudioRecord() {
        try {
            bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            bufferSize = Math.max(bufferSize, WINDOW_SIZE_SAMPLES * 2) * BUFFER_SIZE_FACTOR;

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

        } catch (Exception e) {
            if (listener != null) {
                listener.onError("Errore inizializzazione AudioRecord: " + e.getMessage());
            }
        }
    }

    public void startRecording() {
        if (audioRecord == null || audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            if (listener != null) {
                listener.onError("AudioRecord non inizializzato");
            }
            return;
        }

        if (isRecording) {
            return;
        }

        try {
            audioRecord.startRecording();
            isRecording = true;

            executorService.execute(this::recordingLoop);

        } catch (Exception e) {
            if (listener != null) {
                listener.onError("Errore avvio registrazione: " + e.getMessage());
            }
        }
    }

    public void stopRecording() {
        isRecording = false;

        if (audioRecord != null && audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
            try {
                audioRecord.stop();
            } catch (Exception e) {
                if (listener != null) {
                    listener.onError("Errore stop registrazione: " + e.getMessage());
                }
            }
        }
    }

    private void recordingLoop() {
        short[] audioBuffer = new short[WINDOW_SIZE_SAMPLES];

        while (isRecording) {
            try {
                int bytesRead = audioRecord.read(audioBuffer, 0, audioBuffer.length);

                if (bytesRead > 0) {
                    // Analizza il volume per rilevare speech/silenzio
                    double rms = calculateRMS(audioBuffer, bytesRead);
                    boolean isSpeaking = rms > SILENCE_THRESHOLD;

                    long currentTime = System.currentTimeMillis();

                    if (isSpeaking) {
                        lastSpeechTime = currentTime;
                        if (!wasSpeaking) {
                            wasSpeaking = true;
                            if (listener != null) {
                                listener.onSpeechDetected();
                            }
                        }
                    } else {
                        if (wasSpeaking && (currentTime - lastSpeechTime > SILENCE_DURATION_MS)) {
                            wasSpeaking = false;
                            if (listener != null) {
                                listener.onSilenceDetected();
                            }
                        }
                    }

                    // Invia i dati audio per la classificazione solo se c'è speech
                    if (isSpeaking && listener != null) {
                        // Crea una copia dei dati per evitare problemi di concorrenza
                        short[] audioData = new short[bytesRead];
                        System.arraycopy(audioBuffer, 0, audioData, 0, bytesRead);
                        listener.onAudioDataReceived(audioData);
                    }

                } else {
                    if (listener != null) {
                        listener.onError("Errore lettura audio: bytesRead = " + bytesRead);
                    }
                    break;
                }

            } catch (Exception e) {
                if (listener != null) {
                    listener.onError("Errore nel loop di registrazione: " + e.getMessage());
                }
                break;
            }
        }
    }

    private double calculateRMS(short[] audioData, int length) {
        double sum = 0;
        for (int i = 0; i < length; i++) {
            sum += audioData[i] * audioData[i];
        }
        return Math.sqrt(sum / length);
    }

    public void release() {
        stopRecording();

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }

        if (audioRecord != null) {
            try {
                audioRecord.release();
            } catch (Exception e) {
                // Log dell'errore se necessario
            }
            audioRecord = null;
        }
    }

    // Metodi getter per informazioni sull'audio
    public int getSampleRate() {
        return SAMPLE_RATE;
    }

    public int getWindowSizeSamples() {
        return WINDOW_SIZE_SAMPLES;
    }

    public boolean isRecording() {
        return isRecording;
    }
}