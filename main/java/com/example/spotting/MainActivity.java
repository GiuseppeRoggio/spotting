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

    private AudioRecorder audioRecorder;
    private AudioPreprocessor audioPreprocessor;
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

        btnRecord.setOnClickListener(v -> toggleRecording());
    }

    private void initComponents() {
        try {
            audioRecorder = new AudioRecorder(this);
            audioPreprocessor = new AudioPreprocessor();
            keywordClassifier = new KeywordClassifier(this);

            logMessage("Componenti inizializzati correttamente");
            logMessage(ModelConfig.getModelInfo());
        } catch (Exception e) {
            logMessage("Errore nell'inizializzazione: " + e.getMessage());
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
                logMessage("Permesso audio concesso");
            } else {
                logMessage("Permesso audio negato - L'app non può funzionare");
            }
        }
    }

    private void toggleRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            logMessage("Permesso audio non concesso");
            return;
        }

        if (!isRecording) {
            startRecording();
        } else {
            stopRecording();
        }
    }

    private void startRecording() {
        try {
            audioRecorder.startRecording();
            isRecording = true;
            btnRecord.setText("STOP RECORDING");
            logMessage("Registrazione avviata");
        } catch (Exception e) {
            logMessage("Errore nell'avvio della registrazione: " + e.getMessage());
        }
    }

    private void stopRecording() {
        try {
            audioRecorder.stopRecording();
            isRecording = false;
            btnRecord.setText("START RECORDING");
            logMessage("Registrazione fermata");
        } catch (Exception e) {
            logMessage("Errore nella chiusura della registrazione: " + e.getMessage());
        }
    }

    // Implementazione delle callback dall'AudioRecorder
    @Override
    public void onAudioDataReceived(short[] audioData) {
        // Preprocessa l'audio
        float[] preprocessedAudio = audioPreprocessor.preprocessAudio(audioData);

        // Classifica l'audio
        String result = keywordClassifier.classify(preprocessedAudio);

        if (result != null && !result.isEmpty()) {
            // Estrai il comando dalla stringa risultato (formato: "comando (xx.xx%)")
            String command = result.split("\\s+")[0];
            String description = ModelConfig.getCommandDescription(command);
            logMessage("🎯 " + description + " - " + result);
        }
    }

    @Override
    public void onSilenceDetected() {
        logMessage("Rilevato silenzio");
    }

    @Override
    public void onSpeechDetected() {
        logMessage("Rilevato parlato");
    }

    @Override
    public void onError(String error) {
        logMessage("Errore: " + error);
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
        if (audioRecorder != null) {
            audioRecorder.release();
        }
        if (keywordClassifier != null) {
            keywordClassifier.close();
        }
    }
}