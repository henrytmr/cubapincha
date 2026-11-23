package com.cubapincha.job.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.snackbar.Snackbar;
import com.cubapincha.job.R;
import com.cubapincha.job.data.Repository;
import com.cubapincha.job.model.Application;
import com.cubapincha.job.util.AnimUtils;
import com.cubapincha.job.util.TextUtils;

/**
 * Línea de tiempo de una candidatura (5 pasos: enviado → visto → en
 * revisión → entrevista → decisión), guía según el estado, recordatorio
 * educado a los 3 días sin respuesta y retirar interés.
 */
public class TrackingSheetFragment extends BottomSheetDialogFragment {
    public static final String REQUEST_KEY = "tracking_withdrawn";
    private static final String ARG_APP_JSON = "application_json";
    /** Días sin respuesta antes de poder recordar (y entre recordatorios). */
    private static final long REMINDER_DAYS = 3;

    public static TrackingSheetFragment newInstance(Application app) {
        TrackingSheetFragment f = new TrackingSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_APP_JSON, app.toJson().toString());
        f.setArguments(args);
        return f;
    }

    private Application current;

    private static Application parseApp(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            return Application.fromJson(new org.json.JSONObject(json));
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_tracking, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        current = parseApp(getArguments() != null ? getArguments().getString(ARG_APP_JSON) : null);
        if (current == null) {
            dismiss();
            return;
        }
        TextView title = view.findViewById(R.id.tracking_title);
        TextView subtitle = view.findViewById(R.id.tracking_subtitle);
        title.setText(current.jobTitle);
        subtitle.setText(current.companyName + " · " + current.jobLocationText());

        LinearLayout timeline = view.findViewById(R.id.timeline);
        String status = current.status;
        boolean closed = "rejected".equals(status) || "withdrawn".equals(status);
        String[] steps = {
                getString(R.string.timeline_sent),
                getString(R.string.timeline_seen),
                getString(R.string.timeline_reviewing),
                getString(R.string.timeline_interview),
                getString(R.string.timeline_hired)
        };
        // "Visto" se alcanza al pasar a revisión: la empresa ya abrió la candidatura.
        int reached = 0;
        if ("sent".equals(status)) reached = 0;
        else if ("reviewing".equals(status)) reached = 2;
        else if ("interview".equals(status)) reached = 3;
        else if ("hired".equals(status)) reached = 4;
        if (closed) reached = 0;

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < steps.length; i++) {
            View stepView = inflater.inflate(R.layout.item_timeline_step, timeline, false);
            TextView stepTitle = stepView.findViewById(R.id.step_title);
            TextView stepSubtitle = stepView.findViewById(R.id.step_subtitle);
            ImageView icon = stepView.findViewById(R.id.step_icon);
            View line = stepView.findViewById(R.id.step_line);
            stepTitle.setText(steps[i]);
            boolean done = i <= reached;
            boolean isLast = i == steps.length - 1;
            if (isLast) line.setVisibility(View.INVISIBLE);
            if (done) {
                icon.setImageResource(R.drawable.ic_check);
                icon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.confirm_green));
                stepTitle.setTextColor(UiUtils.resolveThemeColor(requireContext(), com.google.android.material.R.attr.colorOnSurface));
                stepSubtitle.setText(i == reached && !closed ? "Estás aquí" : "Completado");
            } else {
                icon.setImageResource(R.drawable.ic_clock);
                icon.setColorFilter(UiUtils.resolveThemeColor(requireContext(), com.google.android.material.R.attr.colorOnSurfaceVariant));
                stepTitle.setTextColor(UiUtils.resolveThemeColor(requireContext(), com.google.android.material.R.attr.colorOnSurfaceVariant));
                stepSubtitle.setText("Pendiente");
            }
            timeline.addView(stepView);
        }
        if (closed) {
            View stepView = inflater.inflate(R.layout.item_timeline_step, timeline, false);
            TextView stepTitle = stepView.findViewById(R.id.step_title);
            TextView stepSubtitle = stepView.findViewById(R.id.step_subtitle);
            ImageView icon = stepView.findViewById(R.id.step_icon);
            stepView.findViewById(R.id.step_line).setVisibility(View.INVISIBLE);
            stepTitle.setText(R.string.timeline_rejected);
            stepSubtitle.setText("withdrawn".equals(status) ? "Retirado por ti" : "La empresa cerró el proceso");
            icon.setImageResource(R.drawable.ic_info);
            icon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.error_red));
            timeline.addView(stepView);
        }

        TextView guidance = view.findViewById(R.id.guidance_text);
        int guidanceRes;
        switch (status) {
            case "reviewing": guidanceRes = R.string.guidance_reviewing; break;
            case "interview": guidanceRes = R.string.guidance_interview; break;
            case "hired": guidanceRes = R.string.guidance_hired; break;
            case "rejected": guidanceRes = R.string.guidance_rejected; break;
            case "withdrawn": guidanceRes = R.string.guidance_withdrawn; break;
            default: guidanceRes = R.string.guidance_sent;
        }
        guidance.setText(guidanceRes);

        View btnReminder = view.findViewById(R.id.btn_reminder);
        btnReminder.setVisibility("sent".equals(status) ? View.VISIBLE : View.GONE);
        btnReminder.setOnClickListener(v -> {
            long sinceSent = TextUtils.daysSince(current.createdAt);
            long sinceReminder = current.reminderSentAt != null && !current.reminderSentAt.isEmpty()
                    ? TextUtils.daysSince(current.reminderSentAt) : Long.MAX_VALUE;
            if (sinceSent < REMINDER_DAYS || sinceReminder < REMINDER_DAYS) {
                Snackbar.make(requireView(), R.string.reminder_too_soon, Snackbar.LENGTH_LONG).show();
                return;
            }
            Repository.get(requireContext()).sendReminder(current.applicationId, result -> {
                if (!isAdded()) return;
                if (result.ok) {
                    current.reminderSentAt = TextUtils.nowIso();
                    Snackbar.make(requireView(), R.string.reminder_sent, Snackbar.LENGTH_LONG).show();
                    AnimUtils.hapticTick(v);
                } else {
                    Snackbar.make(requireView(), result.friendlyMessage(), Snackbar.LENGTH_LONG).show();
                }
            });
        });

        View btnWithdraw = view.findViewById(R.id.btn_withdraw);
        // Se puede retirar mientras el proceso está abierto (también en entrevista,
        // igual que en el detalle de la oferta).
        boolean canWithdraw = "sent".equals(status) || "reviewing".equals(status);
        btnWithdraw.setVisibility(canWithdraw ? View.VISIBLE : View.GONE);
        btnWithdraw.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                .setTitle(R.string.withdraw_confirm_title)
                .setMessage(R.string.withdraw_confirm_body)
                .setPositiveButton(R.string.withdraw_interest, (d, w) ->
                        Repository.get(requireContext()).withdrawInterest(current.applicationId, result -> {
                            if (isAdded()) {
                                if (result.ok) {
                                    Snackbar.make(requireView(), R.string.interest_withdrawn, Snackbar.LENGTH_LONG).show();
                                    AnimUtils.hapticTick(v);
                                    getParentFragmentManager().setFragmentResult(REQUEST_KEY, new Bundle());
                                    dismiss();
                                } else {
                                    Snackbar.make(requireView(), result.friendlyMessage(), Snackbar.LENGTH_LONG).show();
                                }
                            }
                        }))
                .setNegativeButton(R.string.cancel, null)
                .show());
        view.findViewById(R.id.btn_close).setOnClickListener(v -> dismiss());
    }
}
