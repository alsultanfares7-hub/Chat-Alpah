package com.alpha.privateapp;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class AiClient {

    // رابط سيرفر Railway الخاص بك
    private static final String API_URL = "https://chat-alpah-production.up.railway.app/api/chat";
    
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void ask(String prompt, Consumer<String> callback) {
        executor.execute(() -> {
            String answer;
            try {
                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(10000); // 10 ثواني مهلة اتصال
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);

                // تجهيز طلب الـ JSON
                JSONObject jsonParam = new JSONObject();
                jsonParam.put("message", prompt);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonParam.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int statusCode = conn.getResponseCode();
                InputStream is = (statusCode >= 200 && statusCode < 300) 
                        ? conn.getInputStream() 
                        : conn.getErrorStream();

                Scanner scanner = new Scanner(is, "UTF-8").useDelimiter("\\A");
                String responseStr = scanner.hasNext() ? scanner.next() : "";

                if (statusCode == 200) {
                    JSONObject jsonResponse = new JSONObject(responseStr);
                    answer = jsonResponse.optString("reply", "لم يصل رد من السيرفر.");
                } else {
                    answer = "خطأ في الاتصال بالسيرفر (" + statusCode + ")";
                }

            } catch (Exception e) {
                answer = "عذراً، تعذر الاتصال بالخادم. تأكد من اتصال الإنترنت.";
            }

            // إرجاع النتيجة للواجهة الرئيسية (Main Thread)
            String finalAnswer = answer;
            mainHandler.post(() -> callback.accept(finalAnswer));
        });
    }
}
