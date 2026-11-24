package com.cubapincha.job.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.cubapincha.job.R;
import com.cubapincha.job.data.Repository;
import com.cubapincha.job.data.SessionManager;
import com.cubapincha.job.model.RatingsPage;
import com.cubapincha.job.util.PushManager;
import com.cubapincha.job.util.TextUtils;

/** Perfil y ajustes: modo demo/conectado, URL de la API, tema, sincronización. */
public class ProfileFragment extends Fragment {
    private TextView syncStatus, pushStatus;
    private RatingStarsView ratingsStars;
    private TextView ratingsSummary, ratingsEmpty, ratingsError;
    private LinearLayout ratingsList;
    private MaterialButton ratingsMore;
    private String ratingsUserId = "";
    private String ratingsCursor = "0";
    private boolean ratingsLoading;
    private SwitchMaterial notifSwitch;
    private ActivityResultLauncher<String> requestNotifications;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Permiso de notificaciones (Android 13+): se pide al activar el
        // interruptor; si se niega, el interruptor vuelve a apagado.
        requestNotifications = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    SessionManager session = SessionManager.get(requireContext());
                    if (Boolean.TRUE.equals(granted)) {
                        session.setNotificationsEnabled(true);
                    } else {
                        session.setNotificationsEnabled(false);
                        if (notifSwitch != null) {
                            notifSwitch.setOnCheckedChangeListener(null);
                            notifSwitch.setChecked(false);
                            notifSwitch.setOnCheckedChangeListener(this::onNotifToggled);
                        }
                        if (isAdded()) {
                            Snackbar.make(requireView(), R.string.notifications_permission_denied,
                                    Snackbar.LENGTH_LONG).show();
                        }
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainActivity activity = (MainActivity) requireActivity();
        SessionManager session = SessionManager.get(requireContext());
        Repository repo = Repository.get(requireContext());

        TextView avatar = view.findViewById(R.id.avatar);
        TextView displayName = view.findViewById(R.id.display_name);
        TextView emailMasked = view.findViewById(R.id.email_masked);
        TextView roleStatus = view.findViewById(R.id.role_status);
        avatar.setText(TextUtils.initials(session.getDisplayName()));
        // Foto de perfil / logotipo, si se eligió una en el registro.
        ImageView avatarPhoto = view.findViewById(R.id.avatar_photo);
        String photoUri = session.getPhotoUri();
        if (photoUri != null && !photoUri.isEmpty()) {
            try {
                avatarPhoto.setImageURI(Uri.parse(photoUri));
                avatarPhoto.setVisibility(View.VISIBLE);
                avatar.setVisibility(View.GONE);
            } catch (Exception ignored) { }
        }
        displayName.setText(session.getDisplayName());
        emailMasked.setText(session.getUserId().isEmpty() ? "" : "Cuenta CubaPincha");
        boolean company = "company".equals(session.getRole());
        roleStatus.setText(company ? "Empresa · " + statusLabel(session.getStatus())
                : "Trabajador · " + statusLabel(session.getStatus()));
        View companyBell = view.findViewById(R.id.btn_company_notifications);
        if (companyBell != null) companyBell.setVisibility(View.GONE);
        if (companyBell != null) {
            companyBell.setVisibility(View.GONE);
        }

        ratingsUserId = session.getUserId();
        ratingsStars = view.findViewById(R.id.ratings_stars);
        ratingsSummary = view.findViewById(R.id.ratings_summary);
        ratingsEmpty = view.findViewById(R.id.ratings_empty);
        ratingsError = view.findViewById(R.id.ratings_error);
        ratingsList = view.findViewById(R.id.ratings_list);
        ratingsMore = view.findViewById(R.id.ratings_more);
        ratingsMore.setOnClickListener(v -> loadProfileRatings(true));
        loadProfileRatings(false);

        int percent = session.getProfilePercent();
        TextView percentText = view.findViewById(R.id.profile_percent_text);
        percentText.setText(getString(R.string.profile_percent, percent));
        LinearProgressIndicator progress = view.findViewById(R.id.profile_progress);
        progress.setProgress(percent);
        view.findViewById(R.id.card_profile_progress).setOnClickListener(v -> activity.openEditProfile());
        view.findViewById(R.id.card_saved_jobs).setOnClickListener(v -> activity.openSavedJobs());
        view.findViewById(R.id.card_saved_searches).setOnClickListener(v -> {
            repo.getSavedSearches(result -> {
                if (!isAdded()) return;
                if (result.ok && result.data != null && !result.data.isEmpty()) {
                    java.util.List<com.cubapincha.job.model.SavedSearch> searches = result.data;
                    String[] names = new String[searches.size()];
                    for (int i = 0; i < searches.size(); i++) names[i] = searches.get(i).name;
                    new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.saved_searches)
                            .setItems(names, (d, which) -> {
                                com.cubapincha.job.model.SavedSearch chosen = searches.get(which);
                                new AlertDialog.Builder(requireContext())
                                        .setTitle(chosen.name)
                                        .setMessage("Aplicar esta búsqueda en Buscar, o eliminarla.")
                                        .setPositiveButton("Aplicar", (d2, w2) ->
                                                activity.openSearchWithFilter(chosen.toFilter()))
                                        .setNegativeButton("Eliminar", (d2, w2) ->
                                                repo.deleteSavedSearch(chosen.filterId, del -> {
                                                    if (isAdded()) {
                                                        Snackbar.make(view, del.ok
                                                                ? "Búsqueda eliminada." : del.friendlyMessage(),
                                                                Snackbar.LENGTH_SHORT).show();
                                                    }
                                                }))
                                        .setNeutralButton(R.string.cancel, null)
                                        .show();
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                } else {
                    Snackbar.make(view, "Aún no guardaste ninguna búsqueda.", Snackbar.LENGTH_SHORT).show();
                }
            });
        });

        // La conexión se configura localmente al preparar la build; no hay pantalla de conexión.
        pushStatus = view.findViewById(R.id.push_status);
        updatePushStatus();
        session.setDemoMode(false);

        // Interruptores y tema
        RadioGroup themeGroup = view.findViewById(R.id.theme_group);
        String themeMode = session.getThemeMode();
        ((RadioButton) view.findViewById(R.id.radio_theme_system)).setChecked("system".equals(themeMode));
        ((RadioButton) view.findViewById(R.id.radio_theme_light)).setChecked("light".equals(themeMode));
        ((RadioButton) view.findViewById(R.id.radio_theme_dark)).setChecked("dark".equals(themeMode));
        themeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String mode = checkedId == R.id.radio_theme_light ? "light"
                    : checkedId == R.id.radio_theme_dark ? "dark" : "system";
            session.setThemeMode(mode);
            com.cubapincha.job.CubaPinchaApp.applyThemeMode(mode);
            // Transición de superficies de 220 ms (§9) al recrear.
            requireActivity().overridePendingTransition(R.anim.fade_in_cp, R.anim.fade_out_cp);
        });
        notifSwitch = view.findViewById(R.id.switch_notifications);
        notifSwitch.setChecked(session.isNotificationsEnabled());
        notifSwitch.setOnCheckedChangeListener(this::onNotifToggled);
        SwitchMaterial dataSaverSwitch = view.findViewById(R.id.switch_data_saver);
        dataSaverSwitch.setChecked(session.isDataSaver());
        dataSaverSwitch.setOnCheckedChangeListener((button, checked) -> {
            session.setDataSaver(checked);
            if (checked) {
                Snackbar.make(view, R.string.data_saver_hint, Snackbar.LENGTH_LONG).show();
            }
        });
        SwitchMaterial introSoundSwitch = view.findViewById(R.id.switch_intro_sound);
        introSoundSwitch.setChecked(session.isIntroSoundEnabled());
        introSoundSwitch.setOnCheckedChangeListener((button, checked) ->
                session.setIntroSoundEnabled(checked));

        // Sincronización
        syncStatus = view.findViewById(R.id.sync_status);
        refreshSyncStatus();
        view.findViewById(R.id.btn_sync).setOnClickListener(v -> {
            syncStatus.setText(R.string.loading);
            repo.syncNow(result -> {
                if (!isAdded()) return;
                syncStatus.setText(result.ok ? result.data : result.friendlyMessage());
            });
        });

        view.findViewById(R.id.btn_logout).setOnClickListener(v ->
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.logout)
                        .setMessage(R.string.logout_confirm)
                        .setPositiveButton(R.string.logout, (d, w) -> activity.logout())
                        .setNegativeButton(R.string.cancel, null)
                        .show());
    }

    /** Al activar las notificaciones (Android 13+) se pide el permiso del sistema. */
    private void onNotifToggled(android.widget.CompoundButton button, boolean checked) {
        SessionManager session = SessionManager.get(requireContext());
        if (!checked) {
            session.setNotificationsEnabled(false);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            session.setNotificationsEnabled(true);
        }
    }

    /** Estado del push según los recursos Firebase integrados en la app. */
    private void updatePushStatus() {
        if (pushStatus == null) return;
        boolean configured = PushManager.isConfigured(requireContext());
        pushStatus.setText(configured ? R.string.push_configured : R.string.push_not_configured);
    }

    private void loadProfileRatings(boolean append) {
        if (!isAdded() || ratingsLoading || ratingsList == null) return;
        String cursor = append ? ratingsCursor : "0";
        if (!append) {
            ratingsCursor = "0";
            ratingsList.removeAllViews();
            ratingsEmpty.setVisibility(View.GONE);
            ratingsError.setVisibility(View.GONE);
            ratingsSummary.setText("Cargando valoraciones…");
        }
        ratingsLoading = true;
        ratingsMore.setEnabled(false);
        Repository.get(requireContext()).getRatings(ratingsUserId, 20, cursor, result -> {
            ratingsLoading = false;
            if (!isAdded() || getView() == null) return;
            if (!result.ok || result.data == null) {
                ratingsError.setText(result.friendlyMessage().isEmpty()
                        ? "No se pudieron cargar las valoraciones. Comprueba tu conexión e inténtalo de nuevo."
                        : result.friendlyMessage());
                ratingsError.setVisibility(View.VISIBLE);
                ratingsMore.setText(append ? "Reintentar" : "Cargar valoraciones");
                ratingsMore.setVisibility(View.VISIBLE);
                ratingsMore.setEnabled(true);
                return;
            }
            RatingsPage page = result.data;
            ratingsStars.setRating(Math.max(0, Math.min(5, (int) Math.round(page.average))));
            ratingsSummary.setText(RatingUi.summaryText(page.average, page.total));
            if (!append) ratingsList.removeAllViews();
            RatingUi.appendRatingCards(requireContext(), ratingsList, page.ratings);
            if (page.total == 0 && ratingsList.getChildCount() == 0) {
                ratingsEmpty.setVisibility(View.VISIBLE);
            } else {
                ratingsEmpty.setVisibility(View.GONE);
            }
            ratingsCursor = page.nextCursor == null ? "" : page.nextCursor;
            boolean more = !ratingsCursor.isEmpty();
            ratingsMore.setText("Ver más");
            ratingsMore.setVisibility(more ? View.VISIBLE : View.GONE);
            ratingsMore.setEnabled(more);
        });
    }

    private void updateModeUi(boolean demo) { /* conexión fija: no-op */ }

    private void refreshSyncStatus() {
        if (syncStatus == null) return;
        int pending = Repository.get(requireContext()).pendingCount();
        syncStatus.setText(pending > 0
                ? getString(R.string.sync_summary, 0, pending, 0)
                : getString(R.string.synced));
    }

    private String statusLabel(String status) {
        if (status == null) return "";
        switch (status) {
            case "active": return "Activa";
            case "incomplete": return "Incompleta";
            case "pending_verification": return "En revisión";
            case "suspended": return "Suspendida";
            default: return status;
        }
    }
}
