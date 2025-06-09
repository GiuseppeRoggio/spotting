package com.example.spotting;

import java.util.HashMap;
import java.util.Map;

public class ModelConfig {

    // Informazioni sul modello aggiornato
    public static final String MODEL_NAME = "Google Speech Commands v2";
    public static final String MODEL_FILE = "speech_commands.tflite";
    public static final String MODEL_VERSION = "v2.0";

    // NUOVO: Configurazioni tecniche aggiornate per il modello 44032
    public static final int SAMPLE_RATE = 16000; // Hz
    public static final int INPUT_LENGTH = 44032; // campioni (~2.75 secondi)
    public static final int NUM_CLASSES = 12;
    public static final float DEFAULT_CONFIDENCE_THRESHOLD = 0.6f; // Soglia aggiornata

    // Durata audio in secondi
    public static final float AUDIO_DURATION_SECONDS = INPUT_LENGTH / (float) SAMPLE_RATE;

    // Labels del modello aggiornato
    private static final String[] MODEL_LABELS = {
            "silence", "unknown", "yes", "no", "up", "down",
            "left", "right", "on", "off", "stop", "go"
    };

    // Mappa dei comandi con le loro descrizioni
    private static final Map<String, String> COMMAND_DESCRIPTIONS = new HashMap<>();

    static {
        // Inizializza le descrizioni dei comandi
        COMMAND_DESCRIPTIONS.put("yes", "Sì - Conferma affermativa");
        COMMAND_DESCRIPTIONS.put("no", "No - Negazione");
        COMMAND_DESCRIPTIONS.put("up", "Su - Movimento verso l'alto");
        COMMAND_DESCRIPTIONS.put("down", "Giù - Movimento verso il basso");
        COMMAND_DESCRIPTIONS.put("left", "Sinistra - Movimento a sinistra");
        COMMAND_DESCRIPTIONS.put("right", "Destra - Movimento a destra");
        COMMAND_DESCRIPTIONS.put("on", "Accendi - Attiva qualcosa");
        COMMAND_DESCRIPTIONS.put("off", "Spegni - Disattiva qualcosa");
        COMMAND_DESCRIPTIONS.put("stop", "Stop - Ferma l'azione");
        COMMAND_DESCRIPTIONS.put("go", "Vai - Inizia l'azione");
        COMMAND_DESCRIPTIONS.put("silence", "Silenzio rilevato");
        COMMAND_DESCRIPTIONS.put("unknown", "Comando sconosciuto");
    }

    /**
     * Restituisce la descrizione di un comando
     */
    public static String getCommandDescription(String command) {
        return COMMAND_DESCRIPTIONS.getOrDefault(command, "Comando non riconosciuto: " + command);
    }

    /**
     * Restituisce informazioni complete sul modello
     */
    public static String getModelInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== CONFIGURAZIONE MODELLO ===\n");
        info.append("Nome: ").append(MODEL_NAME).append("\n");
        info.append("File: ").append(MODEL_FILE).append("\n");
        info.append("Versione: ").append(MODEL_VERSION).append("\n");
        info.append("Frequenza campionamento: ").append(SAMPLE_RATE).append(" Hz\n");
        info.append("Lunghezza input: ").append(INPUT_LENGTH).append(" campioni\n");
        info.append("Durata audio: ").append(String.format("%.2f", AUDIO_DURATION_SECONDS)).append(" secondi\n");
        info.append("Numero classi: ").append(NUM_CLASSES).append("\n");
        info.append("Soglia confidenza: ").append(DEFAULT_CONFIDENCE_THRESHOLD).append("\n");
        info.append("=== COMANDI SUPPORTATI ===\n");

        // Lista dei comandi (esclusi silence e unknown)
        String[] commands = {"yes", "no", "up", "down", "left", "right", "on", "off", "stop", "go"};
        for (String cmd : commands) {
            info.append("• ").append(cmd.toUpperCase()).append(": ").append(getCommandDescription(cmd)).append("\n");
        }

        return info.toString();
    }

    /**
     * Restituisce tutti i labels del modello (inclusi silence e unknown)
     */
    public static String[] getAllLabels() {
        return MODEL_LABELS.clone();
    }

    /**
     * Restituisce solo i comandi vocali riconosciuti (senza silence/unknown)
     */
    public static String[] getSupportedCommands() {
        return new String[]{"yes", "no", "up", "down", "left", "right", "on", "off", "stop", "go"};
    }

    /**
     * Restituisce una stringa con i comandi separati da virgola
     */
    public static String getSupportedCommandsString() {
        return "yes, no, up, down, left, right, on, off, stop, go";
    }

    /**
     * Verifica se un comando è supportato (esclude silence e unknown)
     */
    public static boolean isCommandSupported(String command) {
        return COMMAND_DESCRIPTIONS.containsKey(command) &&
                !command.equals("silence") &&
                !command.equals("unknown");
    }

    /**
     * Restituisce informazioni tecniche per il debug
     */
    public static String getTechnicalInfo() {
        return String.format(
                "Input: %d campioni float32 normalizzati [-1,1]\n" +
                        "Durata: %.2f secondi @ %d Hz\n" +
                        "Output: %d probabilità [0,1]\n" +
                        "Preprocessing: Normalizzazione base (pipeline incorporata nel modello)\n" +
                        "Framework: TensorFlow Lite\n" +
                        "Architettura: CNN ottimizzata per keyword spotting",
                INPUT_LENGTH, AUDIO_DURATION_SECONDS, SAMPLE_RATE, NUM_CLASSES
        );
    }

    /**
     * Restituisce le configurazioni per AudioRecorder
     */
    public static int getBufferSizeInSamples() {
        return INPUT_LENGTH;
    }

    /**
     * Restituisce le configurazioni per AudioPreprocessor
     */
    public static int getExpectedSamples() {
        return INPUT_LENGTH;
    }

    /**
     * Restituisce la soglia di confidenza default
     */
    public static float getDefaultConfidenceThreshold() {
        return DEFAULT_CONFIDENCE_THRESHOLD;
    }
}