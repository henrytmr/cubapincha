package com.cubapincha.job.ui;

import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.cubapincha.job.R;

/** Renderiza reacciones públicas; like y dislike nunca quedan activos a la vez. */
public final class ReactionUi {
    private ReactionUi() { }

    public interface Actions {
        void onReaction(String reaction);
        void onComments();
    }

    public static void bind(View root, String reaction, int likes, int dislikes, int comments,
                            Actions actions) {
        if (root == null) return;
        ImageButton like = root.findViewById(R.id.btn_like);
        ImageButton dislike = root.findViewById(R.id.btn_dislike);
        View commentsButton = root.findViewById(R.id.btn_comments);
        TextView likeCount = root.findViewById(R.id.like_count);
        TextView dislikeCount = root.findViewById(R.id.dislike_count);
        TextView commentCount = root.findViewById(R.id.comment_count);
        if (like == null || dislike == null || commentsButton == null) return;
        boolean liked = "like".equals(reaction);
        boolean disliked = "dislike".equals(reaction);
        int neutral = UiUtils.resolveThemeColor(root.getContext(), com.google.android.material.R.attr.colorOnSurfaceVariant);
        like.setColorFilter(liked ? UiUtils.resolveThemeColor(root.getContext(), com.google.android.material.R.attr.colorPrimary) : neutral);
        dislike.setColorFilter(disliked ? UiUtils.resolveThemeColor(root.getContext(), com.google.android.material.R.attr.colorError) : neutral);
        like.setContentDescription(liked ? "Quitar Me gusta" : "Me gusta");
        dislike.setContentDescription(disliked ? "Quitar No me gusta" : "No me gusta");
        if (likeCount != null) likeCount.setText(String.valueOf(Math.max(0, likes)));
        if (dislikeCount != null) dislikeCount.setText(String.valueOf(Math.max(0, dislikes)));
        if (commentCount != null) commentCount.setText(String.valueOf(Math.max(0, comments)));
        UiUtils.setDebouncedClickListener(like, v -> { if (actions != null) actions.onReaction("like"); });
        UiUtils.setDebouncedClickListener(dislike, v -> { if (actions != null) actions.onReaction("dislike"); });
        UiUtils.setDebouncedClickListener(commentsButton, v -> { if (actions != null) actions.onComments(); });
    }
}
