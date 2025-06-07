package com.example.spotting;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;
import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

public class KeywordClassifier {
    private static final String TAG = "KeywordClassifier";
    private static final String MODEL_PATH = "speech_commands.tflite";

    private Interpreter tflite;
    private int inputSize;
    private int outputSize;
    private boolean isInitialized = false;

    // Buffer per accumulare audio fino alla dimensione richiesta
    private Queue<Float> audioBuffer = new LinkedList<>();
    private final Object bufferLock = new Object();

    // Labels per il modello Google Speech Commands v2
    private static final String[] LABELS = {
            "silence",    // 0
            "unknown",    // 1
            "yes",        // 2
            "no",         // 3
            "up",         // 4
            "down",       // 5
            "left",       // 6
            "right",      // 7
            "on",         // 8
            "off",        // 9
            "stop",       // 10
            "go"          // 11
    };

    public KeywordClassifier(Context context) {
        try {
            // Carica il modello
            MappedByteBuffer tfliteModel = loadModelFile(context);

            // Configura l'interprete
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(2);

            tflite = new Interpreter(tfliteModel, options);

            // Ottieni le dimensioni del modello
            int[] inputShape = tflite.getInputTensor(0).shape();
            int[] outputShape = tflite.getOutputTensor(0).shape();

            Log.d(TAG, "Input shape: " + Arrays.toString(inputShape));
            Log.d(TAG, "Output shape: " + Arrays.toString(outputShape));

            // Per il modello Google Speech Commands, l'input dovrebbe essere [1, samples]
            if (inputShape.length >= 2) {
                inputSize = inputShape[1]; // Seconda dimensione (numero di campioni)
            } else {
                inputSize = inputShape[0]; // Prima dimensione se è 1D
            }

            if (outputShape.length >= 2) {
                outputSize = outputShape[1]; // Seconda dimensione
            } else {
                outputSize = outputShape[0]; // Prima dimensione se è 1D
            }

            Log.d(TAG, "Configurazione modello:");
            Log.d(TAG, "- Input size: " + inputSize + " campioni");
            Log.d(TAG, "- Output size: " + outputSize + " classi");
            Log.d(TAG, "- Durata audio richiesta: " + (inputSize / 16000.0f) + " secondi");
            Log.d(TAG, "- Classi supportate: " + LABELS.length);

            // Inizializza il buffer
            audioBuffer.clear();

            isInitialized = true;
            Log.d(TAG, "✅ KeywordClassifier inizializzato correttamente");

        } catch (Exception e) {
            Log.e(TAG, "❌ Errore nell'inizializzazione del KeywordClassifier", e);
            isInitialized = false;
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

    public String classify(float[] audioData) {
        if (!isInitialized || tflite == null) {
            Log.e(TAG, "❌ Classificatore non inizializzato");
            return null;
        }

        if (audioData == null) {
            Log.e(TAG, "❌ Audio data è null");
            return null;
        }

        synchronized (bufferLock) {
            // Aggiungi i nuovi campioni al buffer
            for (float sample : audioData) {
                audioBuffer.offer(sample);
            }

            // Se abbiamo abbastanza campioni, esegui la classificazione
            if (audioBuffer.size() >= inputSize) {
                float[] inputData = new float[inputSize];

                // Estrai i campioni dal buffer
                for (int i = 0; i < inputSize; i++) {
                    inputData[i] = audioBuffer.poll();
                }

                // Esegui la classificazione
                return performClassification(inputData);
            }
        }

        return null; // Non abbastanza dati ancora
    }

    private String performClassification(float[] inputData) {
        try {
            // Prepara l'input per TensorFlow Lite
            float[][] input = new float[1][inputSize];
            System.arraycopy(inputData, 0, input[0], 0, inputSize);

            // Prepara l'output
            float[][] output = new float[1][outputSize];

            // Esegui l'inferenza
            tflite.run(input, output);

            // Interpreta il risultato
            return interpretOutput(output[0]);

        } catch (Exception e) {
            Log.e(TAG, "❌ Errore durante la classificazione", e);
            return null;
        }
    }

    private String interpretOutput(float[] probabilities) {
        if (probabilities == null || probabilities.length == 0) {
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

        // Converte in percentuale
        float confidence = maxProb * 100f;

        // Log delle probabilità per debug
        StringBuilder sb = new StringBuilder("Probabilità: ");
        for (int i = 0; i < Math.min(probabilities.length, LABELS.length); i++) {
            sb.append(String.format("%s:%.1f%% ", LABELS[i], probabilities[i] * 100f));
        }
        Log.d(TAG, sb.toString());

        // Verifica soglia di confidenza
        float threshold = ModelConfig.DEFAULT_CONFIDENCE_THRESHOLD * 100f;
        if (confidence < threshold) {
            Log.d(TAG, "Confidenza troppo bassa: " + String.format("%.1f%%", confidence));
            return null;
        }

        // Restituisci solo comandi riconoscibili (non silence/unknown con bassa confidenza)
        if (maxIndex < LABELS.length) {
            String label = LABELS[maxIndex];

            // Filtra silence e unknown se hanno bassa confidenza
            if ((label.equals("silence") || label.equals("unknown")) && confidence < 70f) {
                Log.d(TAG, "Comando ignorato: " + label + " (confidenza troppo bassa)");
                return null;
            }

            Log.d(TAG, "🎯 COMANDO RICONOSCIUTO: " + label + " (confidenza: " + String.format("%.1f%%)", confidence));
            return String.format("%s (%.1f%%)", label, confidence);
        }

        return null;
    }

    // Metodo per resettare il buffer (utile per nuove sessioni)
    public void resetBuffer() {
        synchronized (bufferLock) {
            audioBuffer.clear();
            Log.d(TAG, "Buffer audio resettato");
        }
    }

    // Metodo per verificare quanti campioni sono nel buffer
    public int getBufferSize() {
        synchronized (bufferLock) {
            return audioBuffer.size();
        }
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public int getInputSize() {
        return inputSize;
    }

    public int getOutputSize() {
        return outputSize;
    }

    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }

        synchronized (bufferLock) {
            audioBuffer.clear();
        }

        isInitialized = false;
        Log.d(TAG, "KeywordClassifier chiuso");
    }
}