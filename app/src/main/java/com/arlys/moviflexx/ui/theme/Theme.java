package com.arlys.moviflexx.ui.theme;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Color;

public class Theme {

    public static void applyGradientBackground(View view, boolean isDarkMode) {
        if (view instanceof LinearLayout) {
            LinearLayout layout = (LinearLayout) view;
            GradientDrawable gradient = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    isDarkMode ?
                            new int[]{Color.parseColor("#FF4A0D9A"), Color.parseColor("#FF7B3EC9")} :  // Oscuro
                            new int[]{Color.parseColor("#FF6A11CE"), Color.parseColor("#FF9D4EDD")}    // Claro
            );
            gradient.setCornerRadius(50f);
            layout.setBackground(gradient);
        }
    }

    // Método para obtener colores según modo
    public static int getPrimaryColor(boolean isDarkMode) {
        return isDarkMode ?
                Color.parseColor("#FFBB86FC") :  // Morado claro para oscuro
                Color.parseColor("#FF6200EE");   // Morado para claro
    }

    public static int getBackgroundColor(boolean isDarkMode) {
        return isDarkMode ?
                Color.parseColor("#FF121212") :  // Fondo oscuro
                Color.WHITE;                     // Fondo claro
    }
}