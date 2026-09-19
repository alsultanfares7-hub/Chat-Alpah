package com.alpha.privateapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private EditText input;
    private ImageButton sendButton;
    private MessageAdapter adapter;
    private ArrayList<Message> messages;
    private AiClient aiClient;

    private String pendingImageBase64 = null;

    private ActivityResultLauncher<String> imagePicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        input = findViewById(R.id.input);
        sendButton = findViewById(R.id.sendButton);

        ImageButton addButton = findViewById(R.id.addButton);
        RecyclerView list = findViewById(R.id.messages);

        messages = new ArrayList<>();
        adapter = new MessageAdapter(messages);

        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        aiClient = new AiClient();

        messages.add(new Message(
                "هلا 👋\nأنا ALPHA. اكتب أي فكرة ونبدأ.",
                false
        ));
        adapter.notifyItemInserted(0);

        imagePicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) {
                        return;
                    }

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

        addButton.setOnClickListener(v ->
                imagePicker.launch("image/*")
        );
    }

    private void sendMessage() {
        String prompt = input.getText().toString().trim();

        if (prompt.isEmpty()) {
            if (pendingImageBase64 != null) {
                prompt = "حلل هذه الصورة ووضح لي ما فيها.";
            } else {
                return;
            }
        }

        // Add the user message to the local conversation first.
        addMessage(new Message(prompt, true));

        input.setText("");
        sendButton.setEnabled(false);

        String image = pendingImageBase64;
        pendingImageBase64 = null;

        // Copy a snapshot so the callback cannot change the request history.
        ArrayList<Message> requestHistory = new ArrayList<>(messages);

        aiClient.ask(requestHistory, image, answer -> {
            addMessage(new Message(answer, false));
            sendButton.setEnabled(true);
        });
    }

    private String imageToBase64(Uri uri) throws Exception {
        Bitmap bitmap;

        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            bitmap = BitmapFactory.decodeStream(inputStream);
        }

        if (bitmap == null) {
            throw new Exception("Unable to decode image");
        }

        // Resize large images to keep the request manageable.
        int maxSize = 1600;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        if (width > maxSize || height > maxSize) {
            float scale = Math.min(
                    (float) maxSize / width,
                    (float) maxSize / height
            );

            bitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    Math.round(width * scale),
                    Math.round(height * scale),
                    true
            );
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        bitmap.compress(
                Bitmap.CompressFormat.JPEG,
                85,
                output
        );

        bitmap.recycle();

        return android.util.Base64.encodeToString(
                output.toByteArray(),
                android.util.Base64.NO_WRAP
        );
    }

    private void addMessage(Message message) {
        int position = messages.size();

        messages.add(message);
        adapter.notifyItemInserted(position);

        RecyclerView list = findViewById(R.id.messages);
        list.scrollToPosition(position);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
