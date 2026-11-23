package com.cubapincha.job.ui;

import android.os.Bundle;
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

import com.google.android.material.snackbar.Snackbar;
import com.cubapincha.job.R;
import com.cubapincha.job.adapter.JobAdapter;
import com.cubapincha.job.data.Repository;
import com.cubapincha.job.model.Job;
import com.cubapincha.job.util.AnimUtils;

/** Mis guardadas: funciona también sin conexión (caché local). */
public class SavedJobsFragment extends Fragment implements JobAdapter.Listener {
    private JobAdapter adapter;
    private LinearLayout skeletonContainer, emptyState, errorState;
    private TextView errorBody;
    private RecyclerView list;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_saved_jobs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        list = view.findViewById(R.id.list_saved);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new JobAdapter(this);
        list.setAdapter(adapter);
        skeletonContainer = view.findViewById(R.id.skeleton_container);
        emptyState = view.findViewById(R.id.empty_state);
        errorState = view.findViewById(R.id.error_state);
        errorBody = view.findViewById(R.id.error_body);
        view.findViewById(R.id.btn_retry).setOnClickListener(v -> loadSaved());
        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        view.findViewById(R.id.btn_explore).setOnClickListener(v ->
                ((MainActivity) requireActivity()).selectTab(R.id.nav_search));
        loadSaved();
    }

    private void loadSaved() {
        skeletonContainer.setVisibility(View.VISIBLE);
        AnimUtils.pulse(skeletonContainer);
        emptyState.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);
        list.setVisibility(View.GONE);
        Repository.get(requireContext()).getSavedJobs(result -> {
            AnimUtils.stopPulse(skeletonContainer);
            skeletonContainer.setVisibility(View.GONE);
            if (!isAdded()) return;
            if (result.ok && result.data != null) {
                if (result.data.isEmpty()) {
                    emptyState.setVisibility(View.VISIBLE);
                } else {
                    list.setVisibility(View.VISIBLE);
                    adapter.setJobs(result.data);
                }
            } else {
                // Un fallo no es "sin guardadas": estado de error con reintento.
                errorBody.setText(result.friendlyMessage());
                errorState.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onJobClick(Job job) {
        ((MainActivity) requireActivity()).openJobDetail(job);
    }

    @Override
    public void onSaveClick(Job job, ImageButton saveButton) {
        // Quitar de guardadas desde aquí.
        Repository.get(requireContext()).saveJob(job.jobId, false, result -> {
            if (!isAdded()) return;
            if (result.ok) {
                Snackbar.make(requireView(), "Oferta quitada de guardadas.", Snackbar.LENGTH_SHORT).show();
                loadSaved();
            } else {
                Snackbar.make(requireView(), result.friendlyMessage(), Snackbar.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        // Detener el pulso infinito del skeleton al destruir la vista.
        AnimUtils.stopPulse(skeletonContainer);
        super.onDestroyView();
    }
}
