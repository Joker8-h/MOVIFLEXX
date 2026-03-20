package com.arlys.moviflexx.model;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.controller.Notificaciones;


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

        SessionManager session  = new SessionManager(activity);
        int            idUsuario = session.getIdUsuario();

        if (idUsuario <= 0) {
            tvBadge.setVisibility(View.GONE);
            return;
        }

        // Paso 1: contar notificaciones no leídas
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
                    // Paso 2: sumar pagos pendientes al badge
                    contarPagosPendientesYActualizarBadge(activity, tvBadge, noLeidas, idUsuario);
                },
                error -> activity.runOnUiThread(() -> tvBadge.setVisibility(View.GONE))
        );
    }

    private static void contarPagosPendientesYActualizarBadge(
            Activity activity, TextView tvBadge, int countNotif, int idUsuario) {

        ConexionApi.getInstance(activity).getArrayNoCache(
                Constantes.MIS_RESERVAS,
                response -> {
                    // Contar viajes finalizados sin pago verificado
                    java.util.List<Integer> candidatos = new java.util.ArrayList<>();
                    for (int i = 0; i < response.length(); i++) {
                        org.json.JSONObject reserva = response.optJSONObject(i);
                        if (reserva == null) continue;
                        org.json.JSONObject viaje = reserva.optJSONObject("viaje");
                        if (viaje == null) continue;
                        String estViaje = viaje.optString("estado", "").toUpperCase().trim();
                        if (!"FINALIZADO".equals(estViaje) && !"COMPLETADO".equals(estViaje)) continue;
                        int viajeId = reserva.optInt("idViajes", 0);
                        if (viajeId == 0)
                            viajeId = viaje.optInt("idViajes", viaje.optInt("id", 0));
                        if (viajeId > 0) candidatos.add(viajeId);
                    }
                    // Verificar cuáles no tienen pago
                    verificarPagosYActualizarBadge(
                            activity, tvBadge, countNotif, idUsuario, candidatos, 0, new int[]{0});
                },
                error -> activity.runOnUiThread(() -> {
                    // Si falla, al menos mostrar las notificaciones normales
                    if (countNotif > 0) {
                        tvBadge.setVisibility(View.VISIBLE);
                        tvBadge.setText(countNotif > 99 ? "99+" : String.valueOf(countNotif));
                    } else {
                        tvBadge.setVisibility(View.GONE);
                    }
                })
        );
    }

    private static void verificarPagosYActualizarBadge(
            Activity activity, TextView tvBadge, int countNotif,
            int idUsuario, java.util.List<Integer> candidatos,
            int indice, int[] sinPagar) {

        if (indice >= candidatos.size()) {
            // Todos verificados — actualizar badge
            final int total = countNotif + sinPagar[0];
            activity.runOnUiThread(() -> {
                if (total > 0) {
                    tvBadge.setVisibility(View.VISIBLE);
                    tvBadge.setText(total > 99 ? "99+" : String.valueOf(total));
                } else {
                    tvBadge.setVisibility(View.GONE);
                }
            });
            return;
        }

        int viajeId = candidatos.get(indice);
        ConexionApi.getInstance(activity).getObjectNoCache(
                Constantes.pagoDeUsuarioEnViaje(viajeId, idUsuario),
                response -> {
                    // Pago existe → no contar
                    verificarPagosYActualizarBadge(
                            activity, tvBadge, countNotif, idUsuario,
                            candidatos, indice + 1, sinPagar);
                },
                error -> {
                    // Pago no existe → contar como pendiente
                    sinPagar[0]++;
                    verificarPagosYActualizarBadge(
                            activity, tvBadge, countNotif, idUsuario,
                            candidatos, indice + 1, sinPagar);
                }
        );
    }
}