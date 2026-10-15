package com.rutarescue.model.family;

import com.rutarescue.exception.InvalidFamilyRecordException;
import com.rutarescue.validation.RecordValidator;

import java.util.UUID;

/**
 * One person in a family evacuation record (DOCX section 4 "My Record").
 * Child and senior flags are derived from the age so they can never disagree with it.
 */
public final class FamilyMember {

    public static final int CHILD_AGE_LIMIT = 18;
    public static final int SENIOR_AGE = 60;

    private final String memberId;
    private final String fullName;
    private final int age;
    private final boolean pwd;
    private final boolean pregnant;
    private final String supportNeeds;

    public FamilyMember(String memberId, String fullName, int age, boolean pwd, boolean pregnant,
                        String supportNeeds) throws InvalidFamilyRecordException {
        this.memberId = memberId == null || memberId.isBlank() ? UUID.randomUUID().toString() : memberId;
        this.fullName = RecordValidator.personName("memberName", fullName);
        this.age = RecordValidator.age(age);
        this.pwd = pwd;
        this.pregnant = pregnant;
        this.supportNeeds = RecordValidator.optionalText("supportNeeds", supportNeeds);
    }

    public String getMemberId() {
        return memberId;
    }

    public String getFullName() {
        return fullName;
    }

    public int getAge() {
        return age;
    }

    public boolean isChild() {
        return age < CHILD_AGE_LIMIT;
    }

    public boolean isSenior() {
        return age >= SENIOR_AGE;
    }

    public boolean isPwd() {
        return pwd;
    }

    public boolean isPregnant() {
        return pregnant;
    }

    public String getSupportNeeds() {
        return supportNeeds;
    }

    /** True when the member may need priority help during evacuation. */
    public boolean needsPriorityHelp() {
        return isSenior() || pwd || pregnant || age < 5 || !supportNeeds.isEmpty();
    }

    /** Short description, e.g. "Senior - 67 years - Needs medication". */
    public String describe() {
        StringBuilder out = new StringBuilder();
        if (isChild()) {
            out.append("Child");
        } else if (isSenior()) {
            out.append("Senior");
        } else {
            out.append("Adult");
        }
        out.append(" • ").append(age).append(age == 1 ? " year" : " years");
        if (pwd) out.append(" • PWD");
        if (pregnant) out.append(" • Pregnant");
        if (!supportNeeds.isEmpty()) out.append(" • ").append(supportNeeds);
        return out.toString();
    }
}
