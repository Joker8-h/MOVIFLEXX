package com.arlys.moviflexx.adapter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.controller.CalificacionController;
import com.arlys.moviflexx.controller.DetalleViajeActivity;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.Manager.CalificacionesManager;
import com.arlys.moviflexx.model.SessionManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ViajesAdapter extends RecyclerView.Adapter<ViajesAdapter.ViajeViewHolder> {

    private static final String TAG = "ViajesAdapter";

    private static final String EST_CREADO     = "CREADO";
    private static final String EST_PROGRAMADO = "PROGRAMADO";
    private static final String EST_DISPONIBLE = "DISPONIBLE";
    private static final String EST_EN_CURSO   = "EN_CURSO";
    private static final String EST_INICIADO   = "INICIADO";
    private static final String EST_FINALIZADO = "FINALIZADO";
    private static final String EST_CANCELADO  = "CANCELADO";

    private static final java.util.Set<String> ESTADOS_A_BORDO = new java.util.HashSet<>(
            java.util.Arrays.asList("EN_CURSO", "INICIADO", "RECOGIDO", "COMPLETADO", "FINALIZADO")
    );

    private static final java.util.Set<Integer> viajesPagoMostrado =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    public interface OnViajeClickListener {
        void onViajeClick(int viajeId);
    }

    private final Context               context;
    private final List<JSONObject>      viajes;
    private final boolean               esConductor;
    private final Map<Integer, String>  cacheConductores = new HashMap<>();
    private final Map<Integer, Boolean> cacheCalificado  = new HashMap<>();

    private OnViajeClickListener viajeClickListener;

    public ViajesAdapter(Context context, List<JSONObject> viajes) {
        this.context     = context;
        this.viajes      = viajes;
        this.esConductor = new SessionManager(context).isConductor();
    }

    public void setOnViajeClickListener(OnViajeClickListener listener) {
        this.viajeClickListener = listener;
    }

    private Activity resolveActivity(Context ctx) {
        if (ctx instanceof Activity) return (Activity) ctx;
        if (ctx instanceof ContextWrapper) return resolveActivity(((ContextWrapper) ctx).getBaseContext());
        return null;
    }

    @NonNull
    @Override
    public ViajeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_viaje, parent, false);
        return new ViajeViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViajeViewHolder h, int position) {
        JSONObject v    = viajes.get(position);
        JSONObject ruta = v.optJSONObject("ruta");
        JSONObject veh  = v.optJSONObject("vehiculo");
        JSONObject cond = v.optJSONObject("conductor");

        int    viajeId = v.optInt("idViajes", v.optInt("id", 0));
        int    idRuta  = v.optInt("idRuta", ruta != null ? ruta.optInt("idRuta", 0) : 0);
        String estado  = v.optString("estado", EST_CREADO).trim().toUpperCase();

        // ── RUTA ──────────────────────────────────────────────────────────────
        if (ruta != null) {
            String origen = ruta.optString("origen", "");
            String desc   = ruta.optString("descripcion", ruta.optString("nombre", ""));
            StringBuilder sb = new StringBuilder();
            if (!origen.isEmpty()) sb.append(origen);
            if (!desc.isEmpty())   sb.append("\nRuta: ").append(desc);
            if (idRuta > 0)        sb.append("\nRuta #").append(idRuta);
            h.txtRuta.setText(sb.length() > 0 ? sb.toString() : "Ruta no disponible");
        } else {
            h.txtRuta.setText(idRuta > 0 ? "Ruta #" + idRuta : "Ruta no disponible");
        }

        // ── FECHA — el XML ya tiene el ícono 🕒, solo ponemos texto ──────────
        String fecha = v.optString("fechaHoraSalida", "");
        if (fecha.contains("T")) fecha = fecha.replace("T", " ").replaceAll("\\.\\d{3}Z$", "");
        if (fecha.length() >= 16) fecha = fecha.substring(0, 16);
        h.txtFecha.setText(fecha.isEmpty() ? "--" : fecha);

        // ── CUPOS — el XML ya tiene el ícono 👥 ───────────────────────────────
        int cuposDisp = v.optInt("cuposDisponibles", 0);
        int cuposTot  = v.optInt("cuposTotales", cuposDisp);
        String txtCupo;
        if (cuposTot > 8 || cuposDisp > 8) {
            txtCupo = "Cupos: (actualiza tu vehículo)";
            cuposDisp = 1;
        } else {
            txtCupo = cuposDisp + " / " + cuposTot + " cupos";
        }
        h.txtCupos.setText(txtCupo);

        // ── PRECIO — el XML ya tiene el ícono 💰 ─────────────────────────────
        double precio = v.optDouble("precio", -1);
        if (precio < 0) {
            try { precio = Double.parseDouble(v.optString("precio", "0")); }
            catch (Exception ignored) { precio = 0; }
        }
        if (precio <= 0) {
            double km = v.optDouble("distanciaKm", v.optDouble("distancia", 0));
            if (km > 0) precio = Math.ceil((km * 700.0) / 100.0) * 100.0;
        }
        if (precio <= 0 && ruta != null) {
            precio = ruta.optDouble("precio",
                    ruta.optDouble("costoPorPasajero",
                            ruta.optDouble("costoCombustible",
                                    ruta.optDouble("precioSugerido", 0))));
            if (precio <= 0) {
                double km = ruta.optDouble("distanciaKm", ruta.optDouble("distancia", 0));
                if (km > 0) precio = Math.ceil((km * 700.0) / 100.0) * 100.0;
            }
            if (precio <= 0) {
                String desc = ruta.optString("descripcion", "");
                try {
                    java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("([\\d.]+)\\s*km").matcher(desc);
                    if (m.find()) {
                        double km = Double.parseDouble(m.group(1));
                        if (km > 0) precio = Math.ceil((km * 700.0) / 100.0) * 100.0;
                    }
                } catch (Exception ignored) {}
            }
        }
        // Solo número — el XML ya tiene el ícono 💰
        h.txtPrecio.setText(precio > 0
                ? String.format(Locale.getDefault(), "$%,.0f COP", precio)
                : "Precio no definido");

        // ── CONDUCTOR — el XML ya tiene íconos de persona y teléfono ─────────
        SessionManager session = new SessionManager(context);
        if (esConductor) {
            String nom = cond != null ? cond.optString("nombre", session.getNombre()) : session.getNombre();
            String tel = cond != null
                    ? cond.optString("telefono", cond.optString("celular", session.getTelefono()))
                    : session.getTelefono();
            h.txtConductor.setText(nom);
            h.txtTelefono.setText(tel == null || tel.isEmpty() ? "N/A" : tel);
        } else {
            int idCond = v.optInt("idConductor", v.optInt("conductorId", v.optInt("conductor_id", -1)));
            if (idCond <= 0 && veh != null) idCond = veh.optInt("idUsuario", -1);
            if (idCond > 0) {
                if (cacheConductores.containsKey(idCond)) {
                    h.txtConductor.setText(cacheConductores.get(idCond));
                    h.txtTelefono.setText("");
                } else {
                    h.txtConductor.setText("Cargando...");
                    h.txtTelefono.setText("");
                    cargarNombreConductor(idCond, h);
                }
            } else {
                h.txtConductor.setText("Conductor");
                if (veh != null) {
                    String marca = veh.optString("marca", "");
                    String placa = veh.optString("placa", "");
                    h.txtTelefono.setText(!marca.isEmpty()
                            ? marca + (!placa.isEmpty() ? " · " + placa : "") : "");
                } else h.txtTelefono.setText("");
            }
        }

        // ── VEHICULO — el XML ya tiene el ícono 🚘 ────────────────────────────
        if (veh != null) {
            h.txtVehiculo.setText(veh.optString("marca", "") + " "
                    + veh.optString("modelo", "") + " (" + veh.optString("placa", "---") + ")");
        } else {
            String n = session.getVehiculoNombre();
            String p = session.getVehiculoPlaca();
            h.txtVehiculo.setText(n != null && !n.isEmpty()
                    ? n + " (" + p + ")" : "Vehículo no asignado");
        }

        // ── SECCIÓN PASAJEROS (solo conductor) ───────────────────────────────
        final int    fViajeId = viajeId;
        final String fEstado  = estado;
        final int    fIdCond  = session.getIdUsuario();

        if (esConductor) {
            boolean mostrarPasajeros =
                    EST_CREADO.equals(estado)     || EST_PROGRAMADO.equals(estado) ||
                            EST_DISPONIBLE.equals(estado) || EST_EN_CURSO.equals(estado)   ||
                            EST_INICIADO.equals(estado)   || EST_FINALIZADO.equals(estado);

            if (h.layoutSeccionPasajeros != null) {
                if (mostrarPasajeros) {
                    h.layoutSeccionPasajeros.setVisibility(View.VISIBLE);
                    cargarPasajerosRobusto(h, fViajeId, fEstado, fIdCond);
                } else {
                    h.layoutSeccionPasajeros.setVisibility(View.GONE);
                }
            }
        } else {
            if (h.layoutSeccionPasajeros != null) h.layoutSeccionPasajeros.setVisibility(View.GONE);
        }

        // ── RESET BOTONES ─────────────────────────────────────────────────────
        h.btnAceptar.setVisibility(View.GONE);
        h.btnIniciar.setVisibility(View.GONE);
        h.btnFinalizar.setVisibility(View.GONE);
        h.btnCancelar.setVisibility(View.GONE);
        h.btnDetalle.setVisibility(View.GONE);
        h.btnCalificar.setVisibility(View.GONE);

        final int fIdConductor;
        {
            int tmp = v.optInt("idConductor", v.optInt("conductorId", -1));
            if (tmp <= 0 && cond != null) {
                for (String k : new String[]{"id","idUsuarios","idUsuario"}) {
                    int id = cond.optInt(k, -1);
                    if (id > 0) { tmp = id; break; }
                }
            }
            fIdConductor = tmp;
        }

        // ── BOTONES SEGÚN ROL + ESTADO ────────────────────────────────────────
        if (esConductor) {
            switch (estado) {
                case EST_CREADO: case EST_PROGRAMADO: case EST_DISPONIBLE:
                    h.btnIniciar.setVisibility(View.VISIBLE);
                    h.btnCancelar.setVisibility(View.VISIBLE);
                    break;
                case EST_EN_CURSO: case EST_INICIADO:
                    h.btnFinalizar.setVisibility(View.VISIBLE);
                    h.btnDetalle.setVisibility(View.VISIBLE);
                    break;
                case EST_FINALIZADO:
                    h.btnDetalle.setVisibility(View.VISIBLE);
                    mostrarBtnCalificarSiPendiente(h, fViajeId, session.getIdUsuario(), 0);
                    break;
                default:
                    h.btnDetalle.setVisibility(View.VISIBLE);
                    break;
            }
        } else {
            boolean estadoReservable = EST_EN_CURSO.equals(estado) || EST_INICIADO.equals(estado)
                    || EST_CREADO.equals(estado) || EST_PROGRAMADO.equals(estado) || EST_DISPONIBLE.equals(estado);
            boolean hayCupos = cuposDisp > 0 || (cuposTot > 0 && cuposDisp == 0);

            if (estadoReservable) {
                if (hayCupos) {
                    h.btnAceptar.setVisibility(View.VISIBLE);
                    h.btnAceptar.setText(cuposDisp > 0 ? "Reservar (" + cuposDisp + ")" : "Reservar");
                }
                h.btnDetalle.setVisibility(View.VISIBLE);
            } else if (EST_FINALIZADO.equals(estado)) {
                h.btnDetalle.setVisibility(View.VISIBLE);
                mostrarBtnCalificarSiPendiente(h, fViajeId, session.getIdUsuario(), fIdConductor);
                verificarYMostrarPagoPasajero(fViajeId, fIdConductor);
            } else if (!EST_CANCELADO.equals(estado)) {
                h.btnDetalle.setVisibility(View.VISIBLE);
            }
        }

        // ── LISTENERS ─────────────────────────────────────────────────────────
        View.OnClickListener irDetalle = vw -> {
            if (viajeClickListener != null) viajeClickListener.onViajeClick(fViajeId);
            abrirDetalle(fViajeId);
        };
        h.itemView.setOnClickListener(irDetalle);
        h.btnAceptar.setOnClickListener(irDetalle);
        h.btnDetalle.setOnClickListener(irDetalle);
        h.btnIniciar.setOnClickListener(vw -> cambiarEstado(fViajeId, Constantes.viajeIniciar((long) fViajeId), "iniciado"));
        h.btnCancelar.setOnClickListener(vw -> confirmarCancelar(fViajeId));
        h.btnFinalizar.setOnClickListener(vw -> confirmarYFinalizar(fViajeId));
        h.btnCalificar.setOnClickListener(vw -> iniciarFlujoCalificacion(fViajeId, session.getIdUsuario()));
    }

    // =========================================================================
    //  CARGA ROBUSTA DE PASAJEROS
    // =========================================================================
    private void cargarPasajerosRobusto(ViajeViewHolder h, int viajeId, String estado, int idConductor) {
        if (h.layoutPasajerosLista == null) return;
        h.layoutPasajerosLista.removeAllViews();
        if (h.loaderPasajeros      != null) h.loaderPasajeros.setVisibility(View.VISIBLE);
        if (h.txtSinPasajeros      != null) h.txtSinPasajeros.setVisibility(View.GONE);
        if (h.txtContadorPasajeros != null) h.txtContadorPasajeros.setText("...");

        ConexionApi.getInstance(context).getObjectNoCache(Constantes.viajePorId((long) viajeId),
                viajeObj -> {
                    JSONArray usuarios = viajeObj.optJSONArray("usuarios");
                    if (usuarios != null && usuarios.length() > 0) {
                        JSONArray reservasNormalizadas = normalizarUsuariosAReservas(usuarios);
                        procesarReservasEnCard(h, reservasNormalizadas, estado, viajeId, idConductor);
                    } else {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (!h.itemView.isAttachedToWindow()) return;
                            if (h.loaderPasajeros      != null) h.loaderPasajeros.setVisibility(View.GONE);
                            if (h.txtContadorPasajeros != null) h.txtContadorPasajeros.setText("0");
                            if (h.txtSinPasajeros      != null) {
                                h.txtSinPasajeros.setVisibility(View.VISIBLE);
                                boolean enCurso    = EST_EN_CURSO.equals(estado) || EST_INICIADO.equals(estado);
                                boolean finalizado = EST_FINALIZADO.equals(estado);
                                h.txtSinPasajeros.setText(
                                        finalizado ? "No hubo pasajeros en este viaje" :
                                                enCurso    ? "Sin pasajeros a bordo aun" :
                                                        "Sin pasajeros reservados aun");
                            }
                        });
                    }
                },
                err -> mostrarErrorCargaPasajeros(h)
        );
    }

    private JSONArray normalizarUsuariosAReservas(JSONArray usuarios) {
        JSONArray result = new JSONArray();
        for (int i = 0; i < usuarios.length(); i++) {
            try {
                JSONObject u = usuarios.getJSONObject(i);
                JSONObject reserva = new JSONObject();
                reserva.put("estado",         u.optString("estado", "ACTIVA"));
                reserva.put("numeroAsientos", u.optInt("numeroAsientos", 1));
                JSONObject usuarioObj = u.optJSONObject("usuario");
                if (usuarioObj != null) {
                    int idU = usuarioObj.optInt("idUsuarios", usuarioObj.optInt("id", 0));
                    if (idU > 0) usuarioObj.put("id", idU);
                    reserva.put("pasajero", usuarioObj);
                    reserva.put("usuario",  usuarioObj);
                } else {
                    int idU = u.optInt("idUsuarios", 0);
                    JSONObject fallback = new JSONObject();
                    fallback.put("id",         idU);
                    fallback.put("idUsuarios", idU);
                    fallback.put("nombre",     "Pasajero");
                    reserva.put("pasajero", fallback);
                    reserva.put("usuario",  fallback);
                }
                result.put(reserva);
            } catch (Exception e) {
                Log.e(TAG, "normalizarUsuarios: " + e.getMessage());
            }
        }
        return result;
    }

    // =========================================================================
    //  PROCESAR RESERVAS EN CARD
    // =========================================================================
    private void procesarReservasEnCard(ViajeViewHolder h, JSONArray reservas,
                                        String estado, int viajeId, int idConductor) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (!h.itemView.isAttachedToWindow()) return;
            if (h.loaderPasajeros != null) h.loaderPasajeros.setVisibility(View.GONE);
            if (h.layoutPasajerosLista == null) return;
            h.layoutPasajerosLista.removeAllViews();

            boolean esFinalizado = EST_FINALIZADO.equals(estado);
            boolean esEnCurso    = EST_EN_CURSO.equals(estado) || EST_INICIADO.equals(estado);

            java.util.List<JSONObject> aBordo    = new java.util.ArrayList<>();
            java.util.List<JSONObject> reservados = new java.util.ArrayList<>();

            if (reservas != null) {
                for (int i = 0; i < reservas.length(); i++) {
                    JSONObject res = reservas.optJSONObject(i);
                    if (res == null) continue;
                    String estRes = res.optString("estado", "").toUpperCase().trim();
                    if (!esFinalizado && ("CANCELADO".equals(estRes) || "CANCELADA".equals(estRes))) continue;
                    if (ESTADOS_A_BORDO.contains(estRes)) aBordo.add(res);
                    else reservados.add(res);
                }
            }

            int totalMostrados = 0;
            float d = context.getResources().getDisplayMetrics().density;

            if (!aBordo.isEmpty() && (esEnCurso || esFinalizado)) {
                agregarSubHeader(h.layoutPasajerosLista, "A bordo (" + aBordo.size() + ")", "#00695C", "#E0F7FA", d);
                for (int i = 0; i < aBordo.size(); i++) {
                    agregarFilaPasajeroDesdeReserva(h.layoutPasajerosLista, aBordo.get(i), esFinalizado, viajeId, idConductor, totalMostrados, true);
                    totalMostrados++;
                }
            }

            if (!reservados.isEmpty()) {
                String labelReservados = esEnCurso
                        ? "Esperando recogida (" + reservados.size() + ")"
                        : "Reservados (" + reservados.size() + ")";
                if (!aBordo.isEmpty())
                    agregarSubHeader(h.layoutPasajerosLista, labelReservados, "#1565C0", "#E3F2FD", d);
                for (int i = 0; i < reservados.size(); i++) {
                    agregarFilaPasajeroDesdeReserva(h.layoutPasajerosLista, reservados.get(i), esFinalizado, viajeId, idConductor, totalMostrados, false);
                    totalMostrados++;
                }
            }

            if (h.txtContadorPasajeros != null) h.txtContadorPasajeros.setText(String.valueOf(totalMostrados));

            if (totalMostrados == 0 && h.txtSinPasajeros != null) {
                h.txtSinPasajeros.setVisibility(View.VISIBLE);
                h.txtSinPasajeros.setText(esFinalizado ? "No hubo pasajeros en este viaje"
                        : esEnCurso ? "Sin pasajeros a bordo aún" : "Sin pasajeros reservados aún");
            }
        });
    }

    private void agregarSubHeader(LinearLayout container, String texto,
                                  String colorTexto, String colorFondo, float d) {
        TextView tv = new TextView(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int)(4*d); lp.bottomMargin = (int)(4*d);
        tv.setLayoutParams(lp);
        tv.setText(texto);
        tv.setTextSize(11.5f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(Color.parseColor(colorTexto));
        tv.setPadding((int)(10*d), (int)(5*d), (int)(10*d), (int)(5*d));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(8*d);
        bg.setColor(Color.parseColor(colorFondo));
        tv.setBackground(bg);
        container.addView(tv);
    }

    private void agregarFilaPasajeroDesdeReserva(LinearLayout container, JSONObject res,
                                                 boolean esFinalizado, int viajeId,
                                                 int idConductor, int index, boolean aBordo) {
        int    idP      = -1;
        String nomP     = "";
        int    asientos = res.optInt("numeroAsientos", res.optInt("asientos", 1));
        String estRes   = res.optString("estado", "").toUpperCase().trim();

        JSONObject po = null;
        for (String k : new String[]{"pasajero","usuario","user","passenger"}) {
            JSONObject c = res.optJSONObject(k);
            if (c != null) { po = c; break; }
        }
        if (po != null) {
            for (String k : new String[]{"id","idUsuarios","idUsuario","userId"}) {
                int id = po.optInt(k, -1); if (id > 0) { idP = id; break; }
            }
            for (String k : new String[]{"nombre","nombreCompleto","name","nombres"}) {
                String n = po.optString(k, "");
                if (!n.isEmpty() && !n.equals("null")) { nomP = n; break; }
            }
            if (nomP.isEmpty()) {
                String n = po.optString("nombres",""), a = po.optString("apellidos","");
                if (!n.isEmpty() || !a.isEmpty()) nomP = (n + " " + a).trim();
            }
        }
        if (idP <= 0) {
            for (String k : new String[]{"idUsuarios","idUsuario","idPasajero","pasajeroId"}) {
                int id = res.optInt(k, -1); if (id > 0) { idP = id; break; }
            }
        }
        if (nomP.isEmpty()) nomP = "Pasajero";

        agregarFilaPasajero(container, idP, nomP, asientos, estRes,
                esFinalizado, viajeId, idConductor, index, aBordo);
    }

    private void agregarFilaPasajero(LinearLayout container, int idPasajero, String nombre,
                                     int asientos, String estadoReserva, boolean esFinalizado,
                                     int viajeId, int idConductor, int index, boolean aBordo) {
        float d  = context.getResources().getDisplayMetrics().density;
        int p4   = (int)(4*d), p6 = (int)(6*d), p8 = (int)(8*d);

        LinearLayout fila = new LinearLayout(context);
        LinearLayout.LayoutParams lpF = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpF.bottomMargin = (int)(6*d);
        fila.setLayoutParams(lpF);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setGravity(Gravity.CENTER_VERTICAL);
        fila.setPadding(p8, p6, p8, p6);

        GradientDrawable bgFila = new GradientDrawable();
        bgFila.setShape(GradientDrawable.RECTANGLE);
        bgFila.setCornerRadius(12*d);
        if (aBordo) {
            bgFila.setColor(Color.parseColor("#E0F7FA"));
            bgFila.setStroke((int)(1*d), Color.parseColor("#80DEEA"));
        } else {
            bgFila.setColor(index % 2 == 0 ? Color.parseColor("#F0FFFE") : Color.parseColor("#FFFFFF"));
            bgFila.setStroke((int)(1*d), Color.parseColor("#E0F2F1"));
        }
        fila.setBackground(bgFila);

        // Avatar
        TextView avatar = new TextView(context);
        LinearLayout.LayoutParams lpAv = new LinearLayout.LayoutParams((int)(34*d), (int)(34*d));
        lpAv.rightMargin = p8;
        avatar.setLayoutParams(lpAv);
        avatar.setGravity(Gravity.CENTER);
        avatar.setTextColor(Color.WHITE);
        avatar.setTextSize(13f);
        avatar.setTypeface(null, Typeface.BOLD);
        avatar.setText(nombre.isEmpty() ? "P" : nombre.substring(0,1).toUpperCase());
        int[] cols = {0xFF3DCFC7, 0xFF1A2035, 0xFFFFA726, 0xFFEF5350, 0xFF42A5F5};
        GradientDrawable bgAv = new GradientDrawable();
        bgAv.setShape(GradientDrawable.OVAL);
        bgAv.setColor(aBordo ? 0xFF00838F : cols[index % cols.length]);
        avatar.setBackground(bgAv);
        fila.addView(avatar);

        // Columna texto
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvNom = new TextView(context);
        tvNom.setText(nombre);
        tvNom.setTextSize(13f);
        tvNom.setTypeface(null, Typeface.BOLD);
        tvNom.setTextColor(Color.parseColor("#1A2035"));
        tvNom.setMaxLines(1);
        tvNom.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(tvNom);

        LinearLayout sub = new LinearLayout(context);
        sub.setOrientation(LinearLayout.HORIZONTAL);
        sub.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpSub = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpSub.topMargin = (int)(2*d);
        sub.setLayoutParams(lpSub);

        // Asientos sin emoji (el XML tiene el ícono)
        TextView tvAs = new TextView(context);
        tvAs.setText("" + asientos);
        tvAs.setTextSize(11f);
        tvAs.setTextColor(Color.parseColor("#6B7280"));
        sub.addView(tvAs);

        // Badge de estado sin emojis
        TextView badge = new TextView(context);
        badge.setText(etiquetaBadge(estadoReserva));
        badge.setTextSize(10f);
        badge.setTextColor(Color.WHITE);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setPadding(p6, (int)(1*d), p6, (int)(1*d));
        LinearLayout.LayoutParams lpBadge = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpBadge.leftMargin = p6;
        badge.setLayoutParams(lpBadge);
        GradientDrawable bgBadge = new GradientDrawable();
        bgBadge.setShape(GradientDrawable.RECTANGLE);
        bgBadge.setCornerRadius(20*d);
        bgBadge.setColor(colorBadge(estadoReserva));
        badge.setBackground(bgBadge);
        sub.addView(badge);
        col.addView(sub);
        fila.addView(col);

        // Botón calificar (solo finalizados)
        if (esFinalizado && idPasajero > 0) {
            Button btnCal = new Button(context);
            LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams((int)(38*d), (int)(30*d));
            lpBtn.leftMargin = p4;
            btnCal.setLayoutParams(lpBtn);
            btnCal.setText("★");
            btnCal.setTextSize(12f);
            btnCal.setTextColor(Color.parseColor("#1A2035"));
            btnCal.setPadding(0, 0, 0, 0);
            GradientDrawable bgBtn = new GradientDrawable();
            bgBtn.setShape(GradientDrawable.RECTANGLE);
            bgBtn.setCornerRadius(14*d);
            bgBtn.setColor(Color.parseColor("#FFF8E1"));
            bgBtn.setStroke((int)(1*d), Color.parseColor("#FFE082"));
            btnCal.setBackground(bgBtn);

            final int    fIdP  = idPasajero;
            final String fNomP = nombre;
            final float  fDens = d;

            new CalificacionesManager(context).verificarCalificacion(viajeId, idConductor, fIdP,
                    new CalificacionesManager.OnVerificacionListener() {
                        @Override public void onDebeCalificar() {
                            if (container.isAttachedToWindow())
                                container.post(() -> btnCal.setVisibility(View.VISIBLE));
                        }
                        @Override public void onYaCalifico(int p, String e) {
                            if (container.isAttachedToWindow())
                                container.post(() -> marcarCalificado(btnCal, fDens));
                        }
                    });

            btnCal.setOnClickListener(vw -> {
                Activity act = resolveActivity(context);
                if (act == null || act.isFinishing()) return;
                CalificacionController.mostrarBottomSheetCalificar(act, viajeId, fIdP, fNomP,
                        idConductor, true, (pun, com) -> marcarCalificado(btnCal, fDens));
            });
            fila.addView(btnCal);
        }

        container.addView(fila);
    }

    private void marcarCalificado(Button btn, float d) {
        btn.setText("✓");
        btn.setEnabled(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(14*d);
        bg.setColor(Color.parseColor("#F0FFF4"));
        bg.setStroke((int)(1*d), Color.parseColor("#A7F3D0"));
        btn.setBackground(bg);
    }

    // Sin emojis en las etiquetas
    private String etiquetaBadge(String e) {
        switch (e.toUpperCase()) {
            case "ACTIVA": case "CONFIRMADA":     return "Confirmada";
            case "PENDIENTE":                     return "Pendiente";
            case "EN_CURSO": case "INICIADO":     return "A bordo";
            case "ESPERANDO_RECOGIDA":            return "Esperando";
            case "RECOGIDO":                      return "Recogido";
            case "CANCELADO": case "CANCELADA":   return "Cancelada";
            case "COMPLETADO": case "FINALIZADO": return "Completada";
            default:                              return e;
        }
    }

    private int colorBadge(String e) {
        switch (e.toUpperCase()) {
            case "ACTIVA": case "CONFIRMADA":     return Color.parseColor("#2E7D32");
            case "PENDIENTE":                     return Color.parseColor("#F57F17");
            case "EN_CURSO": case "INICIADO":
            case "RECOGIDO":                      return Color.parseColor("#00838F");
            case "ESPERANDO_RECOGIDA":            return Color.parseColor("#E65100");
            case "CANCELADO": case "CANCELADA":   return Color.parseColor("#B71C1C");
            case "COMPLETADO": case "FINALIZADO": return Color.parseColor("#1565C0");
            default:                              return Color.parseColor("#546E7A");
        }
    }

    // =========================================================================
    //  BTN CALIFICAR zona botones
    // =========================================================================
    private void mostrarBtnCalificarSiPendiente(ViajeViewHolder h, int viajeId,
                                                int idCalificador, int idCalificado) {
        if (Boolean.TRUE.equals(cacheCalificado.get(viajeId))) {
            h.btnCalificar.setVisibility(View.GONE);
            return;
        }
        h.btnCalificar.setVisibility(View.VISIBLE);
        h.btnCalificar.setText("Calificar viaje");
        if (idCalificado <= 0) return;

        new CalificacionesManager(context).verificarCalificacion(viajeId, idCalificador, idCalificado,
                new CalificacionesManager.OnVerificacionListener() {
                    @Override public void onDebeCalificar() {
                        cacheCalificado.put(viajeId, false);
                        if (h.itemView.isAttachedToWindow())
                            h.itemView.post(() -> h.btnCalificar.setVisibility(View.VISIBLE));
                    }
                    @Override public void onYaCalifico(int p, String e) {
                        cacheCalificado.put(viajeId, true);
                        if (h.itemView.isAttachedToWindow())
                            h.itemView.post(() -> h.btnCalificar.setVisibility(View.GONE));
                    }
                });
    }

    // =========================================================================
    //  FLUJO CALIFICACIÓN
    // =========================================================================
    private void iniciarFlujoCalificacion(int viajeId, int idCalificador) {
        if (esConductor) paso3BuscarPasajero(viajeId);
        else buscarConductorParaCalificar(viajeId, idCalificador);
    }

    private void buscarConductorParaCalificar(int viajeId, int idCalificador) {
        ConexionApi.getInstance(context).getObjectNoCache(Constantes.viajePorId((long) viajeId),
                viajeObj -> {
                    int idC = viajeObj.optInt("idConductor", viajeObj.optInt("conductorId", -1));
                    String nomC = "";
                    JSONObject co = viajeObj.optJSONObject("conductor");
                    if (co != null) {
                        if (idC <= 0) for (String k : new String[]{"id","idUsuarios","idUsuario"}) { int id=co.optInt(k,-1); if(id>0){idC=id;break;} }
                        for (String k : new String[]{"nombre","nombreCompleto","name"}) { String n=co.optString(k,""); if(!n.isEmpty()&&!n.equals("null")){nomC=n;break;} }
                    }
                    if (idC <= 0) { new Handler(Looper.getMainLooper()).post(()->Toast.makeText(context,"No se encontró el conductor",Toast.LENGTH_SHORT).show()); return; }
                    final int fId=idC; final String fNom=nomC.isEmpty()?"el conductor":nomC;
                    new Handler(Looper.getMainLooper()).post(()->abrirBottomSheetCalificacion(viajeId,idCalificador,fId,fNom,false));
                },
                err -> new Handler(Looper.getMainLooper()).post(()->Toast.makeText(context,"Error al obtener datos del viaje",Toast.LENGTH_SHORT).show()));
    }

    private void confirmarCancelar(int viajeId) {
        new AlertDialog.Builder(context).setTitle("Cancelar viaje")
                .setMessage("¿Estás seguro de que quieres cancelar este viaje?")
                .setPositiveButton("Sí, cancelar", (d,w)->cambiarEstado(viajeId,Constantes.viajeCancelar((long)viajeId),"cancelado"))
                .setNegativeButton("No", null).show();
    }

    private void confirmarYFinalizar(int viajeId) {
        new AlertDialog.Builder(context)
                .setTitle("Finalizar viaje")
                .setMessage("¿Finalizar el viaje? Se liberarán todos los cupos.")
                .setPositiveButton("Finalizar", (d,w) -> ejecutarFlujoFinalizar(viajeId))
                .setNegativeButton("Cancelar", null).show();
    }

    private void ejecutarFlujoFinalizar(int viajeId) {
        ConexionApi.getInstance(context).post(
                Constantes.viajePorId((long) viajeId) + "/pasajeros-bajaron", null,
                r1 -> paso2Finalizar(viajeId),
                err1 -> paso2Finalizar(viajeId));
    }

    private void paso2Finalizar(int viajeId) {
        ConexionApi.getInstance(context).post(
                Constantes.viajeFinalizar((long) viajeId), null,
                r2 -> new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(context, "Viaje finalizado correctamente", Toast.LENGTH_SHORT).show();
                    notifyDataSetChanged();
                    new Handler(Looper.getMainLooper()).postDelayed(() -> mostrarSheetPagosConductor(viajeId), 800);
                }),
                err2 -> new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(context, "Error al finalizar el viaje", Toast.LENGTH_LONG).show())
        );
    }

    // =========================================================================
    //  FLUJO PAGO — CONDUCTOR
    // =========================================================================
    private void mostrarSheetPagosConductor(int viajeId) {
        Activity activity = resolveActivity(context);
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        ConexionApi.getInstance(context).getObjectNoCache(Constantes.viajePorId((long) viajeId),
                viajeObj -> {
                    double montoViaje = viajeObj.optDouble("precio", 0);
                    if (montoViaje <= 0) {
                        JSONObject ruta = viajeObj.optJSONObject("ruta");
                        if (ruta != null) montoViaje = ruta.optDouble("precio", ruta.optDouble("costoCombustible", 0));
                    }
                    final double fMonto = montoViaje;

                    java.util.ArrayList<JSONObject> pasajeros = new java.util.ArrayList<>();
                    JSONArray usuarios = viajeObj.optJSONArray("usuarios");
                    if (usuarios != null) {
                        for (int i = 0; i < usuarios.length(); i++) {
                            JSONObject u = usuarios.optJSONObject(i);
                            if (u == null) continue;
                            String est = u.optString("estado","").toUpperCase();
                            if ("CANCELADO".equals(est)||"CANCELADA".equals(est)) continue;
                            pasajeros.add(u);
                        }
                    }
                    if (pasajeros.isEmpty()) {
                        new Handler(Looper.getMainLooper()).post(()->buscarPasajerosYCalificar(viajeId));
                        return;
                    }
                    ConexionApi.getInstance(context).getArrayNoCache(Constantes.pagosPorViaje(viajeId),
                            pagosArr -> {
                                java.util.Map<Integer,JSONObject> mapaPagos = new java.util.HashMap<>();
                                if (pagosArr != null) {
                                    for (int i=0;i<pagosArr.length();i++) {
                                        JSONObject p=pagosArr.optJSONObject(i); if(p==null) continue;
                                        int idU=p.optInt("idUsuario",p.optInt("idPasajero",-1));
                                        if(idU>0) mapaPagos.put(idU,p);
                                    }
                                }
                                new Handler(Looper.getMainLooper()).post(()->
                                        construirYMostrarSheetPagos(activity,viajeId,pasajeros,mapaPagos,fMonto));
                            },
                            err->new Handler(Looper.getMainLooper()).post(()->
                                    construirYMostrarSheetPagos(activity,viajeId,pasajeros,new java.util.HashMap<>(),fMonto))
                    );
                },
                err->new Handler(Looper.getMainLooper()).post(()->buscarPasajerosYCalificar(viajeId))
        );
    }

    private void construirYMostrarSheetPagos(Activity activity, int viajeId,
                                             java.util.ArrayList<JSONObject> pasajeros,
                                             java.util.Map<Integer,JSONObject> mapaPagos,
                                             double montoBase) {
        if (activity.isFinishing()||activity.isDestroyed()) return;

        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(activity, R.style.BottomSheetTheme);

        float d   = activity.getResources().getDisplayMetrics().density;
        int p16=(int)(16*d),p12=(int)(12*d),p8=(int)(8*d),p6=(int)(6*d),p4=(int)(4*d);

        android.widget.ScrollView sv = new android.widget.ScrollView(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p16,p12,p16,(int)(32*d));
        sv.addView(root);

        // Tirón
        View tiron = new View(activity);
        LinearLayout.LayoutParams lpT = new LinearLayout.LayoutParams((int)(40*d),(int)(4*d));
        lpT.gravity=android.view.Gravity.CENTER_HORIZONTAL; lpT.bottomMargin=p12;
        tiron.setLayoutParams(lpT);
        android.graphics.drawable.GradientDrawable tBg=new android.graphics.drawable.GradientDrawable();
        tBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        tBg.setCornerRadius(4*d); tBg.setColor(Color.parseColor("#BDBDBD"));
        tiron.setBackground(tBg); root.addView(tiron);

        TextView tvTitulo = new TextView(activity);
        tvTitulo.setText("Cobro del viaje");
        tvTitulo.setTextSize(20f); tvTitulo.setTypeface(null,Typeface.BOLD);
        tvTitulo.setTextColor(Color.parseColor("#004D40"));
        LinearLayout.LayoutParams lpTit=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        lpTit.bottomMargin=p4; tvTitulo.setLayoutParams(lpTit); root.addView(tvTitulo);

        TextView tvSub = new TextView(activity);
        tvSub.setText("Confirma el pago de cada pasajero antes de calificar");
        tvSub.setTextSize(13f); tvSub.setTextColor(Color.parseColor("#546E7A"));
        LinearLayout.LayoutParams lpSub=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        lpSub.bottomMargin=p16; tvSub.setLayoutParams(lpSub); root.addView(tvSub);

        View sep0=new View(activity);
        LinearLayout.LayoutParams lpS0=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,(int)(1*d));
        lpS0.bottomMargin=p12; sep0.setLayoutParams(lpS0);
        sep0.setBackgroundColor(Color.parseColor("#E0F2F1")); root.addView(sep0);

        final int[] pagosConfirmados={0};
        final int totalPasajeros=pasajeros.size();

        com.google.android.material.button.MaterialButton btnContinuar =
                new com.google.android.material.button.MaterialButton(activity);
        btnContinuar.setText("CONTINUAR A CALIFICAR");
        btnContinuar.setTextSize(15f); btnContinuar.setTextColor(Color.WHITE);
        btnContinuar.setEnabled(false); btnContinuar.setAlpha(0.5f);
        btnContinuar.setBackgroundColor(Color.parseColor("#B0BEC5"));
        LinearLayout.LayoutParams lpBtn=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,(int)(54*d));
        lpBtn.topMargin=p16; btnContinuar.setLayoutParams(lpBtn);
        btnContinuar.setCornerRadius((int)(14*d));

        for (int i=0;i<pasajeros.size();i++) {
            JSONObject u=pasajeros.get(i);
            int idPasajero=-1; String nomPasajero="Pasajero";
            JSONObject usuObj=u.optJSONObject("usuario");
            if (usuObj!=null) {
                for (String k:new String[]{"idUsuarios","id","idUsuario"}) { int id=usuObj.optInt(k,-1); if(id>0){idPasajero=id;break;} }
                for (String k:new String[]{"nombre","nombreCompleto","name","nombres"}) { String n=usuObj.optString(k,""); if(!n.isEmpty()&&!n.equals("null")){nomPasajero=n;break;} }
            }
            if (idPasajero<=0) for (String k:new String[]{"idUsuarios","idUsuario","idPasajero"}) { int id=u.optInt(k,-1); if(id>0){idPasajero=id;break;} }

            final int fIdPas=idPasajero; final String fNomPas=nomPasajero;
            JSONObject pagoExistente=mapaPagos.get(idPasajero);
            boolean yaPagado=pagoExistente!=null&&pagoExistente.optBoolean("confirmacionConductor",false);
            String tipoPagoExistente=pagoExistente!=null?pagoExistente.optString("tipoPago",""):"";
            if (yaPagado) pagosConfirmados[0]++;

            com.google.android.material.card.MaterialCardView cardPas=
                    new com.google.android.material.card.MaterialCardView(activity);
            LinearLayout.LayoutParams lpCard=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
            lpCard.bottomMargin=p8; cardPas.setLayoutParams(lpCard);
            cardPas.setRadius(16*d); cardPas.setCardElevation(0);
            cardPas.setCardBackgroundColor(yaPagado?Color.parseColor("#E8F5E9"):Color.parseColor("#FAFAFA"));
            cardPas.setStrokeWidth((int)(1.5f*d));
            cardPas.setStrokeColor(yaPagado?Color.parseColor("#A5D6A7"):Color.parseColor("#E0E0E0"));

            LinearLayout innerCard=new LinearLayout(activity);
            innerCard.setOrientation(LinearLayout.VERTICAL); innerCard.setPadding(p12,p12,p12,p12);

            LinearLayout filaNom=new LinearLayout(activity);
            filaNom.setOrientation(LinearLayout.HORIZONTAL); filaNom.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lpFN=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
            lpFN.bottomMargin=p8; filaNom.setLayoutParams(lpFN);

            TextView tvAv=new TextView(activity);
            LinearLayout.LayoutParams lpAv=new LinearLayout.LayoutParams((int)(36*d),(int)(36*d));
            lpAv.rightMargin=p8; tvAv.setLayoutParams(lpAv);
            tvAv.setGravity(android.view.Gravity.CENTER);
            tvAv.setText(nomPasajero.isEmpty()?"P":nomPasajero.substring(0,1).toUpperCase());
            tvAv.setTextColor(Color.WHITE); tvAv.setTextSize(15f); tvAv.setTypeface(null,Typeface.BOLD);
            int[] avatarCols={0xFF009688,0xFF1565C0,0xFFE65100,0xFF6A1B9A};
            android.graphics.drawable.GradientDrawable avBg=new android.graphics.drawable.GradientDrawable();
            avBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            avBg.setColor(yaPagado?0xFF2E7D32:avatarCols[i%avatarCols.length]);
            tvAv.setBackground(avBg); filaNom.addView(tvAv);

            LinearLayout colInfo=new LinearLayout(activity);
            colInfo.setOrientation(LinearLayout.VERTICAL);
            colInfo.setLayoutParams(new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));

            TextView tvNom=new TextView(activity);
            tvNom.setText(nomPasajero); tvNom.setTextSize(14.5f);
            tvNom.setTypeface(null,Typeface.BOLD); tvNom.setTextColor(Color.parseColor("#1A2035"));
            colInfo.addView(tvNom);

            double montoFinal=montoBase>0?montoBase:(pagoExistente!=null?pagoExistente.optDouble("monto",0):0);
            java.text.NumberFormat nf=java.text.NumberFormat.getNumberInstance(new java.util.Locale("es","CO"));
            TextView tvMonto=new TextView(activity);
            tvMonto.setText("$"+nf.format(montoFinal)+" COP");
            tvMonto.setTextSize(12f); tvMonto.setTextColor(Color.parseColor("#00897B"));
            tvMonto.setTypeface(null,Typeface.BOLD); colInfo.addView(tvMonto);
            filaNom.addView(colInfo);

            TextView tvEstPago=new TextView(activity);
            tvEstPago.setTextSize(10f); tvEstPago.setTextColor(Color.WHITE);
            tvEstPago.setTypeface(null,Typeface.BOLD);
            tvEstPago.setPadding(p6,(int)(3*d),p6,(int)(3*d));
            tvEstPago.setText(yaPagado?"Pagado":"Pendiente");
            android.graphics.drawable.GradientDrawable badgeBg=new android.graphics.drawable.GradientDrawable();
            badgeBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            badgeBg.setCornerRadius(20*d);
            badgeBg.setColor(yaPagado?Color.parseColor("#2E7D32"):Color.parseColor("#F57F17"));
            tvEstPago.setBackground(badgeBg); filaNom.addView(tvEstPago);
            innerCard.addView(filaNom);

            if (!yaPagado) {
                TextView tvMetodoLabel=new TextView(activity);
                tvMetodoLabel.setText("¿Cómo te pagó?");
                tvMetodoLabel.setTextSize(12f); tvMetodoLabel.setTextColor(Color.parseColor("#546E7A"));
                LinearLayout.LayoutParams lpML=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);
                lpML.bottomMargin=p6; tvMetodoLabel.setLayoutParams(lpML); innerCard.addView(tvMetodoLabel);

                LinearLayout filaMetodos=new LinearLayout(activity);
                filaMetodos.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams lpFM=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
                lpFM.bottomMargin=p8; filaMetodos.setLayoutParams(lpFM);

                final String[] metodoElegido={tipoPagoExistente.isEmpty()?"":tipoPagoExistente};

                com.google.android.material.button.MaterialButton btnEfectivo=
                        new com.google.android.material.button.MaterialButton(activity);
                btnEfectivo.setText("Efectivo");
                btnEfectivo.setTextSize(12f); btnEfectivo.setTextColor(Color.parseColor("#004D40"));
                btnEfectivo.setCornerRadius((int)(10*d)); btnEfectivo.setStrokeWidth((int)(1.5f*d));
                btnEfectivo.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#80CBC4")));
                btnEfectivo.setBackgroundColor(Color.parseColor("#E0F2F1"));
                LinearLayout.LayoutParams lpBE=new LinearLayout.LayoutParams(0,(int)(40*d),1f);
                lpBE.rightMargin=p6; btnEfectivo.setLayoutParams(lpBE);

                com.google.android.material.button.MaterialButton btnTransferencia=
                        new com.google.android.material.button.MaterialButton(activity);
                btnTransferencia.setText("Transferencia");
                btnTransferencia.setTextSize(12f); btnTransferencia.setTextColor(Color.parseColor("#004D40"));
                btnTransferencia.setCornerRadius((int)(10*d)); btnTransferencia.setStrokeWidth((int)(1.5f*d));
                btnTransferencia.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#80CBC4")));
                btnTransferencia.setBackgroundColor(Color.parseColor("#E0F2F1"));
                btnTransferencia.setLayoutParams(new LinearLayout.LayoutParams(0,(int)(40*d),1f));

                filaMetodos.addView(btnEfectivo); filaMetodos.addView(btnTransferencia);
                innerCard.addView(filaMetodos);

                com.google.android.material.button.MaterialButton btnConfirmar=
                        new com.google.android.material.button.MaterialButton(activity);
                btnConfirmar.setText("CONFIRMAR PAGO RECIBIDO");
                btnConfirmar.setTextSize(13f); btnConfirmar.setTextColor(Color.WHITE);
                btnConfirmar.setCornerRadius((int)(12*d));
                btnConfirmar.setBackgroundColor(Color.parseColor("#B0BEC5"));
                btnConfirmar.setEnabled(false); btnConfirmar.setAlpha(0.5f);
                btnConfirmar.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,(int)(46*d)));

                Runnable actualizarSeleccion=()->{
                    boolean esEf="efectivo".equals(metodoElegido[0]);
                    boolean esTr="transferencia".equals(metodoElegido[0]);
                    btnEfectivo.setBackgroundColor(esEf?Color.parseColor("#00897B"):Color.parseColor("#E0F2F1"));
                    btnEfectivo.setTextColor(esEf?Color.WHITE:Color.parseColor("#004D40"));
                    btnTransferencia.setBackgroundColor(esTr?Color.parseColor("#00897B"):Color.parseColor("#E0F2F1"));
                    btnTransferencia.setTextColor(esTr?Color.WHITE:Color.parseColor("#004D40"));
                    boolean hay=!metodoElegido[0].isEmpty();
                    btnConfirmar.setEnabled(hay); btnConfirmar.setAlpha(hay?1f:0.5f);
                    btnConfirmar.setBackgroundColor(hay?Color.parseColor("#00897B"):Color.parseColor("#B0BEC5"));
                };
                btnEfectivo.setOnClickListener(vw->{metodoElegido[0]="efectivo";actualizarSeleccion.run();});
                btnTransferencia.setOnClickListener(vw->{metodoElegido[0]="transferencia";actualizarSeleccion.run();});
                if (!metodoElegido[0].isEmpty()) actualizarSeleccion.run();

                final com.google.android.material.card.MaterialCardView fCard=cardPas;
                final TextView fTvEstPago=tvEstPago;
                final long[] fIdPago={pagoExistente!=null?pagoExistente.optLong("idPago",pagoExistente.optLong("id",-1)):-1};

                btnConfirmar.setOnClickListener(vw->{
                    btnConfirmar.setEnabled(false); btnConfirmar.setText("Procesando...");
                    Runnable confirmarConductor=()->{
                        if(fIdPago[0]<=0) return;
                        ConexionApi.getInstance(context).put(Constantes.pagoConfirmarConductor(fIdPago[0]),new JSONObject(),
                                resp->new Handler(Looper.getMainLooper()).post(()->{
                                    fCard.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
                                    fCard.setStrokeColor(Color.parseColor("#A5D6A7"));
                                    fTvEstPago.setText("Pagado"); badgeBg.setColor(Color.parseColor("#2E7D32"));
                                    fTvEstPago.setBackground(badgeBg); avBg.setColor(0xFF2E7D32); tvAv.setBackground(avBg);
                                    btnEfectivo.setVisibility(View.GONE); btnTransferencia.setVisibility(View.GONE);
                                    btnConfirmar.setVisibility(View.GONE); tvMetodoLabel.setVisibility(View.GONE);
                                    pagosConfirmados[0]++;
                                    if(pagosConfirmados[0]>=totalPasajeros){btnContinuar.setEnabled(true);btnContinuar.setAlpha(1f);btnContinuar.setBackgroundColor(Color.parseColor("#00897B"));}
                                }),
                                err->new Handler(Looper.getMainLooper()).post(()->{btnConfirmar.setEnabled(true);btnConfirmar.setText("CONFIRMAR PAGO RECIBIDO");Toast.makeText(context,"Error al confirmar",Toast.LENGTH_SHORT).show();})
                        );
                    };
                    if(fIdPago[0]>0){confirmarConductor.run();return;}
                    JSONObject body=new JSONObject();
                    try{body.put("idUsuario",fIdPas);body.put("idViaje",viajeId);body.put("monto",montoBase);body.put("tipoPago",metodoElegido[0]);body.put("estado","pendiente");body.put("confirmacionPasajero",true);body.put("confirmacionConductor",false);}catch(Exception ignored){}
                    ConexionApi.getInstance(context).post(Constantes.PAGOS,body,
                            respCreate->{fIdPago[0]=respCreate.optLong("idPago",respCreate.optLong("id",-1));confirmarConductor.run();},
                            errCreate->new Handler(Looper.getMainLooper()).post(()->{btnConfirmar.setEnabled(true);btnConfirmar.setText("CONFIRMAR PAGO RECIBIDO");Toast.makeText(context,"Error registrando pago",Toast.LENGTH_SHORT).show();})
                    );
                });
                innerCard.addView(btnConfirmar);
            } else {
                if (!tipoPagoExistente.isEmpty()) {
                    TextView tvMetodoUsado=new TextView(activity);
                    tvMetodoUsado.setText("Pagó con "+tipoPagoExistente.toLowerCase());
                    tvMetodoUsado.setTextSize(12f); tvMetodoUsado.setTextColor(Color.parseColor("#2E7D32"));
                    tvMetodoUsado.setTypeface(null,Typeface.BOLD); innerCard.addView(tvMetodoUsado);
                }
            }
            cardPas.addView(innerCard); root.addView(cardPas);
        }

        if(pagosConfirmados[0]>=totalPasajeros){btnContinuar.setEnabled(true);btnContinuar.setAlpha(1f);btnContinuar.setBackgroundColor(Color.parseColor("#00897B"));}

        TextView tvNota=new TextView(activity);
        tvNota.setText("Una vez confirmados los pagos podrás calificar a los pasajeros");
        tvNota.setTextSize(11.5f); tvNota.setTextColor(Color.parseColor("#78909C"));
        LinearLayout.LayoutParams lpNota=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        lpNota.topMargin=p8; tvNota.setLayoutParams(lpNota); root.addView(tvNota);
        root.addView(btnContinuar);

        com.google.android.material.button.MaterialButton btnSaltar=
                new com.google.android.material.button.MaterialButton(activity);
        btnSaltar.setText("Saltar y calificar directamente");
        btnSaltar.setTextSize(13f); btnSaltar.setTextColor(Color.parseColor("#78909C"));
        btnSaltar.setBackgroundColor(Color.TRANSPARENT); btnSaltar.setStrokeWidth(0);
        LinearLayout.LayoutParams lpSaltar=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,(int)(44*d));
        lpSaltar.topMargin=p4; btnSaltar.setLayoutParams(lpSaltar);

        final com.google.android.material.bottomsheet.BottomSheetDialog fSheet=sheet;
        btnContinuar.setOnClickListener(vw->{fSheet.dismiss();new Handler(Looper.getMainLooper()).postDelayed(()->buscarPasajerosYCalificar(viajeId),400);});
        btnSaltar.setOnClickListener(vw->{fSheet.dismiss();new Handler(Looper.getMainLooper()).postDelayed(()->buscarPasajerosYCalificar(viajeId),400);});
        root.addView(btnSaltar);
        sheet.setContentView(sv); sheet.show();
    }

    // =========================================================================
    //  FLUJO PAGO — PASAJERO
    // =========================================================================
    private void verificarYMostrarPagoPasajero(int viajeId, int idConductor) {
        if (viajesPagoMostrado.contains(viajeId)) return;
        int idPasajero=new SessionManager(context).getIdUsuario();
        ConexionApi.getInstance(context).getObjectNoCache(Constantes.pagoDeUsuarioEnViaje(viajeId,idPasajero),
                pagoExistente->{long idPago=pagoExistente.optLong("idPago",pagoExistente.optLong("id",-1)); if(idPago>0) viajesPagoMostrado.add(viajeId);},
                err->{viajesPagoMostrado.add(viajeId);new Handler(Looper.getMainLooper()).postDelayed(()->mostrarSheetPagoPasajero(viajeId,idConductor),600);}
        );
    }

    private void mostrarSheetPagoPasajero(int viajeId, int idConductor) {
        Activity activity=resolveActivity(context);
        if(activity==null||activity.isFinishing()||activity.isDestroyed()) return;
        SessionManager session=new SessionManager(context);
        int idPasajero=session.getIdUsuario(); String nomPasajero=session.getNombre();
        ConexionApi.getInstance(context).getObjectNoCache(Constantes.viajePorId((long)viajeId),
                viajeObj->{
                    double monto=viajeObj.optDouble("precio",0);
                    if(monto<=0){JSONObject ruta=viajeObj.optJSONObject("ruta"); if(ruta!=null) monto=ruta.optDouble("precio",ruta.optDouble("costoCombustible",0));}
                    final double fMonto=monto;
                    new Handler(Looper.getMainLooper()).post(()->construirSheetPagoPasajero(activity,viajeId,idPasajero,nomPasajero,idConductor,fMonto));
                },
                err->new Handler(Looper.getMainLooper()).post(()->construirSheetPagoPasajero(activity,viajeId,idPasajero,nomPasajero,idConductor,0))
        );
    }

    private void construirSheetPagoPasajero(Activity activity, int viajeId,
                                            int idPasajero, String nomPasajero,
                                            int idConductor, double montoBase) {
        if(activity.isFinishing()||activity.isDestroyed()) return;
        com.google.android.material.bottomsheet.BottomSheetDialog sheet=
                new com.google.android.material.bottomsheet.BottomSheetDialog(activity,R.style.BottomSheetTheme);
        float d=activity.getResources().getDisplayMetrics().density;
        int p16=(int)(16*d),p12=(int)(12*d),p8=(int)(8*d),p6=(int)(6*d),p4=(int)(4*d);

        android.widget.ScrollView sv=new android.widget.ScrollView(activity);
        LinearLayout root=new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(p16,p12,p16,(int)(32*d));
        sv.addView(root);

        View tiron=new View(activity);
        LinearLayout.LayoutParams lpT=new LinearLayout.LayoutParams((int)(40*d),(int)(4*d));
        lpT.gravity=android.view.Gravity.CENTER_HORIZONTAL; lpT.bottomMargin=p12;
        tiron.setLayoutParams(lpT);
        android.graphics.drawable.GradientDrawable tBg=new android.graphics.drawable.GradientDrawable();
        tBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        tBg.setCornerRadius(4*d); tBg.setColor(Color.parseColor("#BDBDBD"));
        tiron.setBackground(tBg); root.addView(tiron);

        TextView tvTitulo=new TextView(activity);
        tvTitulo.setText("Pagar tu viaje");
        tvTitulo.setTextSize(20f); tvTitulo.setTypeface(null,Typeface.BOLD);
        tvTitulo.setTextColor(Color.parseColor("#004D40"));
        LinearLayout.LayoutParams lpTit=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        lpTit.bottomMargin=p4; tvTitulo.setLayoutParams(lpTit); root.addView(tvTitulo);

        TextView tvSub=new TextView(activity);
        tvSub.setText("Elige cómo le pagas al conductor");
        tvSub.setTextSize(13f); tvSub.setTextColor(Color.parseColor("#546E7A"));
        LinearLayout.LayoutParams lpSub=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        lpSub.bottomMargin=p16; tvSub.setLayoutParams(lpSub); root.addView(tvSub);

        View sep=new View(activity);
        LinearLayout.LayoutParams lpSep=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,(int)(1*d));
        lpSep.bottomMargin=p12; sep.setLayoutParams(lpSep);
        sep.setBackgroundColor(Color.parseColor("#E0F2F1")); root.addView(sep);

        // Card monto
        com.google.android.material.card.MaterialCardView cardMonto=
                new com.google.android.material.card.MaterialCardView(activity);
        LinearLayout.LayoutParams lpCM=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        lpCM.bottomMargin=p16; cardMonto.setLayoutParams(lpCM);
        cardMonto.setRadius(16*d); cardMonto.setCardElevation(0);
        cardMonto.setCardBackgroundColor(Color.parseColor("#E0F7FA"));
        cardMonto.setStrokeWidth((int)(1.5f*d)); cardMonto.setStrokeColor(Color.parseColor("#80DEEA"));
        LinearLayout innerMonto=new LinearLayout(activity);
        innerMonto.setOrientation(LinearLayout.HORIZONTAL); innerMonto.setGravity(android.view.Gravity.CENTER_VERTICAL);
        innerMonto.setPadding(p16,p12,p16,p12);
        TextView tvMontoLabel=new TextView(activity);
        tvMontoLabel.setText("Total a pagar"); tvMontoLabel.setTextSize(14f);
        tvMontoLabel.setTextColor(Color.parseColor("#00695C"));
        tvMontoLabel.setLayoutParams(new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));
        innerMonto.addView(tvMontoLabel);
        java.text.NumberFormat nf=java.text.NumberFormat.getNumberInstance(new java.util.Locale("es","CO"));
        TextView tvMontoValor=new TextView(activity);
        tvMontoValor.setText(montoBase>0?"$"+nf.format(montoBase)+" COP":"Consultar con conductor");
        tvMontoValor.setTextSize(16f); tvMontoValor.setTypeface(null,Typeface.BOLD);
        tvMontoValor.setTextColor(Color.parseColor("#004D40"));
        innerMonto.addView(tvMontoValor); cardMonto.addView(innerMonto); root.addView(cardMonto);

        TextView tvMetodoLabel=new TextView(activity);
        tvMetodoLabel.setText("¿Cómo vas a pagar?"); tvMetodoLabel.setTextSize(14f);
        tvMetodoLabel.setTypeface(null,Typeface.BOLD); tvMetodoLabel.setTextColor(Color.parseColor("#1A2035"));
        LinearLayout.LayoutParams lpML=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        lpML.bottomMargin=p8; tvMetodoLabel.setLayoutParams(lpML); root.addView(tvMetodoLabel);

        final String[] metodoElegido={""};
        LinearLayout filaMetodos=new LinearLayout(activity);
        filaMetodos.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lpFM=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        lpFM.bottomMargin=p16; filaMetodos.setLayoutParams(lpFM);

        com.google.android.material.button.MaterialButton btnEfectivo=new com.google.android.material.button.MaterialButton(activity);
        btnEfectivo.setText("Efectivo"); btnEfectivo.setTextSize(13f);
        btnEfectivo.setTextColor(Color.parseColor("#004D40")); btnEfectivo.setCornerRadius((int)(12*d));
        btnEfectivo.setStrokeWidth((int)(1.5f*d));
        btnEfectivo.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#80CBC4")));
        btnEfectivo.setBackgroundColor(Color.parseColor("#E0F2F1"));
        LinearLayout.LayoutParams lpBE=new LinearLayout.LayoutParams(0,(int)(52*d),1f);
        lpBE.rightMargin=p8; btnEfectivo.setLayoutParams(lpBE);

        com.google.android.material.button.MaterialButton btnTransferencia=new com.google.android.material.button.MaterialButton(activity);
        btnTransferencia.setText("Transferencia"); btnTransferencia.setTextSize(13f);
        btnTransferencia.setTextColor(Color.parseColor("#004D40")); btnTransferencia.setCornerRadius((int)(12*d));
        btnTransferencia.setStrokeWidth((int)(1.5f*d));
        btnTransferencia.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#80CBC4")));
        btnTransferencia.setBackgroundColor(Color.parseColor("#E0F2F1"));
        btnTransferencia.setLayoutParams(new LinearLayout.LayoutParams(0,(int)(52*d),1f));
        filaMetodos.addView(btnEfectivo); filaMetodos.addView(btnTransferencia); root.addView(filaMetodos);

        com.google.android.material.button.MaterialButton btnConfirmar=new com.google.android.material.button.MaterialButton(activity);
        btnConfirmar.setText("CONFIRMAR QUE PAGUÉ"); btnConfirmar.setTextSize(15f);
        btnConfirmar.setTextColor(Color.WHITE); btnConfirmar.setEnabled(false); btnConfirmar.setAlpha(0.5f);
        btnConfirmar.setBackgroundColor(Color.parseColor("#B0BEC5")); btnConfirmar.setCornerRadius((int)(14*d));
        LinearLayout.LayoutParams lpBtn=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,(int)(54*d));
        lpBtn.bottomMargin=p4; btnConfirmar.setLayoutParams(lpBtn); root.addView(btnConfirmar);

        com.google.android.material.button.MaterialButton btnSaltar=new com.google.android.material.button.MaterialButton(activity);
        btnSaltar.setText("Saltar por ahora"); btnSaltar.setTextSize(12f);
        btnSaltar.setTextColor(Color.parseColor("#90A4AE")); btnSaltar.setBackgroundColor(Color.TRANSPARENT); btnSaltar.setStrokeWidth(0);
        btnSaltar.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,(int)(40*d)));
        root.addView(btnSaltar);

        Runnable actualizarUI=()->{
            boolean esEf="efectivo".equals(metodoElegido[0]); boolean esTr="transferencia".equals(metodoElegido[0]);
            btnEfectivo.setBackgroundColor(esEf?Color.parseColor("#00897B"):Color.parseColor("#E0F2F1"));
            btnEfectivo.setTextColor(esEf?Color.WHITE:Color.parseColor("#004D40"));
            btnTransferencia.setBackgroundColor(esTr?Color.parseColor("#00897B"):Color.parseColor("#E0F2F1"));
            btnTransferencia.setTextColor(esTr?Color.WHITE:Color.parseColor("#004D40"));
            boolean hay=!metodoElegido[0].isEmpty(); btnConfirmar.setEnabled(hay); btnConfirmar.setAlpha(hay?1f:0.5f);
            btnConfirmar.setBackgroundColor(hay?Color.parseColor("#00897B"):Color.parseColor("#B0BEC5"));
        };
        btnEfectivo.setOnClickListener(vw->{metodoElegido[0]="efectivo";actualizarUI.run();});
        btnTransferencia.setOnClickListener(vw->{metodoElegido[0]="transferencia";actualizarUI.run();});

        final com.google.android.material.bottomsheet.BottomSheetDialog fSheet=sheet;
        btnSaltar.setOnClickListener(vw->fSheet.dismiss());
        btnConfirmar.setOnClickListener(vw->{
            btnConfirmar.setEnabled(false); btnConfirmar.setText("Registrando pago...");
            JSONObject body=new JSONObject();
            try{body.put("idUsuario",idPasajero);body.put("idViaje",viajeId);body.put("monto",montoBase);body.put("tipoPago",metodoElegido[0]);body.put("estado","pendiente");body.put("confirmacionPasajero",true);body.put("confirmacionConductor",false);}catch(Exception ignored){}
            ConexionApi.getInstance(context).post(Constantes.PAGOS,body,
                    respCreate->{
                        long idPago=respCreate.optLong("idPago",respCreate.optLong("id",-1));
                        if(idPago>0){
                            ConexionApi.getInstance(context).put(Constantes.pagoConfirmarPasajero(idPago),new JSONObject(),
                                    respConfirm->new Handler(Looper.getMainLooper()).post(()->{fSheet.dismiss();Toast.makeText(context,"Pago registrado — el conductor lo confirmará",Toast.LENGTH_LONG).show();new Handler(Looper.getMainLooper()).postDelayed(()->buscarConductorParaCalificar(viajeId,idPasajero),600);}),
                                    errConfirm->new Handler(Looper.getMainLooper()).post(()->{fSheet.dismiss();Toast.makeText(context,"Pago registrado correctamente",Toast.LENGTH_SHORT).show();new Handler(Looper.getMainLooper()).postDelayed(()->buscarConductorParaCalificar(viajeId,idPasajero),600);})
                            );
                        } else {
                            new Handler(Looper.getMainLooper()).post(()->{fSheet.dismiss();Toast.makeText(context,"Pago registrado",Toast.LENGTH_SHORT).show();new Handler(Looper.getMainLooper()).postDelayed(()->buscarConductorParaCalificar(viajeId,idPasajero),600);});
                        }
                    },
                    errCreate->new Handler(Looper.getMainLooper()).post(()->{btnConfirmar.setEnabled(true);btnConfirmar.setText("CONFIRMAR QUE PAGUÉ");Toast.makeText(context,"Error al registrar el pago",Toast.LENGTH_LONG).show();})
            );
        });
        sheet.setContentView(sv); sheet.show();
    }

    // =========================================================================
    //  CALIFICACIÓN — BUSCAR PASAJEROS (CONDUCTOR)
    // =========================================================================
    private void buscarPasajerosYCalificar(int viajeId) {
        Activity activity=resolveActivity(context);
        if(activity==null||activity.isFinishing()||activity.isDestroyed()) return;
        int idConductor=new SessionManager(context).getIdUsuario();
        ConexionApi.getInstance(context).getObjectNoCache(Constantes.viajePorId((long)viajeId),
                viajeObj->{
                    java.util.ArrayList<Integer> ids=new java.util.ArrayList<>();
                    java.util.ArrayList<String> nombres=new java.util.ArrayList<>();
                    JSONArray usuarios=viajeObj.optJSONArray("usuarios");
                    if(usuarios!=null){
                        for(int i=0;i<usuarios.length();i++){
                            JSONObject u=usuarios.optJSONObject(i); if(u==null) continue;
                            String est=u.optString("estado","").toUpperCase().trim();
                            if("CANCELADO".equals(est)||"CANCELADA".equals(est)) continue;
                            int idP=-1; String nomP="";
                            JSONObject usuObj=u.optJSONObject("usuario");
                            if(usuObj!=null){
                                for(String k:new String[]{"idUsuarios","id","idUsuario"}){int id=usuObj.optInt(k,-1);if(id>0){idP=id;break;}}
                                for(String k:new String[]{"nombre","nombreCompleto","name","nombres"}){String n=usuObj.optString(k,"");if(!n.isEmpty()&&!n.equals("null")){nomP=n;break;}}
                                if(nomP.isEmpty()){String n=usuObj.optString("nombres",""),a=usuObj.optString("apellidos","");if(!n.isEmpty()||!a.isEmpty()) nomP=(n+" "+a).trim();}
                            }
                            if(idP<=0) for(String k:new String[]{"idUsuarios","idUsuario","idPasajero"}){int id=u.optInt(k,-1);if(id>0){idP=id;break;}}
                            if(idP>0&&idP!=idConductor){ids.add(idP);nombres.add(nomP.isEmpty()?"Pasajero":nomP);}
                        }
                    }
                    if(!ids.isEmpty()) new Handler(Looper.getMainLooper()).post(()->mostrarCalificacionesEncadenadas(ids,nombres,idConductor,viajeId,0));
                    else paso3BuscarPasajero(viajeId);
                },
                err->paso3BuscarPasajero(viajeId)
        );
    }

    private void paso3BuscarPasajero(int viajeId){intentarEndpointReservas(viajeId,0);}

    private void intentarEndpointReservas(int viajeId,int index){
        String[] urls={
                Constantes.BASE_URL+"/api/reservas/viaje/"+viajeId,
                Constantes.BASE_URL+"/api/reservas?idViaje="+viajeId,
                Constantes.BASE_URL+"/api/reservas?viajeId="+viajeId,
                Constantes.BASE_URL+"/api/viajes/"+viajeId,
        };
        if(index>=urls.length){mostrarErrorCalificacion();return;}
        ConexionApi.getInstance(context).getArrayNoCache(urls[index],
                r->{if(r.length()==0&&index<urls.length-1) intentarEndpointReservas(viajeId,index+1); else procesarReservas(r,viajeId);},
                e->ConexionApi.getInstance(context).getObjectNoCache(urls[index],
                        obj->{JSONArray arr=extraerArrayDeObjeto(obj); if(arr!=null&&arr.length()>0) procesarReservas(arr,viajeId); else intentarEndpointReservas(viajeId,index+1);},
                        e2->intentarEndpointReservas(viajeId,index+1)));
    }

    private JSONArray extraerArrayDeObjeto(JSONObject obj){
        if(obj==null) return null;
        for(String k:new String[]{"items","reservas","content","data","list","results","pasajeros","passengers","bookings"}){JSONArray a=obj.optJSONArray(k);if(a!=null) return a;}
        Iterator<String> keys=obj.keys();
        while(keys.hasNext()){Object val=obj.opt(keys.next());if(val instanceof JSONArray) return (JSONArray)val;}
        return null;
    }

    private void procesarReservas(JSONArray reservas,int viajeId){
        java.util.ArrayList<Integer> ids=new java.util.ArrayList<>();
        java.util.ArrayList<String> nombres=new java.util.ArrayList<>();
        if(reservas==null||reservas.length()==0){new Handler(Looper.getMainLooper()).post(()->Toast.makeText(context,"Este viaje no tiene pasajeros para calificar",Toast.LENGTH_SHORT).show());return;}
        for(int i=0;i<reservas.length();i++){
            JSONObject res=reservas.optJSONObject(i);if(res==null) continue;
            String est=res.optString("estado","").toUpperCase();
            if("CANCELADO".equals(est)||"CANCELADA".equals(est)) continue;
            int idP=-1;String nomP="";
            JSONObject po=null;
            for(String k:new String[]{"pasajero","usuario","user","passenger"}){JSONObject c=res.optJSONObject(k);if(c!=null){po=c;break;}}
            if(po!=null){
                for(String k:new String[]{"id","idUsuarios","idUsuario","userId"}){int id=po.optInt(k,-1);if(id>0){idP=id;break;}}
                for(String k:new String[]{"nombre","nombreCompleto","name","nombres"}){String n=po.optString(k,"");if(!n.isEmpty()&&!n.equals("null")){nomP=n;break;}}
                if(nomP.isEmpty()){String n=po.optString("nombres",""),a=po.optString("apellidos","");if(!n.isEmpty()||!a.isEmpty()) nomP=(n+" "+a).trim();}
            }
            if(idP<=0) for(String k:new String[]{"idUsuarios","idUsuario","idPasajero","pasajeroId"}){int id=res.optInt(k,-1);if(id>0){idP=id;break;}}
            if(idP>0){ids.add(idP);nombres.add(nomP.isEmpty()?"Pasajero":nomP);}
        }
        if(ids.isEmpty()){new Handler(Looper.getMainLooper()).post(()->Toast.makeText(context,"No hay pasajeros activos para calificar",Toast.LENGTH_SHORT).show());return;}
        final int idCal=new SessionManager(context).getIdUsuario();
        new Handler(Looper.getMainLooper()).postDelayed(()->mostrarCalificacionesEncadenadas(ids,nombres,idCal,viajeId,0),800);
    }

    private void mostrarCalificacionesEncadenadas(java.util.ArrayList<Integer> ids,java.util.ArrayList<String> nombres,int idCalificador,int viajeId,int indice){
        if(indice>=ids.size()) return;
        int idP=ids.get(indice);String nomP=nombres.get(indice);int sig=indice+1;
        new CalificacionesManager(context).verificarCalificacion(viajeId,idCalificador,idP,
                new CalificacionesManager.OnVerificacionListener(){
                    @Override public void onDebeCalificar(){abrirBottomSheetCalificacion(viajeId,idCalificador,idP,nomP,true);new Handler(Looper.getMainLooper()).postDelayed(()->mostrarCalificacionesEncadenadas(ids,nombres,idCalificador,viajeId,sig),3000);}
                    @Override public void onYaCalifico(int p,String e){mostrarCalificacionesEncadenadas(ids,nombres,idCalificador,viajeId,sig);}
                });
    }

    private void abrirBottomSheetCalificacion(int viajeId,int idCalificador,int idCalificado,String nomCalificado,boolean esConductorCal){
        Activity activity=resolveActivity(context);
        if(activity==null||activity.isFinishing()||activity.isDestroyed()) return;
        new CalificacionesManager(context).verificarCalificacion(viajeId,idCalificador,idCalificado,
                new CalificacionesManager.OnVerificacionListener(){
                    @Override public void onDebeCalificar(){
                        cacheCalificado.put(viajeId,false);
                        activity.runOnUiThread(()->CalificacionController.mostrarBottomSheetCalificar(activity,viajeId,idCalificado,nomCalificado,idCalificador,esConductorCal,(p,c)->{cacheCalificado.put(viajeId,true);activity.runOnUiThread(()->notifyDataSetChanged());}));
                    }
                    @Override public void onYaCalifico(int p,String e){
                        cacheCalificado.put(viajeId,true);
                        activity.runOnUiThread(()->{Toast.makeText(context,"Ya calificaste este viaje",Toast.LENGTH_SHORT).show();notifyDataSetChanged();});
                    }
                });
    }

    private void mostrarErrorCalificacion(){new Handler(Looper.getMainLooper()).post(()->Toast.makeText(context,"No se pudo cargar la información del pasajero",Toast.LENGTH_LONG).show());}

    // =========================================================================
    //  HELPERS
    // =========================================================================
    private void cargarNombreConductor(int idConductor,ViajeViewHolder holder){
        ConexionApi.getInstance(context).getObject(Constantes.usuarioDetalle((long)idConductor),
                resp->{
                    String nom=extraerNombreDeJson(resp);if(nom.isEmpty()) nom="Conductor";
                    cacheConductores.put(idConductor,nom);final String nf=nom;
                    if(holder.itemView.isAttachedToWindow()) holder.itemView.post(()->{
                        holder.txtConductor.setText(nf);
                        String tel=resp.optString("telefono",resp.optString("celular",""));
                        holder.txtTelefono.setText(tel.isEmpty()?"":tel);
                    });
                },
                err->ConexionApi.getInstance(context).getObject(Constantes.USUARIOS+"/"+idConductor,
                        resp2->{String n2=extraerNombreDeJson(resp2);if(n2.isEmpty()) n2="Conductor";cacheConductores.put(idConductor,n2);final String nf2=n2;if(holder.itemView.isAttachedToWindow()) holder.itemView.post(()->holder.txtConductor.setText(nf2));},
                        err2->{if(holder.itemView.isAttachedToWindow()) holder.itemView.post(()->holder.txtConductor.setText("Conductor"));}));
    }

    private String extraerNombreDeJson(JSONObject obj){
        if(obj==null) return "";
        for(String k:new String[]{"nombre","nombreCompleto","name","fullName","nombreUsuario","displayName"}){String v=obj.optString(k,"");if(!v.isEmpty()&&!v.equals("null")) return v;}
        String n=obj.optString("nombres",""),a=obj.optString("apellidos","");if(!n.isEmpty()||!a.isEmpty()) return (n+" "+a).trim();
        return "";
    }

    private void abrirDetalle(int viajeId){Intent i=new Intent(context,DetalleViajeActivity.class);i.putExtra("ID_VIAJE",viajeId);context.startActivity(i);}

    private void cambiarEstado(int viajeId,String url,String accion){
        ConexionApi.getInstance(context).post(url,null,
                r->{Toast.makeText(context,"Viaje "+accion,Toast.LENGTH_SHORT).show();notifyDataSetChanged();},
                e->Toast.makeText(context,"Error al "+accion,Toast.LENGTH_SHORT).show());
    }

    @Override public int getItemCount(){return viajes.size();}

    // =========================================================================
    //  VIEW HOLDER
    // =========================================================================
    private void mostrarErrorCargaPasajeros(ViajeViewHolder h){
        new Handler(Looper.getMainLooper()).post(()->{
            if(!h.itemView.isAttachedToWindow()) return;
            if(h.loaderPasajeros!=null) h.loaderPasajeros.setVisibility(View.GONE);
            if(h.txtContadorPasajeros!=null) h.txtContadorPasajeros.setText("0");
            if(h.txtSinPasajeros!=null){h.txtSinPasajeros.setVisibility(View.VISIBLE);h.txtSinPasajeros.setText("Sin pasajeros reservados aun");}
        });
    }

    static class ViajeViewHolder extends RecyclerView.ViewHolder {
        TextView     txtRuta,txtFecha,txtCupos,txtPrecio;
        TextView     txtConductor,txtTelefono,txtVehiculo,txtEstado;
        Button       btnAceptar,btnIniciar,btnFinalizar,btnCancelar,btnDetalle,btnCalificar;
        LinearLayout layoutSeccionPasajeros,layoutPasajerosLista;
        TextView     txtContadorPasajeros,txtSinPasajeros;
        ProgressBar  loaderPasajeros;

        ViajeViewHolder(@NonNull View v){
            super(v);
            txtRuta                =v.findViewById(R.id.txtRuta);
            txtFecha               =v.findViewById(R.id.txtFecha);
            txtCupos               =v.findViewById(R.id.txtCupos);
            txtPrecio              =v.findViewById(R.id.txtPrecio);
            txtConductor           =v.findViewById(R.id.txtConductor);
            txtTelefono            =v.findViewById(R.id.txtTelefono);
            txtVehiculo            =v.findViewById(R.id.txtVehiculo);
            txtEstado              =v.findViewById(R.id.txtEstado);
            btnAceptar             =v.findViewById(R.id.btnAceptar);
            btnIniciar             =v.findViewById(R.id.btnIniciar);
            btnFinalizar           =v.findViewById(R.id.btnFinalizar);
            btnCancelar            =v.findViewById(R.id.btnCancelar);
            btnDetalle             =v.findViewById(R.id.btnDetalle);
            btnCalificar           =v.findViewById(R.id.btnCalificar);
            layoutSeccionPasajeros =v.findViewById(R.id.layoutSeccionPasajeros);
            layoutPasajerosLista   =v.findViewById(R.id.layoutPasajerosLista);
            txtContadorPasajeros   =v.findViewById(R.id.txtContadorPasajeros);
            txtSinPasajeros        =v.findViewById(R.id.txtSinPasajeros);
            loaderPasajeros        =v.findViewById(R.id.loaderPasajeros);
        }
    }
}