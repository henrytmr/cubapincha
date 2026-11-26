package com.cubapincha.job.ui;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.cubapincha.job.R;
import com.cubapincha.job.data.Repository;
import com.cubapincha.job.data.SessionManager;
import com.cubapincha.job.model.PublicComment;

import java.util.ArrayList;
import java.util.List;

/** Comentarios públicos de una oferta o perfil; no implementa chat privado. */
public class CommentsBottomSheet extends BottomSheetDialogFragment {
    private static final String ARG_TYPE = "entity_type";
    private static final String ARG_ID = "entity_id";
    private String entityType, entityId;
    private LinearLayout list;
    private TextView state;
    private ProgressBar progress;
    private EditText input;
    private MaterialButton send;
    private int count;
    private OnCountChanged countChanged;

    public interface OnCountChanged { void onCountChanged(int count); }

    public static CommentsBottomSheet newInstance(String entityType, String entityId, int count) {
        CommentsBottomSheet f = new CommentsBottomSheet();
        Bundle b = new Bundle(); b.putString(ARG_TYPE, entityType); b.putString(ARG_ID, entityId); b.putInt("count", count);
        f.setArguments(b); return f;
    }
    public CommentsBottomSheet setOnCountChanged(OnCountChanged listener) { countChanged = listener; return this; }

    @Nullable @Override public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                                                   @Nullable android.view.ViewGroup container,
                                                   @Nullable Bundle savedInstanceState) {
        entityType = getArguments() == null ? "" : getArguments().getString(ARG_TYPE, "");
        entityId = getArguments() == null ? "" : getArguments().getString(ARG_ID, "");
        count = getArguments() == null ? 0 : getArguments().getInt("count", 0);
        LinearLayout root = new LinearLayout(requireContext()); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20), dp(12), dp(20), dp(20));
        TextView title = new TextView(requireContext()); title.setText("Comentarios (" + count + ")"); title.setTextSize(20); title.setTextColor(UiUtils.resolveThemeColor(requireContext(), com.google.android.material.R.attr.colorOnSurface));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        progress = new ProgressBar(requireContext()); progress.setVisibility(View.GONE); root.addView(progress, new LinearLayout.LayoutParams(-1, dp(36)));
        state = new TextView(requireContext()); state.setVisibility(View.GONE); state.setPadding(0, dp(8), 0, dp(8)); root.addView(state);
        ScrollView scroll = new ScrollView(requireContext()); list = new LinearLayout(requireContext()); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout composer = new LinearLayout(requireContext()); composer.setGravity(android.view.Gravity.CENTER_VERTICAL); composer.setPadding(0, dp(12), 0, 0);
        input = new EditText(requireContext()); input.setHint("Escribe un comentario…"); input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE); input.setMaxLines(4); input.setMaxWidth(1000); composer.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        send = new MaterialButton(requireContext()); send.setText("Enviar"); send.setOnClickListener(v -> add()); composer.addView(send, new LinearLayout.LayoutParams(-2, -2)); root.addView(composer);
        load(); return root;
    }
    private void load() { loading(true); Repository.get(requireContext()).listComments(entityType, entityId, 50, null, result -> { if (!isAdded()) return; loading(false); if (!result.ok || result.data == null) { showError(result.friendlyMessage()); return; } render(result.data.comments); }); }
    private void render(List<PublicComment> comments) { list.removeAllViews(); if (comments == null || comments.isEmpty()) { state.setText("Aún no hay comentarios."); state.setVisibility(View.VISIBLE); return; } state.setVisibility(View.GONE); for (PublicComment c : comments) addRow(c); }
    private void addRow(PublicComment c) {
        LinearLayout row = new LinearLayout(requireContext()); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(0, dp(8), 0, dp(8));
        TextView author = new TextView(requireContext()); author.setText((c.authorName == null || c.authorName.isEmpty() ? "Usuario" : c.authorName) + (c.createdAt == null || c.createdAt.isEmpty() ? "" : " · " + c.createdAt)); author.setTextSize(13); author.setTextColor(UiUtils.resolveThemeColor(requireContext(), com.google.android.material.R.attr.colorOnSurfaceVariant)); row.addView(author);
        TextView body = new TextView(requireContext()); body.setText(c.text == null ? "" : c.text); body.setTextSize(15); body.setTextColor(UiUtils.resolveThemeColor(requireContext(), com.google.android.material.R.attr.colorOnSurface)); row.addView(body);
        String me = SessionManager.get(requireContext()).getUserId(); if (me != null && !me.isEmpty() && me.equals(c.authorId)) { MaterialButton del = new MaterialButton(requireContext()); del.setText("Borrar"); del.setOnClickListener(v -> delete(c, del)); row.addView(del, new LinearLayout.LayoutParams(-2, -2)); }
        list.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }
    private void add() { String text = input.getText() == null ? "" : input.getText().toString().trim(); if (text.isEmpty()) { input.setError("Escribe un comentario"); return; } send.setEnabled(false); Repository.get(requireContext()).addComment(entityType, entityId, text, result -> { if (!isAdded()) return; send.setEnabled(true); if (!result.ok || result.data == null) { Snackbar.make(requireView(), result.friendlyMessage(), Snackbar.LENGTH_LONG).show(); return; } input.setText(""); count = result.data.commentCount > 0 ? result.data.commentCount : count + 1; if (countChanged != null) countChanged.onCountChanged(count); load(); }); }
    private void delete(PublicComment c, View button) { button.setEnabled(false); Repository.get(requireContext()).deleteComment(c.commentId, result -> { if (!isAdded()) return; if (!result.ok) { button.setEnabled(true); Snackbar.make(requireView(), result.friendlyMessage(), Snackbar.LENGTH_LONG).show(); return; } count = Math.max(0, count - 1); if (countChanged != null) countChanged.onCountChanged(count); load(); }); }
    private void loading(boolean value) { progress.setVisibility(value ? View.VISIBLE : View.GONE); if (value) state.setVisibility(View.GONE); }
    private void showError(String message) { state.setText(message + "  Pulsa para reintentar."); state.setVisibility(View.VISIBLE); state.setOnClickListener(v -> load()); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
}
