package com.cubapincha.job.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.cubapincha.job.R;
import com.cubapincha.job.adapter.JobAdapter;
import com.cubapincha.job.data.JobFilter;
import com.cubapincha.job.data.Page;
import com.cubapincha.job.data.Repository;
import com.cubapincha.job.data.SeedData;
import com.cubapincha.job.data.SessionManager;
import com.cubapincha.job.model.Job;
import com.cubapincha.job.util.AnimUtils;
import com.cubapincha.job.util.TextUtils;

import java.util.ArrayList;
import java.util.List;

/** Inicio del trabajador: saludo, perfil, búsqueda rápida y ofertas. */
public class HomeFragment extends Fragment implements JobAdapter.Listener {
    private JobAdapter recommendedAdapter;
    private JobAdapter newAdapter;
    private LinearLayout skeletonContainer, emptyState, errorState;
    private RecyclerView listRecommended, listNew;
    private SwipeRefreshLayout swipeRefresh;
    private int viewMode = JobAdapter.VIEW_CARD;
    // Última lista cargada: sobrevive a la destrucción de la vista (al abrir
    // un detalle y volver, el fragmento se conserva en la pila), así la lista
    // se restaura intacta al instante, sin skeleton ni parpadeos.
    private List<Job> cachedRecommended;
    private List<Job> cachedRecent;
    private boolean jobsLoaded = false;
    // Vigilante del esqueleto: si la red no responde en 15 s, el esqueleto
    // se esconde y aparece el estado de reintento (nunca se queda colgado).
    private final Handler watchdogHandler = new Handler(Looper.getMainLooper());
    private Runnable watchdog;
    private boolean jobsRequestDone;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainActivity activity = (MainActivity) requireActivity();
        Repository repo = Repository.get(requireContext());
        SessionManager session = SessionManager.get(requireContext());

        TextView greeting = view.findViewById(R.id.greeting);
        TextView avatar = view.findViewById(R.id.avatar);
        String name = session.getDisplayName();
        String firstName = name.contains(" ") ? name.substring(0, name.indexOf(' ')) : name;
        greeting.setText(getString(R.string.hello_name, firstName.isEmpty() ? "bienvenido" : firstName));
        avatar.setText(TextUtils.initials(name));

        View demoBadge = view.findViewById(R.id.demo_badge);
        demoBadge.setVisibility(repo.isDemoMode() ? View.VISIBLE : View.GONE);

        // Tarjeta "Completa tu perfil"
        View cardProfile = view.findViewById(R.id.card_complete_profile);
        int percent = session.getProfilePercent();
        if (percent < 80) {
            cardProfile.setVisibility(View.VISIBLE);
            TextView percentText = view.findViewById(R.id.profile_percent_text);
            percentText.setText(getString(R.string.profile_percent, percent));
            LinearProgressIndicator progress = view.findViewById(R.id.profile_progress);
            progress.setProgress(percent);
            cardProfile.setOnClickListener(v -> activity.openEditProfile());
        }

        view.findViewById(R.id.card_quick_search).setOnClickListener(v ->
                activity.selectTab(R.id.nav_search));
        view.findViewById(R.id.btn_notifications).setOnClickListener(v -> {
            ImageButton btn = (ImageButton) v;
            btn.setImageResource(R.drawable.avd_bell);
            AnimUtils.playAnimated(btn);
            activity.selectTab(R.id.nav_notifications);
        });

        // Chips de categorías
        ChipGroup chips = view.findViewById(R.id.chip_group);
        for (String category : SeedData.CATEGORIES) {
            Chip chip = new Chip(requireContext());
            chip.setText(category);
            chip.setCheckable(false);
            chip.setOnClickListener(v -> {
                // Buscar se abre por la pestaña: la barra inferior queda sincronizada.
                JobFilter f = new JobFilter();
                f.category = category;
                activity.openSearchWithFilter(f);
            });
            chips.addView(chip);
        }

        if (savedInstanceState != null) {
            viewMode = savedInstanceState.getInt("viewMode", JobAdapter.VIEW_CARD);
        }

        listRecommended = view.findViewById(R.id.list_recommended);
        listNew = view.findViewById(R.id.list_new);
        listRecommended.setLayoutManager(new LinearLayoutManager(requireContext()));
        listNew.setLayoutManager(new LinearLayoutManager(requireContext()));
        recommendedAdapter = new JobAdapter(this);
        newAdapter = new JobAdapter(this);
        recommendedAdapter.setViewMode(viewMode);
        newAdapter.setViewMode(viewMode);
        listRecommended.setAdapter(recommendedAdapter);
        listNew.setAdapter(newAdapter);

        view.findViewById(R.id.btn_view_toggle).setOnClickListener(v -> {
            viewMode = viewMode == JobAdapter.VIEW_CARD ? JobAdapter.VIEW_DETAIL : JobAdapter.VIEW_CARD;
            recommendedAdapter.setViewMode(viewMode);
            newAdapter.setViewMode(viewMode);
            Snackbar.make(view, viewMode == JobAdapter.VIEW_CARD
                    ? R.string.view_card : R.string.view_detail, Snackbar.LENGTH_SHORT).show();
        });

        skeletonContainer = view.findViewById(R.id.skeleton_container);
        emptyState = view.findViewById(R.id.empty_state);
        errorState = view.findViewById(R.id.error_state);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(this::loadJobs);
        view.findViewById(R.id.btn_retry).setOnClickListener(v -> loadJobs());

        if (jobsLoaded && cachedRecommended != null) {
            // Volvemos de un detalle: la lista reaparece tal cual estaba.
            recommendedAdapter.setJobs(cachedRecommended);
            newAdapter.setJobs(cachedRecent);
            updateSectionVisibility();
            loadJobsQuiet();
        } else if (paintFromCache()) {
            // Contenido guardado en el teléfono: pinta al instante, sin
            // esqueleto; la red refresca en segundo plano.
            loadJobsQuiet();
        } else {
            loadJobs();
        }
    }

    /** Pinta las ofertas guardadas en el teléfono (sin red ni esqueleto).
     *  Devuelve true si había algo que mostrar. */
    private boolean paintFromCache() {
        JobFilter filter = new JobFilter();
        filter.limit = 20;
        filter.sort = "recent";
        Page<Job> page = Repository.get(requireContext()).getCachedJobsPage(filter);
        List<Job> all = page.items;
        if (all.isEmpty()) return false;
        List<Job> recommended = new ArrayList<>();
        List<Job> recent = new ArrayList<>();
        for (Job j : all) {
            if (j.matchScore >= 75 && recommended.size() < 5) recommended.add(j);
            else recent.add(j);
        }
        if (recommended.isEmpty()) {
            recommended.addAll(all.subList(0, Math.min(5, all.size())));
            recent = all.subList(Math.min(5, all.size()), all.size());
        }
        cachedRecommended = recommended;
        cachedRecent = recent;
        jobsLoaded = true;
        recommendedAdapter.setJobs(recommended);
        newAdapter.setJobs(recent);
        updateSectionVisibility();
        return true;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("viewMode", viewMode);
    }

    /** Refresco silencioso: actualiza la lista solo si los datos cambiaron. */
    private void loadJobsQuiet() {
        JobFilter filter = new JobFilter();
        filter.limit = 20;
        filter.sort = "recent";
        Repository.get(requireContext()).getJobs(filter, null, result -> {
            if (!isAdded() || !result.ok || result.data == null) return;
            List<Job> all = result.data.items;
            if (all.isEmpty()) {
                cachedRecommended = new ArrayList<>();
                cachedRecent = new ArrayList<>();
                jobsLoaded = true;
                recommendedAdapter.setJobs(cachedRecommended);
                newAdapter.setJobs(cachedRecent);
                updateSectionVisibility();
                emptyState.setVisibility(View.VISIBLE);
                errorState.setVisibility(View.GONE);
                return;
            }
            List<Job> recommended = new ArrayList<>();
            List<Job> recent = new ArrayList<>();
            for (Job j : all) {
                if (j.matchScore >= 75 && recommended.size() < 5) recommended.add(j);
                else recent.add(j);
            }
            if (recommended.isEmpty()) {
                recommended.addAll(all.subList(0, Math.min(5, all.size())));
                recent = all.subList(Math.min(5, all.size()), all.size());
            }
            if (!sameSignature(recommended, cachedRecommended) || !sameSignature(recent, cachedRecent)) {
                cachedRecommended = recommended;
                cachedRecent = recent;
                recommendedAdapter.setJobs(recommended);
                newAdapter.setJobs(recent);
            }
            updateSectionVisibility();
        });
    }

    private void updateSectionVisibility() {
        View root = getView();
        if (root == null) return;
        boolean hasRecommended = cachedRecommended != null && !cachedRecommended.isEmpty();
        boolean hasRecent = cachedRecent != null && !cachedRecent.isEmpty();
        root.findViewById(R.id.section_recommended_header)
                .setVisibility(hasRecommended ? View.VISIBLE : View.GONE);
        listRecommended.setVisibility(hasRecommended ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.section_new).setVisibility(hasRecent ? View.VISIBLE : View.GONE);
        listNew.setVisibility(hasRecent ? View.VISIBLE : View.GONE);
    }

    private static boolean sameSignature(List<Job> a, List<Job> b) {
        if (a == null || b == null) return a == b;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            Job x = a.get(i), y = b.get(i);
            if (!String.valueOf(x.jobId).equals(String.valueOf(y.jobId))
                    || x.saved != y.saved
                    || !String.valueOf(x.applicationStatus).equals(String.valueOf(y.applicationStatus))
                    || !String.valueOf(x.status).equals(String.valueOf(y.status))) {
                return false;
            }
        }
        return true;
    }

    private void loadJobs() {
        boolean hasCachedContent = jobsLoaded && ((cachedRecommended != null && !cachedRecommended.isEmpty())
                || (cachedRecent != null && !cachedRecent.isEmpty()));
        skeletonContainer.setVisibility(hasCachedContent ? View.GONE : View.VISIBLE);
        if (!hasCachedContent) AnimUtils.pulse(skeletonContainer);
        emptyState.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);
        JobFilter filter = new JobFilter();
        filter.limit = 20;
        filter.sort = "recent";
        jobsRequestDone = false;
        if (watchdog != null) watchdogHandler.removeCallbacks(watchdog);
        watchdog = () -> {
            if (jobsRequestDone || !isAdded()) return;
            // La red no respondió a tiempo: esconder el esqueleto y ofrecer
            // reintentar. Si la respuesta llega después, pinta igual.
            skeletonContainer.clearAnimation();
            AnimUtils.stopPulse(skeletonContainer);
            skeletonContainer.setVisibility(View.GONE);
            swipeRefresh.setRefreshing(false);
            updateSectionVisibility();
            errorState.setVisibility(View.VISIBLE);
            TextView body = requireView().findViewById(R.id.error_body);
            body.setText(R.string.connection_slow);
        };
        watchdogHandler.postDelayed(watchdog, 15000);
        Repository.get(requireContext()).getJobs(filter, null, result -> {
            jobsRequestDone = true;
            if (watchdog != null) watchdogHandler.removeCallbacks(watchdog);
            swipeRefresh.setRefreshing(false);
            skeletonContainer.clearAnimation();
            AnimUtils.stopPulse(skeletonContainer);
            skeletonContainer.setVisibility(View.GONE);
            if (!isAdded()) return;
            if (result.ok && result.data != null) {
                errorState.setVisibility(View.GONE);
                List<Job> all = result.data.items;
                if (all.isEmpty()) {
                    cachedRecommended = new ArrayList<>();
                    cachedRecent = new ArrayList<>();
                    jobsLoaded = true;
                    recommendedAdapter.setJobs(cachedRecommended);
                    newAdapter.setJobs(cachedRecent);
                    updateSectionVisibility();
                    emptyState.setVisibility(View.VISIBLE);
                    return;
                }
                List<Job> recommended = new ArrayList<>();
                List<Job> recent = new ArrayList<>();
                for (Job j : all) {
                    if (j.matchScore >= 75 && recommended.size() < 5) recommended.add(j);
                    else recent.add(j);
                }
                if (recommended.isEmpty()) {
                    recommended.addAll(all.subList(0, Math.min(5, all.size())));
                    recent = all.subList(Math.min(5, all.size()), all.size());
                }
                cachedRecommended = recommended;
                cachedRecent = recent;
                jobsLoaded = true;
                recommendedAdapter.setJobs(recommended);
                newAdapter.setJobs(recent);
                updateSectionVisibility();
            } else {
                // Conserva las últimas ofertas válidas: un fallo transitorio
                // no debe vaciar la pantalla ni parecer un catálogo vacío.
                updateSectionVisibility();
                errorState.setVisibility(View.VISIBLE);
                TextView body = requireView().findViewById(R.id.error_body);
                body.setText(result.friendlyMessage());
            }
        });
    }

    @Override
    public void onJobClick(Job job) {
        ((MainActivity) requireActivity()).openJobDetail(job);
    }

    @Override
    public void onSaveClick(Job job, ImageButton saveButton) {
        boolean newState = !job.saved;
        job.saved = newState;
        Repository.get(requireContext()).saveJob(job.jobId, newState, result -> {
            if (!isAdded()) return;
            if (newState) {
                Snackbar.make(requireView(), R.string.offer_saved, Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo, v -> {
                            job.saved = false;
                            Repository.get(requireContext()).saveJob(job.jobId, false, r -> {
                            });
                            recommendedAdapter.updateJob(job);
                            newAdapter.updateJob(job);
                        })
                        .show();
            }
            recommendedAdapter.updateJob(job);
            newAdapter.updateJob(job);
        });
    }

    @Override
    public void onDestroyView() {
        // Detener el pulso infinito del skeleton al destruir la vista.
        if (watchdog != null) watchdogHandler.removeCallbacks(watchdog);
        AnimUtils.stopPulse(skeletonContainer);
        super.onDestroyView();
    }
}
