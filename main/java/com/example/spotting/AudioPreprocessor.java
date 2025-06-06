package com.example.spotting;

import android.util.Log;

public class AudioPreprocessor {
    private static final String TAG = "AudioPreprocessor";

    // Configurazioni per il modello speech_commands.tflite
    private static final int EXPECTED_SAMPLE_RATE = 16000;
    private static final int EXPECTED_SAMPLES = 16000; // 1 secondo a 16kHz

    // Parametri per la normalizzazione
    private static final float NORMALIZATION_FACTOR = 32768.0f; // Per convertire da int16 a float [-1, 1]

    public AudioPreprocessor() {
        Log.d(TAG, "AudioPreprocessor inizializzato per modello speech_commands.tflite");
        Log.d(TAG, "Input atteso: " + EXPECTED_SAMPLES + " campioni a " + EXPECTED_SAMPLE_RATE + "Hz");
    }

    /**
     * Preprocessa l'audio per il modello Google speech_commands.tflite
     * Il modello si aspetta:
     * - 16000 campioni (1 secondo a 16kHz)
     * - Valori float normalizzati tra -1 e 1
     * - NON MFCC, ma audio raw normalizzato
     */
    public float[] preprocessAudio(short[] audioData) {
        if (audioData == null) {
            Log.e(TAG, "Audio data è null");
            return null;
        }

        // Verifica che abbiamo esattamente 16000 campioni
        if (audioData.length != EXPECTED_SAMPLES) {
            Log.w(TAG, "Lunghezza audio non corretta: " + audioData.length +
                    " (attesi " + EXPECTED_SAMPLES + ")");

            // Ridimensiona se necessario
            audioData = resizeAudio(audioData, EXPECTED_SAMPLES);
        }

        // Converte da short[] a float[] normalizzato
        float[] normalizedAudio = normalizeAudio(audioData);

        // Applica un filtro passa-alto opzionale per rimuovere DC offset
        normalizedAudio = applyHighPassFilter(normalizedAudio);

        // Applica pre-emphasis (opzionale, migliora il riconoscimento vocale)
        normalizedAudio = applyPreEmphasis(normalizedAudio, 0.97f);

        Log.d(TAG, "Audio preprocessato: " + normalizedAudio.length + " campioni float");
        logAudioStats(normalizedAudio);

        return normalizedAudio;
    }

    /**
     * Normalizza l'audio da short (int16) a float [-1, 1]
     */
    private float[] normalizeAudio(short[] audioData) {
        float[] normalized = new float[audioData.length];

        for (int i = 0; i < audioData.length; i++) {
            // Converte da int16 [-32768, 32767] a float [-1, 1]
            normalized[i] = audioData[i] / NORMALIZATION_FACTOR;
        }

        return normalized;
    }

    /**
     * Ridimensiona l'audio alla lunghezza target
     */
    private short[] resizeAudio(short[] audioData, int targetLength) {
        if (audioData.length == targetLength) {
            return audioData;
        }

        short[] resized = new short[targetLength];

        if (audioData.length < targetLength) {
            // Pad con zeri se troppo corto
            System.arraycopy(audioData, 0, resized, 0, audioData.length);
            // I rimanenti elementi sono già 0 per default
            Log.d(TAG, "Audio padding: " + audioData.length + " -> " + targetLength);
        } else {
            // Tronca se troppo lungo (prende gli ultimi campioni)
            int startIndex = audioData.length - targetLength;
            System.arraycopy(audioData, startIndex, resized, 0, targetLength);
            Log.d(TAG, "Audio troncato: " + audioData.length + " -> " + targetLength);
        }

        return resized;
    }

    /**
     * Applica un filtro passa-alto semplice per rimuovere DC offset
     */
    private float[] applyHighPassFilter(float[] audioData) {
        if (audioData.length < 2) {
            return audioData;
        }

        float[] filtered = new float[audioData.length];
        filtered[0] = audioData[0];

        // Filtro passa-alto semplice: y[n] = x[n] - x[n-1] + 0.95 * y[n-1]
        float alpha = 0.95f;
        for (int i = 1; i < audioData.length; i++) {
            filtered[i] = audioData[i] - audioData[i-1] + alpha * filtered[i-1];
        }

        return filtered;
    }

    /**
     * Applica pre-emphasis per migliorare il riconoscimento vocale
     */
    private float[] applyPreEmphasis(float[] audioData, float coefficient) {
        if (audioData.length < 2) {
            return audioData;
        }

        float[] emphasized = new float[audioData.length];
        emphasized[0] = audioData[0];

        // Pre-emphasis: y[n] = x[n] - coefficient * x[n-1]
        for (int i = 1; i < audioData.length; i++) {
            emphasized[i] = audioData[i] - coefficient * audioData[i-1];
        }

        return emphasized;
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

        return rms > threshold;
    }

    /**
     * Applica windowing (finestra di Hamming) se necessario
     */
    public float[] applyHammingWindow(float[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return audioData;
        }

        float[] windowed = new float[audioData.length];
        int N = audioData.length;

        for (int i = 0; i < N; i++) {
            float window = (float) (0.54 - 0.46 * Math.cos(2 * Math.PI * i / (N - 1)));
            windowed[i] = audioData[i] * window;
        }

        return windowed;
    }
}