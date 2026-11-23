package com.cubapincha.job.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cubapincha.job.R;
import com.cubapincha.job.model.RecommendedWorker;
import com.cubapincha.job.ui.UiUtils;
import com.cubapincha.job.ui.RatingStarsView;
import com.cubapincha.job.util.AnimUtils;
import com.cubapincha.job.util.MatchRingView;
import com.cubapincha.job.util.TextUtils;

import java.util.ArrayList;
import java.util.List;

/** Tarjetas del bloque de trabajadores que mejor coinciden con la empresa. */
public class RecommendedWorkerAdapter extends RecyclerView.Adapter<RecommendedWorkerAdapter.Holder> {
    public interface Listener {
        void onWorkerClick(RecommendedWorker worker);
        void onInterestClick(RecommendedWorker worker);
        void onSaveClick(RecommendedWorker worker);
        void onWhatsAppClick(RecommendedWorker worker);
    }

    private final List<RecommendedWorker> items = new ArrayList<>();
    private final Listener listener;
    private int lastAnimatedPosition = -1;

    public RecommendedWorkerAdapter(Listener listener) { this.listener = listener; }

    public void setItems(List<RecommendedWorker> workers) {
        items.clear();
        if (workers != null) items.addAll(workers);
        lastAnimatedPosition = -1;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recommended_worker, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        AnimUtils.resetItemState(h.itemView);
        RecommendedWorker w = items.get(position);
        h.avatar.setText(TextUtils.initials(w.workerName));
        h.name.setText(w.workerName);
        h.headline.setText(w.headline == null || w.headline.isEmpty() ? "Perfil profesional" : w.headline);
        h.location.setText(w.locationText());
        h.salary.setText(w.salaryText());
        int stars = Math.max(0, Math.min(5, (int) Math.round(w.ratingAverage)));
        h.rating.setRating(stars);
        h.rating.setContentDescription(String.format(java.util.Locale.US, "%.1f de 5, %d valoraciones",
                w.ratingAverage, w.ratingTotal));
        String skills = TextUtils.joinComma(w.skills);
        h.skills.setText(skills.isEmpty() ? "" : "Habilidades: " + skills);
        h.skills.setVisibility(skills.isEmpty() ? View.GONE : View.VISIBLE);
        h.ring.setPercent(w.matchPercent, true);
        UiUtils.setDebouncedClickListener(h.itemView, v -> listener.onWorkerClick(w));
        UiUtils.setDebouncedClickListener(h.interest, v -> listener.onInterestClick(w));
        UiUtils.setDebouncedClickListener(h.save, v -> listener.onSaveClick(w));
        UiUtils.setDebouncedClickListener(h.whatsapp, v -> listener.onWhatsAppClick(w));
        int pos = h.getBindingAdapterPosition();
        if (pos != RecyclerView.NO_POSITION && pos > lastAnimatedPosition) {
            lastAnimatedPosition = pos;
            AnimUtils.fadeSlideIn(h.itemView, Math.min(pos, 6) * 40L);
        }
    }

    @Override public int getItemCount() { return items.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        TextView avatar, name, headline, location, salary, skills;
        TextView interest, save, whatsapp;
        RatingStarsView rating;
        MatchRingView ring;
        Holder(View v) {
            super(v);
            avatar = v.findViewById(R.id.recommended_avatar);
            name = v.findViewById(R.id.recommended_name);
            headline = v.findViewById(R.id.recommended_headline);
            location = v.findViewById(R.id.recommended_location);
            salary = v.findViewById(R.id.recommended_salary);
            rating = v.findViewById(R.id.recommended_rating);
            skills = v.findViewById(R.id.recommended_skills);
            ring = v.findViewById(R.id.recommended_match_ring);
            interest = v.findViewById(R.id.btn_recommended_interest);
            save = v.findViewById(R.id.btn_recommended_save);
            whatsapp = v.findViewById(R.id.btn_recommended_whatsapp);
        }
    }
}
