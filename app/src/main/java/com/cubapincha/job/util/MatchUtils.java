package com.cubapincha.job.util;

import com.cubapincha.job.R;
import com.cubapincha.job.model.Job;
import com.cubapincha.job.model.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Por qué una oferta coincide con el trabajador: el porcentaje nunca es una
 * promesa absoluta; estas razones explican la coincidencia en palabras.
 */
public final class MatchUtils {
    private MatchUtils() {}

    /** Razones de coincidencia como ids de string (para mostrar como chips). */
    public static List<Integer> reasons(Job job, User user) {
        List<Integer> out = new ArrayList<>();
        if (job == null || user == null) return out;

        // Misma provincia.
        if (user.provinceCode != null && !user.provinceCode.isEmpty()
                && user.provinceCode.equals(job.provinceCode)) {
            out.add(R.string.match_province);
        }
        // Experiencia relacionada: habilidad del usuario en la oferta, o
        // años de experiencia suficientes para el mínimo pedido.
        boolean experience = false;
        if (user.skills != null && !user.skills.isEmpty()) {
            String hay = TextUtils.normalize(job.title + " " + job.description
                    + " " + TextUtils.joinComma(job.requirements)
                    + " " + TextUtils.joinComma(job.keywords));
            for (String skill : user.skills) {
                if (!skill.trim().isEmpty() && hay.contains(TextUtils.normalize(skill))) {
                    experience = true;
                    break;
                }
            }
        }
        if (!experience && user.experienceYears != null && job.experienceMin != null
                && user.experienceYears >= job.experienceMin) {
            experience = true;
        }
        if (experience) out.add(R.string.match_experience);
        // Salario compatible: los rangos se solapan.
        if (user.salaryMin != null && user.salaryMax != null
                && job.salaryMin != null && job.salaryMax != null
                && user.salaryMin <= job.salaryMax && user.salaryMax >= job.salaryMin) {
            out.add(R.string.match_salary);
        }
        // Modalidad compatible.
        if (user.workModes != null && job.workMode != null && !job.workMode.isEmpty()
                && user.workModes.contains(job.workMode)) {
            out.add(R.string.match_mode);
        }
        // Disponibilidad compatible: el trabajador puede empezar pronto.
        if (user.availability != null
                && (user.availability.contains("Inmediata") || user.availability.contains("2 semanas"))) {
            out.add(R.string.match_availability);
        }
        return out;
    }
}
