package com.cubapincha.job.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.cubapincha.job.R;
import com.cubapincha.job.adapter.NotificationAdapter;
import com.cubapincha.job.data.Repository;
import com.cubapincha.job.data.SessionManager;
import com.cubapincha.job.model.AppNotification;
import com.cubapincha.job.util.AnimUtils;

import java.util.List;

/** Centro de notificaciones. */
public class NotificationsFragment extends Fragment implements NotificationAdapter.Listener {
    private NotificationAdapter adapter;
    private LinearLayout skeletonContainer, emptyState, errorState;
    private TextView errorBody;
    private RecyclerView list;
    private List<AppNotification> shown;
    // Vigilante del esqueleto: si la red no responde en 15 s, el esqueleto
    // se esconde y aparece el estado de reintento (nunca se queda colgado).
    private final Handler watchdogHandler = new Handler(Looper.getMainLooper());
    private Runnable watchdog;
    private boolean requestDone;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notifications, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        list = view.findViewById(R.id.list_notifications);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new NotificationAdapter(this);
        list.setAdapter(adapter);
        skeletonContainer = view.findViewById(R.id.skeleton_container);
        emptyState = view.findViewById(R.id.empty_state);
        errorState = view.findViewById(R.id.error_state);
        errorBody = view.findViewById(R.id.error_body);
        view.findViewById(R.id.btn_retry).setOnClickListener(v -> loadNotifications());
        SwipeRefreshLayout refresh = view.findViewById(R.id.swipe_refresh);
        refresh.setOnRefreshListener(() -> { loadNotifications(); refresh.setRefreshing(false); });
        view.findViewById(R.id.btn_explore).setOnClickListener(v ->
                ((MainActivity) requireActivity()).selectTab(R.id.nav_home));

        ImageView bell = view.findViewById(R.id.bell_icon);
        bell.postDelayed(() -> AnimUtils.playAnimated(bell), 300);

        view.findViewById(R.id.btn_mark_all).setOnClickListener(v -> {
            Repository.get(requireContext()).markAllNotificationsRead(result -> {
                if (isAdded()) {
                    loadNotifications();
                    ((MainActivity) requireActivity()).updateNotificationBadge();
                }
            });
        });
        View btnPrefs = view.findViewById(R.id.btn_prefs);
        updatePrefsButton(btnPrefs);
        btnPrefs.setOnClickListener(v -> {
            boolean enabled = SessionManager.get(requireContext()).isNotificationsEnabled();
            SessionManager.get(requireContext()).setNotificationsEnabled(!enabled);
            updatePrefsButton(v);
            Snackbar.make(view, !enabled ? R.string.notifications_on : R.string.notifications_off,
                    Snackbar.LENGTH_SHORT).show();
        });
        loadFromCacheOrNetwork();
    }

    /** Pinta primero lo guardado en el teléfono (al instante, sin
     *  esqueleto) y refresca en segundo plano; solo si no hay nada
     *  guardado se espera a la red con el esqueleto. */
    private void loadFromCacheOrNetwork() {
        List<AppNotification> cached = Repository.get(requireContext()).getCachedNotifications();
        if (!cached.isEmpty()) {
            shown = cached;
            list.setVisibility(View.VISIBLE);
            adapter.setItems(cached);
            loadNotificationsQuiet();
        } else {
            loadNotifications();
        }
    }

    /** Refresco silencioso: solo toca la lista si algo cambió. */
    private void loadNotificationsQuiet() {
        Repository.get(requireContext()).getNotifications(false, result -> {
            if (!isAdded() || !result.ok || result.data == null) return;
            List<AppNotification> items = result.data.items;
            if (items.isEmpty()) {
                shown = items;
                list.setVisibility(View.GONE);
                emptyState.setVisibility(View.VISIBLE);
                return;
            }
            if (!sameSignature(items, shown)) {
                shown = items;
                list.setVisibility(View.VISIBLE);
                emptyState.setVisibility(View.GONE);
                adapter.setItems(items);
            }
        });
    }

    private static boolean sameSignature(List<AppNotification> a, List<AppNotification> b) {
        if (a == null || b == null) return a == b;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            AppNotification x = a.get(i), y = b.get(i);
            if (!String.valueOf(x.notificationId).equals(String.valueOf(y.notificationId))
                    || x.read != y.read) {
                return false;
            }
        }
        return true;
    }

    /** El botón refleja el estado: atenuado cuando las notificaciones están apagadas. */
    private void updatePrefsButton(View btnPrefs) {
        boolean enabled = SessionManager.get(requireContext()).isNotificationsEnabled();
        btnPrefs.setAlpha(enabled ? 1f : 0.4f);
        btnPrefs.setContentDescription(getString(enabled ? R.string.notifications_on : R.string.notifications_off));
    }

    private void loadNotifications() {
        skeletonContainer.setVisibility(View.VISIBLE);
        AnimUtils.pulse(skeletonContainer);
        emptyState.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);
        list.setVisibility(View.GONE);
        requestDone = false;
        if (watchdog != null) watchdogHandler.removeCallbacks(watchdog);
        watchdog = () -> {
            if (requestDone || !isAdded()) return;
            // La red no respondió a tiempo: esconder el esqueleto y ofrecer
            // reintentar. Si la respuesta llega después, pinta igual.
            AnimUtils.stopPulse(skeletonContainer);
            skeletonContainer.setVisibility(View.GONE);
            errorBody.setText(R.string.connection_slow);
            errorState.setVisibility(View.VISIBLE);
        };
        watchdogHandler.postDelayed(watchdog, 15000);
        Repository.get(requireContext()).getNotifications(false, result -> {
            requestDone = true;
            if (watchdog != null) watchdogHandler.removeCallbacks(watchdog);
            AnimUtils.stopPulse(skeletonContainer);
            skeletonContainer.setVisibility(View.GONE);
            if (!isAdded()) return;
            if (result.ok && result.data != null) {
                errorState.setVisibility(View.GONE);
                shown = result.data.items;
                if (result.data.items.isEmpty()) {
                    emptyState.setVisibility(View.VISIBLE);
                } else {
                    list.setVisibility(View.VISIBLE);
                    adapter.setItems(result.data.items);
                }
            } else {
                // Un fallo no es "sin notificaciones": estado de error con reintento.
                errorBody.setText(result.friendlyMessage());
                errorState.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onNotificationClick(AppNotification n) {
        Repository.get(requireContext()).markNotificationRead(n.notificationId, result -> {
            if (isAdded()) {
                ((MainActivity) requireActivity()).updateNotificationBadge();
                loadNotifications();
            }
        });
        if (n.route != null && n.route.startsWith("job/")) {
            ((MainActivity) requireActivity()).openJobDetail(n.route.substring(4));
        } else if ("profile".equals(n.route)) {
            ((MainActivity) requireActivity()).openEditProfile();
        } else if (n.route != null && n.route.startsWith("application/")) {
            ((MainActivity) requireActivity()).selectTab(R.id.nav_interests);
        } else if (n.route != null && n.route.startsWith("candidate/")) {
            // Notificación de la empresa sobre un candidato: abre Candidatos.
            if ("company".equals(SessionManager.get(requireContext()).getRole())) {
                ((MainActivity) requireActivity()).selectTab(R.id.nav_candidates);
            }
        }
    }

    @Override
    public void onDestroyView() {
        // Detener el pulso infinito del skeleton al destruir la vista.
        if (watchdog != null) watchdogHandler.removeCallbacks(watchdog);
        AnimUtils.stopPulse(skeletonContainer);
        super.onDestroyView();
    }
}
