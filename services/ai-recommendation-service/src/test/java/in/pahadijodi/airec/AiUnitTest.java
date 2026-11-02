package in.pahadijodi.airec;

import static org.junit.jupiter.api.Assertions.*;

import in.pahadijodi.airec.llm.PromptRegistry;
import in.pahadijodi.airec.llm.Redactor;
import in.pahadijodi.airec.service.EducationLevel;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiUnitTest {
    final Redactor redactor = new Redactor();

    @Test
    void redactsContactDetailsAndGovernmentIds() {
        String in = "Mail me at anjali.rawat@example.in or call +91 98765 43210 / 9876543210. PAN ABCDE1234F, Aadhaar 2345 6789 0123, "
                + "see https://insta.example/anjali and @anjali_r";
        String out = redactor.redact(in);
        assertFalse(out.contains("@example"), out);
        assertFalse(out.contains("98765"), out);
        assertFalse(out.contains("ABCDE1234F"), out);
        assertFalse(out.contains("6789"), out);
        assertFalse(out.contains("https://"), out);
        assertFalse(out.contains("@anjali_r"), out);
        assertTrue(out.contains("[EMAIL]") && out.contains("[PHONE]") && out.contains("[ID]") && out.contains("[LINK]") && out.contains("[HANDLE]"), out);
    }

    @Test
    void leavesOrdinaryTextAlone() {
        String s = "I am 28, from Pauri Garhwal, and work as a software engineer. I like trekking in the 2 months of summer.";
        assertEquals(s, redactor.redact(s));
        assertFalse(redactor.containsPii(s));
    }

    @Test
    void redactsInsideStructuredFacts() {
        @SuppressWarnings("unchecked")
        var out = (Map<String, Object>) redactor.redactValue(Map.of("interests", java.util.List.of("trekking", "call 9876543210")));
        assertEquals(java.util.List.of("trekking", "call [PHONE]"), out.get("interests"));
    }

    @Test
    void promptsLoadFromClasspathAndFillPlaceholders() {
        var req = new PromptRegistry().render("assistant.bio.v2",
                Map.of("max_words", "90", "tone", "warm", "language", "English", "structured_facts_json", "{}", "draft_or_empty", ""), Map.of());
        assertTrue(req.system().contains("under 90 words"), req.system());
        assertTrue(req.system().contains("exactly 3 distinct variants"));
        assertFalse(req.system().contains("{{") || req.user().contains("{{"));
        assertEquals("SMALL", req.tier());
        assertThrows(IllegalArgumentException.class, () -> new PromptRegistry().get("nope.v1"));
        var compat = new PromptRegistry().get("compat.explain.v3");
        assertEquals("LARGE", compat.tier());
        assertTrue(compat.system().contains("Saathi") && compat.system().contains("Never claim a marriage will succeed or fail"));
    }

    @Test
    void educationBuckets() {
        assertEquals(EducationLevel.GRADUATE, EducationLevel.of("B.Tech"));
        assertEquals(EducationLevel.POSTGRADUATE, EducationLevel.of("M.Tech"));
        assertEquals(EducationLevel.POSTGRADUATE, EducationLevel.of("MBA"));
        assertEquals(EducationLevel.DOCTORATE, EducationLevel.of("PhD"));
        assertEquals(EducationLevel.DIPLOMA, EducationLevel.of("Polytechnic diploma"));
        assertEquals(EducationLevel.SCHOOL, EducationLevel.of("12th"));
        assertEquals(EducationLevel.UNKNOWN, EducationLevel.of(null));
    }
}
