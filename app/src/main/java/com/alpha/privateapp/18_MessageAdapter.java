package com.alpha.privateapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.Holder> {
    private final List<Message> items;

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
        holder.text.setText(items.get(position).text);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView text;

        Holder(@NonNull View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.messageText);
        }
    }
}
