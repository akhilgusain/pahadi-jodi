package in.pahadijodi.notification.domain;

import static in.pahadijodi.notification.domain.Channel.*;

import java.util.List;

public enum NotificationType {
    WELCOME(Category.TRANSACTIONAL, "/profile", IN_APP, EMAIL),
    INTEREST_RECEIVED(Category.ENGAGEMENT, "/interests", IN_APP, PUSH, EMAIL),
    INTEREST_ACCEPTED(Category.ENGAGEMENT, "/interests", IN_APP, PUSH, EMAIL),
    MATCH_CREATED(Category.ENGAGEMENT, "/matches", IN_APP, PUSH, WHATSAPP),
    NEW_MESSAGE(Category.ENGAGEMENT, "/chat", IN_APP, PUSH),
    PAYMENT_SUCCESS(Category.TRANSACTIONAL, "/subscription", IN_APP, EMAIL, SMS),
    SUBSCRIPTION_EXPIRED(Category.TRANSACTIONAL, "/subscription", IN_APP, EMAIL, PUSH),
    PROFILE_VERIFIED(Category.TRANSACTIONAL, "/profile", IN_APP, PUSH, EMAIL);

    private final Category category;
    private final String deepLink;
    private final List<Channel> channels;

    NotificationType(Category category, String deepLink, Channel... channels) {
        this.category = category;
        this.deepLink = deepLink;
        this.channels = List.of(channels);
    }

    public Category category() { return category; }
    public String deepLink() { return deepLink; }
    public List<Channel> channels() { return channels; }
}
