package com.cubapincha.job.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
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

/** Explorador de trabajadores para empresas; reutiliza la misma tarjeta del panel. */
public class WorkerSearchFragment extends Fragment implements RecommendedWorkerAdapter.Listener {
    private RecommendedWorkerAdapter adapter;
    private EditText query;
    private EditText category;
    private EditText province;
    private EditText municipality;
    private EditText minMatch;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable ViewGroup parent, @Nullable Bundle state) {
        int dp = (int) (getResources().getDisplayMetrics().density + 0.5f);
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16 * dp, 16 * dp, 16 * dp, 0);

        LinearLayout bar = new LinearLayout(requireContext());
        bar.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = new ImageButton(requireContext());
        back.setImageResource(R.drawable.ic_chevron_left);
        back.setContentDescription("Atrás");
        query = new EditText(requireContext());
        query.setHint("Oficio, habilidad o trabajador...");
        query.setSingleLine(true);
        bar.addView(back, new LinearLayout.LayoutParams(48 * dp, 48 * dp));
        bar.addView(query, new LinearLayout.LayoutParams(0, 56 * dp, 1));
        root.addView(bar);

        TextView title = new TextView(requireContext());
        title.setText("Explorar trabajadores");
        title.setTextSize(20);
        root.addView(title);

        LinearLayout filters = new LinearLayout(requireContext());
        filters.setOrientation(LinearLayout.VERTICAL);
        category = addFilter(filters, "Categoría");
        province = addFilter(filters, "Provincia");
        municipality = addFilter(filters, "Municipio");
        minMatch = addFilter(filters, "Mínimo %");
        minMatch.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(filters, new LinearLayout.LayoutParams(-1, -2));

        RecyclerView list = new RecyclerView(requireContext());
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RecommendedWorkerAdapter(this);
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        back.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { scheduleSearch(); }
            public void afterTextChanged(android.text.Editable e) { }
        };
        query.addTextChangedListener(watcher);
        category.addTextChangedListener(watcher);
        province.addTextChangedListener(watcher);
        municipality.addTextChangedListener(watcher);
        minMatch.addTextChangedListener(watcher);
        searchWorkers();
        return root;
    }

    private EditText addFilter(LinearLayout parent, String hint) {
        int dp = (int) (getResources().getDisplayMetrics().density + 0.5f);
        EditText field = new EditText(requireContext());
        field.setHint(hint);
        field.setSingleLine(true);
        field.setTextSize(14);
        field.setPadding(12 * dp, 0, 12 * dp, 0);
        parent.addView(field, new LinearLayout.LayoutParams(-1, 44 * dp));
        return field;
    }

    private void scheduleSearch() {
        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
        pendingSearch = this::searchWorkers;
        searchHandler.postDelayed(pendingSearch, 350);
    }

    private void searchWorkers() {
        if (!isAdded() || query == null) return;
        String q = value(query);
        String provinceCode = value(province);
        String municipalityCode = value(municipality);
        String categoryValue = value(category);
        int minimum = 0;
        try {
            if (!value(minMatch).isEmpty()) minimum = Math.max(0, Integer.parseInt(value(minMatch)));
        } catch (NumberFormatException ignored) { }
        Repository.get(requireContext()).getRecommendedWorkers(q.isEmpty() ? null : q,
                provinceCode.isEmpty() ? null : provinceCode,
                municipalityCode.isEmpty() ? null : municipalityCode,
                categoryValue.isEmpty() ? null : categoryValue,
                minimum, 100, result -> {
                    if (!isAdded() || !result.ok || result.data == null) return;
                    adapter.setItems(result.data);
                });
    }

    private String value(EditText field) {
        return field == null || field.getText() == null ? "" : field.getText().toString().trim();
    }

    @Override
    public void onWorkerClick(RecommendedWorker w) { ((MainActivity) requireActivity()).openWorkerDetail(w); }
    @Override
    public void onProfileClick(RecommendedWorker w) { onWorkerClick(w); }
    @Override
    public void onInterestClick(RecommendedWorker w) { Repository.get(requireContext()).inviteWorker(w.workerId, w.matchedOfferId, "", r -> { }); }
    @Override
    public void onSaveClick(RecommendedWorker w) { Repository.get(requireContext()).saveCandidate(w.workerId, null, w.saved, r -> { }); }
    @Override
    public void onWhatsAppClick(RecommendedWorker w) {
        if (w.phone != null && !w.phone.isEmpty()) {
            startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://wa.me/" + w.phone.replaceAll("[^0-9]", ""))));
        }
    }

    @Override
    public void onDestroyView() {
        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
        super.onDestroyView();
    }
}
