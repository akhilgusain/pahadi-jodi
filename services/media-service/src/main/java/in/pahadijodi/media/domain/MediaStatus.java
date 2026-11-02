package in.pahadijodi.media.domain;

/** UPLOAD_PENDING -> UPLOADED -> SCANNING -> READY | REJECTED */
public enum MediaStatus { UPLOAD_PENDING, UPLOADED, SCANNING, READY, REJECTED }
