package com.alpha.privateapp;

import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private EditText input;
    private ImageButton sendButton;
    private MessageAdapter adapter;
    private ArrayList<Message> messages;
    private AiClient aiClient;

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

        sendButton.setOnClickListener(v -> sendMessage());

        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        addButton.setOnClickListener(v ->
                Toast.makeText(
                        this,
                        "إرفاق الملفات — جاهز للمرحلة التالية",
                        Toast.LENGTH_SHORT
                ).show()
        );
    }

    private void sendMessage() {
        String prompt = input.getText().toString().trim();

        if (prompt.isEmpty()) {
            return;
        }

        addMessage(new Message(prompt, true));
        input.setText("");

        sendButton.setEnabled(false);

        aiClient.ask(prompt, answer -> {
            addMessage(new Message(answer, false));
            sendButton.setEnabled(true);
        });
    }

    private void addMessage(Message message) {
        int position = messages.size();

        messages.add(message);
        adapter.notifyItemInserted(position);

        RecyclerView list = findViewById(R.id.messages);
        list.scrollToPosition(position);
    }
}
