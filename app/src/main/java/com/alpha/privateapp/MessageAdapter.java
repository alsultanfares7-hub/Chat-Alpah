package com.alpha.privateapp;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.LeadingMarginSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.Holder> {

    public interface OnSaveCodeListener {
        void onSaveCode(String code);
    }

    private final List<Message> items;
    private final OnSaveCodeListener saveCodeListener;

    private static final Pattern CODE_BLOCK = Pattern.compile(
            "```[ \\t]*([a-zA-Z0-9_+#.-]+)?[ \\t]*\\r?\\n([\\s\\S]*?)```",
            Pattern.MULTILINE
    );

    private static final Pattern BOLD = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`\\n]+)`");

    public MessageAdapter(List<Message> items) {
        this(items, null);
    }

    public MessageAdapter(List<Message> items, OnSaveCodeListener saveCodeListener) {
        this.items = items;
        this.saveCodeListener = saveCodeListener;
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

        if (message.user) {
            holder.text.setText(message.text);
            hideCopyButton(holder);
            return;
        }

        String response = message.text == null ? "" : message.text;
        boolean hasCode = CODE_BLOCK.matcher(response).find();

        holder.text.setText(
                formatResponse(response),
                TextView.BufferType.SPANNABLE
        );

        if (hasCode && !response.trim().isEmpty()) {
            if (holder.codeActions != null) {
                holder.codeActions.setVisibility(View.VISIBLE);
            }
            if (holder.copyButton != null) {
                holder.copyButton.setVisibility(View.VISIBLE);
                holder.copyButton.setText("نسخ الكود  📋");
                holder.copyButton.setOnClickListener(v -> copyAllCode(v.getContext(), response));
            }
            if (holder.saveButton != null) {
                holder.saveButton.setVisibility(
                        saveCodeListener == null ? View.GONE : View.VISIBLE
                );
                holder.saveButton.setOnClickListener(
                        v -> {
                            if (saveCodeListener != null) {
                                saveCodeListener.onSaveCode(extractAllCode(response));
                            }
                        }
                );
            }
        } else {
            hideCopyButton(holder);
        }
    }

    private SpannableString formatResponse(String text) {
        android.text.SpannableStringBuilder result =
                new android.text.SpannableStringBuilder();

        Matcher codeMatcher = CODE_BLOCK.matcher(text);
        int cursor = 0;

        while (codeMatcher.find()) {
            if (codeMatcher.start() > cursor) {
                appendNormalMarkdown(
                        result,
                        text.substring(cursor, codeMatcher.start())
                );
            }

            String code = codeMatcher.group(2);
            if (code != null) {
                String cleanCode = code.replaceFirst("^\\n", "");
                if (cleanCode.endsWith("\n")) {
                    cleanCode = cleanCode.substring(0, cleanCode.length() - 1);
                }

                int start = result.length();
                result.append(cleanCode);
                int end = result.length();

                result.setSpan(
                        new BackgroundColorSpan(0xFF1E1E1E),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                result.setSpan(
                        new ForegroundColorSpan(0xFFF2F2F2),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                result.setSpan(
                        new TypefaceSpan(Typeface.MONOSPACE),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                result.setSpan(
                        new LeadingMarginSpan.Standard(12, 12),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );

                if (codeMatcher.end() < text.length()) {
                    result.append("\n");
                }
            }

            cursor = codeMatcher.end();
        }

        if (cursor < text.length()) {
            appendNormalMarkdown(result, text.substring(cursor));
        }

        return new SpannableString(result);
    }

    private void appendNormalMarkdown(
            android.text.SpannableStringBuilder result,
            String text
    ) {
        Matcher matcher = Pattern.compile(
                "(\\*\\*([^*]+)\\*\\*)|(`([^`\\n]+)`)"
        ).matcher(text);

        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                appendHeaderAware(result, text.substring(cursor, matcher.start()));
            }

            if (matcher.group(2) != null) {
                int start = result.length();
                result.append(matcher.group(2));
                int end = result.length();
                result.setSpan(
                        new StyleSpan(Typeface.BOLD),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            } else {
                int start = result.length();
                result.append(matcher.group(4));
                int end = result.length();
                result.setSpan(
                        new BackgroundColorSpan(0xFF202020),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                result.setSpan(
                        new TypefaceSpan(Typeface.MONOSPACE),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }

            cursor = matcher.end();
        }

        if (cursor < text.length()) {
            appendHeaderAware(result, text.substring(cursor));
        }
    }

    private void appendHeaderAware(
            android.text.SpannableStringBuilder result,
            String text
    ) {
        Pattern header = Pattern.compile("(?m)^(#{1,3})[ \\t]+(.+)$");
        Matcher matcher = header.matcher(text);
        int cursor = 0;

        while (matcher.find()) {
            if (matcher.start() > cursor) {
                result.append(text.substring(cursor, matcher.start()));
            }

            int start = result.length();
            result.append(matcher.group(2));
            int end = result.length();
            result.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            result.setSpan(
                    new AbsoluteSizeSpan(17, true),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            cursor = matcher.end();
        }

        if (cursor < text.length()) {
            result.append(text.substring(cursor));
        }
    }

    private boolean insideCode(int position, ArrayList<int[]> ranges) {
        for (int[] range : ranges) {
            if (position >= range[0] && position < range[1]) {
                return true;
            }
        }
        return false;
    }

    private String extractAllCode(String response) {
        Matcher matcher = CODE_BLOCK.matcher(response);
        StringBuilder code = new StringBuilder();

        while (matcher.find()) {
            String block = matcher.group(2);
            if (block == null) continue;

            String clean = block.trim();
            if (clean.isEmpty()) continue;

            if (code.length() > 0) {
                code.append("\n\n");
            }
            code.append(clean);
        }

        return code.toString();
    }

    private void copyAllCode(Context context, String response) {
        String code = extractAllCode(response);
        if (code.isEmpty()) return;

        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);

        if (clipboard != null) {
            clipboard.setPrimaryClip(
                    ClipData.newPlainText("ALPHA code", code.toString())
            );
            Toast.makeText(
                    context,
                    "تم نسخ الكود 📋",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void hideCopyButton(Holder holder) {
        if (holder.codeActions != null) {
            holder.codeActions.setVisibility(View.GONE);
        }
        if (holder.copyButton != null) {
            holder.copyButton.setVisibility(View.GONE);
            holder.copyButton.setOnClickListener(null);
        }
        if (holder.saveButton != null) {
            holder.saveButton.setVisibility(View.GONE);
            holder.saveButton.setOnClickListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView text;
        final LinearLayout codeActions;
        final Button copyButton;
        final Button saveButton;

        Holder(@NonNull View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.messageText);
            codeActions = itemView.findViewById(R.id.codeActions);
            copyButton = itemView.findViewById(R.id.copyButton);
            saveButton = itemView.findViewById(R.id.saveButton);
        }
    }
}
