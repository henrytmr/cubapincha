package com.cubapincha.job.ui;

import android.content.Context;
import android.util.AttributeSet;

import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * BottomNavigationView de CubaPincha: conserva las seis entradas de empresa
 * sin alterar el componente ni la navegación del rol trabajador.
 */
public class CompanyBottomNavigationView extends BottomNavigationView {
    public CompanyBottomNavigationView(Context context) {
        super(context);
    }

    public CompanyBottomNavigationView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CompanyBottomNavigationView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public int getMaxItemCount() {
        return 6;
    }
}
