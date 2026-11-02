package in.pahadijodi.notification.service;

import in.pahadijodi.notification.domain.NotificationType;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** en/hi templates from classpath:notification-templates_<locale>.properties (UTF-8), "{name}" placeholders. */
@Component
public class TemplateCatalog {
    public record Rendered(String title, String body) {}

    private final Map<String, Properties> bundles = new ConcurrentHashMap<>();

    public Rendered render(NotificationType type, String locale, Map<String, String> params) {
        Properties primary = bundle(locale);
        Properties fallback = bundle("en");
        return new Rendered(fill(lookup(primary, fallback, type + ".title"), params), fill(lookup(primary, fallback, type + ".body"), params));
    }

    private static String lookup(Properties primary, Properties fallback, String key) {
        String v = primary.getProperty(key);
        if (v == null) v = fallback.getProperty(key);
        if (v == null) throw new IllegalStateException("Missing notification template " + key);
        return v;
    }

    private static String fill(String template, Map<String, String> params) {
        String out = template;
        for (var e : params.entrySet()) out = out.replace("{" + e.getKey() + "}", e.getValue());
        return out;
    }

    private Properties bundle(String locale) {
        String l = "hi".equals(locale) ? "hi" : "en";
        return bundles.computeIfAbsent(l, k -> {
            Properties p = new Properties();
            try (var in = new InputStreamReader(new ClassPathResource("notification-templates_" + k + ".properties").getInputStream(), StandardCharsets.UTF_8)) {
                p.load(in);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot load templates for " + k, e);
            }
            return p;
        });
    }
}
