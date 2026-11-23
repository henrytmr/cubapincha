package com.cubapincha.job.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.cubapincha.job.R;
import com.cubapincha.job.data.RegisterRequest;
import com.cubapincha.job.data.Repository;
import com.cubapincha.job.data.SessionManager;
import com.cubapincha.job.util.AnimUtils;
import com.cubapincha.job.util.PhotoPicker;
import com.cubapincha.job.util.TextUtils;

/** Registro de trabajador en 3 pasos, con "Saltar por ahora". */
public class RegisterWorkerFragment extends Fragment {
    private int step = 1;
    private View step1, step2, step3, btnSkip;
    private MaterialButton btnNext;
    private TextView stepLabel;
    private LinearProgressIndicator stepProgress;
    private ProgressBar progress;
    private TextInputEditText firstName, lastName, email, password, phone;
    private TextInputEditText headline, skills, experience, salaryMin, salaryMax;
    private TextInputEditText locality, contactHours;
    private AutoCompleteTextView province, municipality, workMode, availability;
    private AutoCompleteTextView mobility, salaryPeriod, contactPreference;
    private CheckBox terms, relocate, remote;
    private TextInputLayout emailLayout, passwordLayout;
    private PhotoPicker photoPicker;
    private ImageView photoPreview;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        photoPicker = new PhotoPicker(this, new PhotoPicker.Callback() {
            @Override
            public void onPhotoPicked(Uri uri) {
                SessionManager.get(requireContext()).setPhotoUri(uri.toString());
                if (photoPreview != null) {
                    photoPreview.setImageURI(uri);
                    photoPreview.setVisibility(View.VISIBLE);
                }
                if (isAdded()) {
                    Snackbar.make(requireView(), R.string.photo_added, Snackbar.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onPhotoDenied(String reason) {
                if (isAdded()) {
                    Snackbar.make(requireView(),
                            "camera_error".equals(reason) ? R.string.camera_error : R.string.camera_denied,
                            Snackbar.LENGTH_LONG).show();
                }
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_register_worker, container, false);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("step", step);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainActivity activity = (MainActivity) requireActivity();
        step1 = view.findViewById(R.id.step1);
        step2 = view.findViewById(R.id.step2);
        step3 = view.findViewById(R.id.step3);
        btnNext = view.findViewById(R.id.btn_next);
        btnSkip = view.findViewById(R.id.btn_skip);
        stepLabel = view.findViewById(R.id.step_label);
        stepProgress = view.findViewById(R.id.step_progress);
        progress = view.findViewById(R.id.progress);
        firstName = view.findViewById(R.id.input_first_name);
        lastName = view.findViewById(R.id.input_last_name);
        email = view.findViewById(R.id.input_email);
        password = view.findViewById(R.id.input_password);
        phone = view.findViewById(R.id.input_phone);
        headline = view.findViewById(R.id.input_headline);
        skills = view.findViewById(R.id.input_skills);
        experience = view.findViewById(R.id.input_experience);
        salaryMin = view.findViewById(R.id.input_salary_min);
        salaryMax = view.findViewById(R.id.input_salary_max);
        locality = view.findViewById(R.id.input_locality);
        contactHours = view.findViewById(R.id.input_contact_hours);
        province = view.findViewById(R.id.input_province);
        municipality = view.findViewById(R.id.input_municipality);
        workMode = view.findViewById(R.id.input_work_mode);
        availability = view.findViewById(R.id.input_availability);
        mobility = view.findViewById(R.id.input_mobility);
        salaryPeriod = view.findViewById(R.id.input_salary_period);
        contactPreference = view.findViewById(R.id.input_contact_preference);
        emailLayout = view.findViewById(R.id.layout_email);
        passwordLayout = view.findViewById(R.id.layout_password);
        terms = view.findViewById(R.id.check_terms);
        relocate = view.findViewById(R.id.check_relocate);
        remote = view.findViewById(R.id.check_remote);

        LocationPicker.setup(province, municipality);
        UiUtils.setupDropdown(workMode, new String[]{"Presencial", "Híbrido", "Remoto", "Por zonas"});
        UiUtils.setupDropdown(availability, new String[]{"Inmediata", "En 2 semanas", "En 1 mes"});
        UiUtils.setupDropdown(mobility, new String[]{
                getString(R.string.mobility_my_municipality),
                getString(R.string.mobility_my_province),
                getString(R.string.mobility_anywhere),
                getString(R.string.mobility_remote)});
        UiUtils.setupDropdown(salaryPeriod, com.cubapincha.job.model.Job.salaryPeriodLabels());
        UiUtils.setupDropdown(contactPreference, new String[]{
                getString(R.string.contact_whatsapp),
                getString(R.string.contact_call),
                getString(R.string.contact_email)});

        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (step > 1) {
                showStep(step - 1);
            } else {
                requireActivity().getOnBackPressedDispatcher().onBackPressed();
            }
        });
        view.findViewById(R.id.btn_photo).setOnClickListener(v -> photoPicker.showChoice());
        // Si ya se eligió una foto, mostrarla al volver al paso 3.
        photoPreview = view.findViewById(R.id.photo_preview);
        String savedPhoto = SessionManager.get(requireContext()).getPhotoUri();
        if (savedPhoto != null && !savedPhoto.isEmpty()) {
            try {
                photoPreview.setImageURI(Uri.parse(savedPhoto));
                photoPreview.setVisibility(View.VISIBLE);
            } catch (Exception ignored) { }
        }
        view.findViewById(R.id.btn_cv).setOnClickListener(v ->
                Snackbar.make(view, "El CV se subirá a tu Drive conectado en la próxima versión.", Snackbar.LENGTH_SHORT).show());
        btnSkip.setOnClickListener(v -> finishRegistration(true));
        btnNext.setOnClickListener(v -> {
            if (step == 1) {
                if (validateStep1()) showStep(2);
            } else if (step == 2) {
                showStep(3);
            } else {
                finishRegistration(false);
            }
        });
        // Registro con Google: correo y nombre ya vienen de la cuenta y no
        // hace falta contraseña (la sesión la da el ID token de Google).
        prefillFromGoogle();
        // Al rotar, se vuelve al mismo paso con los datos escritos intactos.
        if (savedInstanceState != null) {
            showStep(savedInstanceState.getInt("step", 1));
        }
    }

    private boolean isGoogleRegistration() {
        return SessionManager.get(requireContext()).hasPendingGoogle();
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        // La vista ya restauró el texto de provincia: recargar sus municipios.
        LocationPicker.refreshMunicipalities(province, municipality);
    }

    private void prefillFromGoogle() {
        SessionManager session = SessionManager.get(requireContext());
        if (!session.hasPendingGoogle()) return;
        email.setText(session.getPendingGoogleEmail());
        String name = session.getPendingGoogleName();
        if (name != null && !name.isEmpty()) {
            String[] parts = name.trim().split("\\s+", 2);
            firstName.setText(parts[0]);
            if (parts.length > 1) lastName.setText(parts[1]);
        }
        passwordLayout.setVisibility(View.GONE);
    }

    private boolean validateStep1() {
        emailLayout.setError(null);
        passwordLayout.setError(null);
        boolean valid = true;
        if (UiUtils.text(firstName).isEmpty()) {
            firstName.setError(getString(R.string.required_field));
            valid = false;
        }
        if (!UiUtils.isValidEmail(UiUtils.text(email))) {
            emailLayout.setError(getString(R.string.invalid_email));
            valid = false;
        }
        if (!isGoogleRegistration() && UiUtils.text(password).length() < 6) {
            passwordLayout.setError("Tu contraseña necesita al menos 6 caracteres.");
            valid = false;
        }
        if (!terms.isChecked()) {
            Snackbar.make(requireView(), R.string.terms_required, Snackbar.LENGTH_LONG).show();
            valid = false;
        }
        return valid;
    }

    private void showStep(int next) {
        step = next;
        step1.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
        step2.setVisibility(step == 2 ? View.VISIBLE : View.GONE);
        step3.setVisibility(step == 3 ? View.VISIBLE : View.GONE);
        stepLabel.setText(getString(R.string.step_of, step, 3));
        stepProgress.setProgress(step);
        btnNext.setText(step == 3 ? R.string.create_my_account : R.string.next);
        btnSkip.setVisibility(step == 2 ? View.VISIBLE : View.GONE);
        View current = step == 1 ? step1 : (step == 2 ? step2 : step3);
        AnimUtils.fadeSlideIn(current);
    }

    private void finishRegistration(boolean skipped) {
        setLoading(true);
        RegisterRequest req = new RegisterRequest();
        req.role = "worker";
        req.email = UiUtils.text(email);
        req.password = UiUtils.text(password);
        if (isGoogleRegistration()) {
            req.googleIdToken = SessionManager.get(requireContext()).getPendingGoogleIdToken();
        }
        req.displayName = (UiUtils.text(firstName) + " " + UiUtils.text(lastName)).trim();
        req.termsAccepted = true;
        req.phone = UiUtils.text(phone);
        req.country = "Cuba";
        if (!skipped) {
            req.headline = UiUtils.text(headline);
            req.skills = TextUtils.splitComma(UiUtils.text(skills));
            req.experienceYears = UiUtils.parseIntOrNull(UiUtils.text(experience));
            req.provinceCode = LocationPicker.provinceCode(province);
            req.provinceName = LocationPicker.provinceName(province);
            req.municipalityCode = LocationPicker.municipalityCode(province, municipality);
            req.municipalityName = LocationPicker.municipalityName(province, municipality);
            req.locality = UiUtils.text(locality);
            String mode = com.cubapincha.job.model.Job.workModeFromLabel(UiUtils.text(workMode));
            if (!mode.isEmpty()) req.workModes.add(mode);
            req.availability = UiUtils.text(availability);
            String mob = UiUtils.text(mobility);
            if (mob.equals(getString(R.string.mobility_my_municipality))) req.mobilityScope = "mi_municipio";
            else if (mob.equals(getString(R.string.mobility_my_province))) req.mobilityScope = "mi_provincia";
            else if (mob.equals(getString(R.string.mobility_anywhere))) req.mobilityScope = "cualquier_lugar";
            else if (mob.equals(getString(R.string.mobility_remote))) req.mobilityScope = "remoto";
            req.canRelocate = relocate.isChecked();
            req.remoteAvailable = remote.isChecked();
            req.salaryMin = UiUtils.parseIntOrNull(UiUtils.text(salaryMin));
            req.salaryMax = UiUtils.parseIntOrNull(UiUtils.text(salaryMax));
            req.preferredSalaryPeriod = com.cubapincha.job.model.Job.salaryPeriodFromLabel(UiUtils.text(salaryPeriod));
            String pref = UiUtils.text(contactPreference);
            if (pref.equals(getString(R.string.contact_whatsapp))) req.contactPreference = "whatsapp";
            else if (pref.equals(getString(R.string.contact_call))) req.contactPreference = "llamada";
            else if (pref.equals(getString(R.string.contact_email))) req.contactPreference = "correo";
            req.contactHours = UiUtils.text(contactHours);
        }
        Repository.get(requireContext()).register(req, result -> {
            setLoading(false);
            if (!isAdded()) return;
            if (result.ok && result.data != null) {
                int percent = skipped ? 35 : 85;
                SessionManager session = SessionManager.get(requireContext());
                session.setProfilePercent(percent);
                session.clearPendingGoogle();
                ((MainActivity) requireActivity()).enterMain();
            } else {
                Snackbar.make(requireView(), result.friendlyMessage(), Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnNext.setEnabled(!loading);
    }
}
