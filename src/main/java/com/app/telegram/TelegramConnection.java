package com.app.telegram;

public class TelegramConnection {

    private final String botToken;
    private final String chatId;

    private volatile boolean connected;

    public TelegramConnection(
            String botToken,
            String chatId) {

        this.botToken = botToken;
        this.chatId = chatId;
        this.connected = false;
    }

    public String getBotToken() {
        return botToken;
    }

    public String getChatId() {
        return chatId;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }
}