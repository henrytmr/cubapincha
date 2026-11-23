package com.cubapincha.job.util;

import android.content.Context;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;
import android.view.autofill.AutofillValue;

import com.google.android.material.textfield.TextInputEditText;

import java.lang.reflect.Method;

/**
 * Campo de texto que no muestra el resaltado amarillo del sistema al
 * autocompletar. Android pinta de amarillo los campos autocompletados
 * (API 26+) y ese color no pertenece a la paleta de CubaPincha.
 *
 * Dos capas lo impiden:
 * 1. Esta clase: el interruptor que pinta el amarillo,
 *    View.setAutofilled(boolean), es un método oculto (@hide): existe en
 *    el teléfono pero no en el SDK público, así que no se puede
 *    sobreescribir. En su lugar, tras cada autocompletado se invoca por
 *    reflexión con false, pospuesto un frame y de nuevo a los 100 ms,
 *    para que corra después de que el framework marque el campo (el
 *    marco puede marcarlo tarde). También se limpia al ganar el foco,
 *    por si el autocompletado llegó antes de que la vista estuviera
 *    lista. Si la reflexión falla en algún dispositivo, el campo
 *    simplemente queda con el resaltado del tema: nunca se rompe nada.
 * 2. El tema: android:autofilledHighlight tiñe el resaltado de menta
 *    translúcido en vez de amarillo (ver Theme.CubaPincha).
 *
 * La marca es solo visual: el valor autocompletado queda igual.
 */
public class NoHighlightEditText extends TextInputEditText {
    /** El método oculto se busca una sola vez; null si no hay acceso. */
    private static Method setAutofilledMethod;
    private static boolean setAutofilledLookedUp;

    public NoHighlightEditText(Context context) {
        super(context);
    }

    public NoHighlightEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NoHighlightEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void autofill(AutofillValue value) {
        super.autofill(value);
        clearAutofillHighlight();
    }

    @Override
    public void autofill(SparseArray<AutofillValue> values) {
        super.autofill(values);
        clearAutofillHighlight();
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction,
                                  android.graphics.Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (focused) clearAutofillHighlight();
    }

    private void clearAutofillHighlight() {
        // Un frame después y de nuevo a los 100 ms: el framework puede
        // marcar el campo después de autofill() y después del primer frame.
        post(this::invokeSetAutofilledFalse);
        postDelayed(this::invokeSetAutofilledFalse, 100L);
    }

    private void invokeSetAutofilledFalse() {
        try {
            if (!setAutofilledLookedUp) {
                setAutofilledLookedUp = true;
                try {
                    setAutofilledMethod = View.class.getMethod("setAutofilled", boolean.class);
                } catch (Exception ignored) {
                    setAutofilledMethod = null;
                }
            }
            if (setAutofilledMethod != null) {
                setAutofilledMethod.invoke(this, false);
            }
        } catch (Exception ignored) {
            // Sin acceso al método oculto: queda el resaltado del tema.
        }
    }
}
