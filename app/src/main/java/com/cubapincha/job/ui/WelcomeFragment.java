package com.cubapincha.job.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.cubapincha.job.R;
import com.cubapincha.job.data.SessionManager;
import com.cubapincha.job.util.AnimUtils;

/**
 * Selección de rol (prompt maestro 2.1, §5): isotipo, nombre, título,
 * subtítulo, tarjeta de trabajador, ruta de conexión, tarjeta de empresa,
 * inicio de sesión y "Ver cómo funciona". Al tocar una tarjeta: háptica
 * ligera, la ruta se ilumina hacia el destino y la navegación ocurre al
 * completar la pulsación elegante (escala 0.97, 120 ms).
 */
public class WelcomeFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_welcome, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainActivity activity = (MainActivity) requireActivity();
        SessionManager session = SessionManager.get(requireContext());
        View route = view.findViewById(R.id.route_connector);

        AnimUtils.staggerIn(
                view.findViewById(R.id.logo),
                view.findViewById(R.id.app_name),
                view.findViewById(R.id.title),
                view.findViewById(R.id.subtitle));
        // Las tarjetas entran con muelle suave; la ruta se dibuja de arriba
        // abajo entre ellas (atenuada a 0.6, se ilumina al elegir un rol).
        AnimUtils.springIn(view.findViewById(R.id.card_worker), 240);
        AnimUtils.drawDown(route, 420, 0.6f);
        AnimUtils.springIn(view.findViewById(R.id.card_company), 480);
        AnimUtils.fadeSlideIn(view.findViewById(R.id.btn_login), 560);
        AnimUtils.fadeSlideIn(view.findViewById(R.id.btn_how), 620);

        view.findViewById(R.id.card_worker).setOnClickListener(v -> {
            AnimUtils.hapticTick(v);
            lightRoute(route);
            AnimUtils.elegantPress(v, () -> {
                session.setPendingRole("worker");
                activity.showFragment(new RegisterWorkerFragment(), true);
            });
        });
        view.findViewById(R.id.card_company).setOnClickListener(v -> {
            AnimUtils.hapticTick(v);
            lightRoute(route);
            AnimUtils.elegantPress(v, () -> {
                session.setPendingRole("company");
                activity.showFragment(new RegisterCompanyFragment(), true);
            });
        });
        view.findViewById(R.id.btn_login).setOnClickListener(v ->
                AnimUtils.elegantPress(v, () ->
                        activity.showFragment(new LoginFragment(), true)));
        view.findViewById(R.id.btn_how).setOnClickListener(v -> {
            if (getParentFragmentManager().findFragmentByTag("how") == null) {
                new HowItWorksFragment().show(getParentFragmentManager(), "how");
            }
        });
    }

    /** La ruta se ilumina desde la tarjeta hacia el siguiente destino. */
    private void lightRoute(View route) {
        if (route == null) return;
        route.animate().cancel();
        route.animate().alpha(1f).setDuration(AnimUtils.DURATION_FAST)
                .setInterpolator(AnimUtils.EASE).start();
    }
}
