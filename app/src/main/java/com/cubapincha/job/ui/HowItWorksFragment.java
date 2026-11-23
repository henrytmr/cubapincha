package com.cubapincha.job.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.cubapincha.job.R;
import com.cubapincha.job.util.AnimUtils;

/** Explicación en 3 pasos, sin crear cuenta. */
public class HowItWorksFragment extends BottomSheetDialogFragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_how, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        AnimUtils.staggerIn(
                view.findViewById(R.id.step1),
                view.findViewById(R.id.step2),
                view.findViewById(R.id.step3));
        view.findViewById(R.id.btn_close).setOnClickListener(v -> dismiss());
    }
}
