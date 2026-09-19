package com.alpha.privateapp;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class AiClient {

    private static final String API_URL =
            "https://chat-alpah-production.up.railway.app/api/chat";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void ask(ArrayList<Message> history, Consumer<String> callback) {
        ask(history, null, callback);
    }

    public void ask(ArrayList<Message> history, String imageBase64,
                    Consumer<String> callback) {
        executor.execute(() -> {
            String answer;

            try {
                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(120000);
                conn.setDoOutput(true);

                JSONObject payload = new JSONObject();
                JSONArray messages = new JSONArray();

                // Send the conversation history so ALPHA understands
                // references such as "عدله" and "ما عجبني".
                for (Message message : history) {
                    JSONObject item = new JSONObject();
                    item.put("role", message.user ? "user" : "assistant");
                    item.put("content", message.text);
                    messages.put(item);
                }

                // If an image was selected, attach it to the latest user message.
                if (imageBase64 != null && !imageBase64.isEmpty() && messages.length() > 0) {
                    JSONObject last = messages.getJSONObject(messages.length() - 1);

                    if ("user".equals(last.optString("role"))) {
                        JSONArray content = new JSONArray();

                        JSONObject textPart = new JSONObject();
                        textPart.put("type", "text");
                        textPart.put("text", last.optString("content"));
                        content.put(textPart);

                        JSONObject imagePart = new JSONObject();
                        imagePart.put("type", "image_url");

                        JSONObject imageUrl = new JSONObject();
                        imageUrl.put(
                                "url",
                                "data:image/jpeg;base64," + imageBase64
                        );

                        imagePart.put("image_url", imageUrl);
                        content.put(imagePart);

                        last.put("content", content);
                    }
                }

                payload.put("messages", messages);

                byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body);
                }

                int statusCode = conn.getResponseCode();

                InputStream stream =
                        statusCode >= 200 && statusCode < 300
                                ? conn.getInputStream()
                                : conn.getErrorStream();

                String responseStr = "";

                if (stream != null) {
                    try (Scanner scanner =
                                 new Scanner(stream, StandardCharsets.UTF_8)
                                         .useDelimiter("\\A")) {
                        if (scanner.hasNext()) {
                            responseStr = scanner.next();
                        }
                    }
                }

                if (statusCode >= 200 && statusCode < 300) {
                    JSONObject response = new JSONObject(responseStr);
                    answer = response.optString(
                            "reply",
                            "لم يصل رد من السيرفر."
                    );
                } else {
                    JSONObject error = responseStr.isEmpty()
                            ? null
                            : new JSONObject(responseStr);

                    answer = error != null
                            ? error.optString(
                                    "error",
                                    "خطأ في الاتصال بالسيرفر (" + statusCode + ")"
                            )
                            : "خطأ في الاتصال بالسيرفر (" + statusCode + ")";
                }

                conn.disconnect();

            } catch (Exception e) {
                answer =
                        "تعذر الاتصال بالخادم. تأكد من الإنترنت وحاول مرة أخرى.";
            }

            String finalAnswer = answer;
            mainHandler.post(() -> callback.accept(finalAnswer));
        });
    }
}
