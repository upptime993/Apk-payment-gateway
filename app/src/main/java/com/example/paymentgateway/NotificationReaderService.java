package com.example.paymentgateway;

import android.app.Notification;
import android.content.Intent;
import android.content.SharedPreferences;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationReaderService extends NotificationListenerService {

    private static final String API_URL = "https://self-payment-gateway.vercel.app/api/notify";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Fungsi untuk mengirim teks ke Terminal di halaman depan
    private void sendLogToUI(String message) {
        Intent intent = new Intent("com.paymentgateway.LOG");
        intent.putExtra("log", message);
        sendBroadcast(intent);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        SharedPreferences prefs = getSharedPreferences("PaymentConfig", MODE_PRIVATE);
        String targetPackage = prefs.getString("packageName", "com.shopee.id");
        String regexPattern = prefs.getString("regex", "(?i)Rp\\s*([\\d\\.]+)");

        String currentPackage = sbn.getPackageName();
        
        // Hanya intip notifikasi dari target package
        if (!currentPackage.equals(targetPackage)) return;

        Notification notification = sbn.getNotification();
        if (notification == null || notification.extras == null) return;

        String text = notification.extras.getString(Notification.EXTRA_TEXT);
        if (text == null) return;

        sendLogToUI("Notif " + targetPackage + " Masuk: " + text);

        // Mulai mencocokkan teks dengan Regex buatanmu
        Pattern pattern = Pattern.compile(regexPattern);
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            String nominalString = matcher.group(1).replace(".", ""); 
            
            try {
                int nominalMasuk = Integer.parseInt(nominalString);
                sendLogToUI("=> Ditemukan nominal: Rp" + nominalMasuk);
                sendLogToUI("=> Mengirim data ke Vercel...");
                sendWebhookToServer(nominalMasuk);
                
            } catch (NumberFormatException e) {
                sendLogToUI("=> Error parsing nominal: " + nominalString);
            }
        } else {
            sendLogToUI("=> Teks notif tidak cocok dengan Regex.");
        }
    }

    private void sendWebhookToServer(int nominal) {
        executor.execute(() -> {
            try {
                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String jsonInputString = "{\"nominal_masuk\": " + nominal + "}";

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if(responseCode == 200 || responseCode == 201) {
                    sendLogToUI("SUKSES! Vercel memproses webhook. (Code " + responseCode + ")");
                } else {
                    sendLogToUI("GAGAL! Server menolak. (Code " + responseCode + ")");
                }
                
            } catch (Exception e) {
                sendLogToUI("ERROR JARINGAN: " + e.getMessage());
            }
        });
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) { }
}
