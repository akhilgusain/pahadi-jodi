package in.pahadijodi.admin.audit;

/** Pure verifier for the hash chain: feed rows in seq order, then call {@link #finish}. */
public final class AuditChain {
    public record Result(boolean valid, long checked, Long brokenAtSeq, String reason) {
        static Result ok(long checked) { return new Result(true, checked, null, null); }
        static Result broken(long checked, long seq, String reason) { return new Result(false, checked, seq, reason); }
    }

    public static final class Verifier {
        private long expectedSeq = 1;
        private String prevHash = AuditLog.GENESIS;
        private long checked;
        private Result failure;

        /** Returns false as soon as the chain is broken; later rows are ignored. */
        public boolean accept(AuditLog row) {
            if (failure != null) return false;
            if (row.getSeq() != expectedSeq) {
                failure = Result.broken(checked, expectedSeq, "Missing or out-of-order entry (expected seq " + expectedSeq + ", found " + row.getSeq() + ")");
            } else if (!prevHash.equals(row.getPrevHash())) {
                failure = Result.broken(checked, row.getSeq(), "prev_hash does not match the previous entry");
            } else if (!row.computeHash().equals(row.getHash())) {
                failure = Result.broken(checked, row.getSeq(), "Entry content does not match its hash (modified)");
            }
            if (failure != null) return false;
            checked++;
            expectedSeq++;
            prevHash = row.getHash();
            return true;
        }

        /** Also checks the pinned head, which catches deletion of the newest entries. */
        public Result finish(long headSeq, String headHash) {
            if (failure != null) return failure;
            if (headSeq != checked || !headHash.equals(prevHash)) {
                return Result.broken(checked, checked + 1, "Chain tail does not match audit_head (entries removed from the end?)");
            }
            return Result.ok(checked);
        }
    }

    private AuditChain() {}
}
