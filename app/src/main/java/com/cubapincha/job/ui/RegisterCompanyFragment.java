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
import com.cubapincha.job.data.SeedData;
import com.cubapincha.job.data.SessionManager;
import com.cubapincha.job.util.AnimUtils;
import com.cubapincha.job.util.PhotoPicker;

/** Registro de empresa en 3 pasos. */
public class RegisterCompanyFragment extends Fragment {
    private int step = 1;
    private View step1, step2, step3;
    private MaterialButton btnNext;
    private TextView stepLabel;
    private LinearProgressIndicator stepProgress;
    private ProgressBar progress;
    private TextInputEditText legalName, email, password, country;
    private TextInputEditText description, locality, phone, website, contactHours;
    private AutoCompleteTextView province, municipality, industry, size, orgType;
    private TextInputLayout emailLayout, passwordLayout;
    private CheckBox terms;
    private PhotoPicker photoPicker;
    private ImageView logoPreview;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        photoPicker = new PhotoPicker(this, new PhotoPicker.Callback() {
            @Override
            public void onPhotoPicked(Uri uri) {
                SessionManager.get(requireContext()).setPhotoUri(uri.toString());
                if (logoPreview != null) {
                    logoPreview.setImageURI(uri);
                    logoPreview.setVisibility(View.VISIBLE);
                }
                if (isAdded()) {
                    Snackbar.make(requireView(), R.string.logo_added, Snackbar.LENGTH_SHORT).show();
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
        return inflater.inflate(R.layout.fragment_register_company, container, false);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("step", step);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        step1 = view.findViewById(R.id.step1);
        step2 = view.findViewById(R.id.step2);
        step3 = view.findViewById(R.id.step3);
        btnNext = view.findViewById(R.id.btn_next);
        stepLabel = view.findViewById(R.id.step_label);
        stepProgress = view.findViewById(R.id.step_progress);
        progress = view.findViewById(R.id.progress);
        legalName = view.findViewById(R.id.input_legal_name);
        email = view.findViewById(R.id.input_email);
        password = view.findViewById(R.id.input_password);
        country = view.findViewById(R.id.input_country);
        description = view.findViewById(R.id.input_description);
        locality = view.findViewById(R.id.input_locality);
        phone = view.findViewById(R.id.input_phone);
        website = view.findViewById(R.id.input_website);
        contactHours = view.findViewById(R.id.input_contact_hours);
        province = view.findViewById(R.id.input_province);
        municipality = view.findViewById(R.id.input_municipality);
        industry = view.findViewById(R.id.input_industry);
        size = view.findViewById(R.id.input_size);
        orgType = view.findViewById(R.id.input_org_type);
        emailLayout = view.findViewById(R.id.layout_email);
        passwordLayout = view.findViewById(R.id.layout_password);
        terms = view.findViewById(R.id.check_terms);

        LocationPicker.setup(province, municipality);
        UiUtils.setupDropdown(industry, SeedData.CATEGORIES);
        UiUtils.setupDropdown(size, new String[]{"1-10", "11-50", "51-200", "Más de 200"});
        String[] orgLabels = new String[com.cubapincha.job.model.User.ORG_TYPES.length];
        for (int i = 0; i < orgLabels.length; i++) {
            orgLabels[i] = com.cubapincha.job.model.User.orgTypeLabel(com.cubapincha.job.model.User.ORG_TYPES[i]);
        }
        UiUtils.setupDropdown(orgType, orgLabels);

        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (step > 1) {
                showStep(step - 1);
            } else {
                requireActivity().getOnBackPressedDispatcher().onBackPressed();
            }
        });
        view.findViewById(R.id.btn_logo).setOnClickListener(v -> photoPicker.showChoice());
        // Si ya se eligió un logotipo, mostrarlo al volver al paso 3.
        logoPreview = view.findViewById(R.id.logo_preview);
        String savedPhoto = SessionManager.get(requireContext()).getPhotoUri();
        if (savedPhoto != null && !savedPhoto.isEmpty()) {
            try {
                logoPreview.setImageURI(Uri.parse(savedPhoto));
                logoPreview.setVisibility(View.VISIBLE);
            } catch (Exception ignored) { }
        }
        btnNext.setOnClickListener(v -> {
            if (step == 1) {
                if (validateStep1()) showStep(2);
            } else if (step == 2) {
                showStep(3);
            } else {
                finishRegistration();
            }
        });
        // Registro con Google: el correo ya viene de la cuenta y no hace
        // falta contraseña (la sesión la da el ID token de Google).
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
        passwordLayout.setVisibility(View.GONE);
    }

    private boolean validateStep1() {
        emailLayout.setError(null);
        passwordLayout.setError(null);
        boolean valid = true;
        if (UiUtils.text(legalName).isEmpty()) {
            legalName.setError(getString(R.string.required_field));
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
        btnNext.setText(step == 3 ? R.string.create_company_account : R.string.next);
        View current = step == 1 ? step1 : (step == 2 ? step2 : step3);
        AnimUtils.fadeSlideIn(current);
    }

    private void finishRegistration() {
        setLoading(true);
        RegisterRequest req = new RegisterRequest();
        req.role = "company";
        req.email = UiUtils.text(email);
        req.password = UiUtils.text(password);
        if (isGoogleRegistration()) {
            req.googleIdToken = SessionManager.get(requireContext()).getPendingGoogleIdToken();
        }
        req.legalName = UiUtils.text(legalName);
        req.displayName = req.legalName;
        req.termsAccepted = true;
        req.country = UiUtils.text(country);
        req.industry = UiUtils.text(industry);
        req.size = UiUtils.text(size);
        req.description = UiUtils.text(description);
        req.phone = UiUtils.text(phone);
        req.provinceCode = LocationPicker.provinceCode(province);
        req.provinceName = LocationPicker.provinceName(province);
        req.municipalityCode = LocationPicker.municipalityCode(province, municipality);
        req.municipalityName = LocationPicker.municipalityName(province, municipality);
        req.locality = UiUtils.text(locality);
        req.contactHours = UiUtils.text(contactHours);
        String orgLabel = UiUtils.text(orgType);
        for (String type : com.cubapincha.job.model.User.ORG_TYPES) {
            if (com.cubapincha.job.model.User.orgTypeLabel(type).equals(orgLabel)) {
                req.orgType = type;
                break;
            }
        }
        req.website = UiUtils.text(website);
        Repository.get(requireContext()).register(req, result -> {
            setLoading(false);
            if (!isAdded()) return;
            if (result.ok && result.data != null) {
                SessionManager session = SessionManager.get(requireContext());
                if (result.data.profilePercent >= 0) session.setProfilePercent(result.data.profilePercent);
                session.clearPendingGoogle();
                MainActivity main = (MainActivity) requireActivity();
                main.enterMain();
                // El aviso se ancla al contenedor de la actividad (persiste);
                // anclarlo a esta vista lo perdía al cambiar de pantalla.
                Snackbar.make(main.findViewById(R.id.fragment_container),
                        R.string.pending_verification, Snackbar.LENGTH_LONG).show();
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
