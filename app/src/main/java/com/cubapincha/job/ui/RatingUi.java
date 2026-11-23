package com.cubapincha.job.ui;

import android.content.Context;
import android.graphics.Color;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.cubapincha.job.data.ApiResult;
import com.cubapincha.job.data.Repository;
import com.cubapincha.job.data.SessionManager;
import com.cubapincha.job.model.Application;
import com.cubapincha.job.model.Rating;
import com.cubapincha.job.model.RatingSubmission;
import com.cubapincha.job.model.RatingsPage;
import com.cubapincha.job.util.TextUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Flujos compartidos de ratings para candidaturas y perfiles públicos. */
public final class RatingUi {
    private static final int PAGE_SIZE = 20;
    private RatingUi() { }

    public static void promptForApplication(Fragment fragment, Application app) {
        if (fragment == null || !fragment.isAdded() || app == null || !"hired".equals(app.status)) return;
        Context context = fragment.requireContext();
        SessionManager session = SessionManager.get(context);
        if (!session.isLoggedIn()) {
            toast(context, "No puedes valorar esta candidatura con esta cuenta.");
            return;
        }
        // workerId/companyId son IDs de perfil; getUserId() es usuarios.id
        // (UID de Auth). El servidor ya entrega la candidatura filtrada para
        // la cuenta y vuelve a autorizarla al escribir. No comparar dominios
        // de identificadores distintos en la UI.
        boolean worker = "worker".equals(session.getRole());
        String recipientId = worker ? app.companyId : app.workerId;
        String recipientName = worker ? app.companyName : app.workerName;
        if (recipientId == null || recipientId.trim().isEmpty()) {
            toast(context, "No se pudo identificar el perfil que vas a valorar.");
            return;
        }
        showRatingForm(fragment, Repository.get(context), app, recipientName);
    }

    private static void checkPreviousRating(Fragment fragment, Repository repository,
                                            Application app, String recipientId,
                                            String authorId, String recipientName,
                                            String cursor, int pagesRead) {
        if (!fragment.isAdded()) return;
        if (pagesRead >= 100) {
            toast(fragment.requireContext(), "No pudimos confirmar si ya valoraste este trabajo. Inténtalo de nuevo.");
            return;
        }
        repository.getRatings(recipientId, PAGE_SIZE, cursor, result -> {
            if (!fragment.isAdded()) return;
            if (!result.ok || result.data == null) {
                toast(fragment.requireContext(), "No pudimos comprobar la valoración. " + result.friendlyMessage());
                return;
            }
            for (Rating rating : result.data.ratings) {
                if (app.applicationId.equals(rating.applicationId)
                        && authorId.equals(rating.authorId)) {
                    toast(fragment.requireContext(), "Ya valoraste este trabajo.");
                    return;
                }
            }
            String next = result.data.nextCursor;
            if (next != null && !next.isEmpty() && !next.equals(cursor)) {
                checkPreviousRating(fragment, repository, app, recipientId,
                        authorId, recipientName, next, pagesRead + 1);
            } else {
                showRatingForm(fragment, repository, app, recipientName);
            }
        });
    }

    private static void showRatingForm(Fragment fragment, Repository repository,
                                       Application app, String recipientName) {
        if (!fragment.isAdded()) return;
        Context context = fragment.requireContext();
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 20);
        form.setPadding(pad, dp(context, 8), pad, 0);

        TextView intro = new TextView(context);
        intro.setText("Comparte cómo fue el trabajo con "
                + (recipientName == null || recipientName.trim().isEmpty() ? "la otra parte" : recipientName.trim())
                + ". Tu valoración será pública.");
        form.addView(intro, matchWrap());

        TextView hint = new TextView(context);
        hint.setText("Elige de 1 a 5 estrellas");
        LinearLayout stars = new LinearLayout(context);
        stars.setOrientation(LinearLayout.HORIZONTAL);
        stars.setGravity(android.view.Gravity.CENTER);
        final TextView[] starViews = new TextView[5];
        final int[] selectedStars = {0};
        for (int i = 0; i < 5; i++) {
            final int starIndex = i;
            TextView star = new TextView(context);
            starViews[i] = star;
            star.setText("☆");
            star.setTextSize(38);
            star.setGravity(android.view.Gravity.CENTER);
            star.setContentDescription("" + (i + 1) + " estrellas");
            LinearLayout.LayoutParams starParams = new LinearLayout.LayoutParams(0,
                    dp(context, 54), 1f);
            stars.addView(star, starParams);
            star.setOnClickListener(v -> {
                selectedStars[0] = starIndex + 1;
                for (int j = 0; j < starViews.length; j++) {
                    starViews[j].setText(j < selectedStars[0] ? "★" : "☆");
                    starViews[j].setTextColor(j < selectedStars[0]
                            ? Color.parseColor("#F4B942") : Color.GRAY);
                }
                hint.setText(selectedStars[0] + " de 5 estrellas");
            });
        }
        LinearLayout.LayoutParams ratingParams = matchWrap();
        ratingParams.topMargin = dp(context, 16);
        form.addView(stars, ratingParams);

        LinearLayout.LayoutParams hintParams = matchWrap();
        hintParams.topMargin = dp(context, 4);
        form.addView(hint, hintParams);

        EditText comment = new EditText(context);
        comment.setHint("Comentario opcional");
        comment.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        comment.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        comment.setMinLines(3);
        comment.setMaxLines(5);
        comment.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});
        LinearLayout.LayoutParams commentParams = matchWrap();
        commentParams.topMargin = dp(context, 12);
        form.addView(comment, commentParams);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle("Valora este trabajo")
                .setView(form)
                .setNegativeButton("Ahora no", null)
                .setPositiveButton("Enviar valoración", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            android.widget.Button send = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            send.setEnabled(true);
            send.setOnClickListener(v -> {
                int selected = selectedStars[0];
                String text = comment.getText() == null ? "" : comment.getText().toString().trim();
                if (selected < 1 || selected > 5) {
                    hint.setText("Elige de 1 a 5 estrellas");
                    return;
                }
                send.setEnabled(false);
                send.setText("Enviando…");
                repository.rateApplication(app.applicationId, selected, text, result -> {
                    if (!fragment.isAdded()) return;
                    if (result.ok && result.data != null) {
                        dialog.dismiss();
                        RatingSubmission submitted = result.data;
                        toast(fragment.requireContext(), "¡Gracias por tu valoración! Media: "
                                + formatAverage(submitted.average) + " · " + submitted.total + " valoraciones.");
                    } else {
                        String message = ratingError(result);
                        if ("CONFLICT".equals(result.errorCode)) {
                            dialog.dismiss();
                        } else {
                            send.setEnabled(true);
                            send.setText("Enviar valoración");
                        }
                        toast(fragment.requireContext(), message);
                    }
                });
            });
        });
        dialog.show();
    }

    private static String ratingError(ApiResult<?> result) {
        if (result == null) return "No se pudo enviar la valoración. Inténtalo de nuevo.";
        switch (result.errorCode == null ? "" : result.errorCode) {
            case "VALIDATION_ERROR": return "Revisa los datos: estrellas de 1 a 5 y comentario de hasta 1000 caracteres.";
            case "AUTH_REQUIRED": return "Inicia sesión de nuevo para valorar este trabajo.";
            case "FORBIDDEN_ROLE": return "No puedes valorar este trabajo.";
            case "NOT_FOUND": return "La candidatura no existe.";
            case "CONFLICT":
                String serverMessage = result.friendlyMessage();
                return serverMessage == null || serverMessage.isEmpty()
                        ? "Ya valoraste este trabajo o la candidatura no está contratada." : serverMessage;
            case "RATE_LIMITED": return "Inténtalo más tarde.";
            case "NO_CONNECTION": return "Necesitas conexión para enviar la valoración.";
            default:
                String message = result.friendlyMessage();
                return message == null || message.isEmpty()
                        ? "No se pudo enviar la valoración. Inténtalo de nuevo." : message;
        }
    }

    /** Abre la vista pública de un perfil con resumen, lista y paginación. */
    public static void showRatings(Fragment fragment, String userId, String displayName) {
        if (fragment == null || !fragment.isAdded()) return;
        if (userId == null || userId.trim().isEmpty()) {
            toast(fragment.requireContext(), "Este perfil aún no está disponible.");
            return;
        }
        new RatingsDialogController(fragment, userId.trim(), displayName).show();
    }

    public static String summaryText(double average, int total) {
        return formatAverage(average) + " · " + total + (total == 1 ? " valoración" : " valoraciones");
    }

    public static String formatAverage(double average) {
        return String.format(Locale.US, "%.1f", average);
    }

    public static void appendRatingCards(Context context, LinearLayout container, List<Rating> ratings) {
        if (ratings == null) return;
        for (Rating rating : ratings) container.addView(createRatingCard(context, rating), matchWrap());
    }

    private static View createRatingCard(Context context, Rating rating) {
        MaterialCardView card = new MaterialCardView(context);
        card.setRadius(dp(context, 12));
        card.setCardElevation(dp(context, 1));
        card.setUseCompatPadding(true);
        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.bottomMargin = dp(context, 8);
        card.setLayoutParams(cardParams);
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
        card.addView(body, matchWrap());

        TextView stars = new TextView(context);
        stars.setText(starsText(rating.stars) + "  " + rating.stars + "/5");
        stars.setTextColor(Color.parseColor("#F4B942"));
        stars.setTextSize(15);
        stars.setTypeface(null, android.graphics.Typeface.BOLD);
        body.addView(stars, matchWrap());

        TextView author = new TextView(context);
        String name = rating.authorName == null || rating.authorName.isEmpty() ? "Usuario" : rating.authorName;
        String job = rating.jobTitle == null || rating.jobTitle.isEmpty() ? "Trabajo" : rating.jobTitle;
        String date = rating.createdAt == null || rating.createdAt.isEmpty() ? "" : " · " + TextUtils.timeAgo(rating.createdAt);
        author.setText(name + " · " + job + date);
        author.setTextSize(13);
        LinearLayout.LayoutParams authorParams = matchWrap();
        authorParams.topMargin = dp(context, 4);
        body.addView(author, authorParams);

        if (rating.comment != null && !rating.comment.trim().isEmpty()) {
            TextView comment = new TextView(context);
            comment.setText(rating.comment.trim());
            comment.setTextSize(14);
            LinearLayout.LayoutParams commentParams = matchWrap();
            commentParams.topMargin = dp(context, 6);
            body.addView(comment, commentParams);
        }
        return card;
    }

    private static String starsText(int count) {
        int safe = Math.max(0, Math.min(5, count));
        StringBuilder out = new StringBuilder();
        for (int i = 1; i <= 5; i++) out.append(i <= safe ? '★' : '☆');
        return out.toString();
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void toast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    private static final class RatingsDialogController {
        private final Fragment fragment;
        private final String userId;
        private final String displayName;
        private TextView summary;
        private TextView empty;
        private TextView error;
        private MaterialButton more;
        private LinearLayout targetList;
        private AlertDialog dialog;
        private String cursor = "0";
        private boolean loading;
        private final Set<String> seen = new HashSet<>();

        RatingsDialogController(Fragment fragment, String userId, String displayName) {
            this.fragment = fragment;
            this.userId = userId;
            this.displayName = displayName == null || displayName.trim().isEmpty() ? "Perfil" : displayName.trim();
        }

        void show() {
            Context context = fragment.requireContext();
            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(context, 18);
            content.setPadding(pad, dp(context, 4), pad, 0);
            summary = new TextView(context);
            summary.setText("Cargando valoraciones…");
            summary.setTextSize(16);
            summary.setTypeface(null, android.graphics.Typeface.BOLD);
            content.addView(summary, matchWrap());
            empty = new TextView(context);
            empty.setText("Aún no tiene valoraciones.");
            empty.setVisibility(View.GONE);
            LinearLayout.LayoutParams emptyParams = matchWrap();
            emptyParams.topMargin = dp(context, 10);
            content.addView(empty, emptyParams);
            error = new TextView(context);
            error.setVisibility(View.GONE);
            LinearLayout.LayoutParams errorParams = matchWrap();
            errorParams.topMargin = dp(context, 8);
            content.addView(error, errorParams);
            targetList = new LinearLayout(context);
            targetList.setOrientation(LinearLayout.VERTICAL);
            content.addView(targetList, matchWrap());
            more = new MaterialButton(context);
            more.setText("Ver más");
            more.setVisibility(View.GONE);
            more.setOnClickListener(v -> loadPage());
            content.addView(more, matchWrap());
            ScrollView scroll = new ScrollView(context);
            scroll.addView(content);
            dialog = new MaterialAlertDialogBuilder(context)
                    .setTitle("Valoraciones · " + displayName)
                    .setView(scroll)
                    .setPositiveButton("Cerrar", null)
                    .create();
            dialog.show();
            loadPage();
        }

        void loadPage() {
            if (loading || !fragment.isAdded()) return;
            loading = true;
            error.setVisibility(View.GONE);
            more.setEnabled(false);
            Repository.get(fragment.requireContext()).getRatings(userId, PAGE_SIZE, cursor, result -> {
                loading = false;
                if (!fragment.isAdded() || dialog == null || !dialog.isShowing()) return;
                if (!result.ok || result.data == null) {
                    String message = result.friendlyMessage();
                    error.setText(message == null || message.isEmpty()
                            ? "No se pudieron cargar las valoraciones." : message);
                    error.setVisibility(View.VISIBLE);
                    more.setVisibility(View.VISIBLE);
                    more.setEnabled(true);
                    more.setText(seen.isEmpty() ? "Reintentar" : "Reintentar carga");
                    return;
                }
                RatingsPage page = result.data;
                summary.setText(summaryText(page.average, page.total));
                if (page.total == 0 && seen.isEmpty()) empty.setVisibility(View.VISIBLE);
                for (Rating rating : page.ratings) {
                    String key = rating.ratingId.isEmpty()
                            ? rating.applicationId + ":" + rating.authorId : rating.ratingId;
                    if (seen.add(key)) {
                        targetList.addView(createRatingCard(fragment.requireContext(), rating), matchWrap());
                    }
                }
                cursor = page.nextCursor == null ? "" : page.nextCursor;
                boolean hasMore = !cursor.isEmpty();
                more.setVisibility(hasMore ? View.VISIBLE : View.GONE);
                more.setEnabled(hasMore);
                more.setText("Ver más");
            });
        }
    }
}
