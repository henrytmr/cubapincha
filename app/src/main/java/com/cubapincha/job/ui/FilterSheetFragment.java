package com.cubapincha.job.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.cubapincha.job.R;
import com.cubapincha.job.data.JobFilter;
import com.cubapincha.job.data.SeedData;
import com.cubapincha.job.model.Job;

/**
 * Hoja inferior de filtros de búsqueda. El filtro viaja por argumentos y el
 * resultado por la API de Fragment Result: funciona tras rotar y tras
 * restaurar el sistema (antes usaba un campo static y un listener que se
 * perdían, y "Aplicar" dejaba de responder).
 *
 * Ubicación en cascada provincia → municipio, alcance de movilidad y
 * "solo con salario visible" (contrato API 1.3.0).
 */
public class FilterSheetFragment extends BottomSheetDialogFragment {
    public static final String REQUEST_KEY = "filters_applied";
    public static final String KEY_FILTER = "filter";
    private static final String ARG_FILTER = "filter";

    public static FilterSheetFragment newInstance(JobFilter filter) {
        FilterSheetFragment f = new FilterSheetFragment();
        Bundle args = new Bundle();
        args.putBundle(ARG_FILTER, filter != null ? filter.copy().toBundle() : new JobFilter().toBundle());
        f.setArguments(args);
        return f;
    }

    private JobFilter current;
    // Campos: onViewStateRestored los necesita tras la restauración.
    private AutoCompleteTextView province, municipality;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_filters, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        current = JobFilter.fromBundle(getArguments() != null ? getArguments().getBundle(ARG_FILTER) : null);

        AutoCompleteTextView category = view.findViewById(R.id.input_category);
        province = view.findViewById(R.id.input_province);
        municipality = view.findViewById(R.id.input_municipality);
        AutoCompleteTextView mobility = view.findViewById(R.id.input_mobility);
        AutoCompleteTextView mode = view.findViewById(R.id.input_mode);
        AutoCompleteTextView contract = view.findViewById(R.id.input_contract);
        TextInputEditText salaryMin = view.findViewById(R.id.input_salary_min);
        TextInputEditText salaryMax = view.findViewById(R.id.input_salary_max);
        CheckBox verified = view.findViewById(R.id.check_verified);
        CheckBox onlySalary = view.findViewById(R.id.check_salary);

        UiUtils.setupDropdown(category, SeedData.CATEGORIES);
        LocationPicker.setup(province, municipality);
        UiUtils.setupDropdown(mobility, new String[]{
                getString(R.string.mobility_my_municipality),
                getString(R.string.mobility_my_province),
                getString(R.string.mobility_anywhere),
                getString(R.string.mobility_remote)});
        UiUtils.setupDropdown(mode, new String[]{"Presencial", "Híbrido", "Remoto", "Por zonas"});
        UiUtils.setupDropdown(contract, new String[]{"Tiempo completo", "Medio tiempo", "Temporal", "Por proyecto", "Indefinido", "Prácticas"});

        if (current.category != null) category.setText(current.category, false);
        if (current.provinceCode != null && !current.provinceCode.isEmpty()) {
            LocationPicker.setSelection(province, municipality, current.provinceCode,
                    current.municipalityCode != null
                            ? municipalityNameFromCode(current.provinceCode, current.municipalityCode) : "");
        }
        if (current.mobilityScope != null) mobility.setText(JobFilter.mobilityScopeLabel(current.mobilityScope), false);
        if (current.workMode != null) mode.setText(Job.workModeLabel(current.workMode), false);
        if (current.contractType != null) contract.setText(Job.contractLabel(current.contractType), false);
        if (current.minSalary != null) salaryMin.setText(String.valueOf(current.minSalary));
        if (current.maxSalary != null) salaryMax.setText(String.valueOf(current.maxSalary));
        verified.setChecked(current.verifiedOnly);
        onlySalary.setChecked(current.onlyWithSalary);

        view.findViewById(R.id.btn_clear).setOnClickListener(v -> {
            deliver(new JobFilter());
            dismiss();
        });
        view.findViewById(R.id.btn_apply).setOnClickListener(v -> {
            JobFilter f = new JobFilter();
            f.q = current.q;
            f.sort = current.sort;
            String cat = UiUtils.text(category);
            f.category = cat.isEmpty() ? null : cat;
            String provCode = LocationPicker.provinceCode(province);
            f.provinceCode = provCode.isEmpty() ? null : provCode;
            String munCode = LocationPicker.municipalityCode(province, municipality);
            f.municipalityCode = munCode.isEmpty() ? null : munCode;
            String mob = UiUtils.text(mobility);
            if (mob.equals(getString(R.string.mobility_my_municipality))) f.mobilityScope = "mi_municipio";
            else if (mob.equals(getString(R.string.mobility_my_province))) f.mobilityScope = "mi_provincia";
            else if (mob.equals(getString(R.string.mobility_anywhere))) f.mobilityScope = "cualquier_lugar";
            else if (mob.equals(getString(R.string.mobility_remote))) f.mobilityScope = "remoto";
            String m = UiUtils.text(mode).toLowerCase();
            if (m.isEmpty()) f.workMode = null;
            else if (m.equals("híbrido")) f.workMode = "hibrido";
            else if (m.equals("por zonas")) f.workMode = "por_zonas";
            else f.workMode = m;
            String c = UiUtils.text(contract);
            if (c.equals("Tiempo completo")) f.contractType = "tiempo_completo";
            else if (c.equals("Medio tiempo")) f.contractType = "medio_tiempo";
            else if (c.equals("Temporal")) f.contractType = "temporal";
            else if (c.equals("Por proyecto")) f.contractType = "por_proyecto";
            else if (c.equals("Indefinido")) f.contractType = "indefinido";
            else if (c.equals("Prácticas")) f.contractType = "practicas";
            f.minSalary = UiUtils.parseIntOrNull(UiUtils.text(salaryMin));
            f.maxSalary = UiUtils.parseIntOrNull(UiUtils.text(salaryMax));
            f.verifiedOnly = verified.isChecked();
            f.onlyWithSalary = onlySalary.isChecked();
            deliver(f);
            dismiss();
        });
    }

    /** Nombre de municipio a partir de su código (para rellenar el filtro guardado). */
    private static String municipalityNameFromCode(String provinceCode, String municipalityCode) {
        for (String name : com.cubapincha.job.data.CubaCatalog.municipalities(provinceCode)) {
            if (com.cubapincha.job.data.CubaCatalog.municipalityCode(provinceCode, name).equals(municipalityCode)) {
                return name;
            }
        }
        return "";
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        // La vista ya restauró el texto de provincia: recargar sus municipios.
        LocationPicker.refreshMunicipalities(province, municipality);
    }

    private void deliver(JobFilter filter) {
        Bundle result = new Bundle();
        result.putBundle(KEY_FILTER, filter.toBundle());
        getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
    }
}
