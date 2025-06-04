package com.example.spotting;

public class AudioPreprocessor {

    // Configurazioni per il preprocessing audio compatibili con speech_commands.tflite
    private static final int SAMPLE_RATE = ModelConfig.SAMPLE_RATE;
    private static final int WINDOW_SIZE_SAMPLES = ModelConfig.AUDIO_LENGTH_SAMPLES;
    private static final int N_MFCC = ModelConfig.N_MFCC;
    private static final int N_FFT = ModelConfig.N_FFT;
    private static final int HOP_LENGTH = ModelConfig.HOP_LENGTH;
    private static final int N_MELS = ModelConfig.N_MELS;

    // Filtri mel precomputati (semplificazione)
    private double[][] melFilters;
    private double[] window;

    public AudioPreprocessor() {
        initializeMelFilters();
        initializeWindow();
    }

    public float[] preprocessAudio(short[] rawAudio) {
        // Step 1: Normalizzazione
        float[] normalizedAudio = normalizeAudio(rawAudio);

        // Step 2: Padding/Trimming a dimensione fissa
        float[] fixedSizeAudio = resizeAudio(normalizedAudio, WINDOW_SIZE_SAMPLES);

        // Step 3: Pre-emphasis (opzionale, migliora le alte frequenze)
        float[] preEmphasizedAudio = preEmphasis(fixedSizeAudio, ModelConfig.PRE_EMPHASIS_COEFF);

        // Step 4: Calcolo MFCC
        float[] mfccFeatures = computeMFCC(preEmphasizedAudio);

        return mfccFeatures;
    }

    private float[] normalizeAudio(short[] rawAudio) {
        float[] normalized = new float[rawAudio.length];

        // Trova il valore massimo assoluto
        float maxValue = 0;
        for (short sample : rawAudio) {
            maxValue = Math.max(maxValue, Math.abs(sample));
        }

        // Normalizza nell'intervallo [-1, 1]
        if (maxValue > 0) {
            for (int i = 0; i < rawAudio.length; i++) {
                normalized[i] = rawAudio[i] / maxValue;
            }
        } else {
            // Se tutto è zero, lascia così
            System.arraycopy(rawAudio, 0, normalized, 0, rawAudio.length);
        }

        return normalized;
    }

    private float[] resizeAudio(float[] audio, int targetSize) {
        float[] resized = new float[targetSize];

        if (audio.length >= targetSize) {
            // Trim: prendi i primi targetSize campioni
            System.arraycopy(audio, 0, resized, 0, targetSize);
        } else {
            // Pad: copia l'audio e riempi il resto con zeri
            System.arraycopy(audio, 0, resized, 0, audio.length);
            // Il resto rimane a zero (comportamento di default per array float)
        }

        return resized;
    }

    private float[] preEmphasis(float[] audio, float alpha) {
        float[] preEmphasized = new float[audio.length];
        preEmphasized[0] = audio[0];

        for (int i = 1; i < audio.length; i++) {
            preEmphasized[i] = audio[i] - alpha * audio[i - 1];
        }

        return preEmphasized;
    }

    private float[] computeMFCC(float[] audio) {
        // Step 1: Short-Time Fourier Transform (STFT)
        float[][] stft = computeSTFT(audio);

        // Step 2: Power Spectrum
        float[][] powerSpectrum = computePowerSpectrum(stft);

        // Step 3: Mel Filter Bank
        float[][] melSpectrum = applyMelFilters(powerSpectrum);

        // Step 4: Logarithm
        float[][] logMelSpectrum = applyLogarithm(melSpectrum);

        // Step 5: Discrete Cosine Transform (DCT)
        float[][] mfccMatrix = applyDCT(logMelSpectrum);

        // Step 6: Flatten per TensorFlow Lite
        return flattenMFCC(mfccMatrix);
    }

    private float[][] computeSTFT(float[] audio) {
        int numFrames = (audio.length - N_FFT) / HOP_LENGTH + 1;
        float[][] stft = new float[numFrames][N_FFT];

        for (int frame = 0; frame < numFrames; frame++) {
            int startIdx = frame * HOP_LENGTH;

            // Applica finestra
            for (int i = 0; i < N_FFT && (startIdx + i) < audio.length; i++) {
                stft[frame][i] = audio[startIdx + i] * (float) window[i];
            }

            // FFT semplificata (solo magnitudine)
            stft[frame] = computeFFTMagnitude(stft[frame]);
        }

        return stft;
    }

    private float[] computeFFTMagnitude(float[] signal) {
        // Implementazione semplificata della FFT
        // In una implementazione reale dovresti usare una libreria FFT ottimizzata
        float[] magnitude = new float[N_FFT / 2];

        for (int k = 0; k < magnitude.length; k++) {
            float real = 0, imag = 0;

            for (int n = 0; n < signal.length; n++) {
                double angle = -2.0 * Math.PI * k * n / N_FFT;
                real += signal[n] * Math.cos(angle);
                imag += signal[n] * Math.sin(angle);
            }

            magnitude[k] = (float) Math.sqrt(real * real + imag * imag);
        }

        return magnitude;
    }

    private float[][] computePowerSpectrum(float[][] stft) {
        float[][] power = new float[stft.length][stft[0].length];

        for (int i = 0; i < stft.length; i++) {
            for (int j = 0; j < stft[i].length; j++) {
                power[i][j] = stft[i][j] * stft[i][j];
            }
        }

        return power;
    }

    private float[][] applyMelFilters(float[][] powerSpectrum) {
        float[][] melSpectrum = new float[powerSpectrum.length][N_MELS];

        for (int frame = 0; frame < powerSpectrum.length; frame++) {
            for (int mel = 0; mel < N_MELS; mel++) {
                float sum = 0;
                for (int freq = 0; freq < powerSpectrum[frame].length && freq < melFilters[mel].length; freq++) {
                    sum += powerSpectrum[frame][freq] * melFilters[mel][freq];
                }
                melSpectrum[frame][mel] = sum;
            }
        }

        return melSpectrum;
    }

    private float[][] applyLogarithm(float[][] melSpectrum) {
        float[][] logMel = new float[melSpectrum.length][melSpectrum[0].length];

        for (int i = 0; i < melSpectrum.length; i++) {
            for (int j = 0; j < melSpectrum[i].length; j++) {
                // Aggiungi un piccolo valore per evitare log(0)
                logMel[i][j] = (float) Math.log(melSpectrum[i][j] + 1e-8);
            }
        }

        return logMel;
    }

    private float[][] applyDCT(float[][] logMelSpectrum) {
        float[][] mfcc = new float[logMelSpectrum.length][N_MFCC];

        for (int frame = 0; frame < logMelSpectrum.length; frame++) {
            for (int cep = 0; cep < N_MFCC; cep++) {
                float sum = 0;
                for (int mel = 0; mel < logMelSpectrum[frame].length; mel++) {
                    sum += logMelSpectrum[frame][mel] *
                            Math.cos(Math.PI * cep * (2 * mel + 1) / (2.0 * logMelSpectrum[frame].length));
                }
                mfcc[frame][cep] = sum;
            }
        }

        return mfcc;
    }

    private float[] flattenMFCC(float[][] mfccMatrix) {
        // Appiattisce la matrice MFCC in un array 1D per TensorFlow Lite
        int totalSize = mfccMatrix.length * mfccMatrix[0].length;
        float[] flattened = new float[totalSize];

        int index = 0;
        for (float[] frame : mfccMatrix) {
            for (float coefficient : frame) {
                flattened[index++] = coefficient;
            }
        }

        return flattened;
    }

    private void initializeMelFilters() {
        // Inizializzazione semplificata dei filtri mel
        // In una implementazione reale dovresti calcolare i filtri triangolari mel
        melFilters = new double[N_MELS][N_FFT / 2];

        for (int i = 0; i < N_MELS; i++) {
            for (int j = 0; j < N_FFT / 2; j++) {
                // Filtro triangolare semplificato
                double center = (double) i * (N_FFT / 2) / N_MELS;
                double width = (double) (N_FFT / 2) / N_MELS;

                if (Math.abs(j - center) < width) {
                    melFilters[i][j] = 1.0 - Math.abs(j - center) / width;
                } else {
                    melFilters[i][j] = 0.0;
                }
            }
        }
    }

    private void initializeWindow() {
        // Finestra di Hamming
        window = new double[N_FFT];
        for (int i = 0; i < N_FFT; i++) {
            window[i] = 0.54 - 0.46 * Math.cos(2.0 * Math.PI * i / (N_FFT - 1));
        }
    }

    // Metodi getter per informazioni sul preprocessing
    public int getExpectedInputSize() {
        return WINDOW_SIZE_SAMPLES;
    }

    public int getOutputFeatureSize() {
        int numFrames = (WINDOW_SIZE_SAMPLES - N_FFT) / HOP_LENGTH + 1;
        return numFrames * N_MFCC;
    }
}