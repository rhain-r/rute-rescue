package com.rutarescue.model.family;

import com.rutarescue.exception.InvalidFamilyRecordException;
import com.rutarescue.model.common.AuditableEntity;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.validation.RecordValidator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A family evacuation record (DOCX section 4 "My Record"; Labs 5-7).
 *
 * Encapsulation:
 *  - every field is private and validated by RecordValidator before it is stored;
 *  - updates go through methods that validate ALL new values first and only then
 *    assign them, so a failed update never leaves a half-changed record;
 *  - the member count is derived from the member list (DOCX section 4).
 *
 * The six legacy fields (ID | head | contact | address | center | size) are the same as
 * the Lab 6/7 family_records.txt format, so the record can still be imported/exported.
 */
public class FamilyRecord extends AuditableEntity {

    private final String recordId;
    private String ownerUid;
    private String headName;
    private String contactNumber;
    private String address;
    private MetroManilaCity city;
    private String preferredCenter;
    /** Size written in a legacy file. Used until real member details are entered. */
    private int declaredSize;
    private final List<FamilyMember> members = new ArrayList<>();
    private final List<TrustedContact> trustedContacts = new ArrayList<>();

    /** Lab constructor: builds a validated record from the six legacy fields. */
    public FamilyRecord(String recordId, String headName, String contactNumber, String address,
                        String preferredCenter, int declaredSize) throws InvalidFamilyRecordException {
        super(null, null, false);
        this.recordId = RecordValidator.familyId(recordId);
        this.headName = RecordValidator.headName(headName);
        this.contactNumber = RecordValidator.contactNumber(contactNumber);
        this.address = RecordValidator.address(address);
        this.preferredCenter = RecordValidator.evacuationCenter(preferredCenter);
        this.declaredSize = RecordValidator.familySize(declaredSize);
    }

    @Override
    public String getEntityType() {
        return "family";
    }

    /**
     * Validated update of the main details. All values are checked first; if any value is
     * invalid an exception is thrown and nothing changes.
     */
    public void updateDetails(String newHeadName, String newContact, String newAddress,
                              MetroManilaCity newCity, String newPreferredCenter, String actorUid, long now)
            throws InvalidFamilyRecordException {
        String checkedHead = RecordValidator.headName(newHeadName);
        String checkedContact = RecordValidator.contactNumber(newContact);
        String checkedAddress = RecordValidator.address(newAddress);
        String checkedCenter = RecordValidator.evacuationCenter(newPreferredCenter);
        if (newCity == null) {
            throw new InvalidFamilyRecordException("city", "Please choose your city or municipality.");
        }
        headName = checkedHead;
        contactNumber = checkedContact;
        address = checkedAddress;
        city = newCity;
        preferredCenter = checkedCenter;
        markUpdated(actorUid, now);
    }

    /** Replaces the member and contact lists in one step (used by the edit screen). */
    public void replaceMembersAndContacts(List<FamilyMember> newMembers, List<TrustedContact> newContacts,
                                          String actorUid, long now) throws InvalidFamilyRecordException {
        if (newMembers == null || newMembers.isEmpty()) {
            throw new InvalidFamilyRecordException("members", "Add at least one family member (including the head of family).");
        }
        if (newMembers.size() > 100) {
            throw new InvalidFamilyRecordException("members", "Family size must be between 1 and 100.");
        }
        members.clear();
        members.addAll(newMembers);
        trustedContacts.clear();
        if (newContacts != null) {
            trustedContacts.addAll(newContacts);
        }
        declaredSize = members.size();
        markUpdated(actorUid, now);
    }

    /** Used only while loading/importing; does not mark the record as updated. */
    public void restoreMembersAndContacts(List<FamilyMember> savedMembers, List<TrustedContact> savedContacts) {
        members.clear();
        if (savedMembers != null) members.addAll(savedMembers);
        trustedContacts.clear();
        if (savedContacts != null) trustedContacts.addAll(savedContacts);
    }

    public void addTrustedContact(TrustedContact contact) {
        if (contact != null) trustedContacts.add(contact);
    }

    public void assignOwner(String uid, MetroManilaCity homeCity) {
        if (uid == null || uid.isBlank()) throw new IllegalArgumentException("Owner uid is required.");
        ownerUid = uid;
        if (city == null) city = homeCity;
    }

    public void restoreCity(MetroManilaCity savedCity) {
        city = savedCity;
    }

    /** Lab 5 toFileString(): the pipe-delimited legacy row for family_records.txt. */
    public String toFileString() {
        return String.join("|", recordId, headName, contactNumber, address, preferredCenter,
                String.valueOf(getFamilySize()));
    }

    /** Member count derived from the member list; falls back to the imported size. */
    public int getFamilySize() {
        return members.isEmpty() ? declaredSize : members.size();
    }

    /**
     * DOCX section 9: an imported size must agree with the reconstructed member count,
     * otherwise the record is flagged for correction.
     */
    public boolean needsMemberReview() {
        return members.isEmpty() || members.size() != declaredSize;
    }

    public boolean hasPriorityMember() {
        for (FamilyMember member : members) {
            if (member.needsPriorityHelp()) return true;
        }
        return false;
    }

    public String getRecordId() { return recordId; }
    public String getOwnerUid() { return ownerUid; }
    public String getHeadName() { return headName; }
    public String getContactNumber() { return contactNumber; }
    public String getAddress() { return address; }
    public MetroManilaCity getCity() { return city; }
    public String getPreferredCenter() { return preferredCenter; }
    public int getDeclaredSize() { return declaredSize; }

    public List<FamilyMember> getMembers() {
        return Collections.unmodifiableList(members);
    }

    public List<TrustedContact> getTrustedContacts() {
        return Collections.unmodifiableList(trustedContacts);
    }

    public FamilyMember findMember(String memberId) {
        for (FamilyMember member : members) {
            if (member.getMemberId().equals(memberId)) return member;
        }
        return null;
    }

    /** Case-insensitive ID comparison used for duplicate detection (Lab 7). */
    public boolean hasSameIdAs(FamilyRecord other) {
        return other != null && recordId.equalsIgnoreCase(other.recordId);
    }
}
