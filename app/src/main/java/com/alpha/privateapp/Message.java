package com.alpha.privateapp;

public class Message {
    public final String text;
    public final boolean user;
    // Optional local path for an attached image. Keeping it with the message
    // lets later requests resend the image as part of the conversation context.
    public final String imagePath;

    public Message(String text, boolean user) {
        this(text, user, null);
    }

    public Message(String text, boolean user, String imagePath) {
        this.text = text == null ? "" : text;
        this.user = user;
        this.imagePath = imagePath;
    }
}
