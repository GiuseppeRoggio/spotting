package com.example.spotting;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class KeywordClassifier {
    private static final String TAG = "KeywordClassifier";
    private static final String MODEL_PATH = "speech_commands.tflite";

    // NUOVO: Configurazione aggiornata per il modello con input 44032
    private static final int EXPECTED_INPUT_SIZE = 44032;
    private static final float CONFIDENCE_THRESHOLD = 0.6f;

    private Interpreter tflite;
    private int inputSize;
    private int outputSize;
    private boolean isInitialized = false;

    // Labels per il modello Google Speech Commands v2
    private static final String[] LABELS = {
            "silence", "unknown", "yes", "no", "up", "down",
            "left", "right", "on", "off", "stop", "go"
    };

    public KeywordClassifier(Context context) {
        try {
            initializeModel(context);
            isInitialized = true;
            Log.d(TAG, "✅ KeywordClassifier inizializzato correttamente");
            Log.d(TAG, "Modello richiede: " + inputSize + " campioni in input");
        } catch (Exception e) {
            Log.e(TAG, "❌ Errore nell'inizializzazione del KeywordClassifier", e);
            isInitialized = false;
        }
    }

    private void initializeModel(Context context) throws Exception {
        MappedByteBuffer tfliteModel = loadModelFile(context);

        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(2);
        tflite = new Interpreter(tfliteModel, options);

        // Ottieni le dimensioni del modello
        int[] inputShape = tflite.getInputTensor(0).shape();
        int[] outputShape = tflite.getOutputTensor(0).shape();

        // Il modello dovrebbe avere shape [1, 44032]
        inputSize = inputShape.length >= 2 ? inputShape[1] : inputShape[0];
        outputSize = outputShape.length >= 2 ? outputShape[1] : outputShape[0];

        Log.d(TAG, "Input shape: " + java.util.Arrays.toString(inputShape));
        Log.d(TAG, "Output shape: " + java.util.Arrays.toString(outputShape));
        Log.d(TAG, "Modello configurato - Input: " + inputSize + ", Output: " + outputSize);

        // Verifica che le dimensioni siano quelle attese
        if (inputSize != EXPECTED_INPUT_SIZE) {
            Log.w(TAG, "⚠️ Dimensione input inaspettata: " + inputSize + " (atteso: " + EXPECTED_INPUT_SIZE + ")");
        }
    }

    private MappedByteBuffer loadModelFile(Context context) throws Exception {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_PATH);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        inputStream.close();
        return buffer;
    }

    /**
     * NUOVO: Classificazione semplificata - il modello riceve direttamente i 44032 campioni
     * Non serve più un buffer circolare complesso, ogni chiamata è indipendente
     */
    public String classify(float[] audioData) {
        if (!isInitialized || tflite == null || audioData == null) {
            Log.e(TAG, "❌ Classificatore non inizializzato o dati audio null");
            return null;
        }

        if (audioData.length != inputSize) {
            Log.e(TAG, "❌ Dimensione audio non corretta: " + audioData.length +
                    " (atteso: " + inputSize + ")");
            return null;
        }

        try {
            return performClassification(audioData);
        } catch (Exception e) {
            Log.e(TAG, "❌ Errore durante la classificazione", e);
            return null;
        }
    }

    private String performClassification(float[] audioData) {
        try {
            // Prepara input per TensorFlow Lite: shape [1, 44032]
            float[][] input = new float[1][inputSize];
            System.arraycopy(audioData, 0, input[0], 0, inputSize);

            // Prepara output
            float[][] output = new float[1][outputSize];

            // Esegui l'inferenza
            long startTime = System.currentTimeMillis();
            tflite.run(input, output);
            long inferenceTime = System.currentTimeMillis() - startTime;

            Log.v(TAG, "Inferenza completata in " + inferenceTime + "ms");

            return interpretOutput(output[0]);

        } catch (Exception e) {
            Log.e(TAG, "❌ Errore durante l'inferenza", e);
            return null;
        }
    }

    private String interpretOutput(float[] probabilities) {
        if (probabilities == null || probabilities.length == 0) {
            Log.w(TAG, "Output del modello vuoto");
            return null;
        }

        // Trova l'indice con la probabilità più alta
        int maxIndex = 0;
        float maxProb = probabilities[0];
        for (int i = 1; i < probabilities.length; i++) {
            if (probabilities[i] > maxProb) {
                maxProb = probabilities[i];
                maxIndex = i;
            }
        }

        float confidence = maxProb * 100f;

        // Log per debug delle probabilità principali
        StringBuilder probsLog = new StringBuilder("Top 3 probabilità: ");
        // Trova i top 3
        float[] tempProbs = probabilities.clone();
        for (int rank = 0; rank < Math.min(3, tempProbs.length); rank++) {
            int topIndex = 0;
            for (int i = 1; i < tempProbs.length; i++) {
                if (tempProbs[i] > tempProbs[topIndex]) {
                    topIndex = i;
                }
            }
            if (topIndex < LABELS.length) {
                probsLog.append(LABELS[topIndex]).append(": ")
                        .append(String.format("%.1f%%", tempProbs[topIndex] * 100)).append(" ");
            }
            tempProbs[topIndex] = -1; // Rimuovi per il prossimo giro
        }
        Log.v(TAG, probsLog.toString());

        // Verifica soglia di confidenza
        if (maxProb < CONFIDENCE_THRESHOLD) {
            Log.d(TAG, "Confidenza troppo bassa: " + String.format("%.1f%% (soglia: %.1f%%)",
                    confidence, CONFIDENCE_THRESHOLD * 100));
            return null;
        }

        // Restituisci il risultato se valido
        if (maxIndex < LABELS.length) {
            String label = LABELS[maxIndex];

            // Filtra silence e unknown con soglia più alta
            if ((label.equals("silence") || label.equals("unknown")) && confidence < 75f) {
                Log.d(TAG, "Filtrato " + label + " con confidenza bassa: " + String.format("%.1f%%", confidence));
                return null;
            }

            // Log del risultato finale
            Log.i(TAG, "🎯 COMANDO RICONOSCIUTO: " + label + " (" + String.format("%.1f%%)", confidence));
            return String.format("%s (%.1f%%)", label, confidence);
        }

        Log.w(TAG, "Indice label non valido: " + maxIndex);
        return null;
    }

    /**
     * Valida che i dati audio siano nel formato corretto
     */
    public boolean validateAudioData(float[] audioData) {
        if (audioData == null) {
            Log.e(TAG, "Audio data è null");
            return false;
        }

        if (audioData.length != inputSize) {
            Log.e(TAG, "Dimensione audio non corretta: " + audioData.length + " (atteso: " + inputSize + ")");
            return false;
        }

        // Verifica che i valori siano nel range [-1, 1]
        boolean hasValidRange = true;
        int outOfRangeCount = 0;
        for (float sample : audioData) {
            if (sample < -1.0f || sample > 1.0f) {
                outOfRangeCount++;
                hasValidRange = false;
            }
        }

        if (!hasValidRange) {
            Log.w(TAG, "⚠️ " + outOfRangeCount + " campioni fuori dal range [-1, 1]");
        }

        return true; // Ritorna true anche con warning per permettere l'elaborazione
    }

    /**
     * Calcola statistiche sull'audio per debug
     */
    public void logAudioStatistics(float[] audioData) {
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

        Log.d(TAG, String.format("Audio stats - Samples: %d, Min: %.4f, Max: %.4f, Mean: %.4f, RMS: %.4f",
                audioData.length, min, max, mean, rms));
    }

    // Metodi di utilità
    public boolean isInitialized() {
        return isInitialized;
    }

    public int getInputSize() {
        return inputSize;
    }

    public int getOutputSize() {
        return outputSize;
    }

    public int getExpectedInputSize() {
        return EXPECTED_INPUT_SIZE;
    }

    public String[] getLabels() {
        return LABELS.clone();
    }

    public float getConfidenceThreshold() {
        return CONFIDENCE_THRESHOLD;
    }

    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }

        isInitialized = false;
        Log.d(TAG, "KeywordClassifier chiuso");
    }
}