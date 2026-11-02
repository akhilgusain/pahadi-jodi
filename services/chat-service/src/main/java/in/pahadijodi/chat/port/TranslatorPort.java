package in.pahadijodi.chat.port;

public interface TranslatorPort {
    /** @param targetLang ISO code, currently "en" or "hi". */
    String translate(String text, String targetLang);
}
