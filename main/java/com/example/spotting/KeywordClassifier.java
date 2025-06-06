package com.example.spotting;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;
import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;

public class KeywordClassifier {
    private static final String TAG = "KeywordClassifier";
    private static final String MODEL_PATH = "speech_commands.tflite";

    private Interpreter tflite;
    private int inputSize;
    private int outputSize;
    private boolean isInitialized = false;

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

            // Per il modello Google Speech Commands, l'input dovrebbe essere [1, 16000]
            if (inputShape.length >= 2) {
                inputSize = inputShape[1]; // Seconda dimensione
            } else {
                inputSize = inputShape[0]; // Prima dimensione se è 1D
            }

            if (outputShape.length >= 2) {
                outputSize = outputShape[1]; // Seconda dimensione
            } else {
                outputSize = outputShape[0]; // Prima dimensione se è 1D
            }

            Log.d(TAG, "Configurazione modello:");
            Log.d(TAG, "- Input size: " + inputSize);
            Log.d(TAG, "- Output size: " + outputSize);
            Log.d(TAG, "- Classi supportate: " + LABELS.length);

            // Verifica che le dimensioni siano corrette
            if (inputSize != 16000) {
                Log.w(TAG, "⚠️ ATTENZIONE: Input size non standard: " + inputSize + " (atteso: 16000)");
                Log.w(TAG, "Il modello potrebbe richiedere preprocessing diverso");
            }

            if (outputSize != LABELS.length) {
                Log.w(TAG, "⚠️ ATTENZIONE: Output size non corrisponde alle label: " + outputSize + " vs " + LABELS.length);
            }

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
        FileChannel fileChannel = inputStream.getChannel(); // FIX: Usa getChannel() invece di getFileChannel()
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

        try {
            // Adatta i dati audio alla dimensione richiesta dal modello
            float[] inputData = prepareInput(audioData);

            if (inputData == null) {
                Log.e(TAG, "❌ Errore nella preparazione dell'input");
                return null;
            }

            // Prepara l'output
            float[][] output = new float[1][outputSize];

            // Esegui l'inferenza
            float[][] input = new float[1][inputSize];
            System.arraycopy(inputData, 0, input[0], 0, inputSize);

            tflite.run(input, output);

            // Trova la classe con probabilità più alta
            return interpretOutput(output[0]);

        } catch (Exception e) {
            Log.e(TAG, "❌ Errore durante la classificazione", e);
            return null;
        }
    }

    private float[] prepareInput(float[] audioData) {
        try {
            if (audioData.length == inputSize) {
                // Dimensione corretta, usa direttamente
                return audioData;
            } else if (audioData.length > inputSize) {
                // Tronca se troppo lungo
                Log.d(TAG, "Troncamento audio: " + audioData.length + " -> " + inputSize);
                float[] truncated = new float[inputSize];
                System.arraycopy(audioData, 0, truncated, 0, inputSize);
                return truncated;
            } else {
                // Pad con zeri se troppo corto
                Log.d(TAG, "Padding audio: " + audioData.length + " -> " + inputSize);
                float[] padded = new float[inputSize];
                System.arraycopy(audioData, 0, padded, 0, audioData.length);
                // Il resto rimane a 0.0f
                return padded;
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Errore nella preparazione dell'input", e);
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
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            StringBuilder sb = new StringBuilder("Probabilità: ");
            for (int i = 0; i < Math.min(probabilities.length, LABELS.length); i++) {
                sb.append(String.format("%s:%.1f%% ", LABELS[i], probabilities[i] * 100f));
            }
            Log.d(TAG, sb.toString());
        }

        // Verifica soglia di confidenza (usa il valore da ModelConfig)
        float threshold = ModelConfig.DEFAULT_CONFIDENCE_THRESHOLD * 100f; // Converte in percentuale
        if (confidence < threshold) {
            Log.d(TAG, "Confidenza troppo bassa: " + String.format("%.1f%%", confidence));
            return null;
        }

        // Restituisci solo comandi riconoscibili (non silence/unknown)
        if (maxIndex < LABELS.length) {
            String label = LABELS[maxIndex];

            // Filtra silence e unknown se hanno bassa confidenza
            if ((label.equals("silence") || label.equals("unknown")) && confidence < 70f) {
                return null;
            }

            return String.format("%s (%.1f%%)", label, confidence);
        }

        return null;
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
        isInitialized = false;
        Log.d(TAG, "KeywordClassifier chiuso");
    }
}