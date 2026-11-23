package com.cubapincha.job.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ProgressBar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.cubapincha.job.R;
import com.cubapincha.job.data.Repository;
import com.cubapincha.job.data.SessionManager;

/** Inicio de sesión con validaciones amables (correo o Google). */
public class LoginFragment extends Fragment {
    private TextInputEditText emailInput, passwordInput;
    private TextInputLayout emailLayout, passwordLayout;
    private ProgressBar progress;
    private View btnLogin, btnGoogle, btnRegister, btnForgot;
    private GoogleSignInClient googleClient;
    private ActivityResultLauncher<Intent> googleLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        googleLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (!isAdded()) return;
                    setLoading(false);
                    if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
                    Task<GoogleSignInAccount> task =
                            GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        if (account == null || account.getEmail() == null) return;
                        handleGoogleAccount(account);
                    } catch (ApiException e) {
                        if (e.getStatusCode() == 12501) return; // el usuario canceló
                        String msg = e.getStatusCode() == 10
                                ? "La configuración OAuth de esta compilación no coincide. Revisa el cliente web y las huellas SHA registradas."
                                : "No se pudo entrar con Google. Revisa tu conexión.";
                        Snackbar.make(requireView(), msg, Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MainActivity activity = (MainActivity) requireActivity();
        emailInput = view.findViewById(R.id.input_email);
        passwordInput = view.findViewById(R.id.input_password);
        emailLayout = view.findViewById(R.id.layout_email);
        passwordLayout = view.findViewById(R.id.layout_password);
        progress = view.findViewById(R.id.progress);
        btnLogin = view.findViewById(R.id.btn_login);
        CheckBox remember = view.findViewById(R.id.check_remember);

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        btnRegister = view.findViewById(R.id.btn_register);
        btnRegister.setOnClickListener(v -> {
            String role = SessionManager.get(requireContext()).getPendingRole();
            activity.showFragment("company".equals(role)
                    ? new RegisterCompanyFragment() : new RegisterWorkerFragment(), true);
        });
        btnGoogle = view.findViewById(R.id.btn_google);
        btnGoogle.setOnClickListener(v -> startGoogleSignIn());
        btnForgot = view.findViewById(R.id.btn_forgot);
        btnForgot.setOnClickListener(v -> {
            String email = UiUtils.text(emailInput);
            if (!UiUtils.isValidEmail(email)) {
                emailLayout.setError(getString(R.string.invalid_email));
                return;
            }
            emailLayout.setError(null);
            if (Repository.get(requireContext()).isDemoMode()) {
                Snackbar.make(view, "En el modo demo no hay cuentas reales: entra con cualquier correo.",
                        Snackbar.LENGTH_LONG).show();
                return;
            }
            setLoading(true);
            Repository.get(requireContext()).resetPassword(email, result -> {
                if (!isAdded()) return;
                setLoading(false);
                Snackbar.make(requireView(), result.ok
                                ? getString(R.string.recovery_sent)
                                : result.friendlyMessage(), Snackbar.LENGTH_LONG).show();
            });
        });

        btnLogin.setOnClickListener(v -> {
            String email = UiUtils.text(emailInput);
            String password = UiUtils.text(passwordInput);
            emailLayout.setError(null);
            passwordLayout.setError(null);
            boolean valid = true;
            if (!UiUtils.isValidEmail(email)) {
                emailLayout.setError(getString(R.string.invalid_email));
                valid = false;
            }
            if (password.isEmpty()) {
                passwordLayout.setError(getString(R.string.empty_password));
                valid = false;
            }
            if (!valid) return;
            UiUtils.hideKeyboard(view);
            setLoading(true);
            String role = SessionManager.get(requireContext()).getPendingRole();
            Repository.get(requireContext()).login(email, password, role, remember.isChecked(), result -> {
                if (!isAdded()) return;
                setLoading(false);
                if (result.ok && result.data != null) {
                    activity.enterMain();
                } else {
                    Snackbar.make(view, result.friendlyMessage(), Snackbar.LENGTH_LONG).show();
                }
            });
        });
    }

    /** Entrada con Google. En demo no hace falta configurar nada (se crea
     *  una sesión de demostración con el correo de Google). En conectado
     *  se necesita el ID de cliente web configurado al preparar la build. */
    private void startGoogleSignIn() {
        SessionManager session = SessionManager.get(requireContext());
        boolean demo = Repository.get(requireContext()).isDemoMode();
        String clientId = session.getGoogleClientId();
        // google-services.json genera este recurso durante el build. Así el
        // flujo conectado no depende de que una preferencia local exista.
        if (clientId.isEmpty()) {
            int resourceId = getResources().getIdentifier(
                    "default_web_client_id", "string", requireContext().getPackageName());
            if (resourceId != 0) clientId = getString(resourceId);
        }
        if (!demo && clientId.isEmpty()) {
            Snackbar.make(requireView(),
                    "Google Sign-In no está configurado en esta compilación. Pide una versión configurada.",
                    Snackbar.LENGTH_LONG).show();
            return;
        }
        GoogleSignInOptions.Builder builder =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail();
        if (!clientId.isEmpty()) builder.requestIdToken(clientId);
        googleClient = GoogleSignIn.getClient(requireActivity(), builder.build());
        // Bloquea dobles pulsaciones mientras Google cierra la sesión previa.
        setLoading(true);
        googleClient.signOut().addOnCompleteListener(t -> {
            if (isAdded()) {
                setLoading(false);
                googleLauncher.launch(googleClient.getSignInIntent());
            }
        });
    }

    private void handleGoogleAccount(GoogleSignInAccount account) {
        MainActivity activity = (MainActivity) requireActivity();
        SessionManager session = SessionManager.get(requireContext());
        String role = session.getPendingRole();
        setLoading(true);
        Repository.get(requireContext()).loginWithGoogle(
                account.getIdToken(), account.getEmail(), role, true, result -> {
                    if (!isAdded()) return;
                    setLoading(false);
                    if (result.ok && result.data != null) {
                        activity.enterMain();
                    } else if ("NOT_FOUND".equals(result.errorCode)) {
                        // Usuario nuevo: registro con los datos de Google.
                        // Supabase Auth intercambia directamente el ID token
                        // de Google; Firebase Auth no participa.
                        session.savePendingGoogle(account.getEmail(),
                                account.getDisplayName(), account.getIdToken());
                        activity.showFragment("company".equals(role)
                                ? new RegisterCompanyFragment()
                                : new RegisterWorkerFragment(), true);
                    } else {
                        Snackbar.make(requireView(), result.friendlyMessage(),
                                Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    private void setLoading(boolean loading) {
        if (progress != null) progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        View[] actions = {btnLogin, btnGoogle, btnRegister, btnForgot};
        for (View action : actions) if (action != null) action.setEnabled(!loading);
    }
}
