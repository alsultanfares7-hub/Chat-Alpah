package com.alpha.privateapp;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class AiClient {

    private static final String BASE_URL = BuildConfig.API_BASE_URL;
    private static final String CHAT_URL = BASE_URL + "/api/chat";
    private static final String STREAM_URL = BASE_URL + "/api/chat/stream";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void ask(ArrayList<Message> history, String imageBase64,
                    Consumer<String> onResult) {
        executor.execute(() -> {
            String answer;
            try {
                answer = performRequest(history, imageBase64);
            } catch (Exception e) {
                answer = friendlyError(e);
            }
            String finalAnswer = answer;
            mainHandler.post(() -> onResult.accept(finalAnswer));
        });
    }

    public void askStreaming(
            ArrayList<Message> history,
            String imageBase64,
            Consumer<String> onDelta,
            Runnable onDone,
            Consumer<String> onError
    ) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = openConnection(STREAM_URL);
                JSONObject payload = buildPayload(history, imageBase64);

                writeBody(conn, payload);
                int status = conn.getResponseCode();

                if (status < 200 || status >= 300) {
                    String body = readStream(conn.getErrorStream());
                    throw new ApiException(status, extractError(body));
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.startsWith("data:")) {
                            continue;
                        }

                        String data = line.substring(5).trim();
                        if (data.isEmpty()) {
                            continue;
                        }

                        JSONObject event = new JSONObject(data);
                        String type = event.optString("type");

                        if ("delta".equals(type)) {
                            String text = event.optString("text", "");
                            if (!text.isEmpty()) {
                                postMain(() -> onDelta.accept(text));
                            }
                        } else if ("error".equals(type)) {
                            throw new ApiException(
                                    500,
                                    event.optString("message", "حدث خطأ أثناء إنشاء الرد.")
                            );
                        } else if ("done".equals(type)) {
                            break;
                        }
                    }
                }

                postMain(onDone);
            } catch (Exception e) {
                String message = e instanceof ApiException
                        ? e.getMessage()
                        : friendlyError(e);
                postMain(() -> onError.accept(message));
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    private String performRequest(ArrayList<Message> history, String imageBase64)
            throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = openConnection(CHAT_URL);
            writeBody(conn, buildPayload(history, imageBase64));

            int status = conn.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? conn.getInputStream()
                    : conn.getErrorStream();
            String response = readStream(stream);

            if (status < 200 || status >= 300) {
                throw new ApiException(status, extractError(response));
            }

            JSONObject json = new JSONObject(response);
            String answer = json.optString("reply", "").trim();
            return answer.isEmpty() ? "لم يصل رد من السيرفر." : answer;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private HttpURLConnection openConnection(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "text/event-stream, application/json");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(180000);
        conn.setDoOutput(true);
        return conn;
    }

    private JSONObject buildPayload(ArrayList<Message> history, String imageBase64)
            throws Exception {
        JSONObject payload = new JSONObject();
        JSONArray messages = new JSONArray();

        for (Message message : history) {
            JSONObject item = new JSONObject();
            item.put("role", message.user ? "user" : "assistant");
            item.put("content", message.text);
            messages.put(item);
        }

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
                imageUrl.put("url", "data:image/jpeg;base64," + imageBase64);
                imagePart.put("image_url", imageUrl);
                content.put(imagePart);

                last.put("content", content);
            }
        }

        payload.put("messages", messages);
        return payload;
    }

    private void writeBody(HttpURLConnection conn, JSONObject payload) throws Exception {
        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = conn.getOutputStream()) {
            output.write(body);
            output.flush();
        }
    }

    private String readStream(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append('\n');
            }
        }
        return result.toString().trim();
    }

    private String extractError(String response) {
        if (response == null || response.isEmpty()) {
            return "تعذر الاتصال بالخادم.";
        }

        try {
            JSONObject json = new JSONObject(response);
            return json.optString("error", "تعذر معالجة الطلب.");
        } catch (Exception ignored) {
            return response.length() > 300
                    ? response.substring(0, 300)
                    : response;
        }
    }

    private String friendlyError(Exception e) {
        if (e instanceof ApiException) {
            ApiException api = (ApiException) e;
            if (api.status == 401 || api.status == 502) {
                return api.getMessage();
            }
            if (api.status == 429) {
                return "تم الوصول إلى حد الاستخدام. حاول بعد قليل.";
            }
            return api.getMessage();
        }
        return "تعذر الاتصال بالخادم. تأكد من الإنترنت وحاول مرة أخرى.";
    }

    private void postMain(Runnable runnable) {
        mainHandler.post(runnable);
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private static class ApiException extends Exception {
        final int status;

        ApiException(int status, String message) {
            super(message == null || message.isEmpty()
                    ? "حدث خطأ في الاتصال بالسيرفر (" + status + ")"
                    : message);
            this.status = status;
        }
    }
}
