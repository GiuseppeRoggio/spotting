package com.example.spotting;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity implements AudioRecorder.AudioRecorderListener {

    private static final int RECORD_AUDIO_PERMISSION_CODE = 1;
    private static final String TAG = "MainActivity";

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

    // Handler per elaborazione audio in background
    private Handler audioProcessingHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initComponents();
        checkPermissions();

        logMessage("🎤 App Keyword Spotting pronta");
        logMessage("📋 Comandi supportati: " + ModelConfig.getSupportedCommandsString());
        logMessage("🔧 " + String.format("Buffer: %d campioni (%.2f secondi)",
                ModelConfig.getExpectedSamples(), ModelConfig.AUDIO_DURATION_SECONDS));
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
            // Handler per elaborazione audio in background
            audioProcessingHandler = new Handler(Looper.getMainLooper());

            // Inizializza KeywordClassifier
            keywordClassifier = new KeywordClassifier(this);
            if (keywordClassifier.isInitialized()) {
                logMessage("✅ KeywordClassifier inizializzato");
                logMessage("📊 Input: " + keywordClassifier.getInputSize() +
                        " campioni, Output: " + keywordClassifier.getOutputSize() + " classi");
                logMessage("🎯 Soglia confidenza: " + (keywordClassifier.getConfidenceThreshold() * 100) + "%");
            } else {
                logMessage("❌ KeywordClassifier non inizializzato");
                return;
            }

            // Inizializza AudioPreprocessor
            audioPreprocessor = new AudioPreprocessor();
            logMessage("✅ AudioPreprocessor inizializzato");
            logMessage("📊 Samples attesi: " + audioPreprocessor.getExpectedSamples());

            // Inizializza AudioRecorder
            audioRecorder = new AudioRecorder(this);
            logMessage("✅ AudioRecorder inizializzato");
            logMessage("🔧 Sample Rate: " + audioRecorder.getSampleRate() + "Hz");
            logMessage("🔧 Buffer Size: " + audioRecorder.getBufferSizeInSamples() + " campioni");
            logMessage("⏱️ Durata buffer: " + String.format("%.2f", audioRecorder.getBufferDurationSeconds()) + " secondi");

        } catch (Exception e) {
            logMessage("❌ Errore inizializzazione: " + e.getMessage());
            Log.e(TAG, "Errore inizializzazione", e);
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
            logMessage("⏳ Il primo buffer completo sarà pronto tra " +
                    String.format("%.1f", audioRecorder.getBufferDurationSeconds()) + " secondi");

            updateUI();

        } catch (Exception e) {
            logMessage("❌ Errore avvio registrazione: " + e.getMessage());
            Log.e(TAG, "Errore avvio registrazione", e);
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
            Log.e(TAG, "Errore stop registrazione", e);
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
        // Questo metodo viene chiamato ogni volta che il buffer circolare è completo (44032 campioni)
        // Elaborazione asincrona per non bloccare il thread audio
        if (audioProcessingHandler != null) {
            audioProcessingHandler.post(() -> processAudioData(audioData));
        } else {
            processAudioData(audioData);
        }
    }

    @Override
    public void onRecordingStopped() {
        logMessage("🔄 Registrazione fermata dal sistema");
        Log.d(TAG, "AudioRecorder fermato dal sistema");
    }

    @Override
    public void onSilenceDetected() {
        // Log del silenzio solo per debug verbose
        Log.v(TAG, "Silenzio rilevato");
    }

    @Override
    public void onSpeechDetected() {
        logMessage("🗣️ Parlato rilevato - Elaborazione in corso...");
        Log.d(TAG, "Speech rilevato");
    }

    @Override
    public void onError(String error) {
        logMessage("❌ Errore AudioRecorder: " + error);
        Log.e(TAG, "Errore AudioRecorder: " + error);

        // In caso di errore, ferma la registrazione
        if (isRecording) {
            stopRecording();
        }
    }

    // ========== ELABORAZIONE AUDIO ==========

    private void processAudioData(short[] rawAudioData) {
        try {
            if (rawAudioData == null || rawAudioData.length == 0) {
                Log.w(TAG, "Dati audio vuoti ricevuti");
                return;
            }

            Log.v(TAG, "Elaborazione audio: " + rawAudioData.length + " campioni");

            // 1. Preprocessa l'audio (normalizzazione di base)
            float[] processedAudio = audioPreprocessor.preprocessAudio(rawAudioData);

            if (processedAudio == null) {
                logMessage("⚠️ Errore nel preprocessing audio");
                return;
            }

            // 2. Verifica se contiene parlato (evita classificazioni inutili su silenzio)
            boolean containsSpeech = audioPreprocessor.containsSpeech(processedAudio, 0.01f);

            if (!containsSpeech) {
                // Silenzio - non classifichiamo per risparmiare risorse
                Log.v(TAG, "Silenzio rilevato - classificazione saltata");
                return;
            }

            // 3. Valida i dati audio prima della classificazione
            if (!keywordClassifier.validateAudioData(processedAudio)) {
                logMessage("⚠️ Dati audio non validi per la classificazione");
                return;
            }

            // 4. Classifica l'audio
            totalClassifications++;
            String result = keywordClassifier.classify(processedAudio);

            if (result != null && !result.isEmpty()) {
                handleClassificationResult(result);
            } else {
                // Log occasionale per l'utente
                if (totalClassifications % 3 == 0) {
                    logMessage("🔍 Analizzando audio... (tentativo " + totalClassifications + ")");
                }
                Log.d(TAG, "Classificazione #" + totalClassifications + " - Nessun comando riconosciuto");
            }

        } catch (Exception e) {
            logMessage("❌ Errore elaborazione audio: " + e.getMessage());
            Log.e(TAG, "Errore elaborazione audio", e);
        }
    }

    private void handleClassificationResult(String result) {
        try {
            // Estrae il comando e la confidenza dal risultato
            String[] parts = result.split("[()%]");
            String command = parts[0].trim();
            float confidence = 0f;

            if (parts.length > 1) {
                try {
                    confidence = Float.parseFloat(parts[1].trim().replace(',', '.'));
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Errore parsing confidenza: " + parts[1]);
                }
            }

            // Verifica se è un comando supportato
            if (!ModelConfig.isCommandSupported(command)) {
                Log.d(TAG, "Comando non supportato: " + command);
                return;
            }

            // Evita duplicati con soglia dinamica basata sulla confidenza
            long currentTime = System.currentTimeMillis();
            long timeSinceLastCommand = currentTime - lastCommandTime;

            // Soglia più bassa per comandi ad alta confidenza
            long duplicateThreshold = confidence > 80f ? 1000 : 1500; // 1-1.5 secondi

            if (command.equals(lastCommand) && timeSinceLastCommand < duplicateThreshold) {
                Log.d(TAG, "Comando duplicato ignorato: " + command + " (dopo " + timeSinceLastCommand + "ms)");
                return;
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

            Log.i(TAG, "Comando riconosciuto: " + command + " (confidenza: " + confidence + "%)");

            // Esegui azione associata al comando
            executeCommand(command, confidence);

        } catch (Exception e) {
            logMessage("❌ Errore gestione risultato: " + e.getMessage());
            Log.e(TAG, "Errore gestione risultato", e);
        }
    }

    private void executeCommand(String command, float confidence) {
        // Implementa azioni specifiche per ogni comando
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

    // ========== METODI PUBBLICI PER CONTROLLO ESTERNO ==========

    public void startListening() {
        if (audioRecorder != null && !isRecording) {
            startRecording();
        }
    }

    public void stopListening() {
        if (audioRecorder != null && isRecording) {
            stopRecording();
        }
    }

    // ========== UTILITY ==========

    private void logMessage(String message) {
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

        // Rilascia le risorse
        if (audioRecorder != null) {
            audioRecorder.release();
            audioRecorder = null;
        }

        if (keywordClassifier != null) {
            keywordClassifier.close();
            keywordClassifier = null;
        }

        // Cleanup handler
        if (audioProcessingHandler != null) {
            audioProcessingHandler.removeCallbacksAndMessages(null);
            audioProcessingHandler = null;
        }

        logMessage("🔄 Risorse rilasciate");
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Ferma la registrazione quando l'app va in background
        if (isRecording) {
            logMessage("⏸️ App in background - Registrazione fermata");
            stopRecording();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "App resumed");
    }
}