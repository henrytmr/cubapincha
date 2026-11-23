package com.cubapincha.job.ui;

import android.widget.AutoCompleteTextView;

import com.cubapincha.job.data.CubaCatalog;

/**
 * Selección de ubicación en cascada: provincia → municipio.
 * El valor inicial siempre es vacío ("Selecciona una provincia"): nunca se
 * preselecciona La Habana ni ninguna otra provincia.
 */
public final class LocationPicker {
    private LocationPicker() {}

    /** Prepara los dos desplegables con su cascada (municipio deshabilitado hasta elegir provincia). */
    public static void setup(AutoCompleteTextView province, AutoCompleteTextView municipality) {
        setup(province, municipality, null);
    }

    /** Igual que setup, pero avisa con onChange cada vez que cambia provincia o municipio. */
    public static void setup(AutoCompleteTextView province, AutoCompleteTextView municipality,
                             Runnable onChange) {
        UiUtils.setupDropdown(province, CubaCatalog.provinceNames());
        province.setOnItemClickListener((parent, view, position, id) -> {
            String code = CubaCatalog.provinceCodes()[position];
            loadMunicipalities(municipality, code);
            if (onChange != null) onChange.run();
        });
        municipality.setOnItemClickListener((parent, view, position, id) -> {
            if (onChange != null) onChange.run();
        });
        refreshMunicipalities(province, municipality);
    }

    /** Recarga los municipios de la provincia actualmente escrita, sin borrar
     *  el municipio ya escrito. Llamar en onViewStateRestored: en onViewCreated
     *  la vista aún no ha restaurado su texto tras una rotación, así que el
     *  municipio quedaba deshabilitado con el desplegable vacío. */
    public static void refreshMunicipalities(AutoCompleteTextView province, AutoCompleteTextView municipality) {
        String existingCode = provinceCode(province);
        if (!existingCode.isEmpty()) {
            loadMunicipalities(municipality, existingCode, false);
        }
    }

    private static void loadMunicipalities(AutoCompleteTextView municipality, String provinceCode) {
        loadMunicipalities(municipality, provinceCode, true);
    }

    private static void loadMunicipalities(AutoCompleteTextView municipality, String provinceCode,
                                           boolean clear) {
        UiUtils.setupDropdown(municipality, CubaCatalog.municipalities(provinceCode));
        municipality.setEnabled(true);
        if (clear) municipality.setText("");
    }

    /** Rellena ambos campos desde códigos guardados (edición de perfil/oferta). */
    public static void setSelection(AutoCompleteTextView province, AutoCompleteTextView municipality,
                                    String provinceCode, String municipalityName) {
        String name = CubaCatalog.provinceName(provinceCode);
        if (!name.isEmpty()) {
            province.setText(name, false);
            UiUtils.setupDropdown(municipality, CubaCatalog.municipalities(provinceCode));
            municipality.setEnabled(true);
            if (municipalityName != null && !municipalityName.isEmpty()) {
                municipality.setText(municipalityName, false);
            }
        }
    }

    public static String provinceCode(AutoCompleteTextView province) {
        return CubaCatalog.provinceCodeByName(UiUtils.text(province));
    }

    public static String provinceName(AutoCompleteTextView province) {
        String name = UiUtils.text(province);
        return CubaCatalog.isValidProvince(CubaCatalog.provinceCodeByName(name)) ? name : "";
    }

    /** Nombre de municipio solo si pertenece a la provincia elegida. */
    public static String municipalityName(AutoCompleteTextView province, AutoCompleteTextView municipality) {
        String code = provinceCode(province);
        String name = UiUtils.text(municipality);
        if (code.isEmpty() || name.isEmpty()) return "";
        return CubaCatalog.isValidMunicipality(code, name) ? name : "";
    }

    public static String municipalityCode(AutoCompleteTextView province, AutoCompleteTextView municipality) {
        String code = provinceCode(province);
        String name = municipalityName(province, municipality);
        return name.isEmpty() ? "" : CubaCatalog.municipalityCode(code, name);
    }
}
