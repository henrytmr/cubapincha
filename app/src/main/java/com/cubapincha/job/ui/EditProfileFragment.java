package com.cubapincha.job.ui;

import android.os.Bundle;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.cubapincha.job.R;
import com.cubapincha.job.data.JobFilter;
import com.cubapincha.job.data.Repository;
import com.cubapincha.job.data.SessionManager;
import com.cubapincha.job.model.Job;
import com.cubapincha.job.model.User;
import com.cubapincha.job.util.TextUtils;
import com.cubapincha.job.util.PhotoPayload;
import com.cubapincha.job.util.PhotoPicker;

/** Edición de perfil (trabajador o empresa): ubicación cubana, contacto y preferencias. */
public class EditProfileFragment extends Fragment {
    private TextInputEditText displayName, headline, skills, experience, availability, workModes, salaryMin;
    private TextInputEditText locality, phone, contactHours;
    private TextInputEditText description, industry, website;
    private AutoCompleteTextView province, municipality, mobility, salaryPeriod, contactPreference, orgType;
    private ProgressBar progress;
    private View btnSave;
    private User currentUser;
    private boolean company;
    private boolean displayNameTouched;
    private boolean assigningServerName;
    /** Guardar solo se habilita cuando el perfil cargó: guardar con el
     *  formulario vacío borraría los datos guardados del usuario. */
    private boolean profileLoaded;
    private PhotoPicker photoPicker;

    @Override public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        photoPicker = new PhotoPicker(this, new PhotoPicker.Callback() {
            @Override public void onPhotoPicked(Uri uri) {
                try {
                    if (isAdded() && getView() != null) {
                        ImageView preview = getView().findViewById(R.id.edit_profile_photo_preview);
                        preview.setImageURI(uri);
                        preview.setVisibility(View.VISIBLE);
                    }
                    PhotoPayload payload = PhotoPayload.fromUri(requireContext(), uri);
                    Repository.get(requireContext()).uploadPhoto("profile", null,
                            payload.detailBase64, payload.thumbBase64, result -> {
                                if (!isAdded()) return;
                                if (result.ok) {
                                    SessionManager.get(requireContext()).setPhotoUri(uri.toString());
                                    Snackbar.make(requireView(), "Foto de perfil actualizada.", Snackbar.LENGTH_SHORT).show();
                                } else {
                                    Snackbar.make(requireView(), result.friendlyMessage(), Snackbar.LENGTH_LONG).show();
                                }
                            });
                } catch (Exception e) {
                    if (isAdded()) Snackbar.make(requireView(), "No se pudo preparar la foto.", Snackbar.LENGTH_LONG).show();
                }
            }
            @Override public void onPhotoDenied(String reason) { }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        SessionManager session = SessionManager.get(requireContext());
        company = "company".equals(session.getRole());
        displayName = view.findViewById(R.id.input_display_name);
        headline = view.findViewById(R.id.input_headline);
        skills = view.findViewById(R.id.input_skills);
        experience = view.findViewById(R.id.input_experience);
        availability = view.findViewById(R.id.input_availability);
        workModes = view.findViewById(R.id.input_work_modes);
        salaryMin = view.findViewById(R.id.input_salary_min);
        locality = view.findViewById(R.id.input_locality);
        phone = view.findViewById(R.id.input_phone);
        contactHours = view.findViewById(R.id.input_contact_hours);
        description = view.findViewById(R.id.input_description);
        industry = view.findViewById(R.id.input_industry);
        website = view.findViewById(R.id.input_website);
        province = view.findViewById(R.id.input_province);
        municipality = view.findViewById(R.id.input_municipality);
        mobility = view.findViewById(R.id.input_mobility);
        salaryPeriod = view.findViewById(R.id.input_salary_period);
        contactPreference = view.findViewById(R.id.input_contact_preference);
        orgType = view.findViewById(R.id.input_org_type);
        progress = view.findViewById(R.id.progress);
        btnSave = view.findViewById(R.id.btn_save);
        ImageView photoPreview = view.findViewById(R.id.edit_profile_photo_preview);
        String savedPhotoUri = session.getPhotoUri();
        if (savedPhotoUri != null && !savedPhotoUri.isEmpty()) {
            try {
                photoPreview.setImageURI(Uri.parse(savedPhotoUri));
                photoPreview.setVisibility(View.VISIBLE);
            } catch (RuntimeException ignored) {
                photoPreview.setImageDrawable(null);
                photoPreview.setVisibility(View.GONE);
            }
        }

        LocationPicker.setup(province, municipality);
        UiUtils.setupDropdown(mobility, new String[]{
                getString(R.string.mobility_my_municipality),
                getString(R.string.mobility_my_province),
                getString(R.string.mobility_anywhere),
                getString(R.string.mobility_remote)});
        UiUtils.setupDropdown(salaryPeriod, Job.salaryPeriodLabels());
        UiUtils.setupDropdown(contactPreference, new String[]{
                getString(R.string.contact_whatsapp),
                getString(R.string.contact_call),
                getString(R.string.contact_email)});
        String[] orgLabels = new String[User.ORG_TYPES.length];
        for (int i = 0; i < orgLabels.length; i++) {
            orgLabels[i] = User.orgTypeLabel(User.ORG_TYPES[i]);
        }
        UiUtils.setupDropdown(orgType, orgLabels);

        view.findViewById(R.id.worker_fields).setVisibility(company ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.company_fields).setVisibility(company ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        view.findViewById(R.id.btn_profile_photo).setOnClickListener(v -> photoPicker.showChoice());

        displayName.setText(session.getDisplayName());
        displayName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!assigningServerName) displayNameTouched = true;
            }
            @Override public void afterTextChanged(Editable editable) { }
        });
        // Guardar se habilita al cargar el perfil: si se guardara antes,
        // los campos aún vacíos borrarían los datos guardados.
        loadProfile(view);

        btnSave.setOnClickListener(v -> {
            if (currentUser == null) return;
            User u = currentUser;
            u.displayName = UiUtils.text(displayName);
            u.provinceCode = LocationPicker.provinceCode(province);
            u.provinceName = LocationPicker.provinceName(province);
            u.municipalityCode = LocationPicker.municipalityCode(province, municipality);
            u.municipalityName = LocationPicker.municipalityName(province, municipality);
            u.locality = UiUtils.text(locality);
            u.phone = UiUtils.text(phone);
            u.contactHours = UiUtils.text(contactHours);
            if (!company) {
                u.headline = UiUtils.text(headline);
                u.skills = TextUtils.splitComma(UiUtils.text(skills));
                u.experienceYears = UiUtils.parseIntOrNull(UiUtils.text(experience));
                u.availability = UiUtils.text(availability);
                u.workModes = TextUtils.splitComma(UiUtils.text(workModes));
                u.salaryMin = UiUtils.parseIntOrNull(UiUtils.text(salaryMin));
                String mob = UiUtils.text(mobility);
                if (mob.equals(getString(R.string.mobility_my_municipality))) u.mobilityScope = "mi_municipio";
                else if (mob.equals(getString(R.string.mobility_my_province))) u.mobilityScope = "mi_provincia";
                else if (mob.equals(getString(R.string.mobility_anywhere))) u.mobilityScope = "cualquier_lugar";
                else if (mob.equals(getString(R.string.mobility_remote))) u.mobilityScope = "remoto";
                u.preferredSalaryPeriod = Job.salaryPeriodFromLabel(UiUtils.text(salaryPeriod));
                String pref = UiUtils.text(contactPreference);
                if (pref.equals(getString(R.string.contact_whatsapp))) u.contactPreference = "whatsapp";
                else if (pref.equals(getString(R.string.contact_call))) u.contactPreference = "llamada";
                else if (pref.equals(getString(R.string.contact_email))) u.contactPreference = "correo";
            } else {
                u.description = UiUtils.text(description);
                u.industry = UiUtils.text(industry);
                u.website = UiUtils.text(website);
                String orgLabel = UiUtils.text(orgType);
                for (String type : User.ORG_TYPES) {
                    if (User.orgTypeLabel(type).equals(orgLabel)) {
                        u.orgType = type;
                        break;
                    }
                }
            }
            setLoading(true);
            Repository.get(requireContext()).updateProfile(u, result -> {
                setLoading(false);
                if (!isAdded()) return;
                if (result.ok) {
                    if (result.data != null && result.data.profilePercent >= 0) {
                        SessionManager.get(requireContext()).setProfilePercent(result.data.profilePercent);
                    }
                    View container = requireActivity().findViewById(R.id.fragment_container);
                    Snackbar.make(container != null ? container : view,
                            "Perfil actualizado. ¡Gracias por cuidarlo!", Snackbar.LENGTH_LONG).show();
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                } else {
                    Snackbar.make(view, result.friendlyMessage(), Snackbar.LENGTH_LONG).show();
                }
            });
        });
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        // La vista ya restauró el texto de provincia: recargar sus municipios.
        LocationPicker.refreshMunicipalities(province, municipality);
    }

    /** Carga el perfil desde el servidor. Si falla, Guardar queda deshabilitado
     *  y se ofrece reintentar: guardar con el formulario vacío borraría los
     *  datos guardados del usuario. */
    private void loadProfile(View anchor) {
        setLoading(true);
        Repository.get(requireContext()).me(result -> {
            if (!isAdded()) return;
            if (!result.ok || result.data == null) {
                setLoading(false);
                Snackbar.make(anchor, result.friendlyMessage(), Snackbar.LENGTH_LONG)
                        .setAction(R.string.retry, v -> loadProfile(anchor))
                        .show();
                return;
            }
            currentUser = result.data;
            profileLoaded = true;
            boolean serverCompany = !currentUser.isWorker();
            if (company != serverCompany) {
                company = serverCompany;
                anchor.findViewById(R.id.worker_fields).setVisibility(company ? View.GONE : View.VISIBLE);
                anchor.findViewById(R.id.company_fields).setVisibility(company ? View.VISIBLE : View.GONE);
            }
            if (!displayNameTouched && currentUser.displayName != null
                    && !currentUser.displayName.trim().isEmpty()) {
                assigningServerName = true;
                displayName.setText(currentUser.displayName);
                assigningServerName = false;
            }
            setLoading(false);
            // Solo se rellenan los campos vacíos: si el usuario ya está
            // escribiendo, su texto no se sobrescribe.
            if (currentUser.provinceCode != null && !currentUser.provinceCode.isEmpty()
                    && UiUtils.text(province).isEmpty()) {
                LocationPicker.setSelection(province, municipality,
                        currentUser.provinceCode, currentUser.municipalityName);
            }
            setIfEmpty(locality, currentUser.locality);
            setIfEmpty(phone, currentUser.phone);
            setIfEmpty(contactHours, currentUser.contactHours);
            if (currentUser.isWorker()) {
                setIfEmpty(headline, currentUser.headline);
                setIfEmpty(skills, TextUtils.joinComma(currentUser.skills));
                if (currentUser.experienceYears != null) {
                    setIfEmpty(experience, String.valueOf(currentUser.experienceYears));
                }
                setIfEmpty(availability, currentUser.availability);
                setIfEmpty(workModes, TextUtils.joinComma(currentUser.workModes));
                if (currentUser.salaryMin != null) setIfEmpty(salaryMin, String.valueOf(currentUser.salaryMin));
                if (currentUser.mobilityScope != null && !currentUser.mobilityScope.isEmpty()
                        && UiUtils.text(mobility).isEmpty()) {
                    mobility.setText(JobFilter.mobilityScopeLabel(currentUser.mobilityScope), false);
                }
                if (currentUser.preferredSalaryPeriod != null && !currentUser.preferredSalaryPeriod.isEmpty()
                        && UiUtils.text(salaryPeriod).isEmpty()) {
                    salaryPeriod.setText(Job.salaryPeriodLabel(currentUser.preferredSalaryPeriod), false);
                }
                if (currentUser.contactPreference != null && !currentUser.contactPreference.isEmpty()
                        && UiUtils.text(contactPreference).isEmpty()) {
                    contactPreference.setText(contactPreferenceLabel(currentUser.contactPreference), false);
                }
            } else {
                setIfEmpty(description, currentUser.description);
                setIfEmpty(industry, currentUser.industry);
                setIfEmpty(website, currentUser.website);
                if (currentUser.orgType != null && !currentUser.orgType.isEmpty()
                        && UiUtils.text(orgType).isEmpty()) {
                    orgType.setText(User.orgTypeLabel(currentUser.orgType), false);
                }
            }
        });
    }

    private String contactPreferenceLabel(String code) {
        switch (code) {
            case "whatsapp": return getString(R.string.contact_whatsapp);
            case "llamada": return getString(R.string.contact_call);
            case "correo": return getString(R.string.contact_email);
            default: return code;
        }
    }

    private static void setIfEmpty(android.widget.TextView field, String value) {
        if (field.getText() == null || field.getText().toString().isEmpty()) {
            field.setText(value);
        }
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(!loading && profileLoaded);
    }
}
