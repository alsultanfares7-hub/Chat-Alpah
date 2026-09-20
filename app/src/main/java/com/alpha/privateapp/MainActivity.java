package com.alpha.privateapp;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "alpha_chat";
    private static final String KEY_MESSAGES = "messages";
    private static final String WELCOME = "هلا 👋\nأنا ALPHA. اكتب أي فكرة ونبدأ.";

    private EditText input;
    private ImageButton sendButton;
    private RecyclerView list;
    private MessageAdapter adapter;
    private ArrayList<Message> messages;
    private AiClient aiClient;
    private SharedPreferences preferences;

    private String pendingImageBase64;
    private ActivityResultLauncher<String> imagePicker;
    private boolean waitingForResponse;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        input = findViewById(R.id.input);
        sendButton = findViewById(R.id.sendButton);
        ImageButton addButton = findViewById(R.id.addButton);
        list = findViewById(R.id.messages);

        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        messages = loadMessages();
        adapter = new MessageAdapter(messages);
        aiClient = new AiClient();

        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        if (messages.isEmpty()) {
            addMessageInternal(new Message(WELCOME, false), false);
        }

        list.post(this::scrollToBottom);

        imagePicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) return;

                    try {
                        pendingImageBase64 = imageToBase64(uri);
                        Toast.makeText(
                                this,
                                "تم إرفاق الصورة. اكتب سؤالك عنها ثم أرسل.",
                                Toast.LENGTH_SHORT
                        ).show();
                    } catch (Exception e) {
                        pendingImageBase64 = null;
                        Toast.makeText(
                                this,
                                "تعذر قراءة الصورة.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );

        sendButton.setOnClickListener(v -> sendMessage());

        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        addButton.setOnClickListener(v -> {
            if (!waitingForResponse) {
                imagePicker.launch("image/*");
            }
        });
    }

    private void sendMessage() {
        if (waitingForResponse) return;

        String prompt = input.getText().toString().trim();
        if (prompt.isEmpty()) {
            if (pendingImageBase64 != null) {
                prompt = "حلل هذه الصورة ووضح لي ما فيها بالتفصيل.";
            } else {
                return;
            }
        }

        addMessageInternal(new Message(prompt, true), true);
        input.setText("");
        hideKeyboard();

        String image = pendingImageBase64;
        pendingImageBase64 = null;

        ArrayList<Message> requestHistory = createRequestHistory();
        setWaiting(true);

        int aiPosition = messages.size();
        addMessageInternal(new Message("", false), true);

        final StringBuilder streamedText = new StringBuilder();

        aiClient.askStreaming(
                requestHistory,
                image,
                delta -> {
                    streamedText.append(delta);
                    updateMessage(aiPosition, streamedText.toString());
                },
                () -> {
                    String finalText = streamedText.toString().trim();
                    if (finalText.isEmpty()) {
                        finalText = "لم يصل رد من الخادم.";
                    }
                    updateMessage(aiPosition, finalText);
                    setWaiting(false);
                },
                error -> {
                    String message = error == null || error.trim().isEmpty()
                            ? "تعذر الاتصال بالخادم."
                            : error;
                    updateMessage(aiPosition, message);
                    setWaiting(false);
                }
        );
    }

    private ArrayList<Message> createRequestHistory() {
        ArrayList<Message> history = new ArrayList<>(messages);

        // The welcome bubble is UI-only and should not become part of the AI context.
        if (!history.isEmpty()) {
            Message first = history.get(0);
            if (!first.user && WELCOME.equals(first.text)) {
                history.remove(0);
            }
        }

        return history;
    }

    private void setWaiting(boolean waiting) {
        waitingForResponse = waiting;
        sendButton.setEnabled(!waiting);
        input.setEnabled(!waiting);

        if (waiting) {
            sendButton.setAlpha(0.55f);
            input.setAlpha(0.7f);
        } else {
            sendButton.setAlpha(1f);
            input.setAlpha(1f);
            input.requestFocus();
        }
    }

    private void addMessage(Message message) {
        addMessageInternal(message, true);
    }

    private void addMessageInternal(Message message, boolean save) {
        int position = messages.size();
        messages.add(message);
        adapter.notifyItemInserted(position);
        scrollToBottom();

        if (save) {
            saveMessages();
        }
    }

    private void updateMessage(int position, String text) {
        if (position < 0 || position >= messages.size()) return;

        Message old = messages.get(position);
        messages.set(position, new Message(text, old.user));
        adapter.notifyItemChanged(position);
        saveMessages();
        scrollToBottom();
    }

    private void scrollToBottom() {
        if (adapter.getItemCount() > 0) {
            list.scrollToPosition(adapter.getItemCount() - 1);
        }
    }

    private void hideKeyboard() {
        InputMethodManager manager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
    }

    private String imageToBase64(Uri uri) throws Exception {
        Bitmap bitmap;

        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(inputStream);
        }

        if (bitmap == null) {
            throw new Exception("Unable to decode image");
        }

        int maxSize = 1600;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if (width > maxSize || height > maxSize) {
            float scale = Math.min(
                    (float) maxSize / width,
                    (float) maxSize / height
            );

            Bitmap resized = Bitmap.createScaledBitmap(
                    bitmap,
                    Math.round(width * scale),
                    Math.round(height * scale),
                    true
            );

            bitmap.recycle();
            bitmap = resized;
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output);
        bitmap.recycle();

        return android.util.Base64.encodeToString(
                output.toByteArray(),
                android.util.Base64.NO_WRAP
        );
    }

    private void saveMessages() {
        try {
            JSONArray array = new JSONArray();
            int start = Math.max(0, messages.size() - 40);

            for (int i = start; i < messages.size(); i++) {
                Message message = messages.get(i);
                JSONObject item = new JSONObject();
                item.put("text", message.text);
                item.put("user", message.user);
                array.put(item);
            }

            preferences.edit().putString(KEY_MESSAGES, array.toString()).apply();
        } catch (Exception ignored) {
            // Persistence should never break the chat UI.
        }
    }

    private ArrayList<Message> loadMessages() {
        ArrayList<Message> result = new ArrayList<>();
        String raw = preferences.getString(KEY_MESSAGES, "");

        if (raw.isEmpty()) return result;

        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                result.add(new Message(
                        item.optString("text", ""),
                        item.optBoolean("user", false)
                ));
            }
        } catch (Exception ignored) {
            result.clear();
        }

        return result;
    }

    @Override
    protected void onDestroy() {
        if (aiClient != null) {
            aiClient.shutdown();
        }
        super.onDestroy();
    }
}
