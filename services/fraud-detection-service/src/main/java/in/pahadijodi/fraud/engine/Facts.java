package in.pahadijodi.fraud.engine;

/** Everything the rules need, pre-computed from stored signals so the engine itself stays pure. */
public record Facts(
        int accountsFromIp1h,
        int accountsFromDevice,
        boolean disposableEmail,
        boolean duplicateProfileText,
        boolean duplicatePhoto,
        int scamHits24h,
        int distinctRecipients1h,
        int paymentFailures24h,
        int chargebacks) {

    public static Facts none() {
        return new Facts(0, 0, false, false, false, 0, 0, 0, 0);
    }
}
