package in.pahadijodi.fraud.service;

import java.util.UUID;

/** Port: "has this user uploaded a photo that another account already uses?" */
public interface PhotoDuplicateDetector {
    void register(UUID userId, long perceptualHash);

    boolean hasDuplicate(UUID userId);
}
