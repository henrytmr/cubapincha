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
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.cubapincha.job.R;
import com.cubapincha.job.adapter.JobAdapter;
import com.cubapincha.job.adapter.RecommendedWorkerAdapter;
import com.cubapincha.job.data.Repository;
import com.cubapincha.job.data.SessionManager;
import com.cubapincha.job.model.Job;
import com.cubapincha.job.model.RecommendedWorker;
import com.cubapincha.job.util.AnimUtils;

import java.util.List;

/** Mis guardadas: ofertas y, para empresas, trabajadores guardados; funciona con caché local. */
public class SavedJobsFragment extends Fragment implements JobAdapter.Listener,
        RecommendedWorkerAdapter.Listener {
    private JobAdapter adapter;
    private RecommendedWorkerAdapter candidateAdapter;
    private LinearLayout skeletonContainer, emptyState, errorState;
    private TextView errorBody, candidatesTitle;
    private RecyclerView list, candidatesList;
    private boolean jobsLoaded, candidatesLoaded, jobsEmpty = true, candidatesEmpty = true;

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
        candidatesList = view.findViewById(R.id.list_saved_candidates);
        candidatesList.setLayoutManager(new LinearLayoutManager(requireContext()));
        candidateAdapter = new RecommendedWorkerAdapter(this);
        candidatesList.setAdapter(candidateAdapter);
        candidatesTitle = view.findViewById(R.id.saved_candidates_title);
        skeletonContainer = view.findViewById(R.id.skeleton_container);
        emptyState = view.findViewById(R.id.empty_state);
        errorState = view.findViewById(R.id.error_state);
        errorBody = view.findViewById(R.id.error_body);
        view.findViewById(R.id.btn_retry).setOnClickListener(v -> loadSaved());
        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        view.findViewById(R.id.btn_explore).setOnClickListener(v -> {
            MainActivity activity = (MainActivity) requireActivity();
            if ("company".equals(SessionManager.get(requireContext()).getRole())) {
                activity.pushFragment(new WorkerSearchFragment());
            } else {
                activity.selectTab(R.id.nav_search);
            }
        });
        loadSaved();
    }

    private void loadSaved() {
        skeletonContainer.setVisibility(View.VISIBLE);
        AnimUtils.pulse(skeletonContainer);
        emptyState.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);
        list.setVisibility(View.GONE);
        candidatesList.setVisibility(View.GONE);
        candidatesTitle.setVisibility(View.GONE);
        jobsLoaded = false;
        candidatesLoaded = false;
        Repository repo = Repository.get(requireContext());
        repo.getSavedJobs(result -> {
            if (!isAdded()) return;
            jobsLoaded = true;
            if (result.ok && result.data != null) {
                jobsEmpty = result.data.isEmpty();
                if (!jobsEmpty) {
                    list.setVisibility(View.VISIBLE);
                    adapter.setJobs(result.data);
                }
            } else {
                errorBody.setText(result.friendlyMessage());
                errorState.setVisibility(View.VISIBLE);
            }
            updateEmptyState();
        });
        repo.getSavedCandidates(50, null, result -> {
            if (!isAdded()) return;
            candidatesLoaded = true;
            if (result.ok && result.data != null) {
                candidatesEmpty = result.data.isEmpty();
                if (!candidatesEmpty) {
                    for (RecommendedWorker worker : result.data) worker.saved = true;
                    candidatesTitle.setVisibility(View.VISIBLE);
                    candidatesList.setVisibility(View.VISIBLE);
                    candidateAdapter.setItems(result.data);
                }
            }
            updateEmptyState();
        });
    }

    private void updateEmptyState() {
        if (!jobsLoaded || !candidatesLoaded) return;
        AnimUtils.stopPulse(skeletonContainer);
        skeletonContainer.setVisibility(View.GONE);
        if (jobsEmpty && candidatesEmpty) emptyState.setVisibility(View.VISIBLE);
    }

    @Override public void onJobClick(Job job) { ((MainActivity) requireActivity()).openJobDetail(job); }

    @Override public void onSaveClick(Job job, ImageButton saveButton) {
        Repository.get(requireContext()).saveJob(job.jobId, false, result -> {
            if (!isAdded()) return;
            if (result.ok) {
                Snackbar.make(requireView(), "Oferta quitada de guardadas.", Snackbar.LENGTH_SHORT).show();
                loadSaved();
            } else Snackbar.make(requireView(), result.friendlyMessage(), Snackbar.LENGTH_LONG).show();
        });
    }

    @Override public void onWorkerClick(RecommendedWorker worker) {
        ((MainActivity) requireActivity()).openWorkerDetail(worker);
    }

    @Override public void onProfileClick(RecommendedWorker worker) { onWorkerClick(worker); }
    @Override public void onInterestClick(RecommendedWorker worker) { }
    @Override public void onWhatsAppClick(RecommendedWorker worker) { }
    @Override public void onSaveClick(RecommendedWorker worker) {
        if (!worker.saved) {
            candidateAdapter.remove(worker);
            candidatesEmpty = candidateAdapter.getItemCount() == 0;
            if (candidatesEmpty) {
                candidatesList.setVisibility(View.GONE);
                candidatesTitle.setVisibility(View.GONE);
                updateEmptyState();
            }
            Repository.get(requireContext()).saveCandidate(worker.workerId, null, false, result -> {
                if (!isAdded() || result.ok) return;
                loadSaved();
                Snackbar.make(requireView(), result.friendlyMessage(), Snackbar.LENGTH_LONG).show();
            });
        }
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isEmpty()) continue;
            if (out.length() > 0) out.append(", ");
            out.append(value);
        }
        return out.toString();
    }

    @Override public void onDestroyView() {
        AnimUtils.stopPulse(skeletonContainer);
        super.onDestroyView();
    }
}
