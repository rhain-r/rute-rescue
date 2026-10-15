package com.rutarescue.ui.resident.record;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.rutarescue.R;
import com.rutarescue.exception.InvalidFamilyRecordException;
import com.rutarescue.model.center.EvacuationCenter;
import com.rutarescue.model.common.MetroManilaCity;
import com.rutarescue.model.family.FamilyMember;
import com.rutarescue.model.family.FamilyRecord;
import com.rutarescue.model.family.TrustedContact;
import com.rutarescue.model.user.UserProfile;
import com.rutarescue.repository.ListenerHandle;
import com.rutarescue.ui.common.BaseActivity;
import com.rutarescue.ui.common.Dialogs;
import com.rutarescue.validation.InputValidator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Create or edit the family evacuation record. Every value goes through RecordValidator
 * (via the FamilyRecord model) before anything is saved; a failed field is shown on its
 * own input, and nothing is stored until the whole form is valid (Lab 7 "validation
 * before persistence").
 */
public class FamilyEditActivity extends BaseActivity {

    private final Map<String, TextInputLayout> layouts = new HashMap<>();
    private final List<FamilyMember> members = new ArrayList<>();
    private final List<TrustedContact> contacts = new ArrayList<>();
    private TextInputEditText idField;
    private TextInputEditText head;
    private TextInputEditText contact;
    private TextInputEditText address;
    private MaterialAutoCompleteTextView city;
    private MaterialAutoCompleteTextView center;
    private MetroManilaCity selectedCity;
    private FamilyRecord record;
    private UserProfile profile;
    private ListenerHandle centersListener = ListenerHandle.NONE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_family_edit);
        applySystemBarInsets(findViewById(R.id.family_root));
        ((TextView) findViewById(R.id.toolbar_title)).setText(R.string.family_record);
        findViewById(R.id.toolbar_back).setOnClickListener(v -> finish());
        profile = container().session().getProfile();

        idField = findViewById(R.id.family_id);
        head = findViewById(R.id.family_head);
        contact = findViewById(R.id.family_contact);
        address = findViewById(R.id.family_address);
        city = findViewById(R.id.family_city);
        center = findViewById(R.id.family_center);
        layouts.put("familyId", findViewById(R.id.family_id_layout));
        layouts.put("headName", findViewById(R.id.family_head_layout));
        layouts.put("contactNumber", findViewById(R.id.family_contact_layout));
        layouts.put("address", findViewById(R.id.family_address_layout));
        layouts.put("city", findViewById(R.id.family_city_layout));
        layouts.put("evacuationCenter", findViewById(R.id.family_center_layout));

        city.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, MetroManilaCity.values()));
        city.setOnItemClickListener((parent, view, position, id) -> selectedCity = (MetroManilaCity) parent.getItemAtPosition(position));
        centersListener = container().centers().listenPublished(ui(data -> {
            List<String> names = new ArrayList<>();
            for (EvacuationCenter c : data.getData()) names.add(c.getName());
            center.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names));
        }));

        findViewById(R.id.family_add_member).setOnClickListener(v -> editMember(-1));
        findViewById(R.id.family_add_contact).setOnClickListener(v -> addContact());
        findViewById(R.id.family_save).setOnClickListener(v -> save());

        container().family().load(profile.getUid(), ui(data -> {
            record = data.getData();
            if (record == null) {
                container().family().nextRecordId(ui(id -> idField.setText(id)));
                selectedCity = profile.getCity();
                if (selectedCity != null) city.setText(selectedCity.toString(), false);
                head.setText(profile.getDisplayName());
            } else {
                fillForm(record);
            }
            refreshLists();
        }));
    }

    @Override
    protected void onDestroy() {
        centersListener.remove();
        super.onDestroy();
    }

    private void fillForm(FamilyRecord existing) {
        idField.setText(existing.getRecordId());
        head.setText(existing.getHeadName());
        contact.setText(existing.getContactNumber());
        address.setText(existing.getAddress());
        selectedCity = existing.getCity() == null ? profile.getCity() : existing.getCity();
        if (selectedCity != null) city.setText(selectedCity.toString(), false);
        center.setText(existing.getPreferredCenter(), false);
        members.clear();
        members.addAll(existing.getMembers());
        contacts.clear();
        contacts.addAll(existing.getTrustedContacts());
    }

    private void save() {
        for (TextInputLayout layout : layouts.values()) layout.setError(null);
        TextView membersError = findViewById(R.id.family_members_error);
        membersError.setVisibility(View.GONE);
        long now = System.currentTimeMillis();
        try {
            FamilyRecord target = record;
            if (target == null) {
                target = new FamilyRecord(Dialogs.text(idField), Dialogs.text(head), Dialogs.text(contact),
                        Dialogs.text(address), Dialogs.text(center), Math.max(1, members.size()));
            }
            target.assignOwner(profile.getUid(), selectedCity);
            target.updateDetails(Dialogs.text(head), Dialogs.text(contact), Dialogs.text(address), selectedCity,
                    Dialogs.text(center), profile.getUid(), now);
            target.replaceMembersAndContacts(members, contacts, profile.getUid(), now);
            record = target;
        } catch (InvalidFamilyRecordException invalid) {
            TextInputLayout layout = layouts.get(invalid.getField());
            if (layout != null) {
                layout.setError(invalid.getMessage());
            } else {
                membersError.setText(invalid.getMessage());
                membersError.setVisibility(View.VISIBLE);
            }
            return;
        }
        findViewById(R.id.family_save).setEnabled(false);
        container().family().save(record, ui(ignored -> {
            showMessage(container().connectivity.isOnline() ? "Family record saved"
                    : "Saved on this phone - it will sync when you are online");
            finish();
        }));
    }

    // ------------------------------------------------------------------ members and contacts

    private void refreshLists() {
        LinearLayout memberList = findViewById(R.id.family_members);
        memberList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < members.size(); i++) {
            final int index = i;
            FamilyMember member = members.get(i);
            View row = row(inflater, memberList, R.drawable.ic_record, member.getFullName(), member.describe());
            row.setOnClickListener(v -> editMember(index));
            row.findViewById(R.id.row_action).setOnClickListener(v -> {
                members.remove(index);
                refreshLists();
            });
            memberList.addView(row);
        }
        if (members.isEmpty()) memberList.addView(emptyText("Add every household member, including the head of family."));

        LinearLayout contactList = findViewById(R.id.family_contacts);
        contactList.removeAllViews();
        for (int i = 0; i < contacts.size(); i++) {
            final int index = i;
            TrustedContact item = contacts.get(i);
            View row = row(inflater, contactList, R.drawable.ic_bell, item.getName(), item.describe());
            row.findViewById(R.id.row_action).setOnClickListener(v -> {
                contacts.remove(index);
                refreshLists();
            });
            contactList.addView(row);
        }
        if (contacts.isEmpty()) contactList.addView(emptyText("Optional: someone outside the household to call."));
    }

    private View row(LayoutInflater inflater, LinearLayout parent, int icon, String title, String subtitle) {
        View row = inflater.inflate(R.layout.item_simple_row, parent, false);
        ((ImageView) row.findViewById(R.id.row_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.row_title)).setText(title);
        ((TextView) row.findViewById(R.id.row_subtitle)).setText(subtitle);
        ImageButton remove = row.findViewById(R.id.row_action);
        remove.setImageResource(R.drawable.ic_close);
        remove.setVisibility(View.VISIBLE);
        return row;
    }

    private TextView emptyText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setPadding(24, 24, 24, 24);
        view.setTextAppearance(R.style.TextAppearance_RR_Caption);
        return view;
    }

    /** Add (index -1) or edit a member. The dialog stays open until the input is valid. */
    private void editMember(int index) {
        View form = LayoutInflater.from(this).inflate(R.layout.dialog_member, null);
        TextInputEditText name = form.findViewById(R.id.member_name);
        TextInputEditText age = form.findViewById(R.id.member_age);
        MaterialCheckBox pwd = form.findViewById(R.id.member_pwd);
        MaterialCheckBox pregnant = form.findViewById(R.id.member_pregnant);
        TextInputEditText needs = form.findViewById(R.id.member_needs);
        FamilyMember existing = index >= 0 ? members.get(index) : null;
        if (existing != null) {
            name.setText(existing.getFullName());
            age.setText(String.valueOf(existing.getAge()));
            pwd.setChecked(existing.isPwd());
            pregnant.setChecked(existing.isPregnant());
            needs.setText(existing.getSupportNeeds());
        } else if (members.isEmpty()) {
            name.setText(Dialogs.text(head));
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(existing == null ? R.string.add_member : R.string.edit)
                .setView(form)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            TextInputLayout nameLayout = form.findViewById(R.id.member_name_layout);
            TextInputLayout ageLayout = form.findViewById(R.id.member_age_layout);
            nameLayout.setError(null);
            ageLayout.setError(null);
            try {
                int parsedAge = InputValidator.parseAge(Dialogs.text(age));
                FamilyMember member = new FamilyMember(existing == null ? null : existing.getMemberId(),
                        Dialogs.text(name), parsedAge, pwd.isChecked(), pregnant.isChecked(), Dialogs.text(needs));
                if (existing == null) members.add(member); else members.set(index, member);
                refreshLists();
                dialog.dismiss();
            } catch (NumberFormatException notANumber) {
                ageLayout.setError(notANumber.getMessage());
            } catch (IllegalArgumentException outOfRange) {
                ageLayout.setError(outOfRange.getMessage());
            } catch (InvalidFamilyRecordException invalid) {
                ("age".equals(invalid.getField()) ? ageLayout : nameLayout).setError(invalid.getMessage());
            }
        });
    }

    private void addContact() {
        View form = LayoutInflater.from(this).inflate(R.layout.dialog_contact, null);
        TextInputEditText name = form.findViewById(R.id.contact_name);
        TextInputEditText phone = form.findViewById(R.id.contact_phone);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.add_contact)
                .setView(form)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            TextInputLayout phoneLayout = form.findViewById(R.id.contact_phone_layout);
            TextInputLayout nameLayout = form.findViewById(R.id.contact_name_layout);
            phoneLayout.setError(null);
            nameLayout.setError(null);
            try {
                if (Dialogs.text(phone).isBlank()) {
                    throw new InvalidFamilyRecordException("contactNumber", "Enter the contact's mobile number.");
                }
                contacts.add(new TrustedContact(Dialogs.text(name), Dialogs.text(phone)));
                refreshLists();
                dialog.dismiss();
            } catch (InvalidFamilyRecordException invalid) {
                ("contactNumber".equals(invalid.getField()) ? phoneLayout : nameLayout).setError(invalid.getMessage());
            }
        });
    }
}
