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

public class MainActivity extends AppCompatActivity {

    private static final int RECORD_AUDIO_PERMISSION_CODE = 1;

    private Button btnRecord;
    private TextView tvLog;
    private ScrollView scrollViewLog;

    private KeywordClassifier keywordClassifier;
    private boolean isRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initComponents();
        checkPermissions();

        logMessage("App pronta");
    }

    private void initViews() {
        btnRecord = findViewById(R.id.btnRecord);
        tvLog = findViewById(R.id.tvLog);
        scrollViewLog = findViewById(R.id.scrollViewLog);

        btnRecord.setOnClickListener(v -> testClassifier());
    }

    private void initComponents() {
        try {
            // Inizializza solo KeywordClassifier per il test
            keywordClassifier = new KeywordClassifier(this);
            if (keywordClassifier.isInitialized()) {
                logMessage("✅ KeywordClassifier inizializzato");
                logMessage("📊 Modello info: Input=" + keywordClassifier.getInputSize() +
                        ", Output=" + keywordClassifier.getOutputSize());
                logMessage(ModelConfig.getTechnicalInfo());
            } else {
                logMessage("❌ KeywordClassifier non inizializzato correttamente");
            }
        } catch (Exception e) {
            logMessage("❌ Errore KeywordClassifier: " + e.getMessage());
            keywordClassifier = null;
        }
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    RECORD_AUDIO_PERMISSION_CODE);
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
            }
        }
    }

    private void testClassifier() {
        if (keywordClassifier == null || !keywordClassifier.isInitialized()) {
            logMessage("❌ KeywordClassifier non disponibile");
            return;
        }

        // Test con dati audio fittizi per verificare che il classificatore funzioni
        logMessage("🧪 Test del classificatore...");

        try {
            // Crea un array di test con la dimensione corretta
            int inputSize = keywordClassifier.getInputSize();
            float[] testAudio = new float[inputSize];

            // Riempi con valori casuali normalizzati
            for (int i = 0; i < inputSize; i++) {
                testAudio[i] = (float) (Math.random() * 2.0 - 1.0); // Valori tra -1 e 1
            }

            // Prova la classificazione
            String result = keywordClassifier.classify(testAudio);

            if (result != null && !result.isEmpty()) {
                String command = result.split("\\s+")[0];
                String description = ModelConfig.getCommandDescription(command);
                logMessage("🎯 Test completato: " + description + " - " + result);
            } else {
                logMessage("🔍 Test completato: Nessun comando riconosciuto (normale con dati casuali)");
            }

        } catch (Exception e) {
            logMessage("❌ Errore nel test: " + e.getMessage());
            e.printStackTrace();
        }
    }

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
        if (keywordClassifier != null) {
            keywordClassifier.close();
        }
    }
}