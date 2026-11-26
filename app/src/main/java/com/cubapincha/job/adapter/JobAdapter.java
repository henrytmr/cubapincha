package com.cubapincha.job.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.cubapincha.job.ui.RatingStarsView;
import com.cubapincha.job.ui.RatingUi;
import com.cubapincha.job.ui.CommentsBottomSheet;
import com.cubapincha.job.ui.ReactionUi;
import androidx.fragment.app.Fragment;
import com.cubapincha.job.ui.RatingStarsView;
import com.cubapincha.job.ui.RatingUi;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.cubapincha.job.R;
import com.cubapincha.job.data.Repository;
import com.cubapincha.job.data.LocalDb;
import com.cubapincha.job.model.Job;
import com.cubapincha.job.ui.UiUtils;
import com.cubapincha.job.util.AnimUtils;
import com.cubapincha.job.util.MatchRingView;
import com.cubapincha.job.util.NetworkImageLoader;
import com.cubapincha.job.util.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Adaptador de ofertas con dos presentaciones del mismo registro:
 * VISTA TARJETA (explorar muchas) y VISTA FICHA (leer la descripción).
 * El cambio de vista no pierde filtros ni posición.
 */
public class JobAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public static final int VIEW_CARD = 0;
    public static final int VIEW_DETAIL = 1;
    public static final int VIEW_GRID = 2;

    public interface Listener {
        void onJobClick(Job job);
        void onSaveClick(Job job, ImageButton saveButton);
        default void onRatingClick(Job job) { }
        default void onReaction(Job job, String reaction) { }
        default void onCommentsClick(Job job) { }
    }

    private final List<Job> jobs = new ArrayList<>();
    private final Listener listener;
    private int lastAnimatedPosition = -1;
    private int viewMode = VIEW_CARD;
    private boolean showAllPhotos;

    public JobAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setViewMode(int mode) {
        if (this.viewMode != mode) {
            this.viewMode = mode;
            lastAnimatedPosition = -1;
            notifyDataSetChanged();
        }
    }

    public void setPhotosVisible(boolean visible) {
        if (showAllPhotos != visible) { showAllPhotos = visible; notifyDataSetChanged(); }
    }

    public boolean arePhotosVisible() { return showAllPhotos; }

    public int getViewMode() {
        return viewMode;
    }

    public void setJobs(List<Job> list) {
        jobs.clear();
        if (list != null) jobs.addAll(list);
        // Conjunto de datos nuevo: la animación de entrada vuelve a empezar
        // desde cero, con posiciones coherentes con la lista actual.
        lastAnimatedPosition = -1;
        notifyDataSetChanged();
    }

    /** Añade una página más al final (scroll infinito), sin reiniciar la lista. */
    public void appendJobs(List<Job> more) {
        if (more == null || more.isEmpty()) return;
        int start = jobs.size();
        jobs.addAll(more);
        notifyItemRangeInserted(start, more.size());
    }

    public void updateJob(Job updated) {
        for (int i = 0; i < jobs.size(); i++) {
            if (java.util.Objects.equals(jobs.get(i).jobId, updated.jobId)) {
                jobs.set(i, updated);
                notifyItemChanged(i);
                return;
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        return viewMode;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_DETAIL) {
            return new DetailHolder(inflater.inflate(R.layout.item_job_detail, parent, false));
        }
        return new CardHolder(inflater.inflate(R.layout.item_job_card, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        // Primero, estado conocido y visible: una vista reciclada puede llegar
        // con alpha 0 o desplazada si su animación de entrada fue interrumpida.
        AnimUtils.resetItemState(holder.itemView);
        Job job = jobs.get(position);
        if (holder instanceof CardHolder) {
            ((CardHolder) holder).bind(job, position);
        } else {
            ((DetailHolder) holder).bind(job, position);
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        // Sin esto, un animador pendiente sobre la vista reciclada puede dejar
        // invisible el siguiente ítem que reutilice esta vista.
        AnimUtils.resetItemState(holder.itemView);
    }

    @Override
    public int getItemCount() {
        return jobs.size();
    }

    private void bindSaveButton(ImageButton btn, Job job) {
        btn.setImageResource(job.saved ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark);
        // Colores del tema (no fijos de claro): el marcador se ve de noche.
        btn.setColorFilter(job.saved
                ? UiUtils.resolveThemeColor(btn.getContext(),
                        com.google.android.material.R.attr.colorPrimary)
                : UiUtils.resolveThemeColor(btn.getContext(),
                        com.google.android.material.R.attr.colorOnSurfaceVariant));
        UiUtils.setDebouncedClickListener(btn, v -> {
            // Icono animado: el marcador se rellena con un "pop" elegante.
            if (!job.saved) {
                btn.setImageResource(R.drawable.avd_bookmark);
                AnimUtils.playAnimated(btn);
                AnimUtils.hapticTick(btn);
            } else {
                AnimUtils.pop(btn);
            }
            if (listener != null) listener.onSaveClick(job, btn);
        });
    }

    private void bindReactions(View root, Job job) {
        ReactionUi.bind(root, job.myReaction, job.likeCount, job.dislikeCount, job.commentCount,
                new ReactionUi.Actions() {
                    @Override public void onReaction(String reaction) { if (listener != null) listener.onReaction(job, reaction); }
                    @Override public void onComments() { if (listener != null) listener.onCommentsClick(job); }
                });
    }

    private void bindClick(View view, Job job) {
        UiUtils.setDebouncedClickListener(view, v -> {
            if (listener != null) listener.onJobClick(job);
        });
    }

    class CardHolder extends RecyclerView.ViewHolder {
        TextView avatar, title, company, location, mode, salary, date, matchLabel, closedLabel;
        RatingStarsView ratingStars;
        ImageView verified, photo;
        ImageButton saveBtn, photoEye;
        ImageView avatarPhoto;
        View btnView;
        MatchRingView ring;

        CardHolder(View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.company_avatar);
            avatarPhoto = itemView.findViewById(R.id.company_avatar_photo);
            title = itemView.findViewById(R.id.job_title);
            company = itemView.findViewById(R.id.company_name);
            ratingStars = itemView.findViewById(R.id.company_rating_stars);
            verified = itemView.findViewById(R.id.verified_icon);
            location = itemView.findViewById(R.id.job_location);
            mode = itemView.findViewById(R.id.job_mode);
            salary = itemView.findViewById(R.id.job_salary);
            date = itemView.findViewById(R.id.job_date);
            matchLabel = itemView.findViewById(R.id.match_label);
            closedLabel = itemView.findViewById(R.id.closed_label);
            saveBtn = itemView.findViewById(R.id.btn_save);
            photoEye = itemView.findViewById(R.id.btn_job_photo_eye);
            photo = itemView.findViewById(R.id.job_photo);
            btnView = itemView.findViewById(R.id.btn_view);
            ring = itemView.findViewById(R.id.match_ring);
        }

        void bind(Job job, int position) {
            Context ctx = itemView.getContext();
            avatar.setText(TextUtils.initials(job.companyName));
            boolean hasCompanyPhoto = job.companyPhotoThumbUrl != null && !job.companyPhotoThumbUrl.isEmpty();
            avatarPhoto.setVisibility(hasCompanyPhoto ? View.VISIBLE : View.GONE);
            avatar.setVisibility(hasCompanyPhoto ? View.GONE : View.VISIBLE);
            if (!hasCompanyPhoto) avatarPhoto.setImageDrawable(null);
            if (avatarPhoto.getVisibility() == View.VISIBLE) NetworkImageLoader.load(avatarPhoto, job.companyPhotoThumbUrl);
            title.setText(job.title);
            company.setText(job.companyName);
            verified.setVisibility(job.companyVerified ? View.VISIBLE : View.GONE);
            location.setText(job.locationText());
            mode.setText(Job.workModeLabel(job.workMode));
            salary.setText(job.salaryText());
            date.setText(TextUtils.timeAgo(job.publishedAt != null && !job.publishedAt.isEmpty()
                    ? job.publishedAt : job.createdAt));
            boolean closed = !"published".equals(job.status);
            closedLabel.setVisibility(closed ? View.VISIBLE : View.GONE);
            if (job.matchScore > 0) {
                ring.setVisibility(View.VISIBLE);
                matchLabel.setVisibility(View.VISIBLE);
                ring.setPercent(job.matchScore, false);
                matchLabel.setText(ctx.getString(R.string.match_label, job.matchScore));
            } else {
                ring.setVisibility(View.GONE);
                matchLabel.setVisibility(View.GONE);
            }
            bindSaveButton(saveBtn, job);
            bindReactions(itemView, job);
            photoEye.setVisibility(View.VISIBLE);
            boolean visible = showAllPhotos || LocalDb.get(photoEye.getContext()).isPhotoVisible("job", job.jobId);
            photoEye.setImageResource(visible ? R.drawable.ic_visibility : R.drawable.ic_visibility_off);
            photoEye.setContentDescription(visible ? "Ocultar foto" : "Mostrar foto");
            UiUtils.setDebouncedClickListener(photoEye, v -> {
                boolean next = !(showAllPhotos || LocalDb.get(v.getContext()).isPhotoVisible("job", job.jobId));
                LocalDb.get(v.getContext()).setPhotoVisible("job", job.jobId, next);
                notifyDataSetChanged();
            });
            boolean hasPhoto = visible && job.photoThumbUrl != null && !job.photoThumbUrl.isEmpty();
            photo.setVisibility(hasPhoto ? View.VISIBLE : View.GONE);
            photo.setBackgroundResource(hasPhoto ? R.drawable.bg_skeleton : android.R.color.transparent);
            if (!hasPhoto) photo.setImageDrawable(null);
            if (photo.getVisibility() == View.VISIBLE) NetworkImageLoader.load(photo, job.photoThumbUrl);
            bindClick(itemView, job);
            bindClick(btnView, job);
            if (position > lastAnimatedPosition) {
                lastAnimatedPosition = position;
                AnimUtils.fadeSlideIn(itemView, Math.min(position, 6) * 40L);
            }
        }
    }

    class DetailHolder extends RecyclerView.ViewHolder {
        TextView title, company, meta, salary, description, requirements, date;
        ImageView verified;
        ImageButton saveBtn;
        View btnView;
        MatchRingView ring;

        DetailHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.job_title);
            company = itemView.findViewById(R.id.company_name);
            verified = itemView.findViewById(R.id.verified_icon);
            meta = itemView.findViewById(R.id.job_meta);
            salary = itemView.findViewById(R.id.job_salary);
            description = itemView.findViewById(R.id.job_description);
            requirements = itemView.findViewById(R.id.job_requirements);
            date = itemView.findViewById(R.id.job_date);
            saveBtn = itemView.findViewById(R.id.btn_save);
            btnView = itemView.findViewById(R.id.btn_view);
            ring = itemView.findViewById(R.id.match_ring);
        }

        void bind(Job job, int position) {
            title.setText(job.title);
            company.setText(job.companyName);
            verified.setVisibility(job.companyVerified ? View.VISIBLE : View.GONE);
            meta.setText(job.locationText() + " · " + Job.workModeLabel(job.workMode)
                    + " · " + Job.contractLabel(job.contractType));
            salary.setText(job.salaryText());
            description.setText(job.description);
            if (job.requirements != null && !job.requirements.isEmpty()) {
                requirements.setVisibility(View.VISIBLE);
                requirements.setText("Requisitos: " + TextUtils.joinComma(job.requirements));
            } else {
                requirements.setVisibility(View.GONE);
            }
            date.setText(TextUtils.timeAgo(job.publishedAt != null && !job.publishedAt.isEmpty()
                    ? job.publishedAt : job.createdAt));
            ring.setVisibility(job.matchScore > 0 ? View.VISIBLE : View.GONE);
            ring.setPercent(job.matchScore, false);
            bindSaveButton(saveBtn, job);
            bindReactions(itemView, job);
            bindClick(itemView, job);
            bindClick(btnView, job);
            if (position > lastAnimatedPosition) {
                lastAnimatedPosition = position;
                AnimUtils.fadeSlideIn(itemView, Math.min(position, 6) * 40L);
            }
        }
    }
}
