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

    // ✅ NUOVO: Gestione thread per elaborazione audio
    private Handler audioProcessingHandler;

    // ✅ NUOVO: Controllo qualità audio
    private int consecutiveSilenceCount = 0;
    private static final int MAX_CONSECUTIVE_SILENCE = 3; // 3 secondi di silenzio

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
            // ✅ NUOVO: Handler per elaborazione audio in background
            audioProcessingHandler = new Handler(Looper.getMainLooper());

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
            // ✅ NUOVO: Reset del buffer prima di iniziare
            if (keywordClassifier != null) {
                keywordClassifier
                        .resetBuffer();
                Log.d(TAG, "Buffer reset prima dell'avvio registrazione");
            }

            audioRecorder.startRecording();
            isRecording = true;

            // Reset statistiche
            totalClassifications = 0;
            successfulClassifications = 0;
            lastCommandTime = System.currentTimeMillis();
            consecutiveSilenceCount = 0; // ✅ NUOVO: Reset contatore silenzio

            logMessage("🎙️ Registrazione AVVIATA - Parlare ora...");
            logMessage("🎯 In ascolto per i comandi vocali...");
            logMessage("🔧 Buffer classifier: " + keywordClassifier.getBufferSize() + " campioni");

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
        // ✅ MIGLIORAMENTO: Elaborazione asincrona per non bloccare il thread audio
        if (audioProcessingHandler != null) {
            audioProcessingHandler.post(() -> processAudioData(audioData));
        } else {
            processAudioData(audioData);
        }
    }

    @Override
    public void onRecordingStopped() {
        // Quando la registrazione si ferma, svuota completamente il buffer
        if (keywordClassifier != null) {
            keywordClassifier.clearBuffer();
            logMessage("🧹 Buffer audio svuotato per arresto registrazione");
            Log.d(TAG, "Buffer svuotato per arresto registrazione");
        }
    }

    @Override
    public void onSilenceDetected() {
        // ✅ NUOVO: Gestione intelligente del silenzio
        consecutiveSilenceCount++;

        // Se abbiamo troppo silenzio consecutivo, suggerisci una pulizia del buffer
        if (consecutiveSilenceCount >= MAX_CONSECUTIVE_SILENCE && keywordClassifier != null) {
            int bufferSize = keywordClassifier.getBufferSize();
            if (bufferSize > keywordClassifier.getInputSize() * 2) {
                keywordClassifier.forceBufferCleanup();
                Log.d(TAG, "Pulizia buffer forzata dopo silenzio prolungato");
            }
        }

        // Log occasionale per debug (ogni 5 secondi di silenzio)
        if (consecutiveSilenceCount % 5 == 0) {
            Log.v(TAG, "Silenzio prolungato: " + consecutiveSilenceCount + " secondi");
        }
    }

    @Override
    public void onSpeechDetected() {
        // ✅ NUOVO: Reset del contatore silenzio quando rileva parlato
        consecutiveSilenceCount = 0;

        logMessage("🗣️ Parlato rilevato - Elaborazione in corso...");

        // ✅ NUOVO: Log dimensione buffer per debug
        if (keywordClassifier != null) {
            int bufferSize = keywordClassifier.getBufferSize();
            Log.d(TAG, "Parlato rilevato - Buffer size: " + bufferSize);
        }
    }

    @Override
    public void onError(String error) {
        logMessage("❌ Errore AudioRecorder: " + error);
        Log.e(TAG, "Errore audio: " + error);

        // In caso di errore, pulisci il buffer per evitare stati inconsistenti
        if (keywordClassifier != null) {
            keywordClassifier.clearBuffer();
            logMessage("🧹 Buffer audio pulito dopo errore");
        }
    }

    // ========== ELABORAZIONE AUDIO ==========

    private void processAudioData(short[] rawAudioData) {
        try {
            // ✅ NUOVO: Log periodico delle dimensioni buffer per debug
            if (totalClassifications % 10 == 0 && keywordClassifier != null) {
                int bufferSize = keywordClassifier.getBufferSize();
                Log.d(TAG, "Classificazione #" + totalClassifications + " - Buffer: " + bufferSize + "/" + keywordClassifier.getInputSize());
            }

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
                Log.v(TAG, "Silenzio rilevato - classificazione saltata");
                return;
            }

            // 3. ✅ MIGLIORAMENTO: Verifica che il buffer del classifier sia in uno stato valido
            if (keywordClassifier.getBufferSize() > keywordClassifier.getInputSize() * 5) {
                Log.w(TAG, "Buffer troppo grande, forzando pulizia prima della classificazione");
                keywordClassifier.forceBufferCleanup();
            }

            // 4. Classifica l'audio - ora il buffer è gestito in modo più intelligente
            totalClassifications++;
            String result = keywordClassifier.classify(processedAudio);

            if (result != null && !result.isEmpty()) {
                handleClassificationResult(result);
            } else {
                // ✅ MIGLIORAMENTO: Log più informativo quando non riconosce comandi
                int bufferSize = keywordClassifier.getBufferSize();
                Log.d(TAG, "Classificazione #" + totalClassifications + " - Nessun comando riconosciuto (buffer: " + bufferSize + ")");

                // Log occasionale per l'utente
                if (totalClassifications % 5 == 0) {
                    logMessage("🔍 Analizzando audio... (tentativo " + totalClassifications + ")");
                }
            }

        } catch (Exception e) {
            logMessage("❌ Errore elaborazione audio: " + e.getMessage());
            Log.e(TAG, "Errore elaborazione audio", e);

            // In caso di errore durante l'elaborazione, pulisci il buffer
            if (keywordClassifier != null) {
                keywordClassifier.clearBuffer();
                Log.d(TAG, "Buffer pulito dopo errore elaborazione");
            }
        }
    }

    private void handleClassificationResult(String result) {
        try {
            // Estrae il comando e la confidenza dal risultato
            String[] parts = result.split("[()%]");
            String command = parts[0].trim();
            float confidence = parts.length > 1 ? Float.parseFloat(parts[1].trim().replace(',', '.')) : 0f;

            // Verifica se è un comando supportato
            if (!ModelConfig.isCommandSupported(command)) {
                Log.d(TAG, "Comando non supportato: " + command);
                return;
            }

            // ✅ MIGLIORAMENTO: Evita duplicati con soglia dinamica basata sulla confidenza
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

            // ✅ NUOVO: Log buffer size per debug
            if (keywordClassifier != null) {
                logMessage("   📊 Buffer: " + keywordClassifier.getBufferSize() + " campioni");
            }

            // Log per debug
            Log.i(TAG, "Comando riconosciuto: " + command + " (confidenza: " + confidence + "%)");

            // Qui potresti aggiungere azioni specifiche per ogni comando
            executeCommand(command, confidence);

        } catch (Exception e) {
            logMessage("❌ Errore gestione risultato: " + e.getMessage());
            Log.e(TAG, "Errore gestione risultato", e);
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

    // ========== METODI PUBBLICI PER CONTROLLO ESTERNO ==========

    public void startListening() {
        if (audioRecorder != null && !isRecording) {
            startRecording();
        }
    }

    public void stopListening() {
        if (audioRecorder != null && isRecording) {
            stopRecording();
            // Il buffer viene automaticamente svuotato nel callback onRecordingStopped
        }
    }

    // ✅ NUOVO: Metodo per debug del buffer
    public void logBufferStatus() {
        if (keywordClassifier != null) {
            int bufferSize = keywordClassifier.getBufferSize();
            int inputSize = keywordClassifier.getInputSize();
            float usage = (float) bufferSize / inputSize * 100f;
            logMessage("📊 Buffer Status: " + bufferSize + "/" + inputSize + " (" + String.format("%.1f%%)", usage));
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

        // Rilascia le risorse
        if (audioRecorder != null) {
            audioRecorder.release();
        }

        if (keywordClassifier != null) {
            keywordClassifier.close(); // Questo ferma anche il timer di pulizia automatica
        }

        // ✅ NUOVO: Cleanup handler
        if (audioProcessingHandler != null) {
            audioProcessingHandler.removeCallbacksAndMessages(null);
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

    @Override
    protected void onResume() {
        super.onResume();
        // ✅ NUOVO: Log dello stato quando l'app torna in foreground
        if (keywordClassifier != null) {
            Log.d(TAG, "App resumed - Buffer size: " + keywordClassifier.getBufferSize());
        }
    }
}