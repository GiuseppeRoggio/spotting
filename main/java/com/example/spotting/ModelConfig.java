package com.example.spotting;

import java.util.HashMap;
import java.util.Map;

public class ModelConfig {

    // Informazioni sul modello
    public static final String MODEL_NAME = "Google Speech Commands";
    public static final String MODEL_FILE = "speech_commands.tflite";
    public static final String MODEL_VERSION = "v0.02";

    // Configurazioni tecniche
    public static final int SAMPLE_RATE = 16000; // Hz
    public static final int INPUT_LENGTH = 16000; // campioni (1 secondo)
    public static final int NUM_CLASSES = 12;
    public static final float DEFAULT_CONFIDENCE_THRESHOLD = 0.1f;

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
        COMMAND_DESCRIPTIONS.put("_silence_", "Silenzio rilevato");
        COMMAND_DESCRIPTIONS.put("_unknown_", "Comando sconosciuto");
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
        info.append("Lunghezza input: ").append(INPUT_LENGTH).append(" campioni (1 secondo)\n");
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
     * Verifica se un comando è supportato
     */
    public static boolean isCommandSupported(String command) {
        return COMMAND_DESCRIPTIONS.containsKey(command) &&
                !command.equals("_silence_") &&
                !command.equals("_unknown_");
    }

    /**
     * Restituisce informazioni tecniche per il debug
     */
    public static String getTechnicalInfo() {
        return String.format(
                "Input: %d campioni float32 normalizzati [-1,1]\n" +
                        "Output: %d probabilità [0,1]\n" +
                        "Preprocessing: Normalizzazione + Pre-emphasis + High-pass filter\n" +
                        "Framework: TensorFlow Lite\n" +
                        "Architettura: CNN ottimizzata per keyword spotting",
                INPUT_LENGTH, NUM_CLASSES
        );
    }
}