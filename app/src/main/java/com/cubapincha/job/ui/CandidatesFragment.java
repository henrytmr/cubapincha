package com.cubapincha.job.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.cubapincha.job.R;
import com.cubapincha.job.adapter.CandidateAdapter;
import com.cubapincha.job.data.Repository;
import com.cubapincha.job.model.Application;
import com.cubapincha.job.util.AnimUtils;

import java.util.ArrayList;
import java.util.List;

/** Candidatos de la empresa, con gestión de estados y notas internas. */
public class CandidatesFragment extends Fragment implements CandidateAdapter.Listener {
    private static final String ARG_JOB_ID = "job_id";

    public static CandidatesFragment newInstance(String jobId) {
        CandidatesFragment f = new CandidatesFragment();
        Bundle args = new Bundle();
        args.putString(ARG_JOB_ID, jobId);
        f.setArguments(args);
        return f;
    }

    /** true si esta instancia filtra por una oferta (detalle apilado),
     *  false si es la pestaña "Candidatos" (todas las ofertas). */
    public boolean isFilteredByJob() {
        return getArguments() != null && getArguments().getString(ARG_JOB_ID) != null;
    }

    private String jobFilter;
    private CandidateAdapter adapter;
    private LinearLayout skeletonContainer, emptyState, errorState;
    private TextView errorBody;
    private RecyclerView list;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_candidates, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        jobFilter = getArguments() != null ? getArguments().getString(ARG_JOB_ID) : null;
        list = view.findViewById(R.id.list_candidates);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new CandidateAdapter(this);
        list.setAdapter(adapter);
        skeletonContainer = view.findViewById(R.id.skeleton_container);
        emptyState = view.findViewById(R.id.empty_state);
        errorState = view.findViewById(R.id.error_state);
        errorBody = view.findViewById(R.id.error_body);
        view.findViewById(R.id.btn_retry).setOnClickListener(v -> loadCandidates());
        SwipeRefreshLayout refresh = view.findViewById(R.id.swipe_refresh);
        refresh.setOnRefreshListener(() -> { loadCandidates(); refresh.setRefreshing(false); });
        view.findViewById(R.id.btn_my_jobs).setOnClickListener(v ->
                ((MainActivity) requireActivity()).selectTab(R.id.nav_jobs));

        TextView filterLabel = view.findViewById(R.id.filter_label);
        if (jobFilter != null) {
            filterLabel.setVisibility(View.VISIBLE);
            Repository.get(requireContext()).getJob(jobFilter, result -> {
                if (isAdded() && result.ok && result.data != null) {
                    filterLabel.setText("Candidatos de: " + result.data.title);
                }
            });
        }
        loadCandidates();
    }

    private void loadCandidates() {
        skeletonContainer.setVisibility(View.VISIBLE);
        AnimUtils.pulse(skeletonContainer);
        emptyState.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);
        list.setVisibility(View.GONE);
        Repository.get(requireContext()).getApplications(null, result -> {
            AnimUtils.stopPulse(skeletonContainer);
            skeletonContainer.setVisibility(View.GONE);
            if (!isAdded()) return;
            if (result.ok && result.data != null) {
                List<Application> filtered = new ArrayList<>();
                for (Application a : result.data) {
                    if (jobFilter == null || jobFilter.equals(a.jobId)) filtered.add(a);
                }
                if (filtered.isEmpty()) {
                    emptyState.setVisibility(View.VISIBLE);
                } else {
                    list.setVisibility(View.VISIBLE);
                    adapter.setItems(filtered);
                }
            } else {
                // Un fallo no es "sin candidatos": estado de error con reintento.
                errorBody.setText(result.friendlyMessage());
                errorState.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onCandidateClick(Application app) {
        StringBuilder sb = new StringBuilder();
        sb.append(app.workerHeadline != null ? app.workerHeadline : "").append("\n");
        sb.append(app.workerCity != null ? app.workerCity : "").append("\n");
        if (app.workerExperienceYears != null) {
            sb.append(app.workerExperienceYears).append(" años de experiencia\n");
        }
        if (app.workerSkills != null && !app.workerSkills.isEmpty()) {
            sb.append("Habilidades: ").append(com.cubapincha.job.util.TextUtils.joinComma(app.workerSkills)).append("\n");
        }
        if (app.internalNote != null && !app.internalNote.isEmpty()) {
            sb.append("\nNota interna: ").append(app.internalNote);
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(app.workerName)
                .setMessage(sb.toString().trim())
                .setPositiveButton(R.string.accept, null)
                .setNeutralButton("Valoraciones", (dialog, which) ->
                        RatingUi.showRatings(this, app.workerId, app.workerName))
                .show();
    }

    @Override
    public void onStatusClick(Application app, String newStatus) {
        Repository.get(requireContext()).changeApplicationStatus(app.applicationId, newStatus, null, result -> {
            if (isAdded()) {
                if (result.ok) {
                    Snackbar.make(requireView(), app.workerName + " · "
                            + Application.statusLabel(newStatus), Snackbar.LENGTH_SHORT).show();
                    loadCandidates();
                } else {
                    Snackbar.make(requireView(), result.friendlyMessage(), Snackbar.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    public void onRateClick(Application app) {
        RatingUi.promptForApplication(this, app);
    }

    @Override
    public void onNoteClick(Application app) {
        EditText input = new EditText(requireContext());
        input.setHint(R.string.internal_note_hint);
        if (app.internalNote != null) input.setText(app.internalNote);
        input.setMinHeight(120);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.internal_note)
                .setView(input)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String note = input.getText().toString().trim();
                    Repository.get(requireContext()).changeApplicationStatus(
                            app.applicationId, app.status, note, result -> {
                                if (isAdded()) {
                                    if (result.ok) {
                                        Snackbar.make(requireView(), "Nota guardada. Solo tu equipo la verá.",
                                                Snackbar.LENGTH_SHORT).show();
                                        loadCandidates();
                                    } else {
                                        Snackbar.make(requireView(), result.friendlyMessage(),
                                                Snackbar.LENGTH_LONG).show();
                                    }
                                }
                            });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onSaveClick(Application app) {
        Repository.get(requireContext()).saveCandidate(app.workerId, app.applicationId, true, result -> {
            if (isAdded()) {
                Snackbar.make(requireView(), result.ok ? getString(R.string.candidate_saved)
                        : result.friendlyMessage(), Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDeleteClick(Application app) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Eliminar candidatura")
                .setMessage("¿Quieres eliminar esta candidatura recibida? Esta acción no se puede deshacer.")
                .setPositiveButton(R.string.delete, (dialog, which) ->
                        Repository.get(requireContext()).deleteApplication(app.applicationId, result -> {
                            if (!isAdded()) return;
                            Snackbar.make(requireView(), result.ok ? "Candidatura eliminada."
                                    : result.friendlyMessage(), Snackbar.LENGTH_SHORT).show();
                            if (result.ok) loadCandidates();
                        }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        // Detener el pulso infinito del skeleton al destruir la vista.
        AnimUtils.stopPulse(skeletonContainer);
        super.onDestroyView();
    }
}
