package in.pahadijodi.user.service;

import in.pahadijodi.user.domain.*;
import in.pahadijodi.user.domain.Repos.*;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Own transaction so a duplicate-create race (event consumer vs first GET /me) cannot poison the caller's tx. */
@Component
public class UserBootstrap {
    private final AppUsers users;
    private final Contacts contacts;
    private final Preferences prefs;

    public UserBootstrap(AppUsers users, Contacts contacts, Preferences prefs) {
        this.users = users; this.contacts = contacts; this.prefs = prefs;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void create(UUID userId) {
        users.saveAndFlush(new AppUser(userId));
        contacts.save(new Contact(userId));
        prefs.save(new Preference(userId));
    }
}
