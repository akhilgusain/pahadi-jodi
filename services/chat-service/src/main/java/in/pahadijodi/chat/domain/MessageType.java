package in.pahadijodi.chat.domain;

public enum MessageType {
    TEXT, IMAGE, VIDEO, VOICE_NOTE;

    public boolean isMedia() { return this != TEXT; }
}
