package com.example.spotting;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

public class KeywordClassifier {

    // Nome del file del modello TensorFlow Lite (da inserire nella cartella assets)
    private static final String MODEL_FILENAME = "speech_commands.tflite";

    // Labels per il modello Speech Commands specifico
    // 10 comandi vocali riconosciuti dal modello
    private static final String[] LABELS = ModelConfig.VOICE_COMMANDS;

    private static final float CONFIDENCE_THRESHOLD = ModelConfig.CONFIDENCE_THRESHOLD;

    private Interpreter tfliteInterpreter;
    private Context context;

    // Buffer per input e output del modello
    private ByteBuffer inputBuffer;
    private float[][] outputBuffer;

    // Dimensioni del modello
    private int inputSize;
    private int outputSize;

    public KeywordClassifier(Context context) throws IOException {
        this.context = context;
        initializeModel();
    }

    private void initializeModel() throws IOException {
        // Carica il modello TensorFlow Lite
        MappedByteBuffer modelBuffer = loadModelFile();

        // Configura l'interprete
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4); // Usa 4 thread per performance migliori

        tfliteInterpreter = new Interpreter(modelBuffer, options);

        // Ottieni le dimensioni di input e output
        int[] inputShape = tfliteInterpreter.getInputTensor(0).shape();
        int[] outputShape = tfliteInterpreter.getOutputTensor(0).shape();

        inputSize = getShapeSize(inputShape);
        outputSize = getShapeSize(outputShape);

        // Inizializza i buffer
        inputBuffer = ByteBuffer.allocateDirect(inputSize * 4); // 4 byte per float
        inputBuffer.order(ByteOrder.nativeOrder());

        outputBuffer = new float[1][outputSize]; // Batch size = 1

        System.out.println("Modello TensorFlow Lite caricato:");
        System.out.println("Input shape: " + java.util.Arrays.toString(inputShape));
        System.out.println("Output shape: " + java.util.Arrays.toString(outputShape));
        System.out.println("Input size: " + inputSize);
        System.out.println("Output size: " + outputSize);
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_FILENAME);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private int getShapeSize(int[] shape) {
        int size = 1;
        for (int dim : shape) {
            size *= dim;
        }
        return size;
    }

    public String classify(float[] audioFeatures) {
        if (tfliteInterpreter == null) {
            return "Errore: modello non caricato";
        }

        if (audioFeatures == null || audioFeatures.length == 0) {
            return "Errore: dati audio vuoti";
        }

        try {
            // Prepara l'input
            prepareInputBuffer(audioFeatures);

            // Esegui l'inferenza
            tfliteInterpreter.run(inputBuffer, outputBuffer);

            // Interpreta i risultati
            return interpretResults(outputBuffer[0]);

        } catch (Exception e) {
            return "Errore nella classificazione: " + e.getMessage();
        }
    }

    private void prepareInputBuffer(float[] audioFeatures) {
        inputBuffer.rewind();

        // Se le features sono più lunghe dell'input richiesto, tronca
        // Se sono più corte, completa con zeri
        int featuresToCopy = Math.min(audioFeatures.length, inputSize);

        for (int i = 0; i < featuresToCopy; i++) {
            inputBuffer.putFloat(audioFeatures[i]);
        }

        // Riempi il resto con zeri se necessario
        for (int i = featuresToCopy; i < inputSize; i++) {
            inputBuffer.putFloat(0.0f);
        }
    }

    private String interpretResults(float[] predictions) {
        if (predictions == null || predictions.length == 0) {
            return "Nessuna predizione";
        }

        // Trova l'indice con la confidenza più alta
        int maxIndex = 0;
        float maxConfidence = predictions[0];

        for (int i = 1; i < predictions.length; i++) {
            if (predictions[i] > maxConfidence) {
                maxConfidence = predictions[i];
                maxIndex = i;
            }
        }

        // Controlla se la confidenza è sopra la soglia
        if (maxConfidence < CONFIDENCE_THRESHOLD) {
            return ""; // Non abbastanza confidenza, non restituire nulla
        }

        // Restituisci il label con la confidenza
        String label = (maxIndex < LABELS.length) ? LABELS[maxIndex] : "unknown_" + maxIndex;
        return String.format("%s (%.2f%%)", label, maxConfidence * 100);
    }

    public String classifyWithDetails(float[] audioFeatures) {
        if (tfliteInterpreter == null) {
            return "Errore: modello non caricato";
        }

        if (audioFeatures == null || audioFeatures.length == 0) {
            return "Errore: dati audio vuoti";
        }

        try {
            // Prepara l'input
            prepareInputBuffer(audioFeatures);

            // Esegui l'inferenza
            tfliteInterpreter.run(inputBuffer, outputBuffer);

            // Restituisci risultati dettagliati
            return getDetailedResults(outputBuffer[0]);

        } catch (Exception e) {
            return "Errore nella classificazione: " + e.getMessage();
        }
    }

    private String getDetailedResults(float[] predictions) {
        StringBuilder results = new StringBuilder();
        results.append("Top 3 predizioni:\n");

        // Crea una mappa per ordinare le predizioni
        Map<Float, Integer> confidenceMap = new HashMap<>();
        for (int i = 0; i < predictions.length; i++) {
            confidenceMap.put(predictions[i], i);
        }

        // Ordina per confidenza (decrescente)
        float[] sortedConfidences = predictions.clone();
        java.util.Arrays.sort(sortedConfidences);

        // Prendi i top 3
        int count = 0;
        for (int i = sortedConfidences.length - 1; i >= 0 && count < 3; i--) {
            float confidence = sortedConfidences[i];
            if (confidence > 0.01f) { // Solo se > 1%
                Integer index = confidenceMap.get(confidence);
                if (index != null) {
                    String label = (index < LABELS.length) ? LABELS[index] : "unknown_" + index;
                    results.append(String.format("%d. %s: %.2f%%\n",
                            count + 1, label, confidence * 100));
                    count++;
                }
            }
        }

        return results.toString();
    }

    // Metodi di utility
    public String[] getAvailableLabels() {
        return LABELS.clone();
    }

    public float getConfidenceThreshold() {
        return CONFIDENCE_THRESHOLD;
    }

    public int getExpectedInputSize() {
        return inputSize;
    }

    public int getOutputSize() {
        return outputSize;
    }

    public boolean isModelLoaded() {
        return tfliteInterpreter != null;
    }

    public void close() {
        if (tfliteInterpreter != null) {
            tfliteInterpreter.close();
            tfliteInterpreter = null;
        }
    }
}