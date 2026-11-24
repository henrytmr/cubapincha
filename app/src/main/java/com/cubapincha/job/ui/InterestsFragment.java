package com.cubapincha.job.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;
import com.google.android.material.snackbar.Snackbar;

import com.google.android.material.tabs.TabLayout;
import com.cubapincha.job.R;
import com.cubapincha.job.adapter.ApplicationAdapter;
import com.cubapincha.job.data.Repository;
import com.cubapincha.job.model.Application;
import com.cubapincha.job.util.AnimUtils;

import java.util.ArrayList;
import java.util.List;

/** Intereses y seguimiento del trabajador, por pestañas de estado. */
public class InterestsFragment extends Fragment implements ApplicationAdapter.Listener {
    private ApplicationAdapter adapter;
    private LinearLayout skeletonContainer, emptyState, errorState;
    private RecyclerView list;
    private String currentStatus = null;
    private List<Application> shown;
    // Vigilante del esqueleto: si la red no responde en 15 s, el esqueleto
    // se esconde y aparece el estado de reintento (nunca se queda colgado).
    private final Handler watchdogHandler = new Handler(Looper.getMainLooper());
    private Runnable watchdog;
    private boolean requestDone;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_interests, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainActivity activity = (MainActivity) requireActivity();
        list = view.findViewById(R.id.list_applications);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ApplicationAdapter(this);
        list.setAdapter(adapter);
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            @Override public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder a, @NonNull RecyclerView.ViewHolder b) { return false; }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
                int pos = holder.getBindingAdapterPosition(); if (pos == RecyclerView.NO_POSITION || shown == null || pos >= shown.size()) return;
                Application app = shown.remove(pos); adapter.remove(app);
                Repository.get(requireContext()).changeApplicationStatus(app.applicationId, "withdrawn", null, result -> { if (!result.ok && isAdded()) { shown.add(pos, app); adapter.setItems(shown); Snackbar.make(requireView(), result.friendlyMessage(), Snackbar.LENGTH_LONG).show(); } });
                Snackbar.make(requireView(), "Interés retirado", Snackbar.LENGTH_LONG).setAction("Deshacer", v -> { shown.add(pos, app); adapter.setItems(shown); Repository.get(requireContext()).changeApplicationStatus(app.applicationId, "sent", null, ignored -> { }); }).show();
            }
        }).attachToRecyclerView(list);
        skeletonContainer = view.findViewById(R.id.skeleton_container);
        emptyState = view.findViewById(R.id.empty_state);
        errorState = view.findViewById(R.id.error_state);

        TabLayout tabs = view.findViewById(R.id.tabs);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 1:
                        currentStatus = "reviewing";
                        break;
                    case 2:
                        currentStatus = "interview";
                        break;
                    case 3:
                        currentStatus = "hired";
                        break;
                    case 4:
                        currentStatus = "closed";
                        break;
                    default:
                        currentStatus = null;
                }
                loadFromCacheOrNetwork();
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });

        view.findViewById(R.id.btn_explore).setOnClickListener(v ->
                activity.selectTab(R.id.nav_search));
        view.findViewById(R.id.btn_retry).setOnClickListener(v -> loadApplications());

        // Al retirar un interés desde la hoja de seguimiento, la lista se recarga.
        getParentFragmentManager().setFragmentResultListener(
                TrackingSheetFragment.REQUEST_KEY, this, (key, result) -> loadApplications());

        if (savedInstanceState != null) {
            // Al rotar, la pestaña se restaura y su selección dispara la carga.
            currentStatus = savedInstanceState.getString("currentStatus");
            int pos = 0;
            if ("reviewing".equals(currentStatus)) pos = 1;
            else if ("interview".equals(currentStatus)) pos = 2;
            else if ("hired".equals(currentStatus)) pos = 3;
            else if ("closed".equals(currentStatus)) pos = 4;
            if (pos == 0) {
                // La primera pestaña ya está seleccionada: seleccionar de nuevo
                // no dispara el listener, así que se carga directamente.
                loadFromCacheOrNetwork();
            } else {
                tabs.selectTab(tabs.getTabAt(pos));
            }
        } else {
            loadFromCacheOrNetwork();
        }
    }

    /** Pinta primero lo guardado en el teléfono (al instante, sin
     *  esqueleto) y refresca en segundo plano; solo si no hay nada
     *  guardado se espera a la red con el esqueleto. */
    private void loadFromCacheOrNetwork() {
        List<Application> cached = filterByStatus(
                Repository.get(requireContext()).getCachedApplications(), currentStatus);
        if (!cached.isEmpty()) {
            shown = cached;
            list.setVisibility(View.VISIBLE);
            adapter.setItems(cached);
            loadApplicationsQuiet();
        } else {
            loadApplications();
        }
    }

    /** Mismo filtro de estado que la red (ver Repository.getApplications). */
    private static List<Application> filterByStatus(List<Application> apps, String status) {
        List<Application> out = new ArrayList<>();
        for (Application a : apps) {
            if ("closed".equals(status)) {
                if ("rejected".equals(a.status) || "withdrawn".equals(a.status)) out.add(a);
            } else if (status == null || status.isEmpty() || status.equals(a.status)) {
                out.add(a);
            }
        }
        return out;
    }

    /** Refresco silencioso: solo toca la lista si algo cambió. */
    private void loadApplicationsQuiet() {
        Repository.get(requireContext()).getApplications(currentStatus, result -> {
            if (!isAdded() || !result.ok || result.data == null) return;
            List<Application> items = result.data;
            if (items.isEmpty()) {
                shown = items;
                list.setVisibility(View.GONE);
                emptyState.setVisibility(View.VISIBLE);
                return;
            }
            if (!sameSignature(items, shown)) {
                shown = items;
                list.setVisibility(View.VISIBLE);
                emptyState.setVisibility(View.GONE);
                adapter.setItems(items);
            }
        });
    }

    private static boolean sameSignature(List<Application> a, List<Application> b) {
        if (a == null || b == null) return a == b;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            Application x = a.get(i), y = b.get(i);
            if (!String.valueOf(x.applicationId).equals(String.valueOf(y.applicationId))
                    || !String.valueOf(x.status).equals(String.valueOf(y.status))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("currentStatus", currentStatus);
    }

    private void loadApplications() {
        skeletonContainer.setVisibility(View.VISIBLE);
        AnimUtils.pulse(skeletonContainer);
        emptyState.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);
        list.setVisibility(View.GONE);
        requestDone = false;
        if (watchdog != null) watchdogHandler.removeCallbacks(watchdog);
        watchdog = () -> {
            if (requestDone || !isAdded()) return;
            // La red no respondió a tiempo: esconder el esqueleto y ofrecer
            // reintentar. Si la respuesta llega después, pinta igual.
            AnimUtils.stopPulse(skeletonContainer);
            skeletonContainer.setVisibility(View.GONE);
            android.widget.TextView body = requireView().findViewById(R.id.error_body);
            body.setText(R.string.connection_slow);
            errorState.setVisibility(View.VISIBLE);
        };
        watchdogHandler.postDelayed(watchdog, 15000);
        Repository.get(requireContext()).getApplications(currentStatus, result -> {
            requestDone = true;
            if (watchdog != null) watchdogHandler.removeCallbacks(watchdog);
            AnimUtils.stopPulse(skeletonContainer);
            skeletonContainer.setVisibility(View.GONE);
            if (!isAdded()) return;
            if (result.ok && result.data != null) {
                errorState.setVisibility(View.GONE);
                shown = result.data;
                if (result.data.isEmpty()) {
                    emptyState.setVisibility(View.VISIBLE);
                } else {
                    list.setVisibility(View.VISIBLE);
                    adapter.setItems(result.data);
                }
            } else {
                // Un fallo no es "sin intereses": estado de error con reintento.
                errorState.setVisibility(View.VISIBLE);
                android.widget.TextView body = requireView().findViewById(R.id.error_body);
                body.setText(result.friendlyMessage());
            }
        });
    }

    @Override
    public void onApplicationClick(Application app) {
        ((MainActivity) requireActivity()).openJobDetail(app.jobId);
    }

    @Override
    public void onTrackingClick(Application app) {
        if (getParentFragmentManager().findFragmentByTag("tracking") == null) {
            TrackingSheetFragment.newInstance(app).show(getParentFragmentManager(), "tracking");
        }
    }

    @Override
    public void onRateClick(Application app) {
        RatingUi.promptForApplication(this, app);
    }

    @Override
    public void onDestroyView() {
        // Detener el pulso infinito del skeleton al destruir la vista.
        if (watchdog != null) watchdogHandler.removeCallbacks(watchdog);
        AnimUtils.stopPulse(skeletonContainer);
        super.onDestroyView();
    }
}
