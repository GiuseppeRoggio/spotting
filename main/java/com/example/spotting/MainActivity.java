package com.example.spotting;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity implements AudioRecorder.AudioRecorderListener {

    private static final int RECORD_AUDIO_PERMISSION_CODE = 1;

    private Button btnRecord;
    private TextView tvLog;
    private ScrollView scrollViewLog;

    private KeywordClassifier keywordClassifier;
    private AudioRecorder audioRecorder;
    private AudioPreprocessor audioPreprocessor;

    private boolean isRecording = false;

    // Contatori per statistiche
    private int totalClassifications = 0;
    private int successfulClassifications = 0;
    private long lastCommandTime = 0;
    private String lastCommand = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initComponents();
        checkPermissions();

        logMessage("🎤 App Keyword Spotting pronta");
        logMessage("📋 " + ModelConfig.getSupportedCommandsString());
    }

    private void initViews() {
        btnRecord = findViewById(R.id.btnRecord);
        tvLog = findViewById(R.id.tvLog);
        scrollViewLog = findViewById(R.id.scrollViewLog);

        btnRecord.setOnClickListener(v -> toggleRecording());
        updateUI();
    }

    private void initComponents() {
        try {
            // Inizializza KeywordClassifier
            keywordClassifier = new KeywordClassifier(this);
            if (keywordClassifier.isInitialized()) {
                logMessage("✅ KeywordClassifier inizializzato");
                logMessage("📊 Input: " + keywordClassifier.getInputSize() +
                        " campioni, Output: " + keywordClassifier.getOutputSize() + " classi");
            } else {
                logMessage("❌ KeywordClassifier non inizializzato");
                return;
            }

            // Inizializza AudioPreprocessor
            audioPreprocessor = new AudioPreprocessor();
            logMessage("✅ AudioPreprocessor inizializzato");

            // Inizializza AudioRecorder (passa 'this' come listener)
            audioRecorder = new AudioRecorder(this);
            logMessage("✅ AudioRecorder inizializzato");
            logMessage("🔧 Sample Rate: " + audioRecorder.getSampleRate() + "Hz");
            logMessage("🔧 Buffer Size: " + audioRecorder.getBufferSizeInSamples() + " campioni");

        } catch (Exception e) {
            logMessage("❌ Errore inizializzazione: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    RECORD_AUDIO_PERMISSION_CODE);
        } else {
            logMessage("✅ Permesso audio già concesso");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                logMessage("✅ Permesso audio concesso");
            } else {
                logMessage("❌ Permesso audio negato - L'app non può funzionare");
                btnRecord.setEnabled(false);
            }
        }
    }

    private void toggleRecording() {
        if (!hasRequiredComponents()) {
            logMessage("❌ Componenti non inizializzati correttamente");
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            logMessage("❌ Permesso audio non concesso");
            return;
        }

        if (isRecording) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        try {
            audioRecorder.startRecording();
            isRecording = true;

            // Reset statistiche
            totalClassifications = 0;
            successfulClassifications = 0;
            lastCommandTime = System.currentTimeMillis();

            logMessage("🎙️ Registrazione AVVIATA - Parlare ora...");
            logMessage("🎯 In ascolto per i comandi vocali...");

            updateUI();

        } catch (Exception e) {
            logMessage("❌ Errore avvio registrazione: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        try {
            if (audioRecorder != null) {
                audioRecorder.stopRecording();
            }
            isRecording = false;

            logMessage("⏹️ Registrazione FERMATA");
            logMessage("📊 Statistiche sessione:");
            logMessage("   • Classificazioni totali: " + totalClassifications);
            logMessage("   • Comandi riconosciuti: " + successfulClassifications);
            if (totalClassifications > 0) {
                int successRate = (successfulClassifications * 100) / totalClassifications;
                logMessage("   • Tasso successo: " + successRate + "%");
            }

            updateUI();

        } catch (Exception e) {
            logMessage("❌ Errore stop registrazione: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateUI() {
        runOnUiThread(() -> {
            if (isRecording) {
                btnRecord.setText("⏹️ STOP RECORDING");
                btnRecord.setBackgroundResource(android.R.drawable.btn_default);
            } else {
                btnRecord.setText("🎙️ START RECORDING");
                btnRecord.setBackgroundResource(R.drawable.button_background);
            }
        });
    }

    private boolean hasRequiredComponents() {
        return keywordClassifier != null && keywordClassifier.isInitialized() &&
                audioRecorder != null &&
                audioPreprocessor != null;
    }

    // ========== IMPLEMENTAZIONE AudioRecorderListener ==========

    @Override
    public void onAudioDataReceived(short[] audioData) {
        // Questo metodo viene chiamato ogni secondo con un buffer di 16000 campioni
        processAudioData(audioData);
    }

    @Override
    public void onSilenceDetected() {
        // Chiamato quando viene rilevato silenzio
        // Non logghiamo per evitare spam, ma potremmo aggiornare un indicatore UI
    }

    @Override
    public void onSpeechDetected() {
        // Chiamato quando viene rilevato parlato
        logMessage("🗣️ Parlato rilevato - Elaborazione in corso...");
    }

    @Override
    public void onError(String error) {
        logMessage("❌ Errore AudioRecorder: " + error);
    }

    // ========== ELABORAZIONE AUDIO ==========

    private void processAudioData(short[] rawAudioData) {
        try {
            // 1. Preprocessa l'audio (normalizzazione, filtri, etc.)
            float[] processedAudio = audioPreprocessor.preprocessAudio(rawAudioData);

            if (processedAudio == null) {
                logMessage("⚠️ Errore nel preprocessing audio");
                return;
            }

            // 2. Verifica se contiene parlato (evita classificazioni inutili su silenzio)
            boolean containsSpeech = audioPreprocessor.containsSpeech(processedAudio, 0.01f);

            if (!containsSpeech) {
                // Silenzio - non classifichiamo per risparmiare risorse
                return;
            }

            // 3. Classifica l'audio
            totalClassifications++;
            String result = keywordClassifier.classify(processedAudio);

            if (result != null && !result.isEmpty()) {
                handleClassificationResult(result);
            } else {
                // Nessun comando riconosciuto con sufficiente confidenza
                logMessage("🔍 Audio analizzato - Nessun comando riconosciuto");
            }

        } catch (Exception e) {
            logMessage("❌ Errore elaborazione audio: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleClassificationResult(String result) {
        try {
            // Estrae il comando e la confidenza dal risultato
            String[] parts = result.split("[()%]");
            String command = parts[0].trim();
            float confidence = parts.length > 1 ? Float.parseFloat(parts[1].trim()) : 0f;

            // Verifica se è un comando supportato
            if (!ModelConfig.isCommandSupported(command)) {
                return;
            }

            // Evita duplicati troppo ravvicinati nel tempo
            long currentTime = System.currentTimeMillis();
            if (command.equals(lastCommand) && (currentTime - lastCommandTime) < 1500) {
                return; // Ignora se stesso comando entro 1.5 secondi
            }

            // Aggiorna statistiche
            successfulClassifications++;
            lastCommand = command;
            lastCommandTime = currentTime;

            // Ottieni descrizione del comando
            String description = ModelConfig.getCommandDescription(command);

            // Log del risultato
            logMessage("✅ COMANDO RICONOSCIUTO: " + command.toUpperCase());
            logMessage("   📝 " + description);
            logMessage("   🎯 Confidenza: " + String.format("%.1f%%", confidence));

            // Qui potresti aggiungere azioni specifiche per ogni comando
            executeCommand(command, confidence);

        } catch (Exception e) {
            logMessage("❌ Errore gestione risultato: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void executeCommand(String command, float confidence) {
        // Qui puoi implementare azioni specifiche per ogni comando
        // Per ora solo logging, ma potresti controllare dispositivi, navigazione, etc.

        switch (command.toLowerCase()) {
            case "yes":
                logMessage("   ➡️ Azione: Conferma affermativa");
                break;
            case "no":
                logMessage("   ➡️ Azione: Negazione");
                break;
            case "stop":
                logMessage("   ➡️ Azione: Arresto operazione");
                break;
            case "go":
                logMessage("   ➡️ Azione: Avvio operazione");
                break;
            case "up":
            case "down":
            case "left":
            case "right":
                logMessage("   ➡️ Azione: Movimento " + command);
                break;
            case "on":
                logMessage("   ➡️ Azione: Attivazione");
                break;
            case "off":
                logMessage("   ➡️ Azione: Disattivazione");
                break;
            default:
                logMessage("   ➡️ Comando riconosciuto ma nessuna azione definita");
                break;
        }
    }

    // ========== UTILITY ==========

    public void logMessage(String message) {
        runOnUiThread(() -> {
            String timestamp = java.text.DateFormat.getTimeInstance().format(new java.util.Date());
            String logEntry = "[" + timestamp + "] " + message + "\n";
            tvLog.append(logEntry);

            // Auto-scroll verso il basso
            scrollViewLog.post(() -> scrollViewLog.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Cleanup
        if (isRecording) {
            stopRecording();
        }

        if (audioRecorder != null) {
            audioRecorder.release();
        }

        if (keywordClassifier != null) {
            keywordClassifier.close();
        }

        logMessage("🔄 Risorse rilasciate");
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Ferma la registrazione quando l'app va in background
        if (isRecording) {
            stopRecording();
        }
    }
}