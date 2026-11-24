package com.cubapincha.job.ui;

import androidx.appcompat.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cubapincha.job.R;
import com.cubapincha.job.adapter.RecommendedWorkerAdapter;
import com.cubapincha.job.data.Repository;
import com.cubapincha.job.model.RecommendedWorker;
import com.cubapincha.job.model.SavedSearch;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;

/** Buscador empresarial espejo del buscador del trabajador, adaptado a trabajadores. */
public class WorkerSearchFragment extends Fragment implements RecommendedWorkerAdapter.Listener {
    private RecommendedWorkerAdapter adapter;
    private EditText query;
    private TextView resultsCount;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private com.cubapincha.job.data.JobFilter workerFilter = new com.cubapincha.job.data.JobFilter();
    private boolean photosVisible;
    private boolean sortByMatch;
    private View root;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_worker_search, parent, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        root = view;
        query = view.findViewById(R.id.worker_query);
        resultsCount = view.findViewById(R.id.worker_results_count);
        MaterialButton filters = view.findViewById(R.id.worker_filters);
        MaterialButton sort = view.findViewById(R.id.worker_sort);
        MaterialButton save = view.findViewById(R.id.worker_save_search);
        RecyclerView list = view.findViewById(R.id.worker_list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RecommendedWorkerAdapter(this);
        list.setAdapter(adapter);
        filters.setOnClickListener(v -> {
            if (getParentFragmentManager().findFragmentByTag("worker_filters") == null)
                FilterSheetFragment.newInstance(workerFilter).show(getParentFragmentManager(), "worker_filters");
        });
        getParentFragmentManager().setFragmentResultListener(FilterSheetFragment.REQUEST_KEY, this, (key, result) -> {
            workerFilter = com.cubapincha.job.data.JobFilter.fromBundle(result.getBundle(FilterSheetFragment.KEY_FILTER));
            searchWorkers();
        });
        sort.setOnClickListener(v -> {
            sortByMatch = !sortByMatch;
            sort.setText(sortByMatch ? "Mayor coincidencia" : "Ordenar por");
            searchWorkers();
        });
        save.setOnClickListener(v -> saveSearch());
        android.widget.ImageButton eye = view.findViewById(R.id.worker_photo_toggle);
        eye.setOnClickListener(v -> { photosVisible = !photosVisible; adapter.setPhotosVisible(photosVisible); eye.setImageResource(photosVisible ? R.drawable.ic_visibility : R.drawable.ic_visibility_off); eye.setContentDescription(photosVisible ? "Ocultar fotos" : "Mostrar fotos"); });
        loadSavedSearchChips();
        query.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { scheduleSearch(); }
            public void afterTextChanged(android.text.Editable e) { }
        });
        searchWorkers();
    }

    private void saveSearch() {
        SavedSearch saved = new SavedSearch();
        saved.name = value(query).isEmpty() ? "Trabajadores" : value(query);
        saved.query = value(query); saved.category = workerFilter.category;
        saved.provinceCode = workerFilter.provinceCode; saved.municipalityCode = workerFilter.municipalityCode;
        saved.sort = sortByMatch ? "match" : "recent"; saved.frequency = "off";
        Repository.get(requireContext()).saveFilter(saved, result -> {
            if (isAdded()) Snackbar.make(root, result.ok ? "Búsqueda guardada." : result.friendlyMessage(), Snackbar.LENGTH_SHORT).show();
        });
    }

    private void loadSavedSearchChips() {
        Repository.get(requireContext()).getSavedSearches(result -> {
            if (!isAdded() || root == null) return;
            View scroll = root.findViewById(R.id.worker_saved_searches_scroll);
            ChipGroup row = root.findViewById(R.id.worker_saved_searches_row);
            row.removeAllViews();
            if (!result.ok || result.data == null || result.data.isEmpty()) {
                scroll.setVisibility(View.GONE);
                return;
            }
            scroll.setVisibility(View.VISIBLE);
            for (SavedSearch search : result.data) {
                Chip chip = new Chip(requireContext());
                chip.setText(search.name);
                chip.setChipIconResource(R.drawable.ic_search);
                chip.setCheckable(false);
                chip.setOnClickListener(v -> {
                    workerFilter.category = search.category == null ? "" : search.category;
                    workerFilter.provinceCode = search.provinceCode == null ? "" : search.provinceCode;
                    workerFilter.municipalityCode = search.municipalityCode == null ? "" : search.municipalityCode;
                    query.setText(search.query == null ? "" : search.query);
                    searchWorkers();
                });
                chip.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(requireContext()).setTitle(search.name)
                            .setMessage("¿Eliminar esta búsqueda guardada?")
                            .setPositiveButton(R.string.delete, (d, w) ->
                                    Repository.get(requireContext()).deleteSavedSearch(search.filterId, ignored -> loadSavedSearchChips()))
                            .setNegativeButton(R.string.cancel, null).show();
                    return true;
                });
                row.addView(chip);
            }
        });
    }

    private void scheduleSearch() {
        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
        pendingSearch = this::searchWorkers;
        searchHandler.postDelayed(pendingSearch, 350);
    }

    private void searchWorkers() {
        if (!isAdded() || query == null) return;
        Repository.get(requireContext()).getRecommendedWorkers(value(query).isEmpty() ? null : value(query),
                workerFilter.provinceCode == null || workerFilter.provinceCode.isEmpty() ? null : workerFilter.provinceCode,
                workerFilter.municipalityCode == null || workerFilter.municipalityCode.isEmpty() ? null : workerFilter.municipalityCode,
                workerFilter.category == null || workerFilter.category.isEmpty() ? null : workerFilter.category, workerFilter.minMatch, 100, result -> {
                    if (!isAdded()) return;
                    View empty = root.findViewById(R.id.worker_empty);
                    if (!result.ok || result.data == null) {
                        adapter.setItems(new java.util.ArrayList<>());
                        resultsCount.setText("No se pudieron cargar los trabajadores");
                        empty.setVisibility(View.VISIBLE); return;
                    }
                    if (sortByMatch) result.data.sort((a, b) -> Integer.compare(b.matchPercent, a.matchPercent));
                    adapter.setItems(result.data);
                    resultsCount.setText(result.data.size() + " trabajadores");
                    empty.setVisibility(result.data.isEmpty() ? View.VISIBLE : View.GONE);
                    root.findViewById(R.id.worker_list).setVisibility(result.data.isEmpty() ? View.GONE : View.VISIBLE);
                });
    }

    private String value(EditText field) {
        return field == null || field.getText() == null ? "" : field.getText().toString().trim();
    }

    @Override public void onWorkerClick(RecommendedWorker w) { ((MainActivity) requireActivity()).openWorkerDetail(w); }
    @Override public void onProfileClick(RecommendedWorker w) { onWorkerClick(w); }
    @Override public void onInterestClick(RecommendedWorker w) {
        if (w.invited) {
            Repository.get(requireContext()).uninviteWorker(w.workerId, w.invitedOfferId == null || w.invitedOfferId.isEmpty() ? w.matchedOfferId : w.invitedOfferId, r -> { if (r.ok) { w.invited = false; w.invitedOfferId = ""; adapter.setItems(new java.util.ArrayList<>(java.util.Arrays.asList(w))); } });
        } else {
            Repository.get(requireContext()).inviteWorker(w.workerId, w.matchedOfferId, "", r -> { if (r.ok) { w.invited = true; w.invitedOfferId = w.matchedOfferId == null ? "" : w.matchedOfferId; adapter.setItems(new java.util.ArrayList<>(java.util.Arrays.asList(w))); } });
        }
    }
    @Override public void onSaveClick(RecommendedWorker w) { Repository.get(requireContext()).saveCandidate(w.workerId, null, w.saved, r -> { }); }
    @Override public void onWhatsAppClick(RecommendedWorker w) {
        if (w.phone != null && !w.phone.isEmpty()) startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://wa.me/" + w.phone.replaceAll("[^0-9]", ""))));
    }
    @Override public void onDestroyView() {
        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
        root = null; super.onDestroyView();
    }
}
