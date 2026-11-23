package com.cubapincha.job.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.cubapincha.job.util.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catálogo geográfico de Cuba: 15 provincias + el municipio especial
 * Isla de la Juventud, con sus 168 municipios (según la división
 * político-administrativa vigente; verificada contra EcuRed/ONEI).
 *
 * Los códigos de provincia siguen la auditoría Cuba (HAB, ART, MAY, MAT,
 * CFG, VCL, SSP, CAV, CAM, LTU, HOL, GRA, SCU, GUA, PRI, IJ). Los códigos
 * de municipio son estables y legibles: PROVINCIA-nombre-normalizado
 * (p. ej. HAB-plaza-de-la-revolucion).
 *
 * El catálogo vive en la app (sin red) pero puede corregirse desde el
 * backend: la pestaña `catalogos` de Sheets puede devolver listas de
 * municipios por provincia; esas correcciones se guardan localmente y
 * sustituyen a la lista de la app para esa provincia (sin cambiar el APK).
 */
public final class CubaCatalog {
    private CubaCatalog() {}

    /** Versión del catálogo embebido (sube cuando cambian las listas). */
    public static final int CATALOG_VERSION = 1;

    private static final String PREFS = "cubapincha_catalog";
    private static final String KEY_OVERRIDES = "municipality_overrides_json";

    public static final class Province {
        public final String code;
        public final String name;
        public final String[] municipalities;

        Province(String code, String name, String... municipalities) {
            this.code = code;
            this.name = name;
            this.municipalities = municipalities;
        }
    }

    private static final List<Province> PROVINCES = new ArrayList<>();
    private static final Map<String, Province> BY_CODE = new LinkedHashMap<>();
    /** Correcciones del backend: código de provincia -> municipios. */
    private static Map<String, List<String>> overrides;

    static {
        add("PRI", "Pinar del Río",
                "Consolación del Sur", "Guane", "La Palma", "Los Palacios", "Mantua",
                "Minas de Matahambre", "Pinar del Río", "San Juan y Martínez",
                "San Luis", "Sandino", "Viñales");
        add("ART", "Artemisa",
                "Alquízar", "Artemisa", "Bahía Honda", "Bauta", "Caimito",
                "Candelaria", "Guanajay", "Güira de Melena", "Mariel",
                "San Antonio de los Baños", "San Cristóbal");
        add("HAB", "La Habana",
                "Arroyo Naranjo", "Boyeros", "Centro Habana", "Cerro", "Cotorro",
                "Diez de Octubre", "Guanabacoa", "La Habana del Este",
                "La Habana Vieja", "La Lisa", "Marianao", "Playa",
                "Plaza de la Revolución", "Regla", "San Miguel del Padrón");
        add("MAY", "Mayabeque",
                "Batabanó", "Bejucal", "Güines", "Jaruco", "Madruga",
                "Melena del Sur", "Nueva Paz", "Quivicán", "San José de las Lajas",
                "San Nicolás de Bari", "Santa Cruz del Norte");
        add("MAT", "Matanzas",
                "Calimete", "Cárdenas", "Ciénaga de Zapata", "Colón",
                "Jagüey Grande", "Jovellanos", "Limonar", "Los Arabos", "Martí",
                "Matanzas", "Pedro Betancourt", "Perico", "Unión de Reyes");
        add("CFG", "Cienfuegos",
                "Abreus", "Aguada de Pasajeros", "Cienfuegos", "Cruces",
                "Cumanayagua", "Lajas", "Palmira", "Rodas");
        add("VCL", "Villa Clara",
                "Caibarién", "Camajuaní", "Cifuentes", "Corralillo", "Encrucijada",
                "Manicaragua", "Placetas", "Quemado de Güines", "Ranchuelo",
                "Remedios", "Sagua la Grande", "Santa Clara", "Santo Domingo");
        add("SSP", "Sancti Spíritus",
                "Cabaiguán", "Fomento", "Jatibonico", "La Sierpe",
                "Sancti Spíritus", "Taguasco", "Trinidad", "Yaguajay");
        add("CAV", "Ciego de Ávila",
                "Baraguá", "Bolivia", "Chambas", "Ciego de Ávila", "Ciro Redondo",
                "Florencia", "Majagua", "Morón", "Primero de Enero", "Venezuela");
        add("CAM", "Camagüey",
                "Camagüey", "Carlos Manuel de Céspedes", "Esmeralda", "Florida",
                "Guáimaro", "Jimaguayú", "Minas", "Najasa", "Nuevitas",
                "Santa Cruz del Sur", "Sibanicú", "Sierra de Cubitas", "Vertientes");
        add("LTU", "Las Tunas",
                "Amancio", "Colombia", "Jesús Menéndez", "Jobabo", "Las Tunas",
                "Majibacoa", "Manatí", "Puerto Padre");
        add("HOL", "Holguín",
                "Antilla", "Báguanos", "Banes", "Cacocum", "Calixto García",
                "Cueto", "Frank País", "Gibara", "Holguín", "Mayarí", "Moa",
                "Rafael Freyre", "Sagua de Tánamo", "Urbano Noris");
        add("GRA", "Granma",
                "Bartolomé Masó", "Bayamo", "Buey Arriba", "Campechuela",
                "Cauto Cristo", "Guisa", "Jiguaní", "Manzanillo", "Media Luna",
                "Niquero", "Pilón", "Río Cauto", "Yara");
        add("SCU", "Santiago de Cuba",
                "Contramaestre", "Guamá", "Julio Antonio Mella", "Palma Soriano",
                "San Luis", "Santiago de Cuba", "Segundo Frente",
                "Songo-La Maya", "Tercer Frente");
        add("GUA", "Guantánamo",
                "Baracoa", "Caimanera", "El Salvador", "Guantánamo", "Imías",
                "Maisí", "Manuel Tames", "Niceto Pérez", "San Antonio del Sur",
                "Yateras");
        add("IJ", "Isla de la Juventud", "Isla de la Juventud");
    }

    private static void add(String code, String name, String... municipalities) {
        Province p = new Province(code, name, municipalities);
        PROVINCES.add(p);
        BY_CODE.put(code, p);
    }

    public static List<Province> provinces() {
        return new ArrayList<>(PROVINCES);
    }

    /** Nombres de provincia, en orden de occidente a oriente. */
    public static String[] provinceNames() {
        String[] names = new String[PROVINCES.size()];
        for (int i = 0; i < PROVINCES.size(); i++) names[i] = PROVINCES.get(i).name;
        return names;
    }

    /** Códigos de provincia, en el mismo orden que provinceNames(). */
    public static String[] provinceCodes() {
        String[] codes = new String[PROVINCES.size()];
        for (int i = 0; i < PROVINCES.size(); i++) codes[i] = PROVINCES.get(i).code;
        return codes;
    }

    public static Province provinceByCode(String code) {
        return code == null ? null : BY_CODE.get(code);
    }

    public static String provinceName(String code) {
        Province p = provinceByCode(code);
        return p != null ? p.name : "";
    }

    public static String provinceCodeByName(String name) {
        if (name == null) return "";
        for (Province p : PROVINCES) {
            if (p.name.equalsIgnoreCase(name.trim())) return p.code;
        }
        return "";
    }

    /** Municipios de una provincia (con correcciones del backend si las hay). */
    public static String[] municipalities(String provinceCode) {
        if (overrides != null && overrides.containsKey(provinceCode)) {
            List<String> list = overrides.get(provinceCode);
            return list.toArray(new String[0]);
        }
        Province p = provinceByCode(provinceCode);
        return p != null ? p.municipalities.clone() : new String[0];
    }

    /** Código estable de municipio: PROVINCIA-nombre-normalizado. */
    public static String municipalityCode(String provinceCode, String municipalityName) {
        if (provinceCode == null || municipalityName == null) return "";
        String slug = TextUtils.normalize(municipalityName)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return provinceCode + "-" + slug;
    }

    /** Nombre de municipio a partir de su código ("" si no existe). */
    public static String municipalityName(String provinceCode, String municipalityCode) {
        if (provinceCode == null || municipalityCode == null) return "";
        for (String name : municipalities(provinceCode)) {
            if (municipalityCode(provinceCode, name).equals(municipalityCode)) return name;
        }
        return "";
    }

    public static boolean isValidProvince(String code) {
        return code != null && BY_CODE.containsKey(code);
    }

    public static boolean isValidMunicipality(String provinceCode, String municipalityName) {
        if (municipalityName == null) return false;
        for (String m : municipalities(provinceCode)) {
            if (m.equalsIgnoreCase(municipalityName.trim())) return true;
        }
        return false;
    }

    /** Total de municipios del catálogo embebido (168). */
    public static int municipalityCount() {
        int n = 0;
        for (Province p : PROVINCES) n += p.municipalities.length;
        return n;
    }

    // ---- Correcciones desde el backend (pestaña `catalogos`) ----
    /**
     * Guarda correcciones de municipios recibidas del backend.
     * Formato: { "HAB": ["Playa", ...], ... }. Solo se guardan provincias
     * conocidas y listas no vacías; lo demás se ignora sin romper nada.
     */
    public static void saveOverrides(Context ctx, JSONObject overridesJson) {
        if (ctx == null || overridesJson == null) return;
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (Province p : PROVINCES) {
            JSONArray arr = overridesJson.optJSONArray(p.code);
            if (arr != null && arr.length() > 0) {
                List<String> list = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    String name = arr.optString(i, "").trim();
                    if (!name.isEmpty()) list.add(name);
                }
                if (!list.isEmpty()) map.put(p.code, list);
            }
        }
        overrides = map;
        SharedPreferences prefs = ctx.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_OVERRIDES, overridesJson.toString()).apply();
    }

    /** Carga las correcciones guardadas (llamar una vez al iniciar la app). */
    public static void loadOverrides(Context ctx) {
        if (ctx == null || overrides != null) return;
        overrides = new LinkedHashMap<>();
        try {
            SharedPreferences prefs = ctx.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String json = prefs.getString(KEY_OVERRIDES, null);
            if (json == null || json.isEmpty()) return;
            JSONObject o = new JSONObject(json);
            for (Province p : PROVINCES) {
                JSONArray arr = o.optJSONArray(p.code);
                if (arr != null && arr.length() > 0) {
                    List<String> list = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        String name = arr.optString(i, "").trim();
                        if (!name.isEmpty()) list.add(name);
                    }
                    if (!list.isEmpty()) overrides.put(p.code, list);
                }
            }
        } catch (Exception ignored) {
            // Catálogo corrupto: se ignora y se usa el embebido.
            overrides = new LinkedHashMap<>();
        }
    }
}
