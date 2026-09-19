package com.alpha.privateapp;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.Holder> {

    private final List<Message> items;

    private static final Pattern CODE_BLOCK =
            Pattern.compile("```(?:[a-zA-Z0-9_+#.-]+)?\\s*([\\s\\S]*?)```");

    public MessageAdapter(List<Message> items) {
        this.items = items;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).user ? 1 : 0;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        int layout = type == 1
                ? R.layout.item_message_user
                : R.layout.item_message_ai;

        View view = LayoutInflater.from(parent.getContext())
                .inflate(layout, parent, false);

        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Message message = items.get(position);

        holder.text.setText(message.text);

        if (!message.user && holder.copyButton != null) {
            Matcher matcher = CODE_BLOCK.matcher(message.text);

            if (matcher.find()) {
                holder.copyButton.setVisibility(View.VISIBLE);

                holder.copyButton.setOnClickListener(v -> {
                    String code = extractAllCode(message.text);

                    ClipboardManager clipboard =
                            (ClipboardManager) v.getContext()
                                    .getSystemService(Context.CLIPBOARD_SERVICE);

                    if (clipboard != null) {
                        clipboard.setPrimaryClip(
                                ClipData.newPlainText("ALPHA code", code)
                        );

                        Toast.makeText(
                                v.getContext(),
                                "تم نسخ الكود 📋",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                });
            } else {
                holder.copyButton.setVisibility(View.GONE);
                holder.copyButton.setOnClickListener(null);
            }
        }
    }

    private String extractAllCode(String text) {
        Matcher matcher = CODE_BLOCK.matcher(text);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            if (result.length() > 0) {
                result.append("\n\n");
            }

            result.append(matcher.group(1).trim());
        }

        return result.toString();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {

        final TextView text;
        final Button copyButton;

        Holder(@NonNull View itemView) {
            super(itemView);

            text = itemView.findViewById(R.id.messageText);
            copyButton = itemView.findViewById(R.id.copyButton);
        }
    }
}
