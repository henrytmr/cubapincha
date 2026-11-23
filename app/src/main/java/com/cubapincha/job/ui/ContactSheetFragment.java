package com.cubapincha.job.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.cubapincha.job.R;
import com.cubapincha.job.data.Repository;
import com.cubapincha.job.model.Job;

/**
 * Hoja de contacto: muestra la empresa, la oferta, la ubicación y el
 * horario recomendado antes de abrir WhatsApp o llamar, con un mensaje
 * prellenado editable y la opción de copiar el número.
 */
public class ContactSheetFragment extends BottomSheetDialogFragment {
    private static final String ARG_JOB = "job";
    private static final String ARG_PHONE = "phone";
    private static final String ARG_WHATSAPP = "whatsapp";
    private static final String ARG_MESSAGE = "message";

    public static ContactSheetFragment newInstance(Job job, String phone,
                                                   boolean whatsappEnabled, String message) {
        ContactSheetFragment f = new ContactSheetFragment();
        Bundle args = new Bundle();
        args.putString(ARG_JOB, job.toJson().toString());
        args.putString(ARG_PHONE, phone);
        args.putBoolean(ARG_WHATSAPP, whatsappEnabled);
        args.putString(ARG_MESSAGE, message);
        f.setArguments(args);
        return f;
    }

    private Job job;
    private String phone;
    private TextInputEditText messageInput;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_contact, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        try {
            job = Job.fromJson(new org.json.JSONObject(args != null ? args.getString(ARG_JOB, "{}") : "{}"));
        } catch (Exception e) {
            job = new Job();
        }
        phone = args != null ? args.getString(ARG_PHONE, "") : "";
        boolean whatsappEnabled = args != null && args.getBoolean(ARG_WHATSAPP, false);
        String message = args != null ? args.getString(ARG_MESSAGE, "") : "";

        ((TextView) view.findViewById(R.id.contact_company)).setText(job.companyName);
        ((TextView) view.findViewById(R.id.contact_job)).setText(
                job.title + " · " + job.locationText());
        TextView hours = view.findViewById(R.id.contact_hours);
        if (job.contactHours != null && !job.contactHours.isEmpty()) {
            hours.setText(getString(R.string.recommended_hours, job.contactHours));
            hours.setVisibility(View.VISIBLE);
        } else {
            hours.setVisibility(View.GONE);
        }

        messageInput = view.findViewById(R.id.input_message);
        messageInput.setText(message);

        View btnWhatsapp = view.findViewById(R.id.btn_whatsapp);
        btnWhatsapp.setVisibility(whatsappEnabled ? View.VISIBLE : View.GONE);
        btnWhatsapp.setOnClickListener(v -> {
            Repository.get(requireContext()).contactEvent("job", job.jobId, "whatsapp");
            String url = "https://wa.me/" + phone.replaceAll("[^0-9]", "")
                    + "?text=" + Uri.encode(UiUtils.text(messageInput));
            if (openExternal(url)) dismiss();
        });
        view.findViewById(R.id.btn_call).setOnClickListener(v -> {
            Repository.get(requireContext()).contactEvent("job", job.jobId, "call");
            if (openExternal("tel:" + phone)) dismiss();
        });
        view.findViewById(R.id.btn_copy_number).setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("CubaPincha", phone));
            }
            Snackbar.make(view, R.string.number_copied, Snackbar.LENGTH_SHORT).show();
        });
    }

    private boolean openExternal(String uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
            return true;
        } catch (Exception e) {
            if (isAdded()) {
                Snackbar.make(requireView(), "No pudimos abrir esa aplicación.", Snackbar.LENGTH_LONG).show();
            }
            return false;
        }
    }
}
