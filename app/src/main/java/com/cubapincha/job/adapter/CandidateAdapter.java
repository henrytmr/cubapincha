package com.cubapincha.job.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.cubapincha.job.ui.RatingStarsView;
import com.cubapincha.job.ui.RatingUi;
import androidx.fragment.app.Fragment;
import com.cubapincha.job.ui.RatingStarsView;
import com.cubapincha.job.ui.RatingUi;
import androidx.recyclerview.widget.RecyclerView;

import com.cubapincha.job.R;
import com.cubapincha.job.ui.UiUtils;
import com.cubapincha.job.model.Application;
import com.cubapincha.job.util.AnimUtils;
import com.cubapincha.job.util.MatchRingView;
import com.cubapincha.job.util.NetworkImageLoader;
import com.cubapincha.job.util.TextUtils;

import java.util.ArrayList;
import java.util.List;

/** Candidatos para la empresa: compatibilidad y acciones de gestión. */
public class CandidateAdapter extends RecyclerView.Adapter<CandidateAdapter.Holder> {
    public interface Listener {
        void onCandidateClick(Application app);
        void onStatusClick(Application app, String newStatus);
        void onNoteClick(Application app);
        void onSaveClick(Application app);
        void onRateClick(Application app);
        void onDeleteClick(Application app);
    }

    private final List<Application> items = new ArrayList<>();
    private final Listener listener;
    private int lastAnimatedPosition = -1;

    public CandidateAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<Application> list) {
        items.clear();
        if (list != null) items.addAll(list);
        lastAnimatedPosition = -1;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_candidate, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        AnimUtils.resetItemState(h.itemView);
        Application app = items.get(position);
        h.avatar.setText(TextUtils.initials(app.workerName));
        boolean hasPhoto = app.workerPhotoThumbUrl != null && !app.workerPhotoThumbUrl.isEmpty();
        h.avatarPhoto.setVisibility(hasPhoto ? View.VISIBLE : View.GONE);
        h.avatar.setVisibility(hasPhoto ? View.GONE : View.VISIBLE);
        if (hasPhoto) NetworkImageLoader.load(h.avatarPhoto, app.workerPhotoThumbUrl);
        else h.avatarPhoto.setImageDrawable(null);
        h.name.setText(app.workerName);
        h.headline.setText(app.workerHeadline != null && !app.workerHeadline.isEmpty()
                ? app.workerHeadline : app.workerLocationText());
        int match = app.matchScore != null ? app.matchScore : 0;
        h.ring.setVisibility(match > 0 ? View.VISIBLE : View.GONE);
        h.ring.setPercent(match, false);
        h.jobInfo.setText(app.jobTitle + " · " + TextUtils.timeAgo(app.createdAt));
        String skills = TextUtils.joinComma(app.workerSkills);
        h.skills.setText(skills.isEmpty() ? "" : "Habilidades: " + skills);
        h.skills.setVisibility(skills.isEmpty() ? View.GONE : View.VISIBLE);
        h.status.setText("Estado: " + Application.statusLabel(app.status)
                + (app.hasCv ? " · Tiene CV" : ""));
        h.rate.setVisibility("hired".equals(app.status) ? View.VISIBLE : View.GONE);
        // Solo se muestran las transiciones permitidas desde el estado actual.
        boolean isSent = "sent".equals(app.status);
        boolean isReviewing = "reviewing".equals(app.status);
        boolean isInterview = "interview".equals(app.status);
        h.reviewing.setVisibility(isSent ? View.VISIBLE : View.GONE);
        h.interview.setVisibility(isSent || isReviewing ? View.VISIBLE : View.GONE);
        h.hire.setVisibility(isReviewing || isInterview ? View.VISIBLE : View.GONE);
        h.reject.setVisibility(isSent || isReviewing || isInterview ? View.VISIBLE : View.GONE);
        UiUtils.setDebouncedClickListener(h.itemView, v -> {
            if (listener != null) listener.onCandidateClick(app);
        });
        UiUtils.setDebouncedClickListener(h.reviewing, v -> {
            if (listener != null) listener.onStatusClick(app, "reviewing");
        });
        UiUtils.setDebouncedClickListener(h.interview, v -> {
            if (listener != null) listener.onStatusClick(app, "interview");
        });
        UiUtils.setDebouncedClickListener(h.hire, v -> {
            if (listener != null) listener.onStatusClick(app, "hired");
        });
        UiUtils.setDebouncedClickListener(h.reject, v -> {
            if (listener != null) listener.onStatusClick(app, "rejected");
        });
        UiUtils.setDebouncedClickListener(h.note, v -> {
            if (listener != null) listener.onNoteClick(app);
        });
        UiUtils.setDebouncedClickListener(h.save, v -> {
            if (listener != null) listener.onSaveClick(app);
        });
        UiUtils.setDebouncedClickListener(h.rate, v -> {
            if (listener != null) listener.onRateClick(app);
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

    public Application getItemAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position) : null;
    }

    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView avatar, name, headline, jobInfo, skills, status;
        ImageView avatarPhoto;
        TextView reviewing, interview, hire, reject, note, save, rate;
        MatchRingView ring;

        Holder(View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.worker_avatar);
            avatarPhoto = itemView.findViewById(R.id.worker_avatar_photo);
            name = itemView.findViewById(R.id.worker_name);
            headline = itemView.findViewById(R.id.worker_headline);
            jobInfo = itemView.findViewById(R.id.job_info);
            skills = itemView.findViewById(R.id.skills_text);
            status = itemView.findViewById(R.id.status_text);
            ring = itemView.findViewById(R.id.match_ring);
            reviewing = itemView.findViewById(R.id.btn_reviewing);
            interview = itemView.findViewById(R.id.btn_interview);
            hire = itemView.findViewById(R.id.btn_hire);
            reject = itemView.findViewById(R.id.btn_reject);
            note = itemView.findViewById(R.id.btn_note);
            save = itemView.findViewById(R.id.btn_save);
            rate = itemView.findViewById(R.id.btn_rate_work);
        }
    }
}
