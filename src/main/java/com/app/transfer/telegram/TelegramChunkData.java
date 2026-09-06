package com.app.transfer.telegram;

public class TelegramChunkData {

	private final byte[] data;

	public TelegramChunkData(byte[] data) {

		if (data == null) {
			throw new IllegalArgumentException("Chunk data cannot be null.");
		}

		this.data = data;
	}

	public byte[] getData() {
		return data;
	}

	public int length() {
		return data.length;
	}
}