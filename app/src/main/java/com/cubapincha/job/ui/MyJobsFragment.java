package com.cubapincha.job.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.card.MaterialCardView;
import com.cubapincha.job.R;
import com.cubapincha.job.adapter.CompanyJobAdapter;
import com.cubapincha.job.data.JobFilter;
import com.cubapincha.job.data.Repository;
import com.cubapincha.job.data.SessionManager;
import com.cubapincha.job.model.Job;
import com.cubapincha.job.util.AnimUtils;
import com.cubapincha.job.util.ShareUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Mis ofertas: filtros por estado y acciones de gestión.
 *
 * v2.0: los borradores guardados en el teléfono aparecen primero (filtro
 * "Borradores" o "Todas"), el menú ⋮ separa publicar / compartir / cerrar y
 * archivar / eliminar con tarjetas, archivar conserva el historial y
 * ofrece Deshacer; eliminar siempre requiere confirmación.
 */
public class MyJobsFragment extends Fragment implements CompanyJobAdapter.Listener {
    private static final int MENU_PUBLISH = 1;
    private static final int MENU_SHARE = 2;
    private static final int MENU_COPY_LINK = 3;
    private static final int MENU_WHATSAPP = 4;
    private static final int MENU_TELEGRAM = 5;
    private static final int MENU_EMAIL = 6;
    private static final int MENU_CLOSE = 7;
    private static final int MENU_ARCHIVE = 8;
    private static final int MENU_REACTIVATE = 9;
    private static final int MENU_DELETE = 10;

    private CompanyJobAdapter adapter;
    private LinearLayout skeletonContainer, emptyState, errorState;
    private TextView errorBody;
    private RecyclerView list;
    private String statusFilter = null;
    // Vigilante del esqueleto: si la red no responde en 15 s, el esqueleto
    // se esconde y aparece el estado de reintento (nunca se queda colgado).
    private final Handler watchdogHandler = new Handler(Looper.getMainLooper());
    private Runnable watchdog;
    private boolean requestDone;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_my_jobs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainActivity activity = (MainActivity) requireActivity();
        list = view.findViewById(R.id.list_jobs);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new CompanyJobAdapter(this);
        list.setAdapter(adapter);
        skeletonContainer = view.findViewById(R.id.skeleton_container);
        emptyState = view.findViewById(R.id.empty_state);
        errorState = view.findViewById(R.id.error_state);
        errorBody = view.findViewById(R.id.error_body);
        view.findViewById(R.id.btn_retry).setOnClickListener(v -> loadJobs());
        SwipeRefreshLayout refresh = view.findViewById(R.id.swipe_refresh);
        refresh.setOnRefreshListener(() -> { loadJobs(); refresh.setRefreshing(false); });

        view.findViewById(R.id.btn_new).setOnClickListener(v -> activity.openCreateJob(null));
        view.findViewById(R.id.btn_create_first).setOnClickListener(v -> activity.openCreateJob(null));

        view.findViewById(R.id.chip_all).setOnClickListener(v -> setFilter(null));
        view.findViewById(R.id.chip_drafts).setOnClickListener(v -> setFilter("draft"));
        view.findViewById(R.id.chip_active).setOnClickListener(v -> setFilter("published"));
        view.findViewById(R.id.chip_paused).setOnClickListener(v -> setFilter("paused"));
        view.findViewById(R.id.chip_closed).setOnClickListener(v -> setFilter("closed"));
        view.findViewById(R.id.chip_archived).setOnClickListener(v -> setFilter("archived"));

        if (savedInstanceState != null) {
            statusFilter = savedInstanceState.getString("statusFilter");
        }
        updateChipStates();
        loadJobs();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("statusFilter", statusFilter);
    }

    private void updateChipStates() {
        ((com.google.android.material.chip.Chip) requireView().findViewById(R.id.chip_all))
                .setChecked(statusFilter == null);
        ((com.google.android.material.chip.Chip) requireView().findViewById(R.id.chip_drafts))
                .setChecked("draft".equals(statusFilter));
        ((com.google.android.material.chip.Chip) requireView().findViewById(R.id.chip_active))
                .setChecked("published".equals(statusFilter));
        ((com.google.android.material.chip.Chip) requireView().findViewById(R.id.chip_paused))
                .setChecked("paused".equals(statusFilter));
        ((com.google.android.material.chip.Chip) requireView().findViewById(R.id.chip_closed))
                .setChecked("closed".equals(statusFilter));
        ((com.google.android.material.chip.Chip) requireView().findViewById(R.id.chip_archived))
                .setChecked("archived".equals(statusFilter));
    }

    private void setFilter(String status) {
        statusFilter = status;
        updateChipStates();
        loadJobs();
    }

    private void loadJobs() {
        emptyState.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);
        list.setVisibility(View.GONE);
        JobFilter filter = new JobFilter();
        filter.status = statusFilter;
        Repository repo = Repository.get(requireContext());
        // Los borradores locales van primero; el filtro de estado se aplica
        // también a ellos en el cliente.
        repo.getDrafts(drafts -> {
            if (!isAdded()) return;
            List<Job> local = new ArrayList<>();
            if (drafts.ok && drafts.data != null && (statusFilter == null || "draft".equals(statusFilter))) {
                local.addAll(drafts.data);
            }
            if (!local.isEmpty()) {
                // Borradores en el teléfono: pintan al instante, sin
                // esqueleto; la red añade las ofertas en segundo plano.
                list.setVisibility(View.VISIBLE);
                adapter.setItems(new ArrayList<>(local));
            } else {
                skeletonContainer.setVisibility(View.VISIBLE);
                AnimUtils.pulse(skeletonContainer);
            }
            loadNetworkJobs(filter, local);
        });
    }

    /** Ofertas publicadas de la red, con vigilante del esqueleto. */
    private void loadNetworkJobs(JobFilter filter, List<Job> local) {
        requestDone = false;
        if (watchdog != null) watchdogHandler.removeCallbacks(watchdog);
        watchdog = () -> {
            if (requestDone || !isAdded()) return;
            // La red no respondió a tiempo: esconder el esqueleto y ofrecer
            // reintentar (solo si no hay nada pintado). Si la respuesta
            // llega después, pinta igual.
            AnimUtils.stopPulse(skeletonContainer);
            skeletonContainer.setVisibility(View.GONE);
            if (local.isEmpty()) {
                errorBody.setText(R.string.connection_slow);
                errorState.setVisibility(View.VISIBLE);
            }
        };
        watchdogHandler.postDelayed(watchdog, 15000);
        Repository.get(requireContext()).getJobs(filter, null, result -> {
            requestDone = true;
            if (watchdog != null) watchdogHandler.removeCallbacks(watchdog);
            AnimUtils.stopPulse(skeletonContainer);
            skeletonContainer.setVisibility(View.GONE);
            if (!isAdded()) return;
            List<Job> items = new ArrayList<>(local);
            if (result.ok && result.data != null) items.addAll(result.data.items);
            if (!items.isEmpty()) {
                errorState.setVisibility(View.GONE);
                list.setVisibility(View.VISIBLE);
                adapter.setItems(items);
            } else if (!result.ok) {
                // Un fallo no es "sin ofertas": estado de error con reintento.
                errorBody.setText(result.friendlyMessage());
                errorState.setVisibility(View.VISIBLE);
            } else {
                emptyState.setVisibility(View.VISIBLE);
            }
        });
    }

    private static boolean isLocalDraft(Job job) {
        return job.jobId != null && job.jobId.startsWith("draft-");
    }

    @Override
    public void onJobClick(Job job) {
        // Un borrador local no tiene ficha pública: se abre en el editor.
        if (isLocalDraft(job)) {
            ((MainActivity) requireActivity()).openCreateJob(job.jobId);
        } else {
            ((MainActivity) requireActivity()).openJobDetail(job);
        }
    }

    @Override
    public void onEditClick(Job job) {
        ((MainActivity) requireActivity()).openCreateJob(job.jobId);
    }

    @Override
    public void onPauseClick(Job job) {
        // Solo se pausa una oferta activa y solo se reactiva una pausada.
        // Un borrador o una oferta cerrada no admiten esta acción.
        if (!"published".equals(job.status) && !"paused".equals(job.status)) {
            Snackbar.make(requireView(), "Esta oferta no se puede pausar ni reactivar.",
                    Snackbar.LENGTH_SHORT).show();
            return;
        }
        String newStatus = "paused".equals(job.status) ? "published" : "paused";
        if ("published".equals(newStatus) && isPendingVerification()) {
            Snackbar.make(requireView(), "Tu empresa está pendiente de verificación: aún no puedes publicar ofertas.",
                    Snackbar.LENGTH_LONG).show();
            return;
        }
        Repository.get(requireContext()).changeJobStatus(job.jobId, newStatus, result -> {
            if (isAdded()) {
                if (result.ok) {
                    Snackbar.make(requireView(), "paused".equals(newStatus)
                            ? getString(R.string.job_paused) : "Oferta reactivada.", Snackbar.LENGTH_SHORT).show();
                    loadJobs();
                } else {
                    Snackbar.make(requireView(), result.friendlyMessage(), Snackbar.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    public void onDuplicateClick(Job job) {
        Job copy = new Job();
        copy.title = job.title + " (copia)";
        copy.category = job.category;
        copy.description = job.description;
        copy.responsibilities = new ArrayList<>(job.responsibilities);
        copy.requirements = new ArrayList<>(job.requirements);
        copy.benefits = new ArrayList<>(job.benefits);
        copy.city = job.city;
        copy.provinceCode = job.provinceCode;
        copy.provinceName = job.provinceName;
        copy.municipalityCode = job.municipalityCode;
        copy.municipalityName = job.municipalityName;
        copy.locality = job.locality;
        copy.workMode = job.workMode;
        copy.contractType = job.contractType;
        copy.salaryMin = job.salaryMin;
        copy.salaryMax = job.salaryMax;
        copy.salaryPeriod = job.salaryPeriod;
        copy.includesFood = job.includesFood;
        copy.includesTransport = job.includesTransport;
        copy.includesAccommodation = job.includesAccommodation;
        copy.includesTips = job.includesTips;
        copy.paymentNotes = job.paymentNotes;
        copy.contactHours = job.contactHours;
        copy.startDate = job.startDate;
        copy.experienceMin = job.experienceMin;
        copy.languages = new ArrayList<>(job.languages);
        copy.keywords = new ArrayList<>(job.keywords);
        // La copia es una oferta nueva: sin fecha de publicación ni de cierre.
        copy.publishedAt = null;
        copy.expiresAt = null;
        Repository repo = Repository.get(requireContext());
        if (isLocalDraft(job)) {
            // Duplicar un borrador local crea otro borrador local.
            repo.saveDraft(copy, result -> {
                if (isAdded()) {
                    if (result.ok) {
                        Snackbar.make(requireView(), R.string.job_duplicated, Snackbar.LENGTH_SHORT).show();
                        loadJobs();
                    } else {
                        Snackbar.make(requireView(), result.friendlyMessage(), Snackbar.LENGTH_LONG).show();
                    }
                }
            });
            return;
        }
        repo.createJob(copy, result -> {
            if (isAdded()) {
                if (result.ok) {
                    Snackbar.make(requireView(), R.string.job_duplicated, Snackbar.LENGTH_SHORT).show();
                    loadJobs();
                } else {
                    Snackbar.make(requireView(), result.friendlyMessage(), Snackbar.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    public void onCandidatesClick(Job job) {
        ((MainActivity) requireActivity()).openCandidates(job.jobId);
    }

    private boolean isPendingVerification() {
        return "pending_verification".equals(SessionManager.get(requireContext()).getStatus());
    }

    @Override
    public void onMoreClick(Job job, View anchor) {
        boolean local = isLocalDraft(job);
        boolean canPublish = ("draft".equals(job.status) || "paused".equals(job.status))
                && !isPendingVerification();
        boolean canShare = !local && !"draft".equals(job.status);
        boolean canClose = !local && !"closed".equals(job.status) && !"expired".equals(job.status)
                && !"archived".equals(job.status) && !"draft".equals(job.status);
        boolean canArchive = !local && !"archived".equals(job.status) && !"draft".equals(job.status);
        boolean canReactivate = !local && "archived".equals(job.status) && !isPendingVerification();
        // Se mantiene el borrado local de borradores y se habilita también
        // para ofertas remotas publicadas, siempre con confirmación.
        boolean canDelete = true;
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (8 * getResources().getDisplayMetrics().density);
        content.setPadding(pad, 0, pad, pad);
        if (canPublish) addActionCard(content, R.drawable.ic_cp_mark, R.string.publish_job,
                () -> publish(job));
        if (canShare) {
            addActionCard(content, R.drawable.ic_share, R.string.share,
                    () -> ShareUtils.shareSystem(requireContext(), job));
            addActionCard(content, R.drawable.ic_copy, R.string.copy_link, () -> {
                ShareUtils.copyLink(requireContext(), job);
                Snackbar.make(requireView(), R.string.link_copied, Snackbar.LENGTH_SHORT).show();
            });
            addActionCard(content, R.drawable.ic_whatsapp, R.string.whatsapp,
                    () -> ShareUtils.shareWhatsApp(requireContext(), job));
            addActionCard(content, R.drawable.ic_send, R.string.share_via_telegram,
                    () -> ShareUtils.shareTelegram(requireContext(), job));
            addActionCard(content, R.drawable.ic_mail, R.string.share_via_email,
                    () -> ShareUtils.shareEmail(requireContext(), job));
        }
        if (canClose) addActionCard(content, R.drawable.ic_check_circle, R.string.close,
                () -> confirmClose(job));
        if (canArchive) addActionCard(content, R.drawable.ic_archive, R.string.archive,
                () -> confirmArchive(job));
        if (canReactivate) addActionCard(content, R.drawable.ic_play, R.string.reactivate,
                () -> reactivate(job));
        if (canDelete) addActionCard(content, R.drawable.ic_trash, R.string.delete,
                () -> confirmDelete(job));
        new AlertDialog.Builder(requireContext())
                .setTitle(job.title)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void addActionCard(LinearLayout parent, int iconRes, int textRes, Runnable action) {
        float density = getResources().getDisplayMetrics().density;
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setRadius(18 * density);
        card.setCardElevation(1 * density);
        card.setClickable(true);
        card.setFocusable(true);
        LinearLayout row = new LinearLayout(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int) (18 * density), (int) (6 * density),
                (int) (18 * density), (int) (6 * density));
        ImageView icon = new ImageView(requireContext());
        icon.setImageDrawable(ResourcesCompat.getDrawable(getResources(), iconRes, requireContext().getTheme()));
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.brand_primary)));
        row.addView(icon, new LinearLayout.LayoutParams((int) (40 * density), (int) (40 * density)));
        TextView label = new TextView(requireContext());
        label.setText(textRes);
        label.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMarginStart(12);
        row.addView(label, textLp);
        card.addView(row);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, (int) (4 * density), 0, (int) (4 * density));
        parent.addView(card, cardLp);
        card.setOnClickListener(v -> action.run());
    }

    private void publish(Job job) {
        Repository repo = Repository.get(requireContext());
        if (isLocalDraft(job)) {
            repo.publishDraft(job.jobId, result -> {
                if (isAdded()) {
                    Snackbar.make(requireView(), result.ok ? getString(R.string.job_published) : result.friendlyMessage(),
                            Snackbar.LENGTH_LONG).show();
                    loadJobs();
                }
            });
            return;
        }
        repo.publishJob(job.jobId, result -> {
            if (isAdded()) {
                Snackbar.make(requireView(), result.ok ? getString(R.string.job_published) : result.friendlyMessage(),
                        Snackbar.LENGTH_LONG).show();
                loadJobs();
            }
        });
    }

    private void confirmClose(Job job) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.close_confirm_title)
                .setMessage(R.string.close_confirm_body)
                .setPositiveButton(R.string.close, (d, w) -> {
                    String previous = job.status;
                    Repository.get(requireContext()).changeJobStatus(job.jobId, "closed", result -> {
                        if (!isAdded()) return;
                        if (result.ok) {
                            Snackbar.make(requireView(), R.string.job_closed, Snackbar.LENGTH_LONG)
                                    .setAction(R.string.undo, v ->
                                            Repository.get(requireContext()).changeJobStatus(job.jobId, previous, r -> {
                                                if (isAdded()) loadJobs();
                                            }))
                                    .show();
                            loadJobs();
                        } else {
                            Snackbar.make(requireView(), result.friendlyMessage(), Snackbar.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmArchive(Job job) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.archive_confirm_title)
                .setMessage(R.string.archive_confirm_body)
                .setPositiveButton(R.string.archive, (d, w) -> {
                    String previous = job.status;
                    Repository.get(requireContext()).archiveJob(job.jobId, null, result -> {
                        if (!isAdded()) return;
                        if (result.ok) {
                            Snackbar.make(requireView(), R.string.job_archived, Snackbar.LENGTH_LONG)
                                    .setAction(R.string.undo, v ->
                                            Repository.get(requireContext()).changeJobStatus(job.jobId, previous, r -> {
                                                if (isAdded()) loadJobs();
                                            }))
                                    .show();
                            loadJobs();
                        } else {
                            Snackbar.make(requireView(), result.friendlyMessage(), Snackbar.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void reactivate(Job job) {
        Repository.get(requireContext()).changeJobStatus(job.jobId, "published", result -> {
            if (isAdded()) {
                Snackbar.make(requireView(), result.ok ? "Oferta reactivada." : result.friendlyMessage(),
                        Snackbar.LENGTH_LONG).show();
                loadJobs();
            }
        });
    }

    private void confirmDelete(Job job) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_draft_title)
                .setMessage(R.string.delete_draft_body)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    Repository repo = Repository.get(requireContext());
                    if (isLocalDraft(job)) {
                        repo.deleteDraft(job.jobId, result -> {
                            if (!isAdded()) return;
                            if (result.ok) {
                                Snackbar.make(requireView(), R.string.job_deleted, Snackbar.LENGTH_SHORT).show();
                                loadJobs();
                            } else {
                                Snackbar.make(requireView(), result.friendlyMessage(), Snackbar.LENGTH_LONG).show();
                            }
                        });
                        return;
                    }
                    repo.deleteJob(job.jobId, result -> {
                        if (isAdded()) {
                            if (result.ok) {
                                Snackbar.make(requireView(), R.string.job_deleted, Snackbar.LENGTH_SHORT).show();
                                loadJobs();
                            } else {
                                Snackbar.make(requireView(), result.friendlyMessage(), Snackbar.LENGTH_LONG).show();
                            }
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        // Detener el pulso infinito del skeleton al destruir la vista.
        if (watchdog != null) watchdogHandler.removeCallbacks(watchdog);
        AnimUtils.stopPulse(skeletonContainer);
        super.onDestroyView();
    }
}
