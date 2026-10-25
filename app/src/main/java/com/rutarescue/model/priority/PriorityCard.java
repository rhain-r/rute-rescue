package com.rutarescue.model.priority;

import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.common.AuditableEntity;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.user.ResidentProfile;
import com.rutarescue.model.user.StaffProfile;
import com.rutarescue.validation.TextRules;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A resident's priority ID (2026-10-22): issued at registration to a senior citizen, a person
 * with disability or a pregnant resident. The resident saves it as a picture; at the evacuation
 * center a moderator scans its QR, sees this record and goes through the checklist (a valid ID,
 * the group's document, and a few details), marking each item OK or Missing.
 *
 * Privacy: the QR holds only "RUTARESCUE:PRIORITY:" and the random code, never a name. The code
 * is also printed under the QR so it can be typed when a camera cannot read it.
 *
 * Encapsulation: the check only changes through recordCheck(), by a moderator, for known items.
 */
public class PriorityCard extends AuditableEntity {

    public static final String QR_PREFIX = "RUTARESCUE:PRIORITY:";
    /** No 0/O or 1/I, so a code read aloud or typed from the picture is not mistaken. */
    private static final String CODE_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 12;
    private static final Pattern QR_PATTERN = Pattern.compile("RUTARESCUE:PRIORITY:([A-Z0-9]{12})");
    private static final Random RANDOM = new SecureRandom();

    /** Checked for every group: who the person is and that they live in the city. */
    private static final ChecklistItem VALID_ID = new ChecklistItem("valid_id",
            "Valid ID with the same name as this priority ID", ChecklistItem.Kind.DOCUMENT);
    private static final ChecklistItem ADDRESS = new ChecklistItem("address",
            "Home address in the city on the ID card (or a barangay certificate)", ChecklistItem.Kind.DETAIL);
    private static final ChecklistItem CONTACT = new ChecklistItem("emergency_contact",
            "Emergency contact person and phone number", ChecklistItem.Kind.DETAIL);

    private final String ownerUid;
    private final String holderName;
    private final PriorityGroup group;
    private final MetroManilaCity city;
    private final long issuedAt;
    private final Map<String, CheckResult> checks = new LinkedHashMap<>();
    private String flagNote = "";
    private String checkedBy = "";
    private String checkedByName = "";
    private long checkedAt;
    private String checkedAtCenter = "";

    /** Full constructor used by the repository when it reads a stored card. */
    public PriorityCard(String id, String ownerUid, String holderName, PriorityGroup group, MetroManilaCity city,
                        long issuedAt, Map<String, CheckResult> checks, String flagNote, String checkedBy,
                        String checkedByName, long checkedAt, String checkedAtCenter) {
        super(id, null, false);
        if (group == null) throw new IllegalArgumentException("A priority ID needs a priority group.");
        this.ownerUid = ownerUid == null ? "" : ownerUid;
        this.holderName = holderName == null ? "" : holderName;
        this.group = group;
        this.city = city;
        this.issuedAt = issuedAt;
        if (checks != null) this.checks.putAll(checks);
        this.flagNote = flagNote == null ? "" : flagNote;
        this.checkedBy = checkedBy == null ? "" : checkedBy;
        this.checkedByName = checkedByName == null ? "" : checkedByName;
        this.checkedAt = checkedAt;
        this.checkedAtCenter = checkedAtCenter == null ? "" : checkedAtCenter;
    }

    /** Issues the ID to a resident who registered in a priority group (nothing checked yet). */
    public static PriorityCard issue(String code, ResidentProfile resident, PriorityGroup group, long now)
            throws ValidationException {
        if (resident == null) throw new ValidationException("owner", "Sign in first.");
        if (group == null) throw new ValidationException("priorityGroup", "Choose a priority group.");
        if (resident.getCity() == null) throw new ValidationException("city", "Your account needs a city first.");
        if (normalizeCode(code) == null) throw new IllegalArgumentException("Not a priority ID code: " + code);
        PriorityCard card = new PriorityCard(code, resident.getUid(), resident.getDisplayName(), group,
                resident.getCity(), now, null, "", "", "", 0L, "");
        card.markUpdated(resident.getUid(), now);
        return card;
    }

    /** A new random 12-character code (also the database id). */
    public static String newCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) code.append(CODE_LETTERS.charAt(RANDOM.nextInt(CODE_LETTERS.length())));
        return code.toString();
    }

    @Override
    public String getEntityType() {
        return "priorityId";
    }

    /** The checklist for this card: a valid ID, the group's document, the address, a contact, the group's detail. */
    public List<ChecklistItem> getChecklist() {
        List<ChecklistItem> items = new ArrayList<>();
        items.add(VALID_ID);
        items.add(group.getDocument());
        items.add(ADDRESS);
        items.add(CONTACT);
        items.add(group.getDetail());
        return items;
    }

    /**
     * A moderator saves what they found at the center. Every item must be marked OK or Missing;
     * the note (up to 300 characters) tells the resident what to bring.
     */
    public void recordCheck(StaffProfile moderator, Map<String, CheckResult> results, String note, String centerName,
                            long now) throws ValidationException {
        if (moderator == null || !moderator.canVerifyPriorityIds()) {
            throw new IllegalStateException("Only a moderator at an evacuation center checks priority IDs.");
        }
        Map<String, CheckResult> checked = new LinkedHashMap<>();
        for (ChecklistItem item : getChecklist()) {
            CheckResult result = results == null ? null : results.get(item.getKey());
            if (result == null || result == CheckResult.NOT_CHECKED) {
                throw new ValidationException("checks", "Mark every item OK or Missing: " + item.getLabel());
            }
            checked.put(item.getKey(), result);
        }
        String checkedNote = TextRules.optionalText("note", note);
        TextRules.checkLength("note", checkedNote, 0, 300);
        checks.clear();
        checks.putAll(checked);
        flagNote = checkedNote;
        checkedBy = moderator.getUid();
        checkedByName = moderator.getDisplayName();
        checkedAt = now;
        checkedAtCenter = centerName == null ? "" : centerName.strip();
        markUpdated(moderator.getUid(), now);
    }

    public CheckResult resultOf(ChecklistItem item) {
        CheckResult result = checks.get(item.getKey());
        return result == null ? CheckResult.NOT_CHECKED : result;
    }

    /** The items a moderator marked Missing at the last check. */
    public List<ChecklistItem> getMissingItems() {
        List<ChecklistItem> missing = new ArrayList<>();
        for (ChecklistItem item : getChecklist()) {
            if (resultOf(item) == CheckResult.MISSING) missing.add(item);
        }
        return missing;
    }

    public boolean isChecked() {
        return checkedAt > 0;
    }

    /** "Not checked yet", "Complete" or "2 items missing". */
    public String getStatusLabel() {
        if (!isChecked()) return "Not checked yet";
        int missing = getMissingItems().size();
        if (missing == 0) return "Complete";
        return missing == 1 ? "1 item missing" : missing + " items missing";
    }

    /** QR content: only the prefix and the opaque code. */
    public String getQrPayload() {
        return QR_PREFIX + getId();
    }

    /** "ABCD-EFGH-JKLM": printed under the QR for typing. */
    public String getDisplayCode() {
        String id = getId();
        return id.substring(0, 4) + "-" + id.substring(4, 8) + "-" + id.substring(8);
    }

    /**
     * Reads the code from a scanned QR or from what a moderator typed ("abcd-efgh-jklm" works too);
     * null when it is not a priority ID.
     */
    public static String parseQrPayload(String scanned) {
        if (scanned == null) return null;
        Matcher matcher = QR_PATTERN.matcher(scanned.strip());
        return normalizeCode(matcher.matches() ? matcher.group(1) : scanned);
    }

    /** A typed code without spaces or dashes, in capitals; null if it cannot be one of ours. */
    private static String normalizeCode(String typed) {
        if (typed == null) return null;
        String code = typed.replaceAll("[\\s-]", "").toUpperCase(java.util.Locale.ROOT);
        if (code.length() != CODE_LENGTH) return null;
        for (int i = 0; i < code.length(); i++) {
            if (CODE_LETTERS.indexOf(code.charAt(i)) < 0) return null;
        }
        return code;
    }

    public String getOwnerUid() { return ownerUid; }
    public String getHolderName() { return holderName; }
    public PriorityGroup getGroup() { return group; }
    public MetroManilaCity getCity() { return city; }
    public long getIssuedAt() { return issuedAt; }
    public Map<String, CheckResult> getChecks() { return Collections.unmodifiableMap(checks); }
    public String getFlagNote() { return flagNote; }
    public String getCheckedBy() { return checkedBy; }
    public String getCheckedByName() { return checkedByName; }
    public long getCheckedAt() { return checkedAt; }
    public String getCheckedAtCenter() { return checkedAtCenter; }
}
