package in.pahadijodi.fraud;

import static org.junit.jupiter.api.Assertions.*;

import in.pahadijodi.fraud.text.*;
import java.util.Random;
import org.junit.jupiter.api.Test;

class TextUtilsTest {
    static final String A = "I am a software engineer from Pauri Garhwal working in Dehradun. I love trekking, Garhwali folk music "
            + "and cooking traditional food with my family. Looking for a caring partner who values family and traditions.";
    static final String A_REFORMATTED = "  I AM a software engineer from Pauri Garhwal working in Dehradun!! I love trekking, Garhwali folk music "
            + "and cooking traditional food with my family... Looking for a caring partner who values family and traditions.  ";
    static final String UNRELATED = "Chartered accountant based in Haldwani, enjoys cricket and reading history books. Wants a well educated "
            + "and independent life partner, preferably from Kumaon, who enjoys travelling during the winters every year.";

    @Test
    void formattingOnlyChangesGiveIdenticalHash() {
        assertEquals(Simhash.hash(A), Simhash.hash(A_REFORMATTED));
    }

    @Test
    void nearDuplicateIsCloserThanUnrelatedText() {
        String near = A.replace("software engineer", "software developer");
        int dNear = Simhash.hamming(Simhash.hash(A), Simhash.hash(near));
        int dFar = Simhash.hamming(Simhash.hash(A), Simhash.hash(UNRELATED));
        assertTrue(dNear < dFar, "near=" + dNear + " far=" + dFar);
        assertTrue(dFar > 10, "unrelated texts should differ in many bits, was " + dFar);
    }

    @Test
    void bandingGuaranteesCandidateWhenDistanceAtMostThree() {
        Random rnd = new Random(42);
        for (int i = 0; i < 2000; i++) {
            long a = rnd.nextLong();
            long b = a;
            for (int k = 0; k < 3; k++) b ^= 1L << rnd.nextInt(64);
            boolean shared = false;
            for (int band = 0; band < 4; band++) shared |= Simhash.band(a, band) == Simhash.band(b, band);
            assertTrue(shared);
        }
    }

    @Test
    void scamAnalyzerCountsDistinctPhraseFamilies() {
        assertEquals(0, ScamTextAnalyzer.hits("Hi, I enjoy trekking in Uttarakhand. What do you do on weekends?"));
        assertEquals(1, ScamTextAnalyzer.hits("Please send me money"));
        assertTrue(ScamTextAnalyzer.hits("It's an emergency, my mother is sick in hospital. Buy gift cards and send money on Western Union") >= 3);
        assertTrue(ScamTextAnalyzer.hits("Let's move to WhatsApp, I can show you a great crypto investment") >= 2);
        assertEquals(0, ScamTextAnalyzer.hits(null));
    }

    @Test
    void disposableEmailDomains() {
        assertTrue(DisposableEmails.isDisposable("Mailinator.com"));
        assertFalse(DisposableEmails.isDisposable("gmail.com"));
        assertFalse(DisposableEmails.isDisposable(null));
    }
}
