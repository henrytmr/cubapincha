package com.cubapincha.job.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.cubapincha.job.ui.RatingStarsView;
import com.cubapincha.job.ui.RatingUi;
import androidx.fragment.app.Fragment;
import com.cubapincha.job.ui.RatingStarsView;
import com.cubapincha.job.ui.RatingUi;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.ImageButton;

import com.cubapincha.job.R;
import com.cubapincha.job.model.RecommendedWorker;
import com.cubapincha.job.ui.UiUtils;
import com.cubapincha.job.util.AnimUtils;
import com.cubapincha.job.util.MatchRingView;
import com.cubapincha.job.util.NetworkImageLoader;
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
        default void onProfileClick(RecommendedWorker worker) { onWorkerClick(worker); }
    }

    private final List<RecommendedWorker> items = new ArrayList<>();
    private final Listener listener;
    private int lastAnimatedPosition = -1;
    private boolean photosVisible;
    public void setPhotosVisible(boolean visible) { photosVisible = visible; notifyDataSetChanged(); }

    public RecommendedWorkerAdapter(Listener listener) { this.listener = listener; }

    public void setItems(List<RecommendedWorker> workers) {
        items.clear();
        if (workers != null) items.addAll(workers);
        lastAnimatedPosition = -1;
        notifyDataSetChanged();
    }

    public void remove(RecommendedWorker worker) {
        int index = items.indexOf(worker);
        if (index >= 0) {
            items.remove(index);
            notifyItemRemoved(index);
        }
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
        h.avatar.setVisibility(View.VISIBLE);
        h.avatarPhoto.setVisibility(w.photoThumbUrl == null || w.photoThumbUrl.isEmpty() ? View.GONE : View.VISIBLE);
        if (h.avatarPhoto.getVisibility() == View.VISIBLE) NetworkImageLoader.load(h.avatarPhoto, w.photoThumbUrl);
        h.name.setText(w.workerName);
        h.headline.setText(w.headline == null || w.headline.isEmpty() ? "Perfil profesional" : w.headline);
        h.location.setText(w.locationText());
        h.mode.setText(readableMode(w.workModes));
        h.salary.setText(w.salaryText());
        h.date.setText("Perfil profesional");
        h.ring.setPercent(w.matchPercent, true);
        h.matchLabel.setText(String.format(java.util.Locale.US, "%d%% de coincidencia", w.matchPercent));
        UiUtils.setDebouncedClickListener(h.profile, v -> listener.onProfileClick(w));
        UiUtils.setDebouncedClickListener(h.avatar, v -> listener.onProfileClick(w));
        UiUtils.setDebouncedClickListener(h.avatarPhoto, v -> listener.onProfileClick(w));
        h.save.setImageResource(w.saved ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark);
        h.save.setContentDescription(w.saved ? "Quitar de guardados" : "Guardar trabajador");
        h.photoEye.setVisibility(View.VISIBLE);
        h.photoEye.setImageResource(photosVisible ? R.drawable.ic_visibility : R.drawable.ic_visibility_off);
        h.photoEye.setContentDescription(photosVisible ? "Ocultar fotos" : "Mostrar fotos");
        UiUtils.setDebouncedClickListener(h.photoEye, v -> {
            photosVisible = !photosVisible;
            notifyDataSetChanged();
        });
        h.photo.setVisibility(photosVisible && w.photoThumbUrl != null && !w.photoThumbUrl.isEmpty() ? View.VISIBLE : View.GONE);
        if (h.photo.getVisibility() == View.VISIBLE) NetworkImageLoader.load(h.photo, w.photoThumbUrl);
        UiUtils.setDebouncedClickListener(h.itemView, v -> listener.onWorkerClick(w));
        UiUtils.setDebouncedClickListener(h.save, v -> {
            AnimUtils.pop(h.save);
            w.saved = !w.saved;
            h.save.setImageResource(w.saved ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark);
            listener.onSaveClick(w);
        });
        int pos = h.getBindingAdapterPosition();
        if (pos != RecyclerView.NO_POSITION && pos > lastAnimatedPosition) {
            lastAnimatedPosition = pos;
            AnimUtils.fadeSlideIn(h.itemView, Math.min(pos, 6) * 40L);
        }
    }

    @Override public int getItemCount() { return items.size(); }
    private static String readableMode(List<String> modes) {
        if (modes == null || modes.isEmpty()) return "Presencial";
        String code = modes.get(0);
        if ("por_zonas".equals(code)) return "Por zonas";
        if ("hibrido".equals(code)) return "Híbrido";
        if ("remoto".equals(code)) return "Remoto";
        if ("presencial".equals(code)) return "Presencial";
        return code.replace("_", " ");
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView avatar, name, headline, location, mode, salary, date, profile, matchLabel;
        ImageButton save, photoEye;
        android.widget.ImageView photo, avatarPhoto;
        MatchRingView ring;
        Holder(View v) {
            super(v);
            avatar = v.findViewById(R.id.recommended_avatar);
            avatarPhoto = v.findViewById(R.id.recommended_avatar_photo);
            name = v.findViewById(R.id.recommended_name);
            headline = v.findViewById(R.id.recommended_headline);
            location = v.findViewById(R.id.recommended_location);
            salary = v.findViewById(R.id.recommended_salary);
            mode = v.findViewById(R.id.recommended_mode);
            date = v.findViewById(R.id.recommended_date);
            profile = v.findViewById(R.id.recommended_profile);
            matchLabel = v.findViewById(R.id.recommended_match_label);
            ring = v.findViewById(R.id.recommended_match_ring);
            save = v.findViewById(R.id.btn_recommended_save);
            photoEye = v.findViewById(R.id.btn_worker_photo_eye);
            photo = v.findViewById(R.id.worker_photo);
        }
    }
}
