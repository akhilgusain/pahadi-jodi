package in.pahadijodi.chat.adapter;

import in.pahadijodi.chat.port.TranslatorPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Does not translate: prefixes the language tag so the flow is visible end to end. */
@Component
@ConditionalOnProperty(name = "pj.chat.translator.provider", havingValue = "fake", matchIfMissing = true)
public class FakeTranslatorAdapter implements TranslatorPort {
    @Override
    public String translate(String text, String targetLang) {
        return "[" + targetLang + "] " + text;
    }
}
