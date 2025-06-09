package com.example.spotting;

import android.util.Log;

public class AudioPreprocessor {
    private static final String TAG = "AudioPreprocessor";

    // Configurazioni per il modello speech_commands.tflite aggiornato
    private static final int EXPECTED_SAMPLE_RATE = 16000;
    private static final int EXPECTED_SAMPLES = 44032; // Nuovo: il modello si aspetta 44032 campioni

    // Parametri per la normalizzazione
    private static final float NORMALIZATION_FACTOR = 32768.0f; // Per convertire da int16 a float [-1, 1]

    public AudioPreprocessor() {
        Log.d(TAG, "AudioPreprocessor inizializzato per modello speech_commands.tflite");
        Log.d(TAG, "Input atteso: " + EXPECTED_SAMPLES + " campioni a " + EXPECTED_SAMPLE_RATE + "Hz");
        Log.d(TAG, "Durata audio: " + (EXPECTED_SAMPLES / (float) EXPECTED_SAMPLE_RATE) + " secondi");
    }

    /**
     * Preprocessa l'audio per il modello Google speech_commands.tflite aggiornato
     * Il modello si aspetta:
     * - 44032 campioni (~2.75 secondi a 16kHz)
     * - Valori float32 normalizzati tra -1 e 1
     * - Pipeline di elaborazione incorporata nel modello (no preprocessing manuale necessario)
     */
    public float[] preprocessAudio(short[] audioData) {
        if (audioData == null) {
            Log.e(TAG, "Audio data è null");
            return null;
        }

        // Verifica e ridimensiona per ottenere esattamente 44032 campioni
        if (audioData.length != EXPECTED_SAMPLES) {
            Log.w(TAG, "Lunghezza audio non corretta: " + audioData.length +
                    " (attesi " + EXPECTED_SAMPLES + ")");
            audioData = resizeAudio(audioData, EXPECTED_SAMPLES);
        }

        // Converte da short[] a float[] normalizzato
        // Il modello ha una pipeline incorporata, quindi serve solo normalizzazione base
        float[] normalizedAudio = normalizeAudioBasic(audioData);

        Log.d(TAG, "Audio preprocessato: " + normalizedAudio.length + " campioni float32");
        logAudioStats(normalizedAudio);

        return normalizedAudio;
    }

    /**
     * Normalizzazione base da short (int16) a float32 [-1, 1]
     * Il modello si occupa internamente del resto del preprocessing
     */
    private float[] normalizeAudioBasic(short[] audioData) {
        float[] normalized = new float[audioData.length];

        for (int i = 0; i < audioData.length; i++) {
            // Converte da int16 [-32768, 32767] a float32 [-1, 1]
            normalized[i] = audioData[i] / NORMALIZATION_FACTOR;
        }

        return normalized;
    }

    /**
     * Ridimensiona l'audio alla lunghezza target (44032 campioni)
     */
    private short[] resizeAudio(short[] audioData, int targetLength) {
        if (audioData.length == targetLength) {
            return audioData;
        }

        short[] resized = new short[targetLength];

        if (audioData.length < targetLength) {
            // Pad con zeri se troppo corto
            System.arraycopy(audioData, 0, resized, 0, audioData.length);
            // I rimanenti elementi sono già 0 per default (zero padding)
            Log.d(TAG, "Audio padding: " + audioData.length + " -> " + targetLength +
                    " (aggiunti " + (targetLength - audioData.length) + " zeri)");
        } else {
            // Tronca se troppo lungo (prende gli ultimi campioni per catturare la fine del comando)
            int startIndex = audioData.length - targetLength;
            System.arraycopy(audioData, startIndex, resized, 0, targetLength);
            Log.d(TAG, "Audio troncato: " + audioData.length + " -> " + targetLength);
        }

        return resized;
    }

    /**
     * Calcola e logga le statistiche dell'audio per debug
     */
    private void logAudioStats(float[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return;
        }

        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;
        float sum = 0;
        float energy = 0;

        for (float sample : audioData) {
            min = Math.min(min, sample);
            max = Math.max(max, sample);
            sum += sample;
            energy += sample * sample;
        }

        float mean = sum / audioData.length;
        float rms = (float) Math.sqrt(energy / audioData.length);

        Log.d(TAG, String.format("Audio stats - Min: %.4f, Max: %.4f, Mean: %.4f, RMS: %.4f",
                min, max, mean, rms));

        // Verifica che i valori siano nel range corretto
        if (min < -1.0f || max > 1.0f) {
            Log.w(TAG, "⚠️ Valori audio fuori dal range [-1, 1]: min=" + min + ", max=" + max);
        }
    }

    /**
     * Verifica se l'audio contiene parlato o solo silenzio
     */
    public boolean containsSpeech(float[] audioData, float threshold) {
        if (audioData == null || audioData.length == 0) {
            return false;
        }

        // Calcola RMS energy
        float energy = 0;
        for (float sample : audioData) {
            energy += sample * sample;
        }
        float rms = (float) Math.sqrt(energy / audioData.length);

        boolean hasSpeech = rms > threshold;
        Log.v(TAG, "Speech detection - RMS: " + String.format("%.4f", rms) +
                ", Threshold: " + threshold + ", Has speech: " + hasSpeech);

        return hasSpeech;
    }

    /**
     * Converte audio stereo in mono (se necessario)
     */
    public short[] stereoToMono(short[] stereoData) {
        if (stereoData == null || stereoData.length % 2 != 0) {
            Log.w(TAG, "Dati stereo non validi");
            return stereoData;
        }

        short[] monoData = new short[stereoData.length / 2];

        for (int i = 0; i < monoData.length; i++) {
            // Media dei due canali stereo
            int left = stereoData[i * 2];
            int right = stereoData[i * 2 + 1];
            monoData[i] = (short) ((left + right) / 2);
        }

        Log.d(TAG, "Convertito da stereo a mono: " + stereoData.length + " -> " + monoData.length + " campioni");
        return monoData;
    }

    // Getters
    public int getExpectedSamples() {
        return EXPECTED_SAMPLES;
    }

    public int getExpectedSampleRate() {
        return EXPECTED_SAMPLE_RATE;
    }

    public float getExpectedDurationSeconds() {
        return EXPECTED_SAMPLES / (float) EXPECTED_SAMPLE_RATE;
    }
}