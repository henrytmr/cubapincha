package com.cubapincha.job.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.cubapincha.job.R;
import com.cubapincha.job.model.Job;
import com.cubapincha.job.ui.UiUtils;
import com.cubapincha.job.util.AnimUtils;
import com.cubapincha.job.util.TextUtils;

import java.util.ArrayList;
import java.util.List;

/** Ofertas de la empresa con acciones de gestión. */
public class CompanyJobAdapter extends RecyclerView.Adapter<CompanyJobAdapter.Holder> {
    public interface Listener {
        void onJobClick(Job job);
        void onEditClick(Job job);
        void onPauseClick(Job job);
        void onDuplicateClick(Job job);
        void onCandidatesClick(Job job);
        void onMoreClick(Job job, View anchor);
    }

    private final List<Job> items = new ArrayList<>();
    private final Listener listener;
    private int lastAnimatedPosition = -1;

    public CompanyJobAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<Job> list) {
        items.clear();
        if (list != null) items.addAll(list);
        lastAnimatedPosition = -1;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_company_job, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        AnimUtils.resetItemState(h.itemView);
        Job job = items.get(position);
        h.title.setText(job.title);
        h.status.setText(Job.statusLabel(job.status));
        int color = 0;
        boolean themeColor = false;
        String status = job.status != null ? job.status : "";
        switch (status) {
            case "published":
                color = R.color.confirm_green;
                break;
            case "paused":
                color = R.color.amber_notice;
                break;
            case "closed":
            case "expired":
                color = R.color.error_red;
                break;
            case "archived":
                color = R.color.violet_500;
                break;
            default:
                // Borrador u otro estado: secundario del tema (visible de noche).
                themeColor = true;
        }
        h.status.setTextColor(themeColor
                ? UiUtils.resolveThemeColor(h.itemView.getContext(),
                        com.google.android.material.R.attr.colorOnSurfaceVariant)
                : ContextCompat.getColor(h.itemView.getContext(), color));
        h.meta.setText(job.locationText() + " · " + Job.workModeLabel(job.workMode) + " · " + job.salaryText());
        String expiry = job.expiryNotice();
        h.date.setText((expiry.isEmpty() ? "" : expiry + " · ") + ("published".equals(job.status)
                ? "Publicada " + TextUtils.timeAgo(job.publishedAt).toLowerCase()
                : "Actualizada " + TextUtils.timeAgo(job.updatedAt).toLowerCase()));
        h.date.setTextColor(expiry.equals("Vencida") ? ContextCompat.getColor(h.itemView.getContext(), R.color.error_red)
                : expiry.isEmpty() ? UiUtils.resolveThemeColor(h.itemView.getContext(), com.google.android.material.R.attr.colorOnSurfaceVariant)
                : ContextCompat.getColor(h.itemView.getContext(), R.color.amber_notice));
        h.pause.setText("paused".equals(job.status) ? "Reactivar" : h.itemView.getContext().getString(R.string.pause));
        // Pausar/Reactivar solo tiene sentido en ofertas publicadas o pausadas.
        h.pause.setVisibility("published".equals(job.status) || "paused".equals(job.status)
                ? View.VISIBLE : View.GONE);
        UiUtils.setDebouncedClickListener(h.itemView, v -> {
            if (listener != null) listener.onJobClick(job);
        });
        UiUtils.setDebouncedClickListener(h.edit, v -> {
            if (listener != null) listener.onEditClick(job);
        });
        UiUtils.setDebouncedClickListener(h.pause, v -> {
            if (listener != null) listener.onPauseClick(job);
        });
        UiUtils.setDebouncedClickListener(h.duplicate, v -> {
            if (listener != null) listener.onDuplicateClick(job);
        });
        UiUtils.setDebouncedClickListener(h.candidates, v -> {
            if (listener != null) listener.onCandidatesClick(job);
        });
        UiUtils.setDebouncedClickListener(h.more, v -> {
            if (listener != null) listener.onMoreClick(job, v);
        });
        int animPos = h.getBindingAdapterPosition();
        if (animPos != RecyclerView.NO_POSITION && animPos > lastAnimatedPosition) {
            lastAnimatedPosition = animPos;
            AnimUtils.fadeSlideIn(h.itemView, Math.min(animPos, 6) * 40L);
        }
    }

    @Override
    public void onViewRecycled(@NonNull Holder holder) {
        super.onViewRecycled(holder);
        AnimUtils.resetItemState(holder.itemView);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView title, status, meta, date;
        TextView edit, pause, duplicate, candidates;
        View more;

        Holder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.job_title);
            status = itemView.findViewById(R.id.status_chip);
            meta = itemView.findViewById(R.id.job_meta);
            date = itemView.findViewById(R.id.job_date);
            edit = itemView.findViewById(R.id.btn_edit);
            pause = itemView.findViewById(R.id.btn_pause);
            duplicate = itemView.findViewById(R.id.btn_duplicate);
            candidates = itemView.findViewById(R.id.btn_candidates);
            more = itemView.findViewById(R.id.btn_more);
        }
    }
}
