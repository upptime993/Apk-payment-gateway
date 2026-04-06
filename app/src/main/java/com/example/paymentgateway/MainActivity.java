package com.example.paymentgateway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private TextView tvLog;

    // Receiver untuk menangkap log dari Service
    private final BroadcastReceiver logReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String logMessage = intent.getStringExtra("log");
            if (logMessage != null) {
                appendLog(logMessage);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("PaymentConfig", MODE_PRIVATE);

        // --- MEMBUAT UI TANPA XML ---
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 30, 30, 30);

        // Input Package Name
        TextView lblPackage = new TextView(this);
        lblPackage.setText("Target Package Name:");
        EditText etPackage = new EditText(this);
        etPackage.setText(prefs.getString("packageName", "com.shopee.id"));

        // Input Regex
        TextView lblRegex = new TextView(this);
        lblRegex.setText("Pola Regex (Menangkap Angka):");
        EditText etRegex = new EditText(this);
        etRegex.setText(prefs.getString("regex", "(?i)Rp\\s*([\\d\\.]+)"));

        // Tombol Simpan
        Button btnSave = new Button(this);
        btnSave.setText("SIMPAN PENGATURAN");
        btnSave.setOnClickListener(v -> {
            prefs.edit()
                 .putString("packageName", etPackage.getText().toString().trim())
                 .putString("regex", etRegex.getText().toString().trim())
                 .apply();
            Toast.makeText(this, "Tersimpan! Notifikasi mulai dipantau dengan aturan baru.", Toast.LENGTH_LONG).show();
            appendLog("Pengaturan diperbarui.");
        });

        // Tombol Izin
        Button btnPermission = new Button(this);
        btnPermission.setText("BERIKAN IZIN NOTIFIKASI");
        btnPermission.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        });

        // Terminal Log
        tvLog = new TextView(this);
        tvLog.setTextColor(Color.GREEN);
        tvLog.setBackgroundColor(Color.BLACK);
        tvLog.setPadding(20, 20, 20, 20);
        tvLog.setTextSize(12f);
        
        ScrollView scrollLog = new ScrollView(this);
        scrollLog.addView(tvLog);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
        scrollParams.setMargins(0, 20, 0, 0);
        scrollLog.setLayoutParams(scrollParams);

        // Masukkan semua ke Layout
        layout.addView(lblPackage);
        layout.addView(etPackage);
        layout.addView(lblRegex);
        layout.addView(etRegex);
        layout.addView(btnSave);
        layout.addView(btnPermission);
        layout.addView(scrollLog);

        setContentView(layout);
        appendLog("Aplikasi Bot Payment berjalan...");
    }

    private void appendLog(String message) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        runOnUiThread(() -> {
            tvLog.append("[" + time + "] " + message + "\n");
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(logReceiver, new IntentFilter("com.paymentgateway.LOG"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(logReceiver);
    }
}
