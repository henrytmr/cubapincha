package com.cubapincha.job.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.os.Looper;
import android.net.Uri;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.cubapincha.job.R;
import com.cubapincha.job.data.Repository;
import com.cubapincha.job.data.SeedData;
import com.cubapincha.job.model.Application;
import com.cubapincha.job.model.Job;
import com.cubapincha.job.util.TextUtils;
import com.cubapincha.job.util.PhotoPayload;
import com.cubapincha.job.util.PhotoPicker;

import java.util.Calendar;

/**
 * Crear / editar oferta por bloques, con vista previa y publicación.
 *
 * v2.0: modo rápido/completo, indicador de calidad (7 puntos), ubicación
 * cubana en cascada, salario CUP con periodo, beneficios incluidos, horario
 * de contacto y fecha de inicio. Sin conexión, "Guardar borrador" guarda en
 * el teléfono (tabla drafts) y "Publicar" avisa sin prometer nada.
 * Al editar una oferta con candidatos se muestra la advertencia de nueva
 * versión. Los borradores locales (id draft-*) se abren y publican desde
 * este mismo formulario.
 */
public class CreateJobFragment extends Fragment {
    private static final String ARG_JOB_ID = "job_id";
    private static final int QUALITY_TOTAL = 7;

    public static CreateJobFragment newInstance(String jobId) {
        CreateJobFragment f = new CreateJobFragment();
        Bundle args = new Bundle();
        args.putString(ARG_JOB_ID, jobId);
        f.setArguments(args);
        return f;
    }

    /** true si esta instancia edita una oferta (detalle apilado), false si
     *  es la pestaña "Crear" (sin oferta). MainActivity lo usa para decidir
     *  si la barra inferior debe verse. */
    public boolean isEditing() {
        return getArguments() != null && getArguments().getString(ARG_JOB_ID) != null;
    }

    private String editJobId;
    private Job editing;
    private boolean isLocalDraft;
    private boolean warningShown;
    private boolean quickMode;
    /** true cuando fillForm ya rellenó la vista una vez: al rotar, la vista
     *  restaura lo escrito por el usuario y fillForm no debe sobrescribirlo
     *  (periodo, beneficios y fecha se perdían en cada rotación). */
    private boolean formFilled;
    private String startDateIso;
    private TextInputEditText title, description, responsibilities, requirements;
    private TextInputEditText locality, salaryMin, salaryMax, benefits, experience, languages, keywords;
    private TextInputEditText contactHours, startDate;
    private AutoCompleteTextView province, municipality, category, mode, contract, salaryPeriod;
    private CheckBox checkFood, checkTransport, checkAccommodation, checkTips;
    private TextInputLayout titleLayout, descriptionLayout, salaryMinLayout, salaryMaxLayout;
    private View groupCompleteInfo, groupCriteria, layoutBenefits, salaryRow;
    private TextView qualityLabel;
    private LinearProgressIndicator qualityBar;
    private MaterialButtonToggleGroup modeToggle;
    private ProgressBar progress;
    private ScrollView scrollRoot;
    private View btnPublish, btnDraft, btnPreview;
    private PhotoPicker photoPicker;
    private PhotoPayload offerPhoto;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        photoPicker = new PhotoPicker(this, new PhotoPicker.Callback() {
            @Override public void onPhotoPicked(Uri uri) {
                try {
                    offerPhoto = PhotoPayload.fromUri(requireContext(), uri);
                    if (isAdded()) Snackbar.make(requireView(), "Foto preparada para la oferta.", Snackbar.LENGTH_SHORT).show();
                } catch (Exception e) {
                    offerPhoto = null;
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
        return inflater.inflate(R.layout.fragment_create_job, container, false);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("quick", quickMode);
        outState.putBoolean("warning", warningShown);
        outState.putBoolean("filled", formFilled);
        outState.putString("startDate", startDateIso);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        scrollRoot = view.findViewById(R.id.scroll_root);
        editJobId = getArguments() != null ? getArguments().getString(ARG_JOB_ID) : null;
        isLocalDraft = editJobId != null && editJobId.startsWith("draft-");
        if (savedInstanceState != null) {
            quickMode = savedInstanceState.getBoolean("quick", false);
            warningShown = savedInstanceState.getBoolean("warning", false);
            formFilled = savedInstanceState.getBoolean("filled", false);
            startDateIso = savedInstanceState.getString("startDate");
        }
        title = view.findViewById(R.id.input_title);
        description = view.findViewById(R.id.input_description);
        responsibilities = view.findViewById(R.id.input_responsibilities);
        requirements = view.findViewById(R.id.input_requirements);
        locality = view.findViewById(R.id.input_locality);
        salaryMin = view.findViewById(R.id.input_salary_min);
        salaryMax = view.findViewById(R.id.input_salary_max);
        benefits = view.findViewById(R.id.input_benefits);
        experience = view.findViewById(R.id.input_experience);
        languages = view.findViewById(R.id.input_languages);
        keywords = view.findViewById(R.id.input_keywords);
        contactHours = view.findViewById(R.id.input_contact_hours);
        startDate = view.findViewById(R.id.input_start_date);
        province = view.findViewById(R.id.input_province);
        municipality = view.findViewById(R.id.input_municipality);
        category = view.findViewById(R.id.input_category);
        mode = view.findViewById(R.id.input_mode);
        contract = view.findViewById(R.id.input_contract);
        salaryPeriod = view.findViewById(R.id.input_salary_period);
        checkFood = view.findViewById(R.id.check_food);
        checkTransport = view.findViewById(R.id.check_transport);
        checkAccommodation = view.findViewById(R.id.check_accommodation);
        checkTips = view.findViewById(R.id.check_tips);
        titleLayout = view.findViewById(R.id.layout_title);
        descriptionLayout = view.findViewById(R.id.layout_description);
        salaryMinLayout = view.findViewById(R.id.layout_salary_min);
        salaryMaxLayout = view.findViewById(R.id.layout_salary_max);
        groupCompleteInfo = view.findViewById(R.id.group_complete_info);
        groupCriteria = view.findViewById(R.id.group_criteria);
        layoutBenefits = view.findViewById(R.id.layout_benefits);
        salaryRow = view.findViewById(R.id.salary_row);
        qualityLabel = view.findViewById(R.id.quality_label);
        qualityBar = view.findViewById(R.id.quality_bar);
        modeToggle = view.findViewById(R.id.mode_toggle);
        progress = view.findViewById(R.id.progress);
        btnPublish = view.findViewById(R.id.btn_publish);
        btnDraft = view.findViewById(R.id.btn_draft);
        btnPreview = view.findViewById(R.id.btn_preview);
        view.findViewById(R.id.btn_offer_photo).setOnClickListener(v -> photoPicker.showChoice());

        LocationPicker.setup(province, municipality, this::updateQuality);
        UiUtils.setupDropdown(category, SeedData.CATEGORIES);
        UiUtils.setupDropdown(mode, new String[]{"Presencial", "Híbrido", "Remoto", "Por zonas"});
        UiUtils.setupDropdown(contract, new String[]{"Tiempo completo", "Medio tiempo", "Temporal", "Por proyecto", "Indefinido", "Prácticas"});
        UiUtils.setupDropdown(salaryPeriod, Job.salaryPeriodLabels());
        salaryPeriod.setText(Job.salaryPeriodLabel("mensual"), false);
        salaryPeriod.setOnItemClickListener((p, v, pos, id) -> {
            updateSalaryRowVisibility();
            updateQuality();
        });
        category.setOnItemClickListener((p, v, pos, id) -> updateQuality());
        mode.setOnItemClickListener((p, v, pos, id) -> updateQuality());

        TextWatcher qualityWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { updateQuality(); }
        };
        title.addTextChangedListener(qualityWatcher);
        description.addTextChangedListener(qualityWatcher);
        requirements.addTextChangedListener(qualityWatcher);
        benefits.addTextChangedListener(qualityWatcher);
        contactHours.addTextChangedListener(qualityWatcher);
        salaryMin.addTextChangedListener(qualityWatcher);
        salaryMax.addTextChangedListener(qualityWatcher);
        View.OnClickListener checkListener = v -> updateQuality();
        checkFood.setOnClickListener(checkListener);
        checkTransport.setOnClickListener(checkListener);
        checkAccommodation.setOnClickListener(checkListener);
        checkTips.setOnClickListener(checkListener);

        modeToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            quickMode = checkedId == R.id.btn_mode_quick;
            applyMode();
        });
        applyMode();

        startDate.setOnClickListener(v -> showDatePicker());

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        if (editJobId != null) {
            ((TextView) view.findViewById(R.id.screen_title)).setText(R.string.edit);
            // Botones bloqueados hasta cargar la oferta: sin ella, Guardar
            // crearía una oferta nueva en lugar de editar la existente.
            setLoading(true);
            loadForEdit();
        }

        btnDraft.setOnClickListener(v -> saveDraft());
        btnPreview.setOnClickListener(v -> {
            Job draft = buildJob();
            if (draft == null) return;
            new AlertDialog.Builder(requireContext())
                    .setTitle(draft.title)
                    .setMessage(draft.locationText()
                            + (!draft.localityText().isEmpty() ? " (" + draft.localityText() + ")" : "")
                            + " · " + Job.workModeLabel(draft.workMode)
                            + " · " + Job.contractLabel(draft.contractType)
                            + "\n" + draft.salaryText()
                            + (draft.contactHours != null && !draft.contactHours.isEmpty()
                                    ? "\nContacto: " + draft.contactHours : "")
                            + "\n\n" + draft.description)
                    .setPositiveButton(R.string.accept, null)
                    .show();
        });
        btnPublish.setOnClickListener(v -> publishFlow());
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        // La vista ya restauró el texto de provincia: recargar sus municipios.
        LocationPicker.refreshMunicipalities(province, municipality);
    }

    private void applyMode() {
        int vis = quickMode ? View.GONE : View.VISIBLE;
        groupCompleteInfo.setVisibility(vis);
        groupCriteria.setVisibility(vis);
        layoutBenefits.setVisibility(vis);
        modeToggle.check(quickMode ? R.id.btn_mode_quick : R.id.btn_mode_complete);
    }

    private void updateSalaryRowVisibility() {
        boolean aConvenir = "a_convenir".equals(Job.salaryPeriodFromLabel(UiUtils.text(salaryPeriod)));
        salaryRow.setVisibility(aConvenir ? View.GONE : View.VISIBLE);
    }

    /** Calidad de la publicación: 7 puntos visibles para la empresa. */
    private void updateQuality() {
        int score = 0;
        boolean remote = "remoto".equals(Job.workModeFromLabel(UiUtils.text(mode)));
        if (remote || (!LocationPicker.provinceCode(province).isEmpty()
                && !LocationPicker.municipalityName(province, municipality).isEmpty())) score++;
        String period = Job.salaryPeriodFromLabel(UiUtils.text(salaryPeriod));
        if ("a_convenir".equals(period)
                || !UiUtils.text(salaryMin).isEmpty() || !UiUtils.text(salaryMax).isEmpty()) score++;
        if (!UiUtils.text(contactHours).isEmpty()) score++;
        if (!UiUtils.text(requirements).isEmpty()) score++;
        if (!UiUtils.text(benefits).isEmpty() || checkFood.isChecked() || checkTransport.isChecked()
                || checkAccommodation.isChecked() || checkTips.isChecked()) score++;
        if (UiUtils.text(description).length() >= 80) score++;
        if (!UiUtils.text(category).isEmpty()) score++;
        qualityBar.setProgress(score);
        qualityLabel.setText(getString(R.string.quality_score, score, QUALITY_TOTAL));
    }

    private void showDatePicker() {
        Calendar c = Calendar.getInstance();
        if (startDateIso != null && startDateIso.length() >= 10) {
            try {
                c.set(Integer.parseInt(startDateIso.substring(0, 4)),
                        Integer.parseInt(startDateIso.substring(5, 7)) - 1,
                        Integer.parseInt(startDateIso.substring(8, 10)));
            } catch (Exception ignored) { }
        }
        new DatePickerDialog(requireContext(), (v, y, m, d) -> {
            startDateIso = String.format("%04d-%02d-%02d", y, m + 1, d);
            startDate.setText(String.format("%02d/%02d/%04d", d, m + 1, y));
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void loadForEdit() {
        Repository repo = Repository.get(requireContext());
        if (isLocalDraft) {
            repo.getDrafts(result -> {
                if (!isAdded()) return;
                setLoading(false);
                Job found = null;
                if (result.ok && result.data != null) {
                    for (Job j : result.data) {
                        if (editJobId.equals(j.jobId)) { found = j; break; }
                    }
                }
                if (found == null) {
                    Snackbar.make(snackbarAnchor(), "No encontramos este borrador.", Snackbar.LENGTH_LONG).show();
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                    return;
                }
                fillForm(found);
            });
            return;
        }
        repo.getJob(editJobId, result -> {
            if (!isAdded()) return;
            if (!result.ok || result.data == null) {
                setLoading(false);
                Snackbar.make(snackbarAnchor(), result.friendlyMessage(), Snackbar.LENGTH_LONG).show();
                requireActivity().getOnBackPressedDispatcher().onBackPressed();
                return;
            }
            fillForm(result.data);
            setLoading(false);
            // Advertencia de nueva versión si la oferta ya tiene candidatos.
            if (!warningShown && !"draft".equals(result.data.status)) {
                repo.getApplications(null, apps -> {
                    if (!isAdded()) return;
                    boolean hasCandidates = false;
                    if (apps.ok && apps.data != null) {
                        for (Application a : apps.data) {
                            if (editJobId.equals(a.jobId)) { hasCandidates = true; break; }
                        }
                    }
                    if (hasCandidates) {
                        warningShown = true;
                        new AlertDialog.Builder(requireContext())
                                .setTitle(R.string.edit_warning_title)
                                .setMessage(R.string.edit_warning_body)
                                .setPositiveButton(R.string.accept, null)
                                .show();
                    }
                });
            }
        });
    }

    private void fillForm(Job job) {
        editing = job;
        if (formFilled) {
            // Segunda llamada tras una rotación: la vista ya restauró lo
            // escrito por el usuario (periodo, beneficios, fecha incluidos).
            // Solo se recalculan los derivados, nunca se rellenan campos.
            updateSalaryRowVisibility();
            updateQuality();
            return;
        }
        // Solo se rellenan los campos vacíos: al rotar, los EditText
        // restauran su propio texto y no debe sobrescribirse lo escrito.
        setIfEmpty(title, job.title);
        setIfEmpty(category, job.category);
        setIfEmpty(description, job.description);
        setIfEmpty(responsibilities, TextUtils.joinComma(job.responsibilities));
        setIfEmpty(requirements, TextUtils.joinComma(job.requirements));
        setIfEmpty(mode, Job.workModeLabel(job.workMode));
        if (job.provinceCode != null && !job.provinceCode.isEmpty() && UiUtils.text(province).isEmpty()) {
            LocationPicker.setSelection(province, municipality, job.provinceCode, job.municipalityName);
        }
        setIfEmpty(locality, job.locality);
        setIfEmpty(contract, Job.contractLabel(job.contractType));
        if (job.salaryMin != null) setIfEmpty(salaryMin, String.valueOf(job.salaryMin));
        if (job.salaryMax != null) setIfEmpty(salaryMax, String.valueOf(job.salaryMax));
        if (job.salaryPeriod != null && !job.salaryPeriod.isEmpty()) {
            salaryPeriod.setText(Job.salaryPeriodLabel(job.salaryPeriod), false);
        }
        checkFood.setChecked(job.includesFood);
        checkTransport.setChecked(job.includesTransport);
        checkAccommodation.setChecked(job.includesAccommodation);
        checkTips.setChecked(job.includesTips);
        setIfEmpty(benefits, TextUtils.joinComma(job.benefits));
        setIfEmpty(contactHours, job.contactHours);
        if (job.startDate != null && !job.startDate.isEmpty()) {
            startDateIso = job.startDate;
            startDate.setText(displayDate(job.startDate));
        }
        if (job.experienceMin != null) setIfEmpty(experience, String.valueOf(job.experienceMin));
        setIfEmpty(languages, TextUtils.joinComma(job.languages));
        setIfEmpty(keywords, TextUtils.joinComma(job.keywords));
        // Si la oferta ya tiene datos de modo completo, abrir en completo.
        if (!job.responsibilities.isEmpty() || !job.requirements.isEmpty()
                || !job.benefits.isEmpty() || job.experienceMin != null) {
            quickMode = false;
            applyMode();
        }
        formFilled = true;
        updateSalaryRowVisibility();
        updateQuality();
    }

    private static String displayDate(String iso) {
        if (iso != null && iso.length() >= 10 && iso.charAt(4) == '-') {
            return iso.substring(8, 10) + "/" + iso.substring(5, 7) + "/" + iso.substring(0, 4);
        }
        return iso != null ? iso : "";
    }

    private static void setIfEmpty(android.widget.TextView field, String value) {
        if (field.getText() == null || field.getText().toString().isEmpty()) {
            field.setText(value);
        }
    }

    /** Ancla para mensajes que se muestran justo antes de navegar: el contenedor
     *  de la actividad sobrevive al cambio de pantalla; la vista del fragmento, no. */
    private View snackbarAnchor() {
        View container = requireActivity().findViewById(R.id.fragment_container);
        return container != null ? container : requireView();
    }

    private void saveDraft() {
        Job draft = buildJob();
        if (draft == null) return;
        Repository repo = Repository.get(requireContext());
        // Borrador local existente: se actualiza en el teléfono.
        // Sin conexión en modo conectado: se guarda en el teléfono.
        if (isLocalDraft || (!repo.isDemoMode() && !repo.isOnline())) {
            if (editing != null) draft.jobId = editing.jobId;
            setLoading(true);
            repo.saveDraft(draft, result -> {
                setLoading(false);
                if (!isAdded()) return;
                if (result.ok) {
                    Snackbar.make(snackbarAnchor(),
                            isLocalDraft ? R.string.draft_saved : R.string.draft_offline_saved,
                            Snackbar.LENGTH_LONG).show();
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                } else {
                    Snackbar.make(requireView(), result.friendlyMessage(), Snackbar.LENGTH_LONG).show();
                }
            });
            return;
        }
        setLoading(true);
        if (editing != null) {
            draft.jobId = editing.jobId;
            repo.updateJob(draft, result -> {
                setLoading(false);
                if (!isAdded()) return;
                if (result.ok) {
                    Snackbar.make(snackbarAnchor(), R.string.draft_saved, Snackbar.LENGTH_LONG).show();
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                } else {
                    Snackbar.make(requireView(), result.friendlyMessage(), Snackbar.LENGTH_LONG).show();
                }
            });
        } else {
            repo.createJob(draft, result -> {
                setLoading(false);
                if (!isAdded()) return;
                if (result.ok) {
                    Snackbar.make(snackbarAnchor(), R.string.draft_saved, Snackbar.LENGTH_LONG).show();
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                } else {
                    Snackbar.make(requireView(), result.friendlyMessage(), Snackbar.LENGTH_LONG).show();
                }
            });
        }
    }

    private void publishFlow() {
        Repository.get(requireContext()).me(profile -> {
            if (!isAdded()) return;
            if (!profile.ok || profile.data == null || profile.data.phone == null || profile.data.phone.trim().isEmpty()) {
                Snackbar.make(snackbarAnchor(), "Agrega tu número de teléfono para publicar", Snackbar.LENGTH_LONG)
                        .setAction("Perfil", v -> ((MainActivity) requireActivity()).openEditProfile()).show();
                return;
            }
            publishFlowAfterPhone();
        });
    }
    private void publishFlowAfterPhone() {
        Job draft = buildJob();
        if (draft == null) return;
        Repository repo = Repository.get(requireContext());
        if (!repo.isDemoMode() && !repo.isOnline()) {
            Snackbar.make(requireView(), R.string.publish_offline, Snackbar.LENGTH_LONG).show();
            return;
        }
        setLoading(true);
        if (isLocalDraft && editing != null) {
            // Publicar borrador local: crea en el servidor y lo borra del teléfono.
            // Primero se guardan los cambios del formulario en el borrador local.
            draft.jobId = editing.jobId;
            repo.saveDraft(draft, saved -> {
                if (!isAdded()) return;
                if (!saved.ok) {
                    setLoading(false);
                    Snackbar.make(requireView(), saved.friendlyMessage(), Snackbar.LENGTH_LONG).show();
                    return;
                }
                repo.publishDraft(editing.jobId, result -> {
                    setLoading(false);
                    if (!isAdded()) return;
                    if (result.ok) {
                        onPublished();
                    } else {
                        Snackbar.make(requireView(), result.friendlyMessage(), Snackbar.LENGTH_LONG).show();
                    }
                });
            });
            return;
        }
        if (editing != null) {
            draft.jobId = editing.jobId;
            repo.updateJob(draft, update -> {
                if (!isAdded()) return;
                if (update.ok) uploadThenPublish(editing.jobId);
                else {
                    setLoading(false);
                    Snackbar.make(requireView(), update.friendlyMessage(), Snackbar.LENGTH_LONG).show();
                }
            });
        } else {
            repo.createJob(draft, created -> {
                if (!isAdded()) return;
                if (created.ok && created.data != null) uploadThenPublish(created.data.jobId);
                else {
                    setLoading(false);
                    Snackbar.make(requireView(), created.friendlyMessage(), Snackbar.LENGTH_LONG).show();
                }
            });
        }
    }

    private void publish(String jobId) {
        Repository.get(requireContext()).publishJob(jobId, result -> {
            setLoading(false);
            if (!isAdded()) return;
            if (result.ok) {
                onPublished();
            } else {
                Snackbar.make(requireView(), result.friendlyMessage(), Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void uploadThenPublish(String jobId) {
        if (offerPhoto == null) {
            publish(jobId);
            return;
        }
        Repository.get(requireContext()).uploadPhoto("offer", jobId,
                offerPhoto.detailBase64, offerPhoto.thumbBase64, uploaded -> {
                    if (!isAdded()) return;
                    if (uploaded.ok) publish(jobId);
                    else {
                        setLoading(false);
                        Snackbar.make(requireView(), "La oferta se creó, pero la foto no pudo subirse. Puedes reintentarlo al editarla.", Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    /** Publicación completada (§6): el botón pasa a menta con check y,
     *  tras 700 ms para ver el éxito, se abre Mis ofertas. */
    private void onPublished() {
        UiUtils.buttonSuccess((MaterialButton) btnPublish, getString(R.string.job_published_short));
        Snackbar.make(snackbarAnchor(), R.string.job_published, Snackbar.LENGTH_LONG).show();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isAdded()) ((MainActivity) requireActivity()).selectTab(R.id.nav_jobs);
        }, 700);
    }

    /** Construye el borrador desde el formulario; null si faltan obligatorios. */
    private Job buildJob() {
        titleLayout.setError(null);
        descriptionLayout.setError(null);
        salaryMinLayout.setError(null);
        salaryMaxLayout.setError(null);
        String titleText = UiUtils.text(title);
        String descText = UiUtils.text(description);
        boolean valid = true;
        View firstInvalid = null;
        if (titleText.isEmpty()) {
            titleLayout.setError(getString(R.string.required_field));
            firstInvalid = title;
            valid = false;
        }
        if (descText.isEmpty()) {
            descriptionLayout.setError(getString(R.string.required_field));
            if (firstInvalid == null) firstInvalid = description;
            valid = false;
        }
        String period = Job.salaryPeriodFromLabel(UiUtils.text(salaryPeriod));
        Integer minSalary = UiUtils.parseIntOrNull(UiUtils.text(salaryMin));
        Integer maxSalary = UiUtils.parseIntOrNull(UiUtils.text(salaryMax));
        if ("a_convenir".equals(period)) {
            minSalary = null;
            maxSalary = null;
        } else if (minSalary != null && maxSalary != null && minSalary > maxSalary) {
            salaryMaxLayout.setError("El salario máximo debe ser mayor que el mínimo.");
            if (firstInvalid == null) firstInvalid = salaryMax;
            valid = false;
        }
        if (!valid) {
            scrollToField(firstInvalid);
            return null;
        }
        Job j = editing != null ? editing : new Job();
        j.title = titleText;
        j.description = descText;
        j.category = UiUtils.text(category);
        j.responsibilities = TextUtils.splitComma(UiUtils.text(responsibilities));
        j.requirements = TextUtils.splitComma(UiUtils.text(requirements));
        j.workMode = Job.workModeFromLabel(UiUtils.text(mode));
        j.provinceCode = LocationPicker.provinceCode(province);
        j.provinceName = LocationPicker.provinceName(province);
        j.municipalityCode = LocationPicker.municipalityCode(province, municipality);
        j.municipalityName = LocationPicker.municipalityName(province, municipality);
        j.locality = UiUtils.text(locality);
        String c = UiUtils.text(contract);
        if (c.equals("Tiempo completo")) j.contractType = "tiempo_completo";
        else if (c.equals("Medio tiempo")) j.contractType = "medio_tiempo";
        else if (c.equals("Temporal")) j.contractType = "temporal";
        else if (c.equals("Por proyecto")) j.contractType = "por_proyecto";
        else if (c.equals("Indefinido")) j.contractType = "indefinido";
        else if (c.equals("Prácticas")) j.contractType = "practicas";
        j.salaryMin = minSalary;
        j.salaryMax = maxSalary;
        j.salaryPeriod = period.isEmpty() ? "mensual" : period;
        j.includesFood = checkFood.isChecked();
        j.includesTransport = checkTransport.isChecked();
        j.includesAccommodation = checkAccommodation.isChecked();
        j.includesTips = checkTips.isChecked();
        j.benefits = TextUtils.splitComma(UiUtils.text(benefits));
        j.contactHours = UiUtils.text(contactHours);
        j.startDate = startDateIso;
        j.experienceMin = UiUtils.parseIntOrNull(UiUtils.text(experience));
        j.languages = TextUtils.splitComma(UiUtils.text(languages));
        j.keywords = TextUtils.splitComma(UiUtils.text(keywords));
        return j;
    }

    private void scrollToField(View target) {
        if (scrollRoot == null || target == null) return;
        int top = 0;
        View node = target;
        while (node != null && node != scrollRoot) {
            top += node.getTop();
            ViewParent parent = node.getParent();
            node = parent instanceof View ? (View) parent : null;
        }
        if (node != scrollRoot) return;
        final int y = Math.max(0, top - (int) (24 * getResources().getDisplayMetrics().density));
        scrollRoot.post(() -> {
            if (!isAdded() || scrollRoot == null) return;
            scrollRoot.smoothScrollTo(0, y);
            target.requestFocus();
        });
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnPublish.setEnabled(!loading);
        btnDraft.setEnabled(!loading);
        btnPreview.setEnabled(!loading);
    }
}
