package com.rutarescue.ui.resident.ligtas;

import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.rutarescue.R;
import com.rutarescue.exception.ValidationException;
import com.rutarescue.model.family.FamilyMember;
import com.rutarescue.model.family.FamilyRecord;
import com.rutarescue.model.ligtas.NeedCategory;
import com.rutarescue.model.user.UserProfile;
import com.rutarescue.ui.common.BaseActivity;
import com.rutarescue.ui.common.Dialogs;
import com.rutarescue.ui.common.ImageCompressor;
import com.rutarescue.ui.common.UiCallback;

import java.io.IOException;
import java.util.List;

/**
 * Ligtas Priority application (DOCX section 4): choose the family member and need
 * category, write a justification and attach a SYNTHETIC/DEMO image only.
 *
 * The bundled "SYNTHETIC DEMO PROOF" image is attached when the screen opens, so the
 * application can be tested on any phone without a real document. "Choose image" replaces
 * it, and "Use demo proof" puts it back.
 */
public class LigtasApplyActivity extends BaseActivity {

    /** The synthetic proof image shipped inside the APK (app/src/main/assets/demo). */
    static final String DEMO_PROOF_ASSET = "demo/demo-proof.png";

    private FamilyRecord family;
    private FamilyMember member;
    private NeedCategory category;
    private byte[] proofJpeg;
    private UserProfile profile;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickImage = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(), this::onImagePicked);

    /** Reads and shrinks one proof image (on a background thread). */
    private interface ProofReader {
        byte[] read() throws IOException;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ligtas_apply);
        applySystemBarInsets(findViewById(R.id.apply_root));
        ((TextView) findViewById(R.id.toolbar_title)).setText(R.string.ligtas_apply_title);
        findViewById(R.id.toolbar_back).setOnClickListener(v -> finish());
        profile = container().session().getProfile();

        MaterialAutoCompleteTextView categoryField = findViewById(R.id.apply_category);
        categoryField.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, NeedCategory.values()));
        categoryField.setOnItemClickListener((parent, view, position, id) ->
                category = (NeedCategory) parent.getItemAtPosition(position));

        findViewById(R.id.apply_pick).setOnClickListener(v -> pickImage.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build()));
        findViewById(R.id.apply_use_demo).setOnClickListener(v -> attachDemoProof());
        findViewById(R.id.apply_submit).setOnClickListener(v -> submit());
        attachDemoProof(); // ready to submit without a real document

        container().family().load(profile.getUid(), ui(data -> {
            family = data.getData();
            if (family == null || family.getMembers().isEmpty()) {
                showMessage("Create your family record with its members first (My Record > Create record).");
                findViewById(R.id.apply_submit).setEnabled(false);
                return;
            }
            MaterialAutoCompleteTextView memberField = findViewById(R.id.apply_member);
            List<FamilyMember> members = family.getMembers();
            String[] names = new String[members.size()];
            for (int i = 0; i < names.length; i++) names[i] = members.get(i).getFullName() + " (" + members.get(i).describe() + ")";
            memberField.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names));
            memberField.setOnItemClickListener((parent, view, position, id) -> {
                member = members.get(position);
                suggestCategory(member, categoryField);
            });
        }));
    }

    /** Pre-selects an obvious category from the member's flags (the resident can change it). */
    private void suggestCategory(FamilyMember selected, MaterialAutoCompleteTextView field) {
        NeedCategory guess = selected.isPregnant() ? NeedCategory.PREGNANT
                : selected.isPwd() ? NeedCategory.PERSON_WITH_DISABILITY
                : selected.isSenior() ? NeedCategory.SENIOR_CITIZEN
                : selected.getAge() < 5 ? NeedCategory.INFANT_OR_YOUNG_CHILD
                : !selected.getSupportNeeds().isEmpty() ? NeedCategory.MEDICAL_NEED : null;
        if (guess != null) {
            category = guess;
            field.setText(guess.toString(), false);
        }
    }

    /** Attaches the bundled "SYNTHETIC DEMO PROOF" image. */
    private void attachDemoProof() {
        prepareProof(() -> ImageCompressor.compressAsset(getAssets(), DEMO_PROOF_ASSET),
                getString(R.string.demo_proof_attached));
    }

    private void onImagePicked(Uri uri) {
        if (uri == null) return;
        prepareProof(() -> ImageCompressor.compress(getContentResolver(), uri),
                getString(R.string.picked_proof_attached));
    }

    /** Shrinks the image off the UI thread, then shows it as the attached proof. */
    private void prepareProof(ProofReader reader, String label) {
        TextView info = findViewById(R.id.apply_image_info);
        info.setText("Preparing image…");
        container().executors.io().execute(() -> {
            try {
                byte[] bytes = reader.read();
                container().executors.main().execute(() -> {
                    if (isFinishing()) return;
                    proofJpeg = bytes;
                    ImageView preview = findViewById(R.id.apply_preview);
                    preview.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
                    preview.setVisibility(View.VISIBLE);
                    info.setText(label + " (" + Math.max(1, bytes.length / 1024) + " KB)");
                });
            } catch (IOException e) {
                container().executors.main().execute(() -> info.setText(e.getMessage()));
            }
        });
    }

    private void submit() {
        TextInputLayout memberLayout = findViewById(R.id.apply_member_layout);
        TextInputLayout categoryLayout = findViewById(R.id.apply_category_layout);
        TextInputLayout justificationLayout = findViewById(R.id.apply_justification_layout);
        memberLayout.setError(null);
        categoryLayout.setError(null);
        justificationLayout.setError(null);
        if (category == null) {
            categoryLayout.setError("Choose a need category.");
            return;
        }
        View progress = findViewById(R.id.apply_progress);
        View submit = findViewById(R.id.apply_submit);
        progress.setVisibility(View.VISIBLE);
        submit.setEnabled(false);
        String justification = Dialogs.text((TextInputEditText) findViewById(R.id.apply_justification));
        container().ligtas().submit(profile, family, member, category, justification, proofJpeg,
                UiCallback.of(this, application -> {
                    showMessage("Application submitted. Your LGU will review it.");
                    finish();
                }, error -> {
                    progress.setVisibility(View.GONE);
                    submit.setEnabled(true);
                    if (error instanceof ValidationException) {
                        String field = ((ValidationException) error).getField();
                        if ("member".equals(field) || "memberName".equals(field)) memberLayout.setError(error.getMessage());
                        else if ("justification".equals(field)) justificationLayout.setError(error.getMessage());
                        else showError(error);
                    } else {
                        showError(error);
                    }
                }));
    }
}
