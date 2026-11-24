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
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.cubapincha.job.R;
import com.cubapincha.job.ui.UiUtils;
import com.cubapincha.job.model.Application;
import com.cubapincha.job.util.AnimUtils;
import com.cubapincha.job.util.TextUtils;

import java.util.ArrayList;
import java.util.List;

/** Candidaturas del trabajador con su estado y acceso al seguimiento. */
public class ApplicationAdapter extends RecyclerView.Adapter<ApplicationAdapter.Holder> {
    public interface Listener {
        void onApplicationClick(Application app);
        void onTrackingClick(Application app);
        void onRateClick(Application app);
        default void onSwipeWithdraw(Application app) { }
    }

    private final List<Application> items = new ArrayList<>();
    private final Listener listener;
    private int lastAnimatedPosition = -1;

    public ApplicationAdapter(Listener listener) {
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
                .inflate(R.layout.item_application, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        AnimUtils.resetItemState(h.itemView);
        Application app = items.get(position);
        h.title.setText(app.jobTitle);
        h.company.setText(app.companyName + " · " + app.jobLocationText());
        h.status.setText(Application.statusLabel(app.status));
        int color;
        String status = app.status != null ? app.status : "";
        switch (status) {
            case "hired":
                color = R.color.confirm_green;
                break;
            case "interview":
                color = R.color.brand_primary;
                break;
            case "rejected":
            case "withdrawn":
                color = R.color.error_red;
                break;
            default:
                color = R.color.night_blue;
        }
        h.status.setTextColor(ContextCompat.getColor(h.itemView.getContext(), color));
        h.date.setText(TextUtils.timeAgo(app.createdAt));
        UiUtils.setDebouncedClickListener(h.itemView, v -> {
            if (listener != null) listener.onApplicationClick(app);
        });
        UiUtils.setDebouncedClickListener(h.tracking, v -> {
            if (listener != null) listener.onTrackingClick(app);
        });
        h.rate.setVisibility("hired".equals(app.status) ? View.VISIBLE : View.GONE);
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

    @Override
    public int getItemCount() {
        return items.size();
    }
    public void remove(Application app) { int i = items.indexOf(app); if (i >= 0) { items.remove(i); notifyItemRemoved(i); } }

    static class Holder extends RecyclerView.ViewHolder {
        TextView title, company, status, date, tracking, rate;

        Holder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.job_title);
            company = itemView.findViewById(R.id.company_name);
            status = itemView.findViewById(R.id.status_chip);
            date = itemView.findViewById(R.id.date_text);
            tracking = itemView.findViewById(R.id.btn_tracking);
            rate = itemView.findViewById(R.id.btn_rate_work);
        }
    }
}
