package com.rutarescue.model.user;

import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.ligtas.LigtasApplication;

/**
 * Common data of every signed-in person.
 *
 * Inheritance + polymorphism: ResidentProfile and LguProfile are real specialisations of
 * a user, and they answer role questions (canReview, getRoleLabel) differently. The UI
 * can treat any UserProfile the same way and let the subclass decide.
 *
 * NOTE: UI checks are only for convenience. The real enforcement is in the Firestore and
 * Storage security rules (firebase/firestore.rules).
 */
public abstract class UserProfile {

    private final String uid;
    private final String displayName;
    private final String email;
    private final MetroManilaCity city;

    protected UserProfile(String uid, String displayName, String email, MetroManilaCity city) {
        if (uid == null || uid.isBlank()) {
            throw new IllegalArgumentException("A user profile needs a uid.");
        }
        this.uid = uid;
        this.displayName = displayName == null || displayName.isBlank() ? "RutaRescue user" : displayName.strip();
        this.email = email == null ? "" : email.strip();
        this.city = city;
    }

    public abstract Role getRole();

    /** Human readable role label shown on screens. */
    public abstract String getRoleLabel();

    /** True only for LGU staff assigned to the application's jurisdiction. */
    public abstract boolean canReview(LigtasApplication application);

    /** True when this user may publish advisories/centers for the given city. */
    public abstract boolean canManage(MetroManilaCity targetCity);

    public String getUid() {
        return uid;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** First name for greetings ("Good morning, Maria."). */
    public String getFirstName() {
        String[] parts = displayName.split("\\s+");
        return parts.length == 0 ? displayName : parts[0];
    }

    public String getEmail() {
        return email;
    }

    /** Home city of a resident, or the assigned jurisdiction of LGU staff (may be null). */
    public MetroManilaCity getCity() {
        return city;
    }
}
