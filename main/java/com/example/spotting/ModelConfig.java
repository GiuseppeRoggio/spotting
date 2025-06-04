package com.example.spotting;

/**
 * Configurazione specifica per il modello speech_commands.tflite
 * Contiene tutti i parametri ottimizzati per i 10 comandi vocali
 */
public class ModelConfig {

    // Informazioni del modello
    public static final String MODEL_NAME = "speech_commands.tflite";
    public static final String MODEL_VERSION = "1.0";
    public static final int NUM_CLASSES = 10;

    // Comandi vocali supportati dal modello
    public static final String[] VOICE_COMMANDS = {
            "stop", "down", "off", "right", "up",
            "go", "on", "yes", "left", "no"
    };

    // Parametri audio ottimizzati per questo modello
    public static final int SAMPLE_RATE = 16000; // 16kHz
    public static final int AUDIO_LENGTH_MS = 1000; // 1 secondo
    public static final int AUDIO_LENGTH_SAMPLES = SAMPLE_RATE * AUDIO_LENGTH_MS / 1000;

    // Parametri per preprocessing MFCC
    public static final int N_MFCC = 13;
    public static final int N_FFT = 2048;
    public static final int HOP_LENGTH = 512;
    public static final int N_MELS = 40;
    public static final int FMIN = 20;
    public static final int FMAX = SAMPLE_RATE / 2;

    // Soglie di confidenza e rilevamento
    public static final float CONFIDENCE_THRESHOLD = 0.6f; // 60% per maggiore accuratezza
    public static final double SILENCE_THRESHOLD = 800.0; // Soglia RMS per speech detection
    public static final int SILENCE_DURATION_MS = 300; // 300ms di silenzio

    // Parametri per normalizzazione audio
    public static final float PRE_EMPHASIS_COEFF = 0.97f;
    public static final float AUDIO_NORMALIZATION_FACTOR = 32768.0f; // 2^15 per 16-bit audio

    // Dimensioni input/output del modello (da verificare con il modello reale)
    public static final int[] EXPECTED_INPUT_SHAPE = {1, 1960}; // Tipico per speech commands
    public static final int[] EXPECTED_OUTPUT_SHAPE = {1, 10}; // 10 classi

    /**
     * Restituisce una descrizione testuale del comando riconosciuto
     */
    public static String getCommandDescription(String command) {
        switch (command.toLowerCase()) {
            case "stop":
                return "Comando: FERMA";
            case "down":
                return "Comando: GIÙ";
            case "off":
                return "Comando: SPEGNI";
            case "right":
                return "Comando: DESTRA";
            case "up":
                return "Comando: SU";
            case "go":
                return "Comando: VAI";
            case "on":
                return "Comando: ACCENDI";
            case "yes":
                return "Comando: SÌ";
            case "left":
                return "Comando: SINISTRA";
            case "no":
                return "Comando: NO";
            default:
                return "Comando sconosciuto";
        }
    }

    /**
     * Verifica se un comando è valido
     */
    public static boolean isValidCommand(String command) {
        if (command == null) return false;

        for (String validCommand : VOICE_COMMANDS) {
            if (validCommand.equalsIgnoreCase(command.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Restituisce statistiche del modello
     */
    public static String getModelInfo() {
        return String.format(
                "Modello: %s\n" +
                        "Versione: %s\n" +
                        "Comandi supportati: %d\n" +
                        "Frequenza campionamento: %d Hz\n" +
                        "Durata finestra: %d ms\n" +
                        "Soglia confidenza: %.0f%%",
                MODEL_NAME, MODEL_VERSION, NUM_CLASSES,
                SAMPLE_RATE, AUDIO_LENGTH_MS, CONFIDENCE_THRESHOLD * 100
        );
    }

    /**
     * Ottiene il colore per la visualizzazione del comando
     */
    public static int getCommandColor(String command) {
        switch (command.toLowerCase()) {
            case "stop":
            case "off":
            case "no":
                return 0xFFE53935; // Rosso per comandi "negativi"
            case "go":
            case "on":
            case "yes":
                return 0xFF43A047; // Verde per comandi "positivi"
            case "up":
            case "down":
            case "left":
            case "right":
                return 0xFF1E88E5; // Blu per comandi direzionali
            default:
                return 0xFF757575; // Grigio per default
        }
    }
}