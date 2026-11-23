package com.cubapincha.job.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cubapincha.job.R;
import com.cubapincha.job.ui.UiUtils;
import com.cubapincha.job.model.AppNotification;
import com.cubapincha.job.util.AnimUtils;
import com.cubapincha.job.util.TextUtils;

import java.util.ArrayList;
import java.util.List;

/** Notificaciones del centro, con punto de "no leída". */
public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.Holder> {
    public interface Listener {
        void onNotificationClick(AppNotification n);
    }

    private final List<AppNotification> items = new ArrayList<>();
    private final Listener listener;
    private int lastAnimatedPosition = -1;

    public NotificationAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<AppNotification> list) {
        items.clear();
        if (list != null) items.addAll(list);
        lastAnimatedPosition = -1;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        AnimUtils.resetItemState(h.itemView);
        AppNotification n = items.get(position);
        h.title.setText(n.title);
        h.body.setText(n.body);
        h.date.setText(TextUtils.timeAgo(n.createdAt));
        h.dot.setVisibility(n.read ? View.GONE : View.VISIBLE);
        int icon;
        String type = n.type != null ? n.type : "";
        switch (type) {
            case "worker_interested":
            case "application_status_changed":
                icon = R.drawable.ic_users;
                break;
            case "interview_invitation":
                icon = R.drawable.ic_calendar;
                break;
            case "offer_expiring":
                icon = R.drawable.ic_clock;
                break;
            case "security_alert":
                icon = R.drawable.ic_alert;
                break;
            default:
                icon = R.drawable.ic_bell;
        }
        h.icon.setImageResource(icon);
        float targetAlpha = n.read ? 0.75f : 1f;
        h.itemView.setAlpha(targetAlpha);
        UiUtils.setDebouncedClickListener(h.itemView, v -> {
            if (listener != null) listener.onNotificationClick(n);
        });
        int animPos = h.getBindingAdapterPosition();
        if (animPos != RecyclerView.NO_POSITION && animPos > lastAnimatedPosition) {
            lastAnimatedPosition = animPos;
            // La animación termina en el alpha que corresponde (leída: 0.75).
            AnimUtils.fadeSlideIn(h.itemView, Math.min(animPos, 6) * 40L, targetAlpha);
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
        ImageView icon;
        TextView title, body, date;
        View dot;

        Holder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.type_icon);
            title = itemView.findViewById(R.id.notif_title);
            body = itemView.findViewById(R.id.notif_body);
            date = itemView.findViewById(R.id.notif_date);
            dot = itemView.findViewById(R.id.unread_dot);
        }
    }
}
