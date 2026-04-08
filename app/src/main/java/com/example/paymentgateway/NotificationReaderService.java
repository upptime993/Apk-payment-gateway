package com.example.paymentgateway;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationReaderService extends NotificationListenerService {

    // =========================================================
    // KONFIGURASI - Sesuaikan URL API kamu di sini
    // =========================================================
    private static final String API_URL = "https://self-payment-gateway.vercel.app/api/notify";
    private static final String CHANNEL_ID = "payment_bot_channel";
    private static final int FOREGROUND_NOTIF_ID = 1;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // =========================================================
    // KIRIM LOG KE UI (MainActivity)
    // =========================================================
    private void sendLogToUI(String message) {
        Intent intent = new Intent("com.paymentgateway.LOG");
        intent.putExtra("log", message);
        sendBroadcast(intent);
    }

    // =========================================================
    // SERVICE DIBUAT - Jalankan sebagai Foreground Service
    // agar tidak di-kill oleh sistem Android
    // =========================================================
    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundService();
    }

    private void startForegroundService() {
        // Buat Notification Channel (wajib untuk Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Payment Bot Service",
                    NotificationManager.IMPORTANCE_LOW // LOW agar tidak bunyi terus
            );
            channel.setDescription("Service untuk memantau notifikasi pembayaran");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        // Buat notifikasi persisten untuk foreground service
        android.app.Notification foregroundNotif = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Payment Bot Aktif")
                .setContentText("Sedang memantau notifikasi ShopeePay...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true) // Tidak bisa di-swipe oleh user
                .build();

        startForeground(FOREGROUND_NOTIF_ID, foregroundNotif);
    }

    // =========================================================
    // NOTIFIKASI MASUK - Inti dari semua logika
    // =========================================================
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {

        // --- Ambil konfigurasi terbaru dari SharedPreferences ---
        SharedPreferences prefs = getSharedPreferences("PaymentConfig", MODE_PRIVATE);

        // FIX BUG #1: Default package name disamakan menjadi "com.shopeepay.id"
        String targetPackage = prefs.getString("packageName", "com.shopeepay.id");

        // FIX BUG #3: Regex default yang lebih robust, mendukung format Rp50.084,00
        String regexPattern = prefs.getString("regex", "(?i)Rp\\.?\\s*([\\d\\.]+(?:,[\\d]+)?)");

        // --- LOG SEMUA NOTIF yang masuk untuk membantu debugging ---
        // Ini sangat berguna untuk memastikan package name ShopeePay yang benar
        sendLogToUI("📱 Notif masuk dari: " + sbn.getPackageName());

        // --- Filter hanya notif dari aplikasi target ---
        if (!sbn.getPackageName().equals(targetPackage)) {
            return; // Abaikan notif dari aplikasi lain
        }

        // --- Ambil objek notifikasi ---
        Notification notification = sbn.getNotification();
        if (notification == null || notification.extras == null) {
            sendLogToUI("⚠️ Notif dari target kosong, diabaikan.");
            return;
        }

        // --- FIX BUG #4: Ambil SEMUA kemungkinan teks termasuk EXTRA_SUB_TEXT ---
        CharSequence title   = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text    = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence bigText = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        CharSequence subText = notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT); // BARU

        // Gabungkan semua teks menjadi satu string besar
        String fullContent = (title   != null ? title.toString()   : "") + " " +
                             (text    != null ? text.toString()    : "") + " " +
                             (bigText != null ? bigText.toString() : "") + " " +
                             (subText != null ? subText.toString() : ""); // BARU

        // Bersihkan spasi berlebih
        fullContent = fullContent.trim();

        if (fullContent.isEmpty()) {
            sendLogToUI("⚠️ Konten notif kosong setelah digabung.");
            return;
        }

        sendLogToUI("📩 Isi Notif: " + fullContent);

        // --- Coba cocokkan dengan Regex ---
        try {
            Pattern pattern = Pattern.compile(regexPattern);
            Matcher matcher = pattern.matcher(fullContent);

            if (matcher.find()) {
                // FIX BUG #3: Bersihkan titik (ribuan) DAN koma (desimal)
                String nominalString = matcher.group(1)
                        .replace(".", "")  // hapus titik pemisah ribuan: 50.084 → 50084
                        .replace(",", ""); // hapus koma desimal: 50084,00 → 5008400 (ambil bulat)

                int nominalMasuk = Integer.parseInt(nominalString);
                sendLogToUI("✅ Nominal Ditemukan: Rp" + nominalMasuk);
                sendWebhookToServer(nominalMasuk);

            } else {
                sendLogToUI("❌ Regex tidak cocok. Teks: [" + fullContent + "]");
                sendLogToUI("💡 Coba ubah Regex di pengaturan aplikasi.");
            }

        } catch (NumberFormatException e) {
            sendLogToUI("❌ Gagal parsing angka: " + e.getMessage());
        } catch (Exception e) {
            sendLogToUI("❌ Error tidak terduga: " + e.getMessage());
        }
    }

    // =========================================================
    // KIRIM DATA KE SERVER API (Vercel)
    // =========================================================
    private void sendWebhookToServer(int nominal) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(API_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000); // 10 detik timeout koneksi
                conn.setReadTimeout(15000);    // 15 detik timeout baca

                // Buat body JSON
                String jsonBody = "{\"nominal_masuk\": " + nominal + "}";

                // Kirim body
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonBody.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                // Baca response
                int responseCode = conn.getResponseCode();

                // Baca body response untuk info lebih detail
                StringBuilder responseBody = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(
                                responseCode >= 200 && responseCode < 300
                                        ? conn.getInputStream()
                                        : conn.getErrorStream(), "utf-8"))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        responseBody.append(line.trim());
                    }
                }

                if (responseCode == 200 || responseCode == 201) {
                    sendLogToUI("🎉 SUKSES! Server merespons (Code " + responseCode + ")");
                    sendLogToUI("   Response: " + responseBody.toString());
                } else {
                    sendLogToUI("❌ GAGAL! Server menolak (Code " + responseCode + ")");
                    sendLogToUI("   Error: " + responseBody.toString());
                }

            } catch (java.net.SocketTimeoutException e) {
                sendLogToUI("❌ ERROR: Koneksi timeout. Cek internet/server.");
            } catch (java.net.UnknownHostException e) {
                sendLogToUI("❌ ERROR: Tidak bisa resolve host. Cek URL API.");
            } catch (Exception e) {
                sendLogToUI("❌ ERROR JARINGAN: " + e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Tidak perlu action ketika notifikasi dihapus
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Hentikan semua thread ketika service mati
        executor.shutdown();
        sendLogToUI("⚠️ Service dihentikan!");
    }
}
