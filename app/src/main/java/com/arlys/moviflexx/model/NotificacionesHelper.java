package com.arlys.moviflexx.model;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.controller.Notificaciones;

/**
 * Helper reutilizable para cargar el badge de notificaciones
 * y configurar la navegación a NotificacionesActivity.
 *
 * Uso en HomePasajero, HomeConductor y Mensajes:
 *
 *   @Override protected void onResume() {
 *       super.onResume();
 *       NotificacionesHelper.configurar(this);
 *   }
 */
public class NotificacionesHelper {

    public static void configurar(Activity activity) {
        FrameLayout btnNotif = activity.findViewById(R.id.btn_notificaciones);
        TextView    tvBadge  = activity.findViewById(R.id.tv_badge_notif);

        if (btnNotif == null) return;

        btnNotif.setOnClickListener(v ->
                activity.startActivity(new Intent(activity, Notificaciones.class)));

        cargarBadge(activity, tvBadge);
    }

    private static void cargarBadge(Activity activity, TextView tvBadge) {
        if (tvBadge == null) return;

        // Obtener el ID del usuario desde la sesión
        SessionManager session = new SessionManager(activity);
        int idUsuario = session.getIdUsuario();

        if (idUsuario <= 0) {
            tvBadge.setVisibility(View.GONE);
            return;
        }

        // URL real: GET /api/notificaciones/usuario/:idUsuario
        String url = Constantes.misNotificaciones(idUsuario);

        ConexionApi.getInstance(activity).getArrayNoCache(
                url,
                response -> {
                    int noLeidas = 0;
                    for (int i = 0; i < response.length(); i++) {
                        org.json.JSONObject obj = response.optJSONObject(i);
                        if (obj == null) continue;
                        boolean leido = obj.optInt("leido", 0) == 1
                                || obj.optBoolean("leido", false);
                        if (!leido) noLeidas++;
                    }
                    final int count = noLeidas;
                    activity.runOnUiThread(() -> {
                        if (count > 0) {
                            tvBadge.setVisibility(View.VISIBLE);
                            tvBadge.setText(count > 99 ? "99+" : String.valueOf(count));
                        } else {
                            tvBadge.setVisibility(View.GONE);
                        }
                    });
                },
                error -> activity.runOnUiThread(() -> tvBadge.setVisibility(View.GONE))
        );
    }
}