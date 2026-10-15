package com.rutarescue.model.center;

/**
 * Where a center record came from and whether the LGU has verified it (DOCX section 4:
 * "Each record shows its verification source and timestamp"). Immutable.
 */
public final class VerificationInfo {

    private final boolean verified;
    private final String source;
    private final String verifiedBy;
    private final long verifiedAt;

    public VerificationInfo(boolean verified, String source, String verifiedBy, long verifiedAt) {
        this.verified = verified;
        this.source = source == null || source.isBlank() ? "Unknown source" : source.strip();
        this.verifiedBy = verifiedBy == null ? "" : verifiedBy;
        this.verifiedAt = verifiedAt;
    }

    public static VerificationInfo unverified(String source) {
        return new VerificationInfo(false, source, "", 0L);
    }

    public static VerificationInfo verified(String source, String verifiedBy, long verifiedAt) {
        return new VerificationInfo(true, source, verifiedBy, verifiedAt);
    }

    public boolean isVerified() {
        return verified;
    }

    public String getSource() {
        return source;
    }

    public String getVerifiedBy() {
        return verifiedBy;
    }

    public long getVerifiedAt() {
        return verifiedAt;
    }
}
