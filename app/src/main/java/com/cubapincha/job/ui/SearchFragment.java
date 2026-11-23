package com.cubapincha.job.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
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

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.cubapincha.job.R;
import com.cubapincha.job.adapter.JobAdapter;
import com.cubapincha.job.data.JobFilter;
import com.cubapincha.job.data.Repository;
import com.cubapincha.job.model.Job;
import com.cubapincha.job.model.SavedSearch;
import com.cubapincha.job.util.AnimUtils;

/** Buscador con filtros, orden, guardado de búsqueda y cambio de vista. */
public class SearchFragment extends Fragment implements JobAdapter.Listener {

    private JobAdapter adapter;
    private JobFilter filter = new JobFilter();
    private LinearLayout skeletonContainer, emptyState, errorState;
    private RecyclerView listJobs;
    private TextView resultsCount;
    private ChipGroup activeChips;
    private TextInputEditText queryInput;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    // Vigilante del esqueleto: si la red no responde en 15 s, el esqueleto
    // se esconde y aparece el estado de reintento (nunca se queda colgado).
    private Runnable watchdog;
    private boolean jobsRequestDone;
    private int viewMode = JobAdapter.VIEW_CARD;
    // Últimos resultados: al volver de un detalle la lista reaparece intacta.
    private java.util.List<Job> cachedItems;
    private int cachedTotal = -1;
    // Paginación: cursor de la siguiente página y guarda de carga en curso.
    private String nextCursor;
    private boolean isLoadingMore = false;
    // Evita que un setText programático dispare la búsqueda con debounce
    // además de la carga explícita (doble carga).
    private boolean suppressWatcher = false;
    private boolean sortDialogShowing = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainActivity activity = (MainActivity) requireActivity();
        // Orden: estado guardado al rotar > filtro pendiente (chip de categoría
        // o búsqueda guardada) > filtro vacío.
        if (savedInstanceState != null) {
            JobFilter restored = JobFilter.fromBundle(savedInstanceState.getBundle("filter"));
            if (restored != null) filter = restored;
            viewMode = savedInstanceState.getInt("viewMode", JobAdapter.VIEW_CARD);
        } else {
            JobFilter pending = activity.takePendingSearchFilter();
            if (pending != null) filter = pending;
        }
        queryInput = view.findViewById(R.id.input_query);
        if (filter.q != null) {
            suppressWatcher = true;
            queryInput.setText(filter.q);
            suppressWatcher = false;
        }
        resultsCount = view.findViewById(R.id.results_count);
        activeChips = view.findViewById(R.id.active_chips);
        skeletonContainer = view.findViewById(R.id.skeleton_container);
        emptyState = view.findViewById(R.id.empty_state);
        errorState = view.findViewById(R.id.error_state);
        listJobs = view.findViewById(R.id.list_jobs);
        listJobs.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new JobAdapter(this);
        adapter.setViewMode(viewMode);
        listJobs.setAdapter(adapter);
        listJobs.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || nextCursor == null || isLoadingMore) return;
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm != null && lm.findLastVisibleItemPosition() >= adapter.getItemCount() - 3) {
                    loadMoreJobs();
                }
            }
        });

        queryInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (suppressWatcher) return;
                filter.q = s.toString().trim();
                if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
                pendingSearch = () -> loadJobs();
                handler.postDelayed(pendingSearch, 400);
            }
        });

        view.findViewById(R.id.btn_filters).setOnClickListener(v -> {
            if (getParentFragmentManager().findFragmentByTag("filters") == null) {
                FilterSheetFragment sheet = FilterSheetFragment.newInstance(filter);
                sheet.show(getParentFragmentManager(), "filters");
            }
        });

        // Resultado de la hoja de filtros: sobrevive a rotar (antes el listener
        // se perdía y "Aplicar" dejaba de responder).
        getParentFragmentManager().setFragmentResultListener(
                FilterSheetFragment.REQUEST_KEY, this, (key, result) -> {
                    JobFilter newFilter = JobFilter.fromBundle(
                            result.getBundle(FilterSheetFragment.KEY_FILTER));
                    filter = newFilter;
                    updateActiveChips();
                    loadJobs();
                });

        view.findViewById(R.id.btn_sort).setOnClickListener(v -> showSortDialog());
        updateViewToggleIcon(view);
        view.findViewById(R.id.btn_view_toggle).setOnClickListener(v -> {
            viewMode = viewMode == JobAdapter.VIEW_CARD ? JobAdapter.VIEW_DETAIL : JobAdapter.VIEW_CARD;
            adapter.setViewMode(viewMode);
            updateViewToggleIcon(view);
        });
        view.findViewById(R.id.btn_save_search).setOnClickListener(v -> showSaveSearchDialog());
        view.findViewById(R.id.btn_clear_filters).setOnClickListener(v -> {
            filter = new JobFilter();
            suppressWatcher = true;
            queryInput.setText("");
            suppressWatcher = false;
            updateActiveChips();
            loadJobs();
        });
        view.findViewById(R.id.btn_retry).setOnClickListener(v -> loadJobs());

        updateActiveChips();
        loadSavedSearchChips();
        if (cachedItems != null) {
            // Volvemos de un detalle: resultados intactos al instante.
            resultsCount.setText(getString(R.string.results_count, cachedTotal));
            listJobs.setVisibility(View.VISIBLE);
            adapter.setJobs(cachedItems);
            loadJobsQuiet();
        } else if (paintFromCache()) {
            // Contenido guardado en el teléfono: pinta al instante, sin
            // esqueleto; la red refresca en segundo plano.
            loadJobsQuiet();
        } else {
            loadJobs();
        }
    }

    /** Pinta los resultados guardados en el teléfono (sin red ni
     *  esqueleto). Devuelve true si había algo que mostrar. */
    private boolean paintFromCache() {
        com.cubapincha.job.data.Page<Job> page =
                Repository.get(requireContext()).getCachedJobsPage(filter.copy());
        if (page.items.isEmpty()) return false;
        cachedItems = page.items;
        cachedTotal = page.total;
        nextCursor = null;
        resultsCount.setText(getString(R.string.results_count, cachedTotal));
        listJobs.setVisibility(View.VISIBLE);
        adapter.setJobs(cachedItems);
        return true;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBundle("filter", filter.toBundle());
        outState.putInt("viewMode", viewMode);
    }

    /** Aplica un filtro completo desde fuera (chip de categoría, búsqueda
     *  guardada): sincroniza el campo de texto, los chips y recarga. */
    public void applyFilter(JobFilter newFilter) {
        filter = newFilter;
        if (queryInput != null) {
            suppressWatcher = true;
            queryInput.setText(filter.q != null ? filter.q : "");
            suppressWatcher = false;
        }
        if (activeChips != null) updateActiveChips();
        if (isAdded()) loadJobs();
    }

    /** Refresco silencioso: solo toca la lista si los datos cambiaron. */
    private void loadJobsQuiet() {
        Repository.get(requireContext()).getJobs(filter.copy(), null, result -> {
            if (!isAdded() || !result.ok || result.data == null) return;
            java.util.List<Job> items = result.data.items;
            boolean same = cachedItems != null && items.size() == cachedItems.size()
                    && result.data.total == cachedTotal;
            if (same) {
                for (int i = 0; i < items.size(); i++) {
                    Job x = items.get(i), y = cachedItems.get(i);
                    if (!String.valueOf(x.jobId).equals(String.valueOf(y.jobId))
                            || x.saved != y.saved
                            || !String.valueOf(x.applicationStatus).equals(String.valueOf(y.applicationStatus))) {
                        same = false;
                        break;
                    }
                }
            }
            if (!same) {
                cachedItems = items;
                cachedTotal = result.data.total;
                nextCursor = result.data.nextCursor;
                resultsCount.setText(getString(R.string.results_count, cachedTotal));
                listJobs.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
                emptyState.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                adapter.setJobs(items);
            } else {
                // Sin cambios en la lista, el cursor de paginación sí puede
                // haber cambiado (p. ej. tras pintar desde la caché).
                nextCursor = result.data.nextCursor;
            }
        });
    }

    private void showSortDialog() {
        if (sortDialogShowing) return;
        sortDialogShowing = true;
        String[] values = {"recent", "salary", "match"};
        int[] icons = {R.drawable.ic_clock, R.drawable.ic_money, R.drawable.ic_wand_match};
        String[] options = {"Más recientes", "Mayor salario", "Mayor coincidencia"};
        final AlertDialog[] dialogRef = new AlertDialog[1];
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (8 * getResources().getDisplayMetrics().density);
        content.setPadding(pad, 0, pad, pad);
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            MaterialCardView card = new MaterialCardView(requireContext());
            card.setRadius(18 * getResources().getDisplayMetrics().density);
            card.setCardElevation(getResources().getDisplayMetrics().density);
            LinearLayout row = new LinearLayout(requireContext());
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding((int) (18 * getResources().getDisplayMetrics().density),
                    (int) (6 * getResources().getDisplayMetrics().density),
                    (int) (18 * getResources().getDisplayMetrics().density),
                    (int) (6 * getResources().getDisplayMetrics().density));
            ImageView icon = new ImageView(requireContext());
            icon.setImageDrawable(ResourcesCompat.getDrawable(getResources(), icons[i], requireContext().getTheme()));
            icon.setImageTintList(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.brand_primary)));
            row.addView(icon, new LinearLayout.LayoutParams((int) (40 * getResources().getDisplayMetrics().density),
                    (int) (40 * getResources().getDisplayMetrics().density)));
            TextView label = new TextView(requireContext());
            label.setText(options[i]);
            label.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            labelLp.setMarginStart((int) (12 * getResources().getDisplayMetrics().density));
            row.addView(label, labelLp);
            card.addView(row);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardLp.setMargins(0, (int) (4 * getResources().getDisplayMetrics().density), 0,
                    (int) (4 * getResources().getDisplayMetrics().density));
            content.addView(card, cardLp);
            card.setOnClickListener(v -> {
                filter.sort = values[index];
                loadJobs();
                if (dialogRef[0] != null) dialogRef[0].dismiss();
            });
        }
        dialogRef[0] = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.sort_by)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .setOnDismissListener(d -> sortDialogShowing = false)
                .show();
    }

    private void showSaveSearchDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_save_search, null);
        TextInputEditText nameInput = dialogView.findViewById(R.id.input_search_name);
        android.widget.RadioGroup freqGroup = dialogView.findViewById(R.id.freq_group);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.save_search)
                .setView(dialogView)
                .setPositiveButton(R.string.save, (d, which) -> {
                    String name = UiUtils.text(nameInput);
                    if (name.isEmpty()) name = "Mi búsqueda";
                    int checked = freqGroup.getCheckedRadioButtonId();
                    String freq = checked == R.id.freq_immediate ? "immediate"
                            : checked == R.id.freq_off ? "off" : "daily";
                    SavedSearch search = new SavedSearch();
                    search.name = name;
                    search.query = filter.q;
                    search.category = filter.category;
                    search.workMode = filter.workMode;
                    search.contractType = filter.contractType;
                    search.provinceCode = filter.provinceCode;
                    search.municipalityCode = filter.municipalityCode;
                    search.mobilityScope = filter.mobilityScope;
                    search.minSalary = filter.minSalary;
                    search.maxSalary = filter.maxSalary;
                    search.verifiedOnly = filter.verifiedOnly;
                    search.onlyWithSalary = filter.onlyWithSalary;
                    search.sort = filter.sort;
                    search.frequency = freq;
                    Repository.get(requireContext()).saveFilter(search, result -> {
                        if (isAdded()) {
                            Snackbar.make(requireView(), result.ok
                                    ? "Búsqueda guardada. Te avisaremos con las novedades."
                                    : result.friendlyMessage(), Snackbar.LENGTH_LONG).show();
                            if (result.ok) loadSavedSearchChips();
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Fila "Mis búsquedas": un toque aplica la búsqueda guardada, una
     *  pulsación larga la elimina (con confirmación). */
    private void loadSavedSearchChips() {
        Repository.get(requireContext()).getSavedSearches(result -> {
            if (!isAdded()) return;
            View scroll = requireView().findViewById(R.id.saved_searches_scroll);
            LinearLayout row = requireView().findViewById(R.id.saved_searches_row);
            row.removeAllViews();
            if (!result.ok || result.data == null || result.data.isEmpty()) {
                scroll.setVisibility(View.GONE);
                return;
            }
            scroll.setVisibility(View.VISIBLE);
            int marginEnd = (int) (8 * getResources().getDisplayMetrics().density);
            for (SavedSearch search : result.data) {
                Chip chip = new Chip(requireContext());
                chip.setText(search.name);
                chip.setChipIconResource(R.drawable.ic_search);
                chip.setCheckable(false);
                chip.setClickable(true);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMarginEnd(marginEnd);
                chip.setLayoutParams(lp);
                chip.setOnClickListener(v -> applyFilter(search.toFilter()));
                chip.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(requireContext())
                            .setTitle(search.name)
                            .setMessage("¿Eliminar esta búsqueda guardada?")
                            .setPositiveButton(R.string.delete, (d, w) ->
                                    Repository.get(requireContext()).deleteSavedSearch(
                                            search.filterId, del -> {
                                                if (isAdded()) loadSavedSearchChips();
                                            }))
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                    return true;
                });
                row.addView(chip);
            }
        });
    }

    private void updateActiveChips() {
        activeChips.removeAllViews();
        if (filter.q != null && !filter.q.isEmpty()) addChip("“" + filter.q + "”", () -> {
            filter.q = null;
            suppressWatcher = true;
            queryInput.setText("");
            suppressWatcher = false;
            updateActiveChips();
            loadJobs();
        });
        if (filter.category != null && !filter.category.isEmpty()) addChip(filter.category, () -> {
            filter.category = null;
            updateActiveChips();
            loadJobs();
        });
        if (filter.workMode != null && !filter.workMode.isEmpty()) addChip(Job.workModeLabel(filter.workMode), () -> {
            filter.workMode = null;
            updateActiveChips();
            loadJobs();
        });
        if (filter.contractType != null && !filter.contractType.isEmpty()) addChip(Job.contractLabel(filter.contractType), () -> {
            filter.contractType = null;
            updateActiveChips();
            loadJobs();
        });
        if (filter.provinceCode != null && !filter.provinceCode.isEmpty()) {
            String label = com.cubapincha.job.data.CubaCatalog.provinceName(filter.provinceCode);
            String mun = filter.municipalityCode != null
                    ? com.cubapincha.job.data.CubaCatalog.municipalityName(filter.provinceCode, filter.municipalityCode)
                    : "";
            if (!mun.isEmpty()) label = mun + ", " + label;
            addChip(label, () -> {
                filter.provinceCode = null;
                filter.municipalityCode = null;
                updateActiveChips();
                loadJobs();
            });
        }
        if (filter.mobilityScope != null && !filter.mobilityScope.isEmpty()) {
            addChip(JobFilter.mobilityScopeLabel(filter.mobilityScope), () -> {
                filter.mobilityScope = null;
                updateActiveChips();
                loadJobs();
            });
        }
        if (filter.minSalary != null || filter.maxSalary != null) {
            String label = (filter.minSalary != null ? filter.minSalary + "" : "0")
                    + " – " + (filter.maxSalary != null ? filter.maxSalary + "" : "∞") + " CUP";
            addChip(label, () -> {
                filter.minSalary = null;
                filter.maxSalary = null;
                updateActiveChips();
                loadJobs();
            });
        }
        if (filter.verifiedOnly) addChip(getString(R.string.verified_only), () -> {
            filter.verifiedOnly = false;
            updateActiveChips();
            loadJobs();
        });
        if (filter.onlyWithSalary) addChip(getString(R.string.only_with_salary), () -> {
            filter.onlyWithSalary = false;
            updateActiveChips();
            loadJobs();
        });
    }

    /** El toggle muestra la vista a la que se cambia (rejilla <-> lista). */
    private void updateViewToggleIcon(View root) {
        android.widget.ImageButton toggle = root.findViewById(R.id.btn_view_toggle);
        toggle.setImageResource(viewMode == JobAdapter.VIEW_CARD
                ? R.drawable.ic_list : R.drawable.ic_grid);
    }

    private void addChip(String text, Runnable onClose) {
        Chip chip = new Chip(requireContext());
        chip.setText(text);
        chip.setCloseIconVisible(true);
        chip.setOnCloseIconClickListener(v -> onClose.run());
        activeChips.addView(chip);
        AnimUtils.chipPop(chip);
    }

    private void loadJobs() {
        skeletonContainer.setVisibility(View.VISIBLE);
        AnimUtils.pulse(skeletonContainer);
        emptyState.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);
        listJobs.setVisibility(View.GONE);
        nextCursor = null;
        isLoadingMore = false;
        jobsRequestDone = false;
        if (watchdog != null) handler.removeCallbacks(watchdog);
        watchdog = () -> {
            if (jobsRequestDone || !isAdded()) return;
            // La red no respondió a tiempo: esconder el esqueleto y ofrecer
            // reintentar. Si la respuesta llega después, pinta igual.
            AnimUtils.stopPulse(skeletonContainer);
            skeletonContainer.setVisibility(View.GONE);
            errorState.setVisibility(View.VISIBLE);
        };
        handler.postDelayed(watchdog, 15000);
        Repository.get(requireContext()).getJobs(filter.copy(), null, result -> {
            jobsRequestDone = true;
            if (watchdog != null) handler.removeCallbacks(watchdog);
            AnimUtils.stopPulse(skeletonContainer);
            skeletonContainer.setVisibility(View.GONE);
            if (!isAdded()) return;
            if (result.ok && result.data != null) {
                errorState.setVisibility(View.GONE);
                resultsCount.setText(getString(R.string.results_count, result.data.total));
                cachedItems = result.data.items;
                cachedTotal = result.data.total;
                nextCursor = result.data.nextCursor;
                if (result.data.items.isEmpty()) {
                    emptyState.setVisibility(View.VISIBLE);
                } else {
                    listJobs.setVisibility(View.VISIBLE);
                    adapter.setJobs(result.data.items);
                }
            } else {
                resultsCount.setText("\u2014");
                errorState.setVisibility(View.VISIBLE);
            }
        });
    }

    /** Carga la siguiente página y la añade al final de la lista. */
    private void loadMoreJobs() {
        if (nextCursor == null || isLoadingMore) return;
        isLoadingMore = true;
        Repository.get(requireContext()).getJobs(filter.copy(), nextCursor, result -> {
            isLoadingMore = false;
            if (!isAdded()) return;
            if (!result.ok || result.data == null) {
                Snackbar.make(requireView(), R.string.load_more_error, Snackbar.LENGTH_LONG)
                        .setAction(R.string.retry, v -> loadMoreJobs())
                        .show();
                return;
            }
            nextCursor = result.data.nextCursor;
            if (cachedItems != null) cachedItems.addAll(result.data.items);
            adapter.appendJobs(result.data.items);
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
                            adapter.updateJob(job);
                        })
                        .show();
            }
            adapter.updateJob(job);
        });
    }

    @Override
    public void onDestroyView() {
        // Detener el pulso infinito del skeleton y cancelar la búsqueda
        // pendiente: sin esto, loadJobs() se ejecutaba sobre vistas destruidas.
        AnimUtils.stopPulse(skeletonContainer);
        if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
        if (watchdog != null) handler.removeCallbacks(watchdog);
        super.onDestroyView();
    }
}
