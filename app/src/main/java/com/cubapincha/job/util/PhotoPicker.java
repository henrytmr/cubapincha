package com.cubapincha.job.util;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;

import com.cubapincha.job.R;
import com.google.android.material.card.MaterialCardView;

import java.io.File;

/**
 * Elegir foto de perfil / logotipo: cámara o galería.
 * - Cámara: pide el permiso CAMERA en el momento de usarla; la foto va a un
 *   archivo de nombre fijo, así que una rotación con la cámara abierta no
 *   pierde la imagen.
 * - Galería: usa el selector de documentos del sistema (OpenDocument), que
 *   sí concede permiso persistente a la imagen elegida: con GetContent el
 *   permiso no era persistible y la foto dejaba de abrirse al reiniciar.
 * Debe crearse en onCreate() del fragmento: los lanzadores se registran
 * antes de que el fragmento arranque.
 */
public class PhotoPicker {
    public interface Callback {
        void onPhotoPicked(Uri uri);
        void onPhotoDenied(String reason);
    }

    private final Fragment fragment;
    private final Callback callback;
    private final ActivityResultLauncher<String> requestCamera;
    private final ActivityResultLauncher<Uri> takePicture;
    private final ActivityResultLauncher<String[]> pickImage;
    private Uri pendingCameraUri;

    public PhotoPicker(Fragment fragment, Callback callback) {
        this.fragment = fragment;
        this.callback = callback;
        requestCamera = fragment.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (Boolean.TRUE.equals(granted)) {
                        launchCamera();
                    } else if (!fragment.shouldShowRequestPermissionRationale(
                            Manifest.permission.CAMERA)) {
                        // Denegación permanente ("No volver a preguntar"):
                        // la única salida es Ajustes del sistema.
                        showCameraSettingsDialog();
                    } else {
                        callback.onPhotoDenied("camera");
                    }
                });
        takePicture = fragment.registerForActivityResult(
                new ActivityResultContracts.TakePicture(), success -> {
                    if (Boolean.TRUE.equals(success)) {
                        // Tras una rotación, pendingCameraUri es null en la
                        // nueva instancia: la URI es determinista y se
                        // recalcula desde el archivo de nombre fijo.
                        Uri uri = pendingCameraUri != null ? pendingCameraUri : cameraUri();
                        callback.onPhotoPicked(uri);
                    }
                });
        pickImage = fragment.registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), uri -> {
                    if (uri != null) {
                        // Conservar el acceso a la imagen tras reiniciar la app.
                        try {
                            fragment.requireContext().getContentResolver()
                                    .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (Exception ignored) { }
                        callback.onPhotoPicked(uri);
                    }
                });
    }

    /** URI fija para la foto de cámara (archivo de nombre fijo en caché). */
    private Uri cameraUri() {
        Context ctx = fragment.requireContext();
        File dir = new File(ctx.getCacheDir(), "images");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "camera_photo.jpg");
        return FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", file);
    }

    private void showCameraSettingsDialog() {
        Context ctx = fragment.requireContext();
        new AlertDialog.Builder(ctx)
                .setMessage(R.string.camera_denied_forever)
                .setPositiveButton(R.string.settings, (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + ctx.getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(intent);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Diálogo para elegir entre cámara y galería. */
    public void showChoice() {
        Context ctx = fragment.requireContext();
        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        float density = ctx.getResources().getDisplayMetrics().density;
        int pad = (int) (8 * density);
        content.setPadding(pad, 0, pad, pad);
        final AlertDialog[] dialogRef = new AlertDialog[1];
        addChoiceCard(content, R.drawable.ic_camera, R.string.photo_take, () -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
            askCamera();
        });
        addChoiceCard(content, R.drawable.photo_library_24px, R.string.photo_gallery, () -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
            pickImage.launch(new String[]{"image/*"});
        });
        dialogRef[0] = new AlertDialog.Builder(ctx)
                .setTitle(R.string.photo_choice_title)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void addChoiceCard(LinearLayout parent, int iconRes, int textRes, Runnable action) {
        Context ctx = fragment.requireContext();
        float density = ctx.getResources().getDisplayMetrics().density;
        MaterialCardView card = new MaterialCardView(ctx);
        card.setRadius(18 * density);
        card.setCardElevation(density);
        card.setClickable(true);
        card.setFocusable(true);
        LinearLayout row = new LinearLayout(ctx);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int) (18 * density), (int) (6 * density),
                (int) (18 * density), (int) (6 * density));
        ImageView icon = new ImageView(ctx);
        icon.setImageDrawable(ResourcesCompat.getDrawable(ctx.getResources(), iconRes, ctx.getTheme()));
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(ctx, R.color.brand_primary)));
        row.addView(icon, new LinearLayout.LayoutParams((int) (40 * density), (int) (40 * density)));
        TextView label = new TextView(ctx);
        label.setText(textRes);
        label.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        labelLp.setMarginStart((int) (12 * density));
        row.addView(label, labelLp);
        card.addView(row);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, (int) (4 * density), 0, (int) (4 * density));
        parent.addView(card, cardLp);
        card.setOnClickListener(v -> action.run());
    }

    private void askCamera() {
        Context ctx = fragment.requireContext();
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            requestCamera.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        try {
            pendingCameraUri = cameraUri();
            takePicture.launch(pendingCameraUri);
        } catch (Exception e) {
            callback.onPhotoDenied("camera_error");
        }
    }
}
