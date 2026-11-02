package in.pahadijodi.admin;

import static org.junit.jupiter.api.Assertions.*;

import in.pahadijodi.admin.audit.AuditChain;
import in.pahadijodi.admin.audit.AuditLog;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuditChainTest {
    private static List<AuditLog> chain(int n) {
        List<AuditLog> rows = new ArrayList<>();
        long seq = 0;
        String prev = AuditLog.GENESIS;
        for (int i = 1; i <= n; i++) {
            AuditLog r = AuditLog.next(seq, prev, "admin-1", "ADMIN", "ACTION_" + i, "USER", "u" + i, "{\"n\":" + i + "}", Instant.parse("2026-03-01T10:00:00.123456Z").plusSeconds(i));
            rows.add(r);
            seq = r.getSeq();
            prev = r.getHash();
        }
        return rows;
    }

    private static AuditChain.Result verify(List<AuditLog> rows, long headSeq, String headHash) {
        var v = new AuditChain.Verifier();
        for (AuditLog r : rows) if (!v.accept(r)) break;
        return v.finish(headSeq, headHash);
    }

    private static AuditChain.Result verify(List<AuditLog> rows) {
        return rows.isEmpty() ? verify(rows, 0, AuditLog.GENESIS) : verify(rows, rows.size(), rows.get(rows.size() - 1).getHash());
    }

    private static void set(Object o, String field, Object v) throws Exception {
        Field f = o.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(o, v);
    }

    @Test
    void intactChainVerifiesAndEmptyChainIsValid() {
        var r = verify(chain(25));
        assertTrue(r.valid());
        assertEquals(25, r.checked());
        assertTrue(verify(List.of()).valid());
    }

    @Test
    void hashCoversPreviousHashAndEveryField() {
        var a = chain(2);
        assertEquals(64, a.get(0).getHash().length());
        assertEquals(a.get(0).getHash(), a.get(1).getPrevHash());
        assertEquals(AuditLog.GENESIS, a.get(0).getPrevHash());
        assertEquals(a.get(0).getHash(), a.get(0).computeHash());
        // timestamps are fixed to millisecond precision so they survive database round trips
        assertEquals(0, a.get(0).getCreatedAt().getNano() % 1_000_000);
    }

    @Test
    void modifyingAnyFieldIsDetectedAtThatRow() throws Exception {
        for (String field : List.of("details", "actorId", "action", "targetId")) {
            var rows = chain(5);
            set(rows.get(2), field, "tampered");
            var r = verify(rows);
            assertFalse(r.valid(), field);
            assertEquals(3L, r.brokenAtSeq(), field);
        }
    }

    @Test
    void deletingAMiddleRowIsDetected() {
        var rows = chain(6);
        rows.remove(3);   // seq 4 missing
        var r = verify(rows, 6, chain(6).get(5).getHash());
        assertFalse(r.valid());
        assertEquals(4L, r.brokenAtSeq());
    }

    @Test
    void deletingTheNewestRowsIsDetectedViaHead() {
        var full = chain(6);
        var truncated = new ArrayList<>(full.subList(0, 4));
        var r = verify(truncated, 6, full.get(5).getHash());   // head still points at seq 6
        assertFalse(r.valid());
        assertTrue(r.reason().contains("audit_head"));
    }

    @Test
    void reorderedRowsAreDetected() {
        var rows = chain(4);
        var swapped = new ArrayList<>(List.of(rows.get(0), rows.get(2), rows.get(1), rows.get(3)));
        assertFalse(verify(swapped, 4, rows.get(3).getHash()).valid());
    }

    @Test
    void recomputingASingleRowsHashDoesNotHideTheEditBecauseTheNextRowLinksToTheOldHash() throws Exception {
        var rows = chain(4);
        set(rows.get(1), "details", "tampered");
        set(rows.get(1), "hash", rows.get(1).computeHash());   // attacker fixes the row's own hash
        var r = verify(rows, 4, rows.get(3).getHash());
        assertFalse(r.valid());
        assertEquals(3L, r.brokenAtSeq());   // row 3's prev_hash no longer matches
    }
}
