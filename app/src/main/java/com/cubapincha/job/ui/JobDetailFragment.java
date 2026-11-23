package com.cubapincha.job.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.RotateDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
import com.cubapincha.job.R;
import com.cubapincha.job.adapter.JobAdapter;
import com.cubapincha.job.data.JobFilter;
import com.cubapincha.job.data.Repository;
import com.cubapincha.job.data.SessionManager;
import com.cubapincha.job.model.Job;
import com.cubapincha.job.model.User;
import com.cubapincha.job.util.AnimUtils;
import com.cubapincha.job.util.MatchRingView;
import com.cubapincha.job.util.MatchUtils;
import com.cubapincha.job.util.ShareUtils;
import com.cubapincha.job.util.TextUtils;

import java.util.ArrayList;
import java.util.List;

/** Ficha completa de la oferta: secciones plegables, interés, contacto. */
public class JobDetailFragment extends Fragment implements JobAdapter.Listener {
    /** Motivos de reporte Cuba (contrato API 1.3.0, mismo orden que el diálogo). */
    private static final String[] REPORT_REASONS =
            {"pide_dinero", "salario_enganoso", "ubicacion_falsa",
             "identidad_no_confirmada", "acoso", "duplicada", "otro"};
    private static final String ARG_JOB_ID = "job_id";
    private static final String ARG_JOB_JSON = "job_json";

    public static JobDetailFragment newInstance(String jobId) {
        JobDetailFragment f = new JobDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_JOB_ID, jobId);
        f.setArguments(args);
        return f;
    }

    /** Apertura con la oferta ya cargada: el detalle se muestra al instante
     *  con los datos de la tarjeta y luego se refresca en segundo plano. */
    public static JobDetailFragment newInstance(Job job) {
        JobDetailFragment f = new JobDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_JOB_ID, job.jobId);
        args.putString(ARG_JOB_JSON, job.toJson().toString());
        f.setArguments(args);
        return f;
    }

    private String jobId;
    private Job job;
    private User currentUser;
    private LinearLayout skeletonContainer, errorState, ctaBar;
    private ScrollView contentScroll;
    private MaterialButton btnInterest, btnSaveBottom;
    private ImageButton btnSaveTop;
    private ImageView interestCheck;
    private JobAdapter similarAdapter;
    // Guarda de carrera (M4): si el usuario guardó o mostró interés después de
    // iniciar el refresco silencioso, ese refresco no sobrescribe su cambio.
    private long refreshStartedMs = 0;
    private long lastLocalMutationMs = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_job_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        jobId = getArguments() != null ? getArguments().getString(ARG_JOB_ID) : "";
        skeletonContainer = view.findViewById(R.id.skeleton_container);
        errorState = view.findViewById(R.id.error_state);
        contentScroll = view.findViewById(R.id.content_scroll);
        ctaBar = view.findViewById(R.id.cta_bar);
        btnInterest = view.findViewById(R.id.btn_interest);
        btnSaveBottom = view.findViewById(R.id.btn_save_bottom);
        btnSaveTop = view.findViewById(R.id.btn_save);
        interestCheck = view.findViewById(R.id.interest_check);

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        view.findViewById(R.id.btn_retry).setOnClickListener(v -> loadJob());

        RecyclerView similarList = view.findViewById(R.id.list_similar);
        similarList.setLayoutManager(new LinearLayoutManager(requireContext()));
        similarAdapter = new JobAdapter(this);
        similarList.setAdapter(similarAdapter);

        btnInterest.setOnClickListener(v -> onInterestClick());
        btnSaveTop.setOnClickListener(v -> toggleSave());
        btnSaveBottom.setOnClickListener(v -> toggleSave());
        view.findViewById(R.id.btn_share).setOnClickListener(v -> shareJob());
        view.findViewById(R.id.btn_report).setOnClickListener(v -> showReportMenu());

        setupSection(view, R.id.btn_section_resp, R.id.job_responsibilities);
        setupSection(view, R.id.btn_section_req, R.id.job_requirements);
        setupSection(view, R.id.btn_section_benefits, R.id.job_benefits);

        // Si venimos de una tarjeta, la oferta ya viaja en los argumentos:
        // se muestra de inmediato y se refresca en silencio. Si solo hay un
        // id (notificación, candidatura), se carga con skeleton.
        String jobJson = getArguments() != null ? getArguments().getString(ARG_JOB_JSON) : null;
        Job instant = parseJobJson(jobJson);
        if (instant != null) {
            job = instant;
            bindJob(true);
            loadSimilar();
            loadContactOptions();
            loadMatchReasons();
            refreshJobSilently();
        } else {
            loadJob();
        }
    }

    /** Razones de coincidencia ("por qué coincide contigo"), solo trabajador. */
    private void loadMatchReasons() {
        if ("company".equals(SessionManager.get(requireContext()).getRole())) return;
        Repository.get(requireContext()).me(result -> {
            if (!isAdded() || !result.ok || result.data == null || job == null) return;
            currentUser = result.data;
            List<Integer> reasons = MatchUtils.reasons(job, currentUser);
            View view = requireView();
            LinearLayout row = view.findViewById(R.id.match_reasons_row);
            row.removeAllViews();
            if (reasons.isEmpty()) return;
            view.findViewById(R.id.match_reasons_label).setVisibility(View.VISIBLE);
            view.findViewById(R.id.match_reasons_scroll).setVisibility(View.VISIBLE);
            for (int resId : reasons) {
                TextView chip = new TextView(requireContext());
                chip.setText(resId);
                chip.setBackgroundResource(R.drawable.bg_chip_soft);
                chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.violet_500));
                chip.setTextSize(13);
                int padH = (int) (10 * getResources().getDisplayMetrics().density);
                int padV = (int) (4 * getResources().getDisplayMetrics().density);
                chip.setPadding(padH, padV, padH, padV);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMarginEnd((int) (8 * getResources().getDisplayMetrics().density));
                row.addView(chip, lp);
            }
        });
    }

    private static Job parseJobJson(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            return Job.fromJson(new org.json.JSONObject(json));
        } catch (Exception e) {
            return null;
        }
    }

    /** Actualiza la oferta en segundo plano sin skeleton ni parpadeos: solo
     *  sincroniza el estado mutable (guardado, interés, estado). */
    private void refreshJobSilently() {
        refreshStartedMs = System.currentTimeMillis();
        Repository.get(requireContext()).getJob(jobId, result -> {
            if (!isAdded() || !result.ok || result.data == null) return;
            // El usuario actuó después de iniciar el refresco: su cambio manda.
            if (lastLocalMutationMs > refreshStartedMs) return;
            job.saved = result.data.saved;
            job.applicationStatus = result.data.applicationStatus;
            job.status = result.data.status;
            job.matchScore = result.data.matchScore;
            updateInterestButton();
            updateSaveButton();
        });
    }

    private void setupSection(View root, int buttonId, int contentId) {
        View content = root.findViewById(contentId);
        MaterialButton button = root.findViewById(buttonId);
        button.setOnClickListener(v -> {
            boolean show = content.getVisibility() != View.VISIBLE;
            content.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) AnimUtils.fadeSlideIn(content);
            // Solo gira el icono chevron: girar el botón entero dejaba el
            // texto de la sección boca abajo al expandir.
            rotateChevron(button, show);
        });
    }

    /** Gira el chevron del botón de sección 0° ↔ 180° con una animación
     *  suave, sin tocar el texto. El icono se envuelve una sola vez en un
     *  RotateDrawable y luego solo se anima su nivel. */
    private void rotateChevron(MaterialButton button, boolean expanded) {
        android.graphics.drawable.Drawable icon = button.getIcon();
        if (icon == null) return;
        RotateDrawable rot;
        if (icon instanceof RotateDrawable) {
            rot = (RotateDrawable) icon;
        } else {
            rot = new RotateDrawable();
            rot.setDrawable(icon);
            rot.setFromDegrees(0f);
            rot.setToDegrees(180f);
            rot.setPivotXRelative(true);
            rot.setPivotX(0.5f);
            rot.setPivotYRelative(true);
            rot.setPivotY(0.5f);
            button.setIcon(rot);
        }
        ValueAnimator anim = ValueAnimator.ofInt(rot.getLevel(), expanded ? 10000 : 0);
        anim.setDuration(220);
        anim.addUpdateListener(a -> rot.setLevel((Integer) a.getAnimatedValue()));
        anim.start();
    }

    private void loadJob() {
        skeletonContainer.setVisibility(View.VISIBLE);
        AnimUtils.pulse(skeletonContainer);
        contentScroll.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);
        ctaBar.setVisibility(View.GONE);
        Repository.get(requireContext()).getJob(jobId, result -> {
            AnimUtils.stopPulse(skeletonContainer);
            skeletonContainer.setVisibility(View.GONE);
            if (!isAdded()) return;
            if (result.ok && result.data != null) {
                job = result.data;
                bindJob(true);
                loadSimilar();
                loadContactOptions();
                loadMatchReasons();
            } else {
                errorState.setVisibility(View.VISIBLE);
            }
        });
    }

    private void bindJob(boolean animate) {
        View view = requireView();
        boolean company = "company".equals(SessionManager.get(requireContext()).getRole());
        TextView title = view.findViewById(R.id.job_title);
        TextView companyName = view.findViewById(R.id.company_name);
        title.setText(job.title);
        companyName.setText(job.companyName);
        companyName.setContentDescription("Ver valoraciones de " + job.companyName);
        companyName.setOnClickListener(v ->
                RatingUi.showRatings(this, job.companyId, job.companyName));
        view.findViewById(R.id.verified_icon).setVisibility(job.companyVerified ? View.VISIBLE : View.GONE);
        MatchRingView ring = view.findViewById(R.id.match_ring);
        view.findViewById(R.id.match_block).setVisibility(job.matchScore > 0 && !company ? View.VISIBLE : View.GONE);
        if (job.matchScore > 0) ring.setPercent(job.matchScore);
        // Etiquetas de confianza: solo aparecen cuando son ciertas.
        view.findViewById(R.id.trust_verified).setVisibility(job.companyVerified ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.trust_verified).setOnClickListener(v ->
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.verified_what)
                        .setMessage(R.string.verified_body)
                        .setPositiveButton(R.string.accept, null)
                        .show());
        view.findViewById(R.id.trust_today).setVisibility(
                "Hoy".equals(TextUtils.timeAgo(job.publishedAt)) ? View.VISIBLE : View.GONE);
        boolean hasContact = job.whatsappEnabled
                || (job.contactPhone != null && !job.contactPhone.isEmpty());
        view.findViewById(R.id.trust_contact).setVisibility(hasContact ? View.VISIBLE : View.GONE);

        boolean closed = !"published".equals(job.status);
        view.findViewById(R.id.closed_label).setVisibility(closed ? View.VISIBLE : View.GONE);

        TextView meta = view.findViewById(R.id.job_meta);
        String location = job.locationText();
        if (job.locality != null && !job.locality.isEmpty()) location += " · " + job.locality;
        meta.setText(location + " · " + Job.workModeLabel(job.workMode)
                + " · " + Job.contractLabel(job.contractType) + " · " + job.category);
        TextView salary = view.findViewById(R.id.job_salary);
        salary.setText(job.salaryText());
        TextView date = view.findViewById(R.id.job_date);
        date.setText("Publicada " + TextUtils.timeAgo(job.publishedAt).toLowerCase());
        TextView description = view.findViewById(R.id.job_description);
        description.setText(job.description);
        setListText(view, R.id.job_responsibilities, job.responsibilities);
        setListText(view, R.id.job_requirements, job.requirements);
        setListText(view, R.id.job_benefits, job.benefits);

        contentScroll.setVisibility(View.VISIBLE);
        ctaBar.setVisibility(company ? View.GONE : View.VISIBLE);
        updateInterestButton();
        updateSaveButton();
        if (animate) {
            AnimUtils.fadeSlideIn(contentScroll);
        } else {
            AnimUtils.resetItemState(contentScroll);
        }
    }

    private void setListText(View view, int id, List<String> items) {
        TextView tv = view.findViewById(id);
        if (items == null || items.isEmpty()) {
            tv.setText("No especificado.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String item : items) sb.append("• ").append(item).append("\n");
        tv.setText(sb.toString().trim());
    }

    private void loadSimilar() {
        JobFilter filter = new JobFilter();
        filter.category = job.category;
        filter.limit = 5;
        Repository.get(requireContext()).getJobs(filter, null, result -> {
            if (!isAdded() || result.data == null) return;
            List<Job> similar = new ArrayList<>();
            for (Job j : result.data.items) {
                if (!j.jobId.equals(job.jobId)) similar.add(j);
            }
            if (!similar.isEmpty()) {
                requireView().findViewById(R.id.similar_title).setVisibility(View.VISIBLE);
                similarAdapter.setJobs(similar);
            }
        });
    }

    private void loadContactOptions() {
        View root = requireView();
        MaterialButton whatsapp = root.findViewById(R.id.btn_whatsapp);
        MaterialButton call = root.findViewById(R.id.btn_call);
        // No ofrecer botones con un teléfono antiguo mientras se carga o si falla la API.
        whatsapp.setVisibility(View.GONE);
        call.setVisibility(View.GONE);
        Repository.get(requireContext()).getContactOptions("job", job.jobId, result -> {
            if (!isAdded()) return;
            View view = requireView();
            if (!result.ok || result.data == null) {
                Snackbar.make(view, "No se pudieron cargar las opciones de contacto. Inténtalo de nuevo.",
                        Snackbar.LENGTH_LONG).show();
                return;
            }
            if (result.ok && result.data != null) {
                boolean showWhatsapp = result.data.whatsappEnabled
                        && result.data.publicPhone != null && !result.data.publicPhone.isEmpty();
                boolean showCall = result.data.phoneEnabled
                        && result.data.publicPhone != null && !result.data.publicPhone.isEmpty();
                whatsapp.setVisibility(showWhatsapp ? View.VISIBLE : View.GONE);
                call.setVisibility(showCall ? View.VISIBLE : View.GONE);
                String phone = result.data.publicPhone;
                // Ambos botones abren la hoja de contacto: empresa, oferta,
                // horario recomendado y mensaje prellenado editable.
                View.OnClickListener openSheet = v -> {
                    if (getParentFragmentManager().findFragmentByTag("contact") == null) {
                        ContactSheetFragment.newInstance(job, phone, showWhatsapp, contactMessage())
                                .show(getParentFragmentManager(), "contact");
                    }
                };
                whatsapp.setOnClickListener(openSheet);
                call.setOnClickListener(openSheet);
            }
        });
    }

    /** Mensaje prellenado para la empresa (editable en la hoja de contacto). */
    private String contactMessage() {
        String municipality = job.municipalityName != null && !job.municipalityName.isEmpty()
                ? job.municipalityName : job.locationText();
        String base = "Hola, vi en CubaPincha la oferta de " + job.title + " en " + municipality + ".";
        if (currentUser != null && currentUser.skills != null && !currentUser.skills.isEmpty()) {
            base += " Tengo experiencia en " + currentUser.skills.get(0)
                    + " y quisiera conocer los próximos pasos.";
        } else if (currentUser != null && currentUser.headline != null && !currentUser.headline.isEmpty()) {
            base += " Soy " + currentUser.headline.toLowerCase() + " y quisiera conocer los próximos pasos.";
        } else {
            base += " Me interesa y quisiera conocer los próximos pasos.";
        }
        return base;
    }

    private void updateInterestButton() {
        boolean sent = job.applicationStatus != null
                && !"withdrawn".equals(job.applicationStatus)
                && !"rejected".equals(job.applicationStatus);
        boolean closed = !"published".equals(job.status);
        // Con la oferta cerrada no se puede mostrar interés, pero sí retirar
        // uno ya enviado: el botón solo se deshabilita para "Me interesa".
        btnInterest.setEnabled(sent || !closed);
        if (sent) {
            btnInterest.setText(R.string.withdraw_interest);
            btnInterest.setIconResource(R.drawable.ic_check_circle);
            interestCheck.setVisibility(View.VISIBLE);
        } else {
            btnInterest.setText(R.string.interested);
            btnInterest.setIconResource(R.drawable.ic_send);
            interestCheck.setVisibility(View.GONE);
        }
    }

    private void updateSaveButton() {
        btnSaveTop.setImageResource(job.saved ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark);
        btnSaveBottom.setText(job.saved ? R.string.saved : R.string.save_offer);
    }

    private void onInterestClick() {
        if (job == null) return;
        boolean sent = job.applicationStatus != null
                && !"withdrawn".equals(job.applicationStatus)
                && !"rejected".equals(job.applicationStatus);
        if (sent) {
            // Retirar interés: buscar la candidatura por jobId. El botón se
            // bloquea hasta terminar: sin esto, un doble toque retira dos veces.
            btnInterest.setEnabled(false);
            Repository.get(requireContext()).getApplications(null, result -> {
                if (!isAdded()) return;
                String appId = null;
                if (result.ok && result.data != null) {
                    for (com.cubapincha.job.model.Application a : result.data) {
                        if (a.jobId.equals(job.jobId)
                                && !"withdrawn".equals(a.status) && !"rejected".equals(a.status)) {
                            appId = a.applicationId;
                            break;
                        }
                    }
                }
                if (appId != null) {
                    lastLocalMutationMs = System.currentTimeMillis();
                    Repository.get(requireContext()).withdrawInterest(appId, r -> {
                        if (!isAdded()) return;
                        btnInterest.setEnabled(true);
                        if (r.ok) {
                            job.applicationStatus = null;
                            updateInterestButton();
                            Snackbar.make(requireView(), R.string.interest_withdrawn, Snackbar.LENGTH_LONG).show();
                        } else {
                            Snackbar.make(requireView(), r.friendlyMessage(), Snackbar.LENGTH_LONG).show();
                        }
                    });
                } else {
                    // Estado local desactualizado: no hay candidatura que retirar.
                    job.applicationStatus = null;
                    btnInterest.setEnabled(true);
                    updateInterestButton();
                    Snackbar.make(requireView(), "No encontramos esta candidatura. Puede que ya no esté activa.",
                            Snackbar.LENGTH_LONG).show();
                }
            });
            return;
        }
        SessionManager session = SessionManager.get(requireContext());
        if (!session.isInterestConfirmed()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.interest_confirm_title)
                    .setMessage(R.string.interest_confirm_body)
                    .setPositiveButton(R.string.interested, (d, w) -> {
                        session.setInterestConfirmed();
                        sendInterest();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } else {
            sendInterest();
        }
    }

    private void sendInterest() {
        lastLocalMutationMs = System.currentTimeMillis();
        btnInterest.setEnabled(false);
        Repository.get(requireContext()).expressInterest(job.jobId, result -> {
            btnInterest.setEnabled(true);
            if (!isAdded()) return;
            if (result.queued) {
                job.applicationStatus = "sent";
                updateInterestButton();
                Snackbar.make(requireView(), R.string.pending_sync, Snackbar.LENGTH_LONG).show();
                return;
            }
            if (result.ok && result.data != null) {
                job.applicationStatus = result.data.status;
                updateInterestButton();
                // Check animado que se dibuja + haptics: confirmación cálida.
                interestCheck.setVisibility(View.VISIBLE);
                interestCheck.setImageResource(R.drawable.avd_check);
                AnimUtils.playAnimated(interestCheck);
                AnimUtils.hapticTick(requireView());
                Snackbar.make(requireView(), R.string.interest_success, Snackbar.LENGTH_LONG)
                        .setAction(R.string.view_tracking, v ->
                                ((MainActivity) requireActivity()).selectTab(R.id.nav_interests))
                        .show();
            } else {
                Snackbar.make(requireView(), result.friendlyMessage(), Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private long lastSaveTapMs = 0;

    private void toggleSave() {
        if (job == null) return;
        long now = System.currentTimeMillis();
        if (now - lastSaveTapMs < 350) return;
        lastSaveTapMs = now;
        lastLocalMutationMs = now;
        boolean newState = !job.saved;
        if (newState) {
            btnSaveTop.setImageResource(R.drawable.avd_bookmark);
            AnimUtils.playAnimated(btnSaveTop);
            AnimUtils.hapticTick(btnSaveTop);
        }
        job.saved = newState;
        updateSaveButton();
        Repository.get(requireContext()).saveJob(job.jobId, newState, result -> {
            if (!isAdded()) return;
            if (newState) {
                Snackbar.make(requireView(), R.string.offer_saved, Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo, v -> {
                            job.saved = false;
                            updateSaveButton();
                            Repository.get(requireContext()).saveJob(job.jobId, false, r -> {
                            });
                        })
                        .show();
            }
        });
    }

    private void shareJob() {
        if (job == null) return;
        showIconChoiceDialog(R.string.share, new int[]{
                        R.drawable.ic_share, R.drawable.ic_copy, R.drawable.ic_whatsapp,
                        R.drawable.ic_send, R.drawable.ic_mail},
                new String[]{getString(R.string.share), getString(R.string.copy_link),
                        getString(R.string.whatsapp), getString(R.string.share_via_telegram),
                        getString(R.string.share_via_email)}, which -> {
                    switch (which) {
                        case 0: ShareUtils.shareSystem(requireContext(), job); break;
                        case 1:
                            ShareUtils.copyLink(requireContext(), job);
                            Snackbar.make(requireView(), R.string.link_copied, Snackbar.LENGTH_SHORT).show();
                            break;
                        case 2: ShareUtils.shareWhatsApp(requireContext(), job); break;
                        case 3: ShareUtils.shareTelegram(requireContext(), job); break;
                        case 4: ShareUtils.shareEmail(requireContext(), job); break;
                    }
                });
    }

    private void showReportMenu() {
        if (job == null) return;
        showIconChoiceDialog(R.string.report, new int[]{
                        R.drawable.ic_money, R.drawable.price_check_24px,
                        R.drawable.location_off_24px, R.drawable.ic_verified_badge,
                        R.drawable.person_off_24px, R.drawable.ic_copy, R.drawable.ic_more},
                new String[]{getString(R.string.report_money), getString(R.string.report_salary),
                        getString(R.string.report_location), getString(R.string.report_identity),
                        getString(R.string.report_harassment), getString(R.string.report_duplicate),
                        "Otro motivo"}, which -> Repository.get(requireContext())
                        .report("job", job.jobId, REPORT_REASONS[which], result -> {
                            if (isAdded()) {
                                Snackbar.make(requireView(), result.ok
                                        ? getString(R.string.report_sent) : result.friendlyMessage(),
                                        Snackbar.LENGTH_LONG).show();
                            }
                        }));
    }

    /** Diálogo temático reutilizable: tarjetas táctiles, iconos outlined y
     * colores resueltos desde el tema para que claro/oscuro sean consistentes. */
    private void showIconChoiceDialog(int titleRes, int[] icons, String[] labels,
                                      ChoiceListener listener) {
        Context context = requireContext();
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dp(20);
        content.setPadding(horizontal, dp(8), horizontal, dp(4));
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            MaterialCardView card = new MaterialCardView(context);
            card.setUseCompatPadding(false);
            card.setRadius(dp(14));
            card.setCardElevation(dp(1));
            card.setStrokeWidth(dp(1));
            card.setStrokeColor(themeColor(com.google.android.material.R.attr.colorOutlineVariant));
            card.setCardBackgroundColor(themeColor(com.google.android.material.R.attr.colorSurface));
            LinearLayout row = new LinearLayout(context);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            ImageView icon = new ImageView(context);
            icon.setImageResource(icons[i]);
            icon.setColorFilter(themeColor(android.R.attr.colorControlNormal));
            row.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
            TextView label = new TextView(context);
            label.setText(labels[i]);
            label.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface));
            label.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1f);
            textParams.setMarginStart(dp(14));
            row.addView(label, textParams);
            card.addView(row);
            card.setOnClickListener(v -> {
                if (v.getTag() instanceof AlertDialog) ((AlertDialog) v.getTag()).dismiss();
                listener.onChoice(index);
            });
            content.addView(card, new LinearLayout.LayoutParams(-1, -2) {{ bottomMargin = dp(8); }});
        }
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(titleRes)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .create();
        for (int i = 0; i < content.getChildCount(); i++) content.getChildAt(i).setTag(dialog);
        dialog.show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int themeColor(int attr) {
        TypedValue value = new TypedValue();
        requireContext().getTheme().resolveAttribute(attr, value, true);
        return value.resourceId != 0 ? ContextCompat.getColor(requireContext(), value.resourceId) : value.data;
    }

    private interface ChoiceListener { void onChoice(int index); }

    @Override
    public void onJobClick(Job job) {
        ((MainActivity) requireActivity()).openJobDetail(job);
    }

    @Override
    public void onSaveClick(Job job, android.widget.ImageButton saveButton) {
        boolean newState = !job.saved;
        job.saved = newState;
        Repository.get(requireContext()).saveJob(job.jobId, newState, result -> {
            if (isAdded()) similarAdapter.updateJob(job);
        });
    }

    @Override
    public void onDestroyView() {
        // Detener el pulso infinito del skeleton al destruir la vista.
        AnimUtils.stopPulse(skeletonContainer);
        super.onDestroyView();
    }
}
