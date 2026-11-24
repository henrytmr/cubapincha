package com.cubapincha.job.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.cubapincha.job.R;
import com.cubapincha.job.adapter.RecommendedWorkerAdapter;
import com.cubapincha.job.data.JobFilter;
import com.cubapincha.job.data.Repository;
import com.cubapincha.job.data.SessionManager;
import com.cubapincha.job.model.Application;
import com.cubapincha.job.model.RecommendedWorker;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Panel de empresa: indicadores y trabajadores recomendados por matchWorkers. */
public class DashboardFragment extends Fragment implements RecommendedWorkerAdapter.Listener {
    private RecommendedWorkerAdapter recommendedAdapter;
    private View root;
    private boolean photosVisible;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        root = view;
        MainActivity activity = (MainActivity) requireActivity();
        SessionManager session = SessionManager.get(requireContext());
        ((TextView) view.findViewById(R.id.greeting)).setText(
                getString(R.string.hello_name, session.getDisplayName()));
        view.findViewById(R.id.btn_company_notifications).setOnClickListener(v ->
                activity.pushFragment(new NotificationsFragment()));

        boolean pending = "pending_verification".equals(session.getStatus());
        view.findViewById(R.id.card_pending).setVisibility(pending ? View.VISIBLE : View.GONE);
        int drafts = Repository.get(requireContext()).draftCount();
        View cardDrafts = view.findViewById(R.id.card_drafts);
        cardDrafts.setVisibility(drafts > 0 ? View.VISIBLE : View.GONE);
        if (drafts > 0) {
            ((TextView) view.findViewById(R.id.drafts_text)).setText(
                    getString(R.string.pending_publish) + ": " + drafts
                            + (drafts == 1 ? " borrador" : " borradores") + " en tu teléfono.");
            cardDrafts.setOnClickListener(v -> activity.selectTab(R.id.nav_jobs));
        }

        RecyclerView list = view.findViewById(R.id.list_recommended);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        recommendedAdapter = new RecommendedWorkerAdapter(this);
        recommendedAdapter.setPhotosVisible(photosVisible);
        list.setAdapter(recommendedAdapter);
        android.widget.ImageButton eye = view.findViewById(R.id.btn_dashboard_photo_toggle);
        eye.setOnClickListener(v -> {
            photosVisible = !photosVisible;
            recommendedAdapter.setPhotosVisible(photosVisible);
            eye.setImageResource(photosVisible ? R.drawable.ic_visibility : R.drawable.ic_visibility_off);
            eye.setContentDescription(photosVisible ? "Ocultar fotos" : "Mostrar fotos");
        });
        View explorer = view.findViewById(R.id.btn_explore_workers);
        if (explorer != null) explorer.setOnClickListener(v -> ((MainActivity) requireActivity()).pushFragment(new WorkerSearchFragment()));
        view.findViewById(R.id.btn_publish).setOnClickListener(v -> activity.openCreateJob(null));
        SwipeRefreshLayout refresh = view.findViewById(R.id.swipe_refresh);
        refresh.setOnRefreshListener(() -> loadStats(view, refresh));
        loadStats(view, refresh);
    }

    private void loadStats(View view, SwipeRefreshLayout refresh) {
        Repository repo = Repository.get(requireContext());
        TextView active = view.findViewById(R.id.stat_active);
        TextView interests = view.findViewById(R.id.stat_interests);
        TextView reviewing = view.findViewById(R.id.stat_reviewing);
        TextView interviews = view.findViewById(R.id.stat_interviews);
        JobFilter filter = new JobFilter();
        filter.status = "published";
        repo.getJobs(filter, null, result -> {
            if (isAdded() && result.ok && result.data != null) active.setText(String.valueOf(result.data.total));
        });
        repo.getApplications(null, result -> {
            if (!isAdded() || !result.ok || result.data == null) return;
            int inReview = 0, inInterview = 0;
            for (Application a : result.data) {
                if ("reviewing".equals(a.status)) inReview++;
                if ("interview".equals(a.status)) inInterview++;
            }
            interests.setText(String.valueOf(result.data.size()));
            reviewing.setText(String.valueOf(inReview));
            interviews.setText(String.valueOf(inInterview));
        });
        repo.getRecommendedWorkers(30, 20, result -> {
            if (!isAdded() || getView() == null) return;
            if (result.ok && result.data != null && !result.data.isEmpty()) {
                recommendedAdapter.setItems(result.data);
                view.findViewById(R.id.recommended_empty).setVisibility(View.GONE);
            } else {
                recommendedAdapter.setItems(new ArrayList<>());
                view.findViewById(R.id.recommended_empty).setVisibility(View.VISIBLE);
            }
        });
        repo.getNotifications(true, result -> { });
        if (refresh != null) refresh.setRefreshing(false);
    }

    @Override public void onWorkerClick(RecommendedWorker worker) {
        ((MainActivity) requireActivity()).openWorkerDetail(worker);
    }

    @Override public void onInterestClick(RecommendedWorker worker) {
        Repository.get(requireContext()).inviteWorker(worker.workerId, worker.matchedOfferId, "",
                result -> {
                    if (isAdded() && root != null) Snackbar.make(root,
                            result.ok ? "Invitación enviada al trabajador." : result.friendlyMessage(),
                            Snackbar.LENGTH_SHORT).show();
                });
    }

    @Override public void onSaveClick(RecommendedWorker worker) {
        boolean requested = worker.saved;
        Repository.get(requireContext()).saveCandidate(worker.workerId, null, requested, result -> {
            if (!isAdded() || root == null) return;
            if (!result.ok && !result.queued) {
                worker.saved = !requested;
                recommendedAdapter.notifyDataSetChanged();
            } else if (result.ok && result.data != null) {
                worker.saved = result.data;
                recommendedAdapter.notifyDataSetChanged();
            }
            Snackbar.make(root, result.ok || result.queued
                    ? (requested ? "Perfil guardado." : "Perfil quitado de guardados.")
                    : result.friendlyMessage(), Snackbar.LENGTH_SHORT).show();
        });
    }

    @Override public void onWhatsAppClick(RecommendedWorker worker) {
        if (worker.phone == null || worker.phone.trim().isEmpty()) {
            Snackbar.make(root, "Este trabajador no tiene WhatsApp disponible.", Snackbar.LENGTH_LONG).show();
            return;
        }
        Repository.get(requireContext()).contactEvent("worker", worker.workerId, "whatsapp");
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/"
                    + worker.phone.replaceAll("[^0-9]", ""))));
        } catch (Exception e) {
            Snackbar.make(root, "No pudimos abrir WhatsApp.", Snackbar.LENGTH_LONG).show();
        }
    }

    @Override public void onProfileClick(RecommendedWorker worker) { onWorkerClick(worker); }

    private static String join(List<String> values) { return join(values, ", "); }
    private static String join(List<String> values, String separator) {
        StringBuilder b = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isEmpty()) continue;
            if (b.length() > 0) b.append(separator);
            b.append(value);
        }
        return b.toString();
    }
}
