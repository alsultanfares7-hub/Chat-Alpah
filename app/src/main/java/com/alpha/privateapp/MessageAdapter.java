package com.alpha.privateapp;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
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

    /*
     * Detects Markdown code blocks:
     *
     * ```lua
     * print("Hello")
     * ```
     *
     * Also supports:
     * ```java
     * ```javascript
     * ```python
     * ```xml
     * ```json
     * ```bash
     * etc.
     */
    private static final Pattern CODE_BLOCK =
            Pattern.compile(
                    "```(?:[a-zA-Z0-9_+#.-]+)?[ \\t]*\\r?\\n?([\\s\\S]*?)```",
                    Pattern.MULTILINE
            );

    public MessageAdapter(List<Message> items) {
        this.items = items;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).user ? 1 : 0;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int type
    ) {
        int layout = type == 1
                ? R.layout.item_message_user
                : R.layout.item_message_ai;

        View view = LayoutInflater.from(parent.getContext())
                .inflate(layout, parent, false);

        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull Holder holder,
            int position
    ) {
        Message message = items.get(position);

        if (message.user) {
            holder.text.setText(message.text);
            hideCopyButton(holder);
            return;
        }

        String response = message.text == null
                ? ""
                : message.text;

        boolean hasCode = CODE_BLOCK.matcher(response).find();

        /*
         * Render the AI response with special formatting
         * for Markdown code blocks.
         */
        holder.text.setText(
                formatResponse(response),
                TextView.BufferType.SPANNABLE
        );

        if (hasCode && holder.copyButton != null) {
            holder.copyButton.setVisibility(View.VISIBLE);

            holder.copyButton.setOnClickListener(v -> {
                String code = extractAllCode(response);

                if (code.trim().isEmpty()) {
                    return;
                }

                ClipboardManager clipboard =
                        (ClipboardManager) v.getContext()
                                .getSystemService(
                                        Context.CLIPBOARD_SERVICE
                                );

                if (clipboard != null) {
                    clipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                    "ALPHA code",
                                    code
                            )
                    );

                    Toast.makeText(
                            v.getContext(),
                            "تم نسخ الكود 📋",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });

        } else {
            hideCopyButton(holder);
        }
    }

    /**
     * Formats normal text and code blocks.
     *
     * Normal text:
     *     يبقى نصًا عاديًا.
     *
     * Code:
     *     يظهر بخط monospace وخلفية مختلفة
     *     حتى يكون واضحًا أنه كود.
     */
    private SpannableString formatResponse(String text) {

        SpannableString result =
                new SpannableString(text);

        Matcher matcher = CODE_BLOCK.matcher(text);

        while (matcher.find()) {

            int start = matcher.start();
            int end = matcher.end();

            /*
             * Make the complete code block visually distinct.
             */
            result.setSpan(
                    new BackgroundColorSpan(
                            0xFF1E1E1E
                    ),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            result.setSpan(
                    new ForegroundColorSpan(
                            0xFFF1F1F1
                    ),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            result.setSpan(
                    new TypefaceSpan(
                            Typeface.MONOSPACE
                    ),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            /*
             * Make the code block slightly emphasized.
             */
            result.setSpan(
                    new StyleSpan(Typeface.NORMAL),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        return result;
    }

    /**
     * Extract every code block from the AI response.
     *
     * The returned text contains code only.
     * Markdown ``` markers are removed.
     */
    private String extractAllCode(String text) {

        Matcher matcher = CODE_BLOCK.matcher(text);

        StringBuilder result =
                new StringBuilder();

        while (matcher.find()) {

            String code = matcher.group(1);

            if (code == null) {
                continue;
            }

            code = code.trim();

            if (code.isEmpty()) {
                continue;
            }

            if (result.length() > 0) {
                result.append("\n\n");
            }

            result.append(code);
        }

        return result.toString();
    }

    private void hideCopyButton(Holder holder) {

        if (holder.copyButton != null) {
            holder.copyButton.setVisibility(View.GONE);
            holder.copyButton.setOnClickListener(null);
        }
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

            text = itemView.findViewById(
                    R.id.messageText
            );

            copyButton = itemView.findViewById(
                    R.id.copyButton
            );
        }
    }
}
