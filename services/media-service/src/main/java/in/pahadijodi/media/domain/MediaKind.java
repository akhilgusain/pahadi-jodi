package in.pahadijodi.media.domain;

public enum MediaKind {
    PHOTO, VIDEO, DOCUMENT, VOICE, SELFIE;

    public boolean isImage() { return this == PHOTO || this == SELFIE; }
}
