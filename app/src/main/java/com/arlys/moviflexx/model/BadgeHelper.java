package com.arlys.moviflexx.model;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomnavigation.BottomNavigationMenuView;
import com.google.android.material.bottomnavigation.BottomNavigationItemView;

/**
 * Helper para mostrar/ocultar badge con número en un ítem del BottomNavigationView.
 * Uso: BadgeHelper.mostrar(nav, R.id.nav_mensajes, 3);
 *      BadgeHelper.ocultar(nav, R.id.nav_mensajes);
 */
public class BadgeHelper {

    private static final String TAG_BADGE = "badge_moviflexx";

    public static void mostrar(BottomNavigationView nav, int itemId, int count) {
        if (nav == null) return;
        BottomNavigationMenuView menuView = (BottomNavigationMenuView) nav.getChildAt(0);
        if (menuView == null) return;

        // Encontrar el índice del ítem
        int index = -1;
        for (int i = 0; i < menuView.getChildCount(); i++) {
            View child = menuView.getChildAt(i);
            if (child instanceof BottomNavigationItemView) {
                BottomNavigationItemView itemView = (BottomNavigationItemView) child;
                if (itemView.getId() == itemId) { index = i; break; }
            }
        }
        if (index < 0) {
            // Fallback: buscar por posición en el menú
            for (int i = 0; i < nav.getMenu().size(); i++) {
                if (nav.getMenu().getItem(i).getItemId() == itemId) { index = i; break; }
            }
        }
        if (index < 0 || index >= menuView.getChildCount()) return;

        View itemView = menuView.getChildAt(index);
        if (!(itemView instanceof FrameLayout)) return;
        FrameLayout item = (FrameLayout) itemView;

        // Eliminar badge anterior si existe
        View badgeAnterior = item.findViewWithTag(TAG_BADGE);
        if (badgeAnterior != null) item.removeView(badgeAnterior);

        if (count <= 0) return;

        Context ctx = nav.getContext();
        float dp = ctx.getResources().getDisplayMetrics().density;

        TextView badge = new TextView(ctx);
        badge.setTag(TAG_BADGE);
        badge.setText(count > 99 ? "99+" : String.valueOf(count));
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(9f);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setMinWidth((int)(16 * dp));
        badge.setMinHeight((int)(16 * dp));
        badge.setPadding((int)(3*dp), 0, (int)(3*dp), 0);

        // Fondo circular rojo
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        bg.setColor(Color.parseColor("#FF3B30"));
        badge.setBackground(bg);

        // Posición: esquina superior derecha del ícono
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.topMargin  = (int)(6  * dp);
        params.leftMargin = (int)(12 * dp);
        badge.setLayoutParams(params);

        item.addView(badge);
    }

    public static void ocultar(BottomNavigationView nav, int itemId) {
        mostrar(nav, itemId, 0);
    }
}