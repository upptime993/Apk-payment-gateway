package com.example.paymentgateway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private TextView tvLog;
    private TextView tvStatus;

    // =========================================================
    // RECEIVER - Menangkap log yang dikirim dari Service
    // =========================================================
    private final BroadcastReceiver logReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String logMessage = intent.getStringExtra("log");
            if (logMessage != null) {
                appendLog(logMessage);
            }
        }
    };

    // =========================================================
    // onCreate - Inisialisasi UI dan Komponen
    // =========================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("PaymentConfig", MODE_PRIVATE);
        buildUI();
    }

    // =========================================================
    // BUILD UI - Membangun semua komponen UI secara programatik
    // =========================================================
    private void buildUI() {
        // Root layout
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(30, 40, 30, 30);
        rootLayout.setBackgroundColor(Color.parseColor("#1E1E1E")); // Dark background

        // --- JUDUL APLIKASI ---
        TextView tvTitle = new TextView(this);
        tvTitle.setText("💳 Payment Gateway Bot");
        tvTitle.setTextSize(20f);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setPadding(0, 0, 0, 10);

        // --- STATUS IZIN ---
        tvStatus = new TextView(this);
        tvStatus.setTextSize(13f);
        tvStatus.setPadding(16, 12, 16, 12);
        tvStatus.setTypeface(null, Typeface.BOLD);

        // --- LABEL & INPUT PACKAGE NAME ---
        TextView lblPackage = new TextView(this);
        lblPackage.setText("📦 Target Package Name:");
        lblPackage.setTextColor(Color.LTGRAY);
        lblPackage.setTextSize(13f);
        lblPackage.setPadding(0, 16, 0, 4);

        EditText etPackage = new EditText(this);
        // FIX BUG #1: Default package disamakan menjadi "com.shopeepay.id"
        etPackage.setText(prefs.getString("packageName", "com.shopeepay.id"));
        etPackage.setTextColor(Color.WHITE);
        etPackage.setHintTextColor(Color.GRAY);
        etPackage.setHint("Contoh: com.shopeepay.id");
        etPackage.setBackgroundColor(Color.parseColor("#2D2D2D"));
        etPackage.setPadding(16, 12, 16, 12);

        // --- LABEL & INPUT REGEX ---
        TextView lblRegex = new TextView(this);
        lblRegex.setText("🔍 Pola Regex (Penangkap Angka):");
        lblRegex.setTextColor(Color.LTGRAY);
        lblRegex.setTextSize(13f);
        lblRegex.setPadding(0, 16, 0, 4);

        EditText etRegex = new EditText(this);
        // FIX BUG #3: Regex default yang lebih robust
        etRegex.setText(prefs.getString("regex", "(?i)Rp\\.?\\s*([\\d\\.]+(?:,[\\d]+)?)"));
        etRegex.setTextColor(Color.WHITE);
        etRegex.setHintTextColor(Color.GRAY);
        etRegex.setHint("Contoh: (?i)Rp\\s*([\\d\\.]+)");
        etRegex.setBackgroundColor(Color.parseColor("#2D2D2D"));
        etRegex.setPadding(16, 12, 16, 12);

        // --- TOMBOL SIMPAN ---
        Button btnSave = new Button(this);
        btnSave.setText("💾 SIMPAN PENGATURAN");
        btnSave.setBackgroundColor(Color.parseColor("#007BFF"));
        btnSave.setTextColor(Color.WHITE);
        btnSave.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnParams.setMargins(0, 16, 0, 8);
        btnSave.setLayoutParams(btnParams);

        btnSave.setOnClickListener(v -> {
            String packageInput = etPackage.getText().toString().trim();
            String regexInput   = etRegex.getText().toString().trim();

            if (packageInput.isEmpty()) {
                Toast.makeText(this, "Package name tidak boleh kosong!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (regexInput.isEmpty()) {
                Toast.makeText(this, "Regex tidak boleh kosong!", Toast.LENGTH_SHORT).show();
                return;
            }

            prefs.edit()
                    .putString("packageName", packageInput)
                    .putString("regex", regexInput)
                    .apply();

            Toast.makeText(this, "✅ Tersimpan! Bot memantau: " + packageInput, Toast.LENGTH_LONG).show();
            appendLog("⚙️ Pengaturan diperbarui.");
            appendLog("   Package : " + packageInput);
            appendLog("   Regex   : " + regexInput);

            // Update indikator status setelah simpan
            updateStatusIndicator();
        });

        // --- TOMBOL IZIN NOTIFIKASI ---
        Button btnPermission = new Button(this);
        btnPermission.setText("🔔 BERIKAN IZIN NOTIFIKASI");
        btnPermission.setBackgroundColor(Color.parseColor("#FFC107"));
        btnPermission.setTextColor(Color.BLACK);
        btnPermission.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams btnPermParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnPermParams.setMargins(0, 0, 0, 8);
        btnPermission.setLayoutParams(btnPermParams);

        btnPermission.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            appendLog("🔑 Membuka halaman pengaturan izin...");
        });

        // --- TOMBOL BERSIHKAN LOG ---
        Button btnClearLog = new Button(this);
        btnClearLog.setText("🗑️ BERSIHKAN LOG");
        btnClearLog.setBackgroundColor(Color.parseColor("#6C757D"));
        btnClearLog.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams btnClearParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnClearParams.setMargins(0, 0, 0, 16);
        btnClearLog.setLayoutParams(btnClearParams);

        btnClearLog.setOnClickListener(v -> {
            tvLog.setText("");
            appendLog("🧹 Log dibersihkan.");
        });

        // --- TERMINAL LOG ---
        TextView lblLog = new TextView(this);
        lblLog.setText("📋 Terminal Log:");
        lblLog.setTextColor(Color.LTGRAY);
        lblLog.setTextSize(13f);
        lblLog.setPadding(0, 0, 0, 6);

        tvLog = new TextView(this);
        tvLog.setTextColor(Color.parseColor("#00FF41")); // Warna hijau terminal
        tvLog.setBackgroundColor(Color.parseColor("#0D0D0D"));
        tvLog.setPadding(20, 20, 20, 20);
        tvLog.setTextSize(11f);
        tvLog.setTypeface(Typeface.MONOSPACE);
        tvLog.setMovementMethod(new ScrollingMovementMethod());

        ScrollView scrollLog = new ScrollView(this);
        scrollLog.addView(tvLog);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f
        );
        scrollLog.setLayoutParams(scrollParams);

        // --- SUSUN SEMUA KOMPONEN ---
        rootLayout.addView(tvTitle);
        rootLayout.addView(tvStatus);
        rootLayout.addView(lblPackage);
        rootLayout.addView(etPackage);
        rootLayout.addView(lblRegex);
        rootLayout.addView(etRegex);
        rootLayout.addView(btnSave);
        rootLayout.addView(btnPermission);
        rootLayout.addView(btnClearLog);
        rootLayout.addView(lblLog);
        rootLayout.addView(scrollLog);

        setContentView(rootLayout);

        // Log pertama setelah UI siap
        appendLog("🚀 Aplikasi Payment Bot dimulai...");
        updateStatusIndicator(); // Cek status izin saat pertama buka
    }

    // =========================================================
    // FIX BUG #6: Cek & Tampilkan Status Izin Notifikasi
    // =========================================================
    private void updateStatusIndicator() {
        if (isNotificationListenerEnabled()) {
            tvStatus.setText("● STATUS: IZIN AKTIF - Bot sedang berjalan");
            tvStatus.setTextColor(Color.parseColor("#28A745")); // Hijau
            tvStatus.setBackgroundColor(Color.parseColor("#1A2E1A"));
            appendLog("✅ Izin Notification Listener: AKTIF");
        } else {
            tvStatus.setText("● STATUS: IZIN BELUM DIBERIKAN - Klik tombol kuning!");
            tvStatus.setTextColor(Color.parseColor("#DC3545")); // Merah
            tvStatus.setBackgroundColor(Color.parseColor("#2E1A1A"));
            appendLog("⚠️ PERINGATAN: Izin Notification Listener BELUM aktif!");
            appendLog("   => Klik tombol BERIKAN IZIN NOTIFIKASI di atas.");
        }
    }

    // Helper: Cek apakah Notification Listener sudah diizinkan
    private boolean isNotificationListenerEnabled() {
        String packageName = getPackageName();
        String enabledListeners = Settings.Secure.getString(
                getContentResolver(),
                "enabled_notification_listeners"
        );
        return enabledListeners != null && enabledListeners.contains(packageName);
    }

    // =========================================================
    // APPEND LOG - Tambah baris baru ke terminal
    // =========================================================
    private void appendLog(String message) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        runOnUiThread(() -> {
            tvLog.append("[" + time + "] " + message + "\n");
        });
    }

    // =========================================================
    // LIFECYCLE - Register & Unregister Receiver
    // =========================================================
    @Override
    protected void onResume() {
        super.onResume();

        // Update status setiap kali kembali ke aplikasi
        // (berguna setelah user balik dari halaman pengaturan izin)
        updateStatusIndicator();

        // Register receiver untuk menerima log dari Service
        ContextCompat.registerReceiver(
                this,
                logReceiver,
                new IntentFilter("com.paymentgateway.LOG"),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister receiver untuk mencegah memory leak
        try {
            unregisterReceiver(logReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver belum terdaftar, abaikan error ini
        }
    }
}
