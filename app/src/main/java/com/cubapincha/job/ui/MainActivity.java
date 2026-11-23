package com.cubapincha.job.ui;

import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.cubapincha.job.R;
import com.cubapincha.job.data.Repository;
import com.cubapincha.job.data.SessionManager;
import com.cubapincha.job.data.SyncScheduler;
import com.cubapincha.job.util.AnimUtils;
import com.cubapincha.job.util.CrashHandler;
import com.cubapincha.job.util.NetworkUtil;
import com.cubapincha.job.util.PushManager;

/**
 * Actividad única: gestiona la navegación por rol, la barra inferior,
 * el banner de conexión y la sincronización al recuperar la red.
 */
public class MainActivity extends AppCompatActivity {
    public static final String ACTION_NOTIFICATIONS_CHANGED =
            "com.cubapincha.job.NOTIFICATIONS_CHANGED";
    private BottomNavigationView bottomNav;
    private LinearLayout offlineBanner;
    private ImageView offlineBannerIcon;
    private TextView offlineBannerText;
    private final Handler bannerHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideBanner = () -> offlineBanner.setVisibility(View.GONE);
    private Repository repository;
    private SessionManager session;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean wasOffline = false;
    /** Filtro pendiente para Buscar (chip de categoría en Inicio, búsqueda guardada). */
    private com.cubapincha.job.data.JobFilter pendingSearchFilter;
    /** Oferta de un enlace compartido, pendiente hasta entrar al menú principal. */
    private String pendingDeepLinkJobId;
    /** Navegaciones que llegaron con el estado ya guardado (p. ej. un
     *  callback de red con la app en segundo plano): se ejecutan en onResume. */
    private final Queue<Runnable> pendingNavigation = new ArrayDeque<>();
    /** Insignia de no leídas en caché (evita una llamada de red por recreación). */
    private int cachedUnreadCount = -1;
    private long lastBadgeFetchMs = 0;
    private final BroadcastReceiver notificationReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            cachedUnreadCount = -1;
            lastBadgeFetchMs = 0;
            updateNotificationBadge();
        }
    };
    /** Diálogo del informe de crash (para cerrarlo si la actividad muere). */
    private AlertDialog crashDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        // Si la app se cerró la vez anterior, mostrar el error guardado para
        // poder compartirlo y corregirlo.
        if (savedInstanceState == null && CrashHandler.hasReport(this)) {
            showCrashReport();
        }

        repository = Repository.get(this);
        session = SessionManager.get(this);
        if (savedInstanceState != null) {
            pendingDeepLinkJobId = savedInstanceState.getString("pending_deep_link_job_id");
        }

        bottomNav = findViewById(R.id.bottom_nav);
        offlineBanner = findViewById(R.id.offline_banner);
        offlineBannerIcon = findViewById(R.id.offline_banner_icon);
        offlineBannerText = findViewById(R.id.offline_banner_text);
        ContextCompat.registerReceiver(this, notificationReceiver,
                new IntentFilter(ACTION_NOTIFICATIONS_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment current = currentFragment();
            if (id == R.id.nav_home && !(current instanceof HomeFragment)) {
                showTab(new HomeFragment());
            } else if (id == R.id.nav_search && !(current instanceof SearchFragment)) {
                showTab(new SearchFragment());
            } else if (id == R.id.nav_interests && !(current instanceof InterestsFragment)) {
                showTab(new InterestsFragment());
            } else if (id == R.id.nav_notifications && !(current instanceof NotificationsFragment)) {
                showTab(new NotificationsFragment());
            } else if (id == R.id.nav_profile && !(current instanceof ProfileFragment)) {
                showTab(new ProfileFragment());
            } else if (id == R.id.nav_dashboard && !(current instanceof DashboardFragment)) {
                showTab(new DashboardFragment());
            } else if (id == R.id.nav_jobs && !(current instanceof MyJobsFragment)) {
                showTab(new MyJobsFragment());
            } else if (id == R.id.nav_create && !(current instanceof CreateJobFragment)) {
                showTab(CreateJobFragment.newInstance(null));
            } else if (id == R.id.nav_candidates && !(current instanceof CandidatesFragment)) {
                showTab(CandidatesFragment.newInstance(null));
            } else {
                return false;
            }
            // Microanimación del icono seleccionado.
            View icon = bottomNav.findViewById(id);
            if (icon != null) AnimUtils.pop(icon);
            return true;
        });

        getSupportFragmentManager().addOnBackStackChangedListener(this::updateBottomNavVisibility);

        if (savedInstanceState == null) {
            showFragment(new SplashFragment(), false);
        } else if (session.isLoggedIn()) {
            // Recreación (p. ej. cambio de tema claro/oscuro): el FragmentManager
            // restaura el fragmento, pero la barra inferior NO se restaura sola:
            // el layout la trae oculta (visibility=gone) y con el menú de
            // trabajador por defecto. Sin esto la app se quedaba "sin menú"
            // hasta salir y volver a entrar. Se infla el menú del rol, se marca
            // la pestaña actual y se muestra la barra.
            restoreBottomNav();
        }
        updateOfflineBanner(NetworkUtil.isOnline(this));
        networkCallback = NetworkUtil.registerCallback(this, online -> runOnUiThread(() -> {
            updateOfflineBanner(online);
            if (online && wasOffline) {
                wasOffline = false;
                // Banner de reconexión con sync; el resultado se muestra por
                // acción (resumen real), nunca un éxito global falso.
                offlineBannerIcon.setImageResource(R.drawable.ic_sync);
                offlineBannerText.setText(R.string.online_banner);
                offlineBanner.setVisibility(View.VISIBLE);
                // Ahorro de datos: la sincronización es manual, nunca automática.
                if (!session.isDataSaver()) {
                    repository.syncNow(result -> {
                        if (isFinishing() || isDestroyed()) return;
                        bannerHandler.removeCallbacks(hideBanner);
                        offlineBanner.setVisibility(View.GONE);
                        if (!result.ok) {
                            Snackbar.make(findViewById(R.id.fragment_container),
                                    R.string.sync_failed, Snackbar.LENGTH_LONG).show();
                        } else if (repository.pendingCount() > 0
                                || (result.data != null && !result.data.contains("Revisar: 0"))) {
                            Snackbar.make(findViewById(R.id.fragment_container),
                                    result.data != null ? result.data : getString(R.string.synced),
                                    Snackbar.LENGTH_LONG).show();
                        }
                    });
                } else {
                    bannerHandler.postDelayed(hideBanner, 2500L);
                }
            } else if (!online) {
                wasOffline = true;
            }
        }));
        // Callback Auth PKCE o enlace compartido (arranque en frío).
        handleSupabaseAuthCallback(getIntent());
        handleDeepLink(getIntent());
        handlePushRoute(getIntent());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // El enlace pendiente no es un campo plano: si el proceso muere
        // antes de entrar, se perdería.
        if (pendingDeepLinkJobId != null) {
            outState.putString("pending_deep_link_job_id", pendingDeepLinkJobId);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleSupabaseAuthCallback(intent);
        handleDeepLink(intent);
        handlePushRoute(intent);
    }

    /** Ruta de una notificación push ("job/{id}"): abre la ficha igual que
     *  un enlace compartido, cuando la sesión ya está dentro. */
    private void handlePushRoute(Intent intent) {
        if (intent == null) return;
        String route = intent.getStringExtra(
                com.cubapincha.job.util.CubaPinchaMessagingService.EXTRA_PUSH_ROUTE);
        if (route == null || !route.startsWith("job/")) return;
        String jobId = route.substring(4);
        if (jobId.isEmpty()) return;
        pendingDeepLinkJobId = jobId;
        openPendingDeepLink();
    }

    /** Callback nativo PKCE de Supabase (confirmación de correo y recovery). */
    private void handleSupabaseAuthCallback(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction()) || intent.getData() == null) return;
        android.net.Uri uri = intent.getData();
        if (!"cubapincha".equals(uri.getScheme()) || !"auth".equals(uri.getHost())
                || !"/callback".equals(uri.getPath())) return;
        String code = uri.getQueryParameter("code");
        String type = uri.getQueryParameter("type");
        String error = uri.getQueryParameter("error_description");
        if (error == null || error.isEmpty()) error = uri.getQueryParameter("error");
        if (error != null && !error.isEmpty()) {
            session.clearPkceVerifier();
            showFragment(new LoginFragment(), false);
            Snackbar.make(findViewById(R.id.fragment_container), "No se pudo completar la verificación. Solicita un enlace nuevo.", Snackbar.LENGTH_LONG).show();
            return;
        }
        if (code == null || code.isEmpty()) {
            // Solo se aceptan códigos PKCE. No aceptar access_token/refresh_token
            // suministrados por otra app mediante un deep link explícito.
            session.clearPkceVerifier();
            showFragment(new LoginFragment(), false);
            Snackbar.make(findViewById(R.id.fragment_container), "El enlace no contiene un código seguro válido. Solicita uno nuevo.", Snackbar.LENGTH_LONG).show();
            return;
        }
        final String callbackType = type == null ? "" : type;
        repository.exchangeAuthCode(code, result -> {
            if (isFinishing() || isDestroyed()) return;
            if (!result.ok || result.data == null) {
                showFragment(new LoginFragment(), false);
                Snackbar.make(findViewById(R.id.fragment_container), result.friendlyMessage(), Snackbar.LENGTH_LONG).show();
                return;
            }
            continueAuthCallback(callbackType);
        });
    }

    private void continueAuthCallback(String type) {
        if ("recovery".equalsIgnoreCase(type)) {
            showPasswordResetDialog();
            return;
        }
        repository.completePendingRegistration(result -> {
            if (isFinishing() || isDestroyed()) return;
            if (result.ok && result.data != null) {
                session.setAuthExpired(false);
                enterMain();
            } else {
                showFragment(new LoginFragment(), false);
                String message = result.errorMessage != null && !result.errorMessage.isEmpty()
                        ? result.errorMessage : "Correo confirmado. Ya puedes iniciar sesión para continuar.";
                Snackbar.make(findViewById(R.id.fragment_container), message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void showPasswordResetDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Nueva contraseña (mínimo 6 caracteres)");
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Restablecer contraseña")
                .setMessage("Escribe una contraseña nueva para tu cuenta.")
                .setView(input)
                .setPositiveButton("Guardar", null)
                .setNegativeButton("Ahora no", (cancelDialog, which) -> showFragment(new LoginFragment(), false))
                .setCancelable(false)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    String newPassword = input.getText().toString();
                    if (newPassword.length() < 6) {
                        input.setError("La contraseña debe tener al menos 6 caracteres.");
                        return;
                    }
                    input.setError(null);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    repository.updatePassword(newPassword, result -> {
                        if (isFinishing() || isDestroyed()) return;
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        if (result.ok) {
                            dialog.dismiss();
                            session.logout();
                            showFragment(new LoginFragment(), false);
                            Snackbar.make(findViewById(R.id.fragment_container), "Contraseña actualizada. Inicia sesión.", Snackbar.LENGTH_LONG).show();
                        } else {
                            Snackbar.make(findViewById(R.id.fragment_container), result.friendlyMessage(), Snackbar.LENGTH_LONG).show();
                        }
                    });
                }));
        dialog.show();
    }

    /** Enlace de oferta compartido: cubapincha://oferta/{id} o
     *  https://henrytmr.github.io/cubapincha/oferta.html?id={id}. Si la sesión ya está dentro, abre
     *  la ficha; si no, queda pendiente hasta entrar (enterMain). */
    private void handleDeepLink(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        android.net.Uri uri = intent.getData();
        if (!Intent.ACTION_VIEW.equals(intent.getAction())) return;
        String jobId = null;
        if ("cubapincha".equals(uri.getScheme()) && "oferta".equals(uri.getHost())) {
            jobId = uri.getLastPathSegment();
        } else if ("https".equals(uri.getScheme()) && "henrytmr.github.io".equals(uri.getHost())) {
            // Formato nuevo: /cubapincha/oferta.html?id={jobId}
            String id = uri.getQueryParameter("id");
            if (id != null && !id.isEmpty()) {
                jobId = id;
            } else {
                // Compatibilidad con formato antiguo /ofertas/{id} si alguien lo usa
                List<String> segments = uri.getPathSegments();
                if (segments.size() >= 2 && "ofertas".equals(segments.get(segments.size() - 2))) {
                    jobId = segments.get(segments.size() - 1);
                }
            }
        }
        if (jobId == null || jobId.isEmpty()) return;
        pendingDeepLinkJobId = jobId;
        openPendingDeepLink();
    }

    /** Abre la oferta pendiente del enlace, solo con la sesión dentro y el
     *  menú principal visible (si no, enterMain lo intentará de nuevo). */
    private void openPendingDeepLink() {
        if (pendingDeepLinkJobId == null) return;
        if (!session.isLoggedIn()) return;
        Fragment current = currentFragment();
        if (current == null || !isTabRoot(current)) return;
        String jobId = pendingDeepLinkJobId;
        pendingDeepLinkJobId = null;
        openJobDetail(jobId);
    }

    /** Muestra el error que cerró la app la vez anterior, para compartirlo. */
    private void showCrashReport() {
        String report = CrashHandler.readReport(this);
        if (report.isEmpty()) {
            CrashHandler.clearReport(this);
            return;
        }
        TextView text = new TextView(this);
        text.setText(report);
        text.setTextIsSelectable(true);
        text.setTextSize(11);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        text.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(text);
        crashDialog = new AlertDialog.Builder(this)
                .setTitle("La app se cerró la vez anterior")
                .setMessage("Este es el error exacto. Compártelo con Muse para corregirlo.")
                .setView(scroll)
                .setPositiveButton("Compartir", (d, w) -> {
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("text/plain");
                    share.putExtra(Intent.EXTRA_TEXT, report);
                    startActivity(Intent.createChooser(share, "Compartir el error"));
                    // Al compartir se borra: si no, el informe reaparecía
                    // en cada arranque frío.
                    CrashHandler.clearReport(this);
                })
                .setNegativeButton("Borrar", (d, w) -> CrashHandler.clearReport(this))
                .setCancelable(true)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (session.isLoggedIn()) PushManager.refreshToken(this);
        updateNotificationBadge();
        // Navegaciones que llegaron con la app en segundo plano.
        drainPendingNavigation();
        // Enlace pendiente que llegó con un detalle abierto.
        openPendingDeepLink();
        // La sesión del servidor caducó y no se pudo renovar sola:
        // volver al inicio de sesión con un aviso claro.
        if (session.isAuthExpired()) {
            session.setAuthExpired(false);
            getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            bottomNav.setVisibility(View.GONE);
            showFragment(new WelcomeFragment(), false);
            Snackbar.make(findViewById(R.id.fragment_container),
                    "Tu sesión caducó. Vuelve a iniciar sesión.", Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (crashDialog != null && crashDialog.isShowing()) crashDialog.dismiss();
        bannerHandler.removeCallbacks(hideBanner);
        NetworkUtil.unregisterCallback(this, networkCallback);
        unregisterReceiver(notificationReceiver);
    }

    private void updateOfflineBanner(boolean online) {
        if (online) {
            bannerHandler.removeCallbacks(hideBanner);
            offlineBanner.setVisibility(View.GONE);
            return;
        }
        offlineBannerIcon.setImageResource(R.drawable.ic_cloud_offline);
        offlineBannerText.setText(R.string.offline_banner);
        offlineBanner.setVisibility(View.VISIBLE);
    }

    private Fragment currentFragment() {
        return getSupportFragmentManager().findFragmentById(R.id.fragment_container);
    }

    private boolean isTabRoot(Fragment f) {
        // Crear oferta y Candidatos existen como pestaña Y como detalle:
        // la instancia de detalle (con oferta) NO es raíz de pestaña, así
        // que la barra inferior se oculta sobre ella como en el resto de
        // detalles. Antes instanceof las trataba igual y la barra quedaba
        // visible sobre el detalle.
        if (f instanceof CreateJobFragment) return !((CreateJobFragment) f).isEditing();
        if (f instanceof CandidatesFragment) return !((CandidatesFragment) f).isFilteredByJob();
        return f instanceof HomeFragment || f instanceof SearchFragment
                || f instanceof InterestsFragment || f instanceof NotificationsFragment
                || f instanceof ProfileFragment || f instanceof DashboardFragment
                || f instanceof MyJobsFragment;
    }

    private void updateBottomNavVisibility() {
        Fragment current = currentFragment();
        boolean show = current != null && isTabRoot(current) && session.isLoggedIn();
        bottomNav.setVisibility(show ? View.VISIBLE : View.GONE);
        // Enlace pendiente que llegó con un detalle abierto: al volver a
        // una pestaña raíz se reintenta (antes se tragaba en silencio).
        if (show) openPendingDeepLink();
    }

    /** ¿Se puede hacer commit ahora? Un commit con el estado guardado
     *  (callback de red con la app en segundo plano) tumba la app con
     *  IllegalStateException. */
    private boolean canCommit() {
        return !isFinishing() && !isDestroyed()
                && !getSupportFragmentManager().isStateSaved();
    }

    /** Navegación segura: si no se puede hacer commit ahora, se encola
     *  y se ejecuta en onResume. */
    private void runWhenSafe(Runnable nav) {
        if (canCommit()) {
            nav.run();
        } else {
            pendingNavigation.add(nav);
        }
    }

    private void drainPendingNavigation() {
        while (!pendingNavigation.isEmpty()) {
            Runnable nav = pendingNavigation.poll();
            if (nav != null && canCommit()) nav.run();
        }
    }

    /** Transición de fragmentos CubaPincha: fundido propio de 220/180 ms;
     *  sin animación si el sistema pidió reducir el movimiento. */
    private void applyFragmentAnimations(androidx.fragment.app.FragmentTransaction tx, boolean withPop) {
        if (AnimUtils.reduceMotion(this)) {
            tx.setTransition(androidx.fragment.app.FragmentTransaction.TRANSIT_NONE);
        } else if (withPop) {
            tx.setCustomAnimations(R.anim.fade_in_cp, R.anim.fade_out_cp,
                    R.anim.fade_in_cp, R.anim.fade_out_cp);
        } else {
            tx.setCustomAnimations(R.anim.fade_in_cp, R.anim.fade_out_cp);
        }
    }

    /** Reemplaza el contenido sin añadir a la pila (pantallas de flujo). */
    public void showFragment(Fragment fragment, boolean addToBackStack) {
        runWhenSafe(() -> {
            FragmentManager fm = getSupportFragmentManager();
            androidx.fragment.app.FragmentTransaction tx = fm.beginTransaction();
            applyFragmentAnimations(tx, true);
            tx.replace(R.id.fragment_container, fragment);
            if (addToBackStack) tx.addToBackStack(null);
            tx.commit();
            // No forzar transacciones mientras FragmentManager está ejecutando
            // callbacks/back stack; se actualiza en el siguiente ciclo de UI.
            getWindow().getDecorView().post(this::updateBottomNavVisibility);
        });
    }

    /** Pestaña principal: limpia la pila para que "atrás" salga de la app. */
    public void showTab(Fragment fragment) {
        runWhenSafe(() -> {
            FragmentManager fm = getSupportFragmentManager();
            fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            androidx.fragment.app.FragmentTransaction tx = fm.beginTransaction();
            applyFragmentAnimations(tx, false);
            tx.replace(R.id.fragment_container, fragment);
            tx.commit();
            getWindow().getDecorView().post(this::updateBottomNavVisibility);
        });
    }

    /** Pantalla de detalle apilada sobre la pestaña actual. */
    public void pushFragment(Fragment fragment) {
        runWhenSafe(() -> {
            FragmentManager fm = getSupportFragmentManager();
            androidx.fragment.app.FragmentTransaction tx = fm.beginTransaction();
            if (AnimUtils.reduceMotion(this)) {
                tx.setTransition(androidx.fragment.app.FragmentTransaction.TRANSIT_NONE);
            } else {
                tx.setCustomAnimations(
                        R.anim.slide_in_right, R.anim.slide_out_left,
                        R.anim.slide_in_left, R.anim.slide_out_right);
            }
            tx.replace(R.id.fragment_container, fragment);
            tx.addToBackStack(null);
            tx.commit();
            getWindow().getDecorView().post(this::updateBottomNavVisibility);
        });
    }

    /** Infla el menú inferior según el rol de la sesión (trabajador/empresa). */
    private void inflateBottomNavMenu() {
        boolean company = "company".equals(session.getRole());
        bottomNav.getMenu().clear();
        bottomNav.inflateMenu(company ? R.menu.menu_bottom_company : R.menu.menu_bottom_worker);
    }

    /** Marca la pestaña del fragmento actual sin disparar el listener. */
    private void syncCheckedTab() {
        Fragment current = currentFragment();
        int itemId = -1;
        if (current instanceof HomeFragment) itemId = R.id.nav_home;
        else if (current instanceof SearchFragment) itemId = R.id.nav_search;
        else if (current instanceof InterestsFragment) itemId = R.id.nav_interests;
        else if (current instanceof NotificationsFragment) itemId = R.id.nav_notifications;
        else if (current instanceof ProfileFragment) itemId = R.id.nav_profile;
        else if (current instanceof DashboardFragment) itemId = R.id.nav_dashboard;
        else if (current instanceof MyJobsFragment) itemId = R.id.nav_jobs;
        else if (current instanceof CreateJobFragment && !((CreateJobFragment) current).isEditing()) itemId = R.id.nav_create;
        else if (current instanceof CandidatesFragment && !((CandidatesFragment) current).isFilteredByJob()) itemId = R.id.nav_candidates;
        if (itemId != -1 && bottomNav.getMenu().findItem(itemId) != null) {
            bottomNav.getMenu().findItem(itemId).setChecked(true);
        }
    }

    /** Restaura la barra inferior tras una recreación (cambio de tema,
     *  rotación con proceso muerto): menú del rol, pestaña actual marcada,
     *  visibilidad correcta e insignia de notificaciones. */
    private void restoreBottomNav() {
        inflateBottomNavMenu();
        syncCheckedTab();
        updateBottomNavVisibility();
        updateNotificationBadge();
    }

    /** Entrada al shell principal según el rol, con el menú correcto. */
    public void enterMain() {
        // Se llama desde el callback de login: si la app está en segundo
        // plano, la navegación espera a onResume (si no, crash).
        runWhenSafe(() -> {
            getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            boolean company = "company".equals(session.getRole());
            inflateBottomNavMenu();
            // Marcar el destino sin disparar el listener: showTab lo crea una sola vez.
            // (Antes, setSelectedItemId + showTab creaban dos instancias del fragmento inicial.)
            bottomNav.getMenu().findItem(company ? R.id.nav_dashboard : R.id.nav_home).setChecked(true);
            showTab(company ? new DashboardFragment() : new HomeFragment());
            updateNotificationBadge();
            // Al entrar, sincronizar en segundo plano para que las listas
            // se llenen solas (no-op en demo o con ahorro de datos).
            SyncScheduler.syncSoon(this);
            // Enlace compartido recibido antes de entrar: abrir su ficha ahora.
            openPendingDeepLink();
        });
    }

    public void selectTab(int itemId) {
        bottomNav.setSelectedItemId(itemId);
    }

    /** Abre Buscar con un filtro ya aplicado (categoría o búsqueda guardada),
     *  sincronizando la barra inferior. Si Buscar ya está abierto, aplica el
     *  filtro directamente. */
    public void openSearchWithFilter(com.cubapincha.job.data.JobFilter filter) {
        Fragment current = currentFragment();
        if (current instanceof SearchFragment) {
            ((SearchFragment) current).applyFilter(filter);
            return;
        }
        pendingSearchFilter = filter;
        selectTab(R.id.nav_search);
    }

    /** Filtro pendiente para Buscar; se consume una sola vez. */
    public com.cubapincha.job.data.JobFilter takePendingSearchFilter() {
        com.cubapincha.job.data.JobFilter f = pendingSearchFilter;
        pendingSearchFilter = null;
        return f;
    }

    public void openJobDetail(String jobId) {
        pushFragment(JobDetailFragment.newInstance(jobId));
    }

    /** Abre el detalle con la oferta ya en mano: se muestra al instante y se
     *  refresca en segundo plano. La tarjeta nunca depende de la red para abrir. */
    public void openJobDetail(com.cubapincha.job.model.Job job) {
        pushFragment(JobDetailFragment.newInstance(job));
    }

    public void openSavedJobs() {
        pushFragment(new SavedJobsFragment());
    }

    public void openEditProfile() {
        pushFragment(new EditProfileFragment());
    }

    public void openCreateJob(String jobIdToEdit) {
        pushFragment(CreateJobFragment.newInstance(jobIdToEdit));
    }

    public void openCandidates(String jobId) {
        pushFragment(CandidatesFragment.newInstance(jobId));
    }

    public void logout() {
        // Repository revoca la sesión en el servidor y limpia todo lo local.
        repository.logout(result -> runWhenSafe(() -> {
            getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            bottomNav.setVisibility(View.GONE);
            showFragment(new WelcomeFragment(), false);
        }));
    }

    public void updateNotificationBadge() {
        if (!session.isLoggedIn()) return;
        long now = System.currentTimeMillis();
        // Caché de 30 s: antes cada recreación (rotación, tema) hacía una
        // llamada de red para lo mismo.
        if (cachedUnreadCount >= 0 && now - lastBadgeFetchMs < 30000) {
            applyBadgeCount(cachedUnreadCount);
            return;
        }
        repository.getNotifications(true, result -> {
            if (isFinishing() || isDestroyed()) return;
            if (result.ok && result.data != null) {
                cachedUnreadCount = result.data.unreadCount;
                lastBadgeFetchMs = System.currentTimeMillis();
                applyBadgeCount(cachedUnreadCount);
            }
        });
    }

    private void applyBadgeCount(int unread) {
        Fragment current = currentFragment();
        if (current instanceof DashboardFragment || current instanceof ProfileFragment) {
            View dot = current.getView() != null
                    ? current.getView().findViewById(R.id.company_notification_dot) : null;
            if (dot != null) dot.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
        }
        if (bottomNav.getMenu().findItem(R.id.nav_notifications) != null) {
            if (unread > 0) {
                com.google.android.material.badge.BadgeDrawable badge =
                        bottomNav.getOrCreateBadge(R.id.nav_notifications);
                badge.clearNumber();
                badge.setVisible(true);
                badge.setBackgroundColor(ContextCompat.getColor(this, R.color.error_red));
            } else {
                bottomNav.removeBadge(R.id.nav_notifications);
            }
        }
    }
}
