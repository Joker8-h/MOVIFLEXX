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

    // =========================================================================
    //  Estados que significan "pasajero a bordo" (recogido / en curso)
    // =========================================================================
    private static final java.util.Set<String> ESTADOS_A_BORDO = new java.util.HashSet<>(
            java.util.Arrays.asList("EN_CURSO", "INICIADO", "RECOGIDO", "COMPLETADO", "FINALIZADO")
    );

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

        // RUTA
        if (ruta != null) {
            String origen = ruta.optString("origen", "");
            String desc   = ruta.optString("descripcion", ruta.optString("nombre", ""));
            StringBuilder sb = new StringBuilder();
            if (!origen.isEmpty()) sb.append(origen);
            if (!desc.isEmpty())   sb.append("\n📋 ").append(desc);
            if (idRuta > 0)        sb.append("\n🔑 Ruta #").append(idRuta);
            h.txtRuta.setText(sb.length() > 0 ? sb.toString() : "Ruta no disponible");
        } else {
            h.txtRuta.setText(idRuta > 0 ? "Ruta #" + idRuta : "Ruta no disponible");
        }

        // FECHA
        String fecha = v.optString("fechaHoraSalida", "");
        if (fecha.contains("T")) fecha = fecha.replace("T", " ").replaceAll("\\.\\d{3}Z$", "");
        if (fecha.length() >= 16) fecha = fecha.substring(0, 16);
        h.txtFecha.setText("🕒 " + (fecha.isEmpty() ? "--" : fecha));

        // CUPOS
        int cuposDisp = v.optInt("cuposDisponibles", 0);
        int cuposTot  = v.optInt("cuposTotales", cuposDisp);
        String txtCupo;
        if (cuposTot > 8 || cuposDisp > 8) {
            txtCupo = "👥 Cupos: (actualiza tu vehículo)";
            cuposDisp = 1;
        } else {
            txtCupo = "👥 " + cuposDisp + " / " + cuposTot + " cupos";
        }
        h.txtCupos.setText(txtCupo);

        // PRECIO
        double precio = v.optDouble("precio", -1);
        if (precio < 0) {
            try { precio = Double.parseDouble(v.optString("precio", "0")); }
            catch (Exception ignored) { precio = 0; }
        }
        h.txtPrecio.setText(precio > 0
                ? String.format(Locale.getDefault(), "💰 $%,.0f COP", precio)
                : "💰 Precio no definido");

        // ESTADO
        h.txtEstado.setText("📌 " + etiquetaEstado(estado));

        // CONDUCTOR
        SessionManager session = new SessionManager(context);
        if (esConductor) {
            String nom = cond != null ? cond.optString("nombre", session.getNombre()) : session.getNombre();
            String tel = cond != null
                    ? cond.optString("telefono", cond.optString("celular", session.getTelefono()))
                    : session.getTelefono();
            h.txtConductor.setText("👤 " + nom);
            h.txtTelefono.setText("📞 " + (tel == null || tel.isEmpty() ? "N/A" : tel));
        } else {
            int idCond = v.optInt("idConductor", v.optInt("conductorId", v.optInt("conductor_id", -1)));
            if (idCond <= 0 && veh != null) idCond = veh.optInt("idUsuario", -1);
            if (idCond > 0) {
                if (cacheConductores.containsKey(idCond)) {
                    h.txtConductor.setText("👤 " + cacheConductores.get(idCond));
                    h.txtTelefono.setText("");
                } else {
                    h.txtConductor.setText("👤 Cargando...");
                    h.txtTelefono.setText("");
                    cargarNombreConductor(idCond, h);
                }
            } else {
                h.txtConductor.setText("👤 Conductor");
                if (veh != null) {
                    String marca = veh.optString("marca", "");
                    String placa = veh.optString("placa", "");
                    h.txtTelefono.setText(!marca.isEmpty()
                            ? "🚗 " + marca + (!placa.isEmpty() ? " · " + placa : "") : "");
                } else h.txtTelefono.setText("");
            }
        }

        // VEHICULO
        if (veh != null) {
            h.txtVehiculo.setText("🚘 " + veh.optString("marca", "") + " "
                    + veh.optString("modelo", "") + " (" + veh.optString("placa", "---") + ")");
        } else {
            String n = session.getVehiculoNombre();
            String p = session.getVehiculoPlaca();
            h.txtVehiculo.setText(n != null && !n.isEmpty()
                    ? "🚘 " + n + " (" + p + ")" : "🚘 Vehículo no asignado");
        }

        // SECCIÓN PASAJEROS (solo conductor)
        final int    fViajeId  = viajeId;
        final String fEstado   = estado;
        final int    fIdCond   = session.getIdUsuario();

        if (esConductor) {
            boolean mostrarPasajeros =
                    EST_CREADO.equals(estado)     || EST_PROGRAMADO.equals(estado) ||
                            EST_DISPONIBLE.equals(estado) || EST_EN_CURSO.equals(estado)   ||
                            EST_INICIADO.equals(estado)   || EST_FINALIZADO.equals(estado);

            if (h.layoutSeccionPasajeros != null) {
                if (mostrarPasajeros) {
                    h.layoutSeccionPasajeros.setVisibility(View.VISIBLE);
                    // ✅ CORREGIDO: usa el nuevo método robusto con cascada de endpoints
                    cargarPasajerosRobusto(h, fViajeId, fEstado, fIdCond);
                } else {
                    h.layoutSeccionPasajeros.setVisibility(View.GONE);
                }
            }
        } else {
            if (h.layoutSeccionPasajeros != null) h.layoutSeccionPasajeros.setVisibility(View.GONE);
        }

        // RESET BOTONES
        h.btnAceptar.setVisibility(View.GONE);
        h.btnIniciar.setVisibility(View.GONE);
        h.btnFinalizar.setVisibility(View.GONE);
        h.btnCancelar.setVisibility(View.GONE);
        h.btnDetalle.setVisibility(View.GONE);
        h.btnCalificar.setVisibility(View.GONE);

        // ID conductor para calificación pasajero→conductor
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

        // BOTONES SEGÚN ROL + ESTADO
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
                    h.btnAceptar.setText(cuposDisp > 0 ? "🚏 Reservar (" + cuposDisp + ")" : "🚏 Reservar");
                }
                h.btnDetalle.setVisibility(View.VISIBLE);
            } else if (EST_FINALIZADO.equals(estado)) {
                h.btnDetalle.setVisibility(View.VISIBLE);
                mostrarBtnCalificarSiPendiente(h, fViajeId, session.getIdUsuario(), fIdConductor);
            } else if (!EST_CANCELADO.equals(estado)) {
                h.btnDetalle.setVisibility(View.VISIBLE);
            }
        }

        // LISTENERS
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
    //  CARGA ROBUSTA DE PASAJEROS — cascada de 5 endpoints
    // =========================================================================

    /**
     * Intenta cargar las reservas del viaje probando múltiples endpoints en cascada.
     * Esto resuelve el problema de "No se pudo cargar pasajeros" cuando el primer
     * endpoint falla o devuelve array vacío.
     */
    /**
     * SOLUCION DEFINITIVA:
     * GET /api/viajes/{id} devuelve los pasajeros en el campo "usuarios"
     * Estructura: { usuarios: [ { idUsuarios, idViajes, estado, numeroAsientos, usuario:{nombre,...} } ] }
     */
    private void cargarPasajerosRobusto(ViajeViewHolder h, int viajeId, String estado, int idConductor) {
        if (h.layoutPasajerosLista == null) return;

        h.layoutPasajerosLista.removeAllViews();
        if (h.loaderPasajeros      != null) h.loaderPasajeros.setVisibility(View.VISIBLE);
        if (h.txtSinPasajeros      != null) h.txtSinPasajeros.setVisibility(View.GONE);
        if (h.txtContadorPasajeros != null) h.txtContadorPasajeros.setText("...");

        String url = Constantes.viajePorId((long) viajeId);
        Log.d(TAG, "Cargando pasajeros desde: " + url);

        ConexionApi.getInstance(context).getObjectNoCache(url,
                viajeObj -> {
                    JSONArray usuarios = viajeObj.optJSONArray("usuarios");
                    Log.d(TAG, "usuarios[] en viaje " + viajeId + ": "
                            + (usuarios != null ? usuarios.length() : "null"));

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
                err -> {
                    Log.e(TAG, "Error cargando viaje " + viajeId + ": " + err.toString());
                    mostrarErrorCargaPasajeros(h);
                }
        );
    }

    /**
     * Convierte el array "usuarios" del backend al formato que espera procesarReservasEnCard.
     * Backend: { idUsuarios, idViajes, estado, numeroAsientos, usuario: { nombre, ... } }
     * Necesitamos: { estado, numeroAsientos, pasajero: { id, nombre } }
     */
    private JSONArray normalizarUsuariosAReservas(JSONArray usuarios) {
        JSONArray result = new JSONArray();
        for (int i = 0; i < usuarios.length(); i++) {
            try {
                JSONObject u = usuarios.getJSONObject(i);
                JSONObject reserva = new JSONObject();

                reserva.put("estado",          u.optString("estado", "ACTIVA"));
                reserva.put("numeroAsientos",  u.optInt("numeroAsientos", 1));

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


    /**
     * Intenta cargar desde urls[index]. Si falla o devuelve vacío, pasa al siguiente.
     */

    /**
     * Último recurso: obtener el viaje completo y extraer pasajeros/reservas del JSON del viaje.
     */


    // =========================================================================
    //  PROCESAR RESERVAS Y MOSTRAR EN CARD
    // =========================================================================

    private void procesarReservasEnCard(ViajeViewHolder h, JSONArray reservas,
                                        String estado, int viajeId, int idConductor) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (!h.itemView.isAttachedToWindow()) return;
            if (h.loaderPasajeros != null) h.loaderPasajeros.setVisibility(View.GONE);
            if (h.layoutPasajerosLista == null) return;
            h.layoutPasajerosLista.removeAllViews();

            boolean esFinalizado  = EST_FINALIZADO.equals(estado);
            boolean esEnCurso     = EST_EN_CURSO.equals(estado) || EST_INICIADO.equals(estado);

            // ✅ Separar pasajeros en dos grupos: A BORDO y RESERVADOS
            java.util.List<JSONObject> aBordo    = new java.util.ArrayList<>();
            java.util.List<JSONObject> reservados = new java.util.ArrayList<>();

            if (reservas != null) {
                for (int i = 0; i < reservas.length(); i++) {
                    JSONObject res = reservas.optJSONObject(i);
                    if (res == null) continue;
                    String estRes = res.optString("estado", "").toUpperCase().trim();

                    // En viajes no finalizados, ignorar cancelados
                    if (!esFinalizado && ("CANCELADO".equals(estRes) || "CANCELADA".equals(estRes))) continue;

                    if (ESTADOS_A_BORDO.contains(estRes)) {
                        aBordo.add(res);
                    } else {
                        reservados.add(res);
                    }
                }
            }

            int totalMostrados = 0;
            float d = context.getResources().getDisplayMetrics().density;

            // ── GRUPO: A BORDO (solo si hay viaje en curso) ──────────────────
            if (!aBordo.isEmpty() && (esEnCurso || esFinalizado)) {
                agregarSubHeader(h.layoutPasajerosLista,
                        "🚗 A bordo (" + aBordo.size() + ")",
                        "#00695C", "#E0F7FA", d);
                for (int i = 0; i < aBordo.size(); i++) {
                    agregarFilaPasajeroDesdeReserva(h.layoutPasajerosLista,
                            aBordo.get(i), esFinalizado, viajeId, idConductor,
                            totalMostrados, true);
                    totalMostrados++;
                }
            }

            // ── GRUPO: RESERVADOS / ESPERANDO ────────────────────────────────
            if (!reservados.isEmpty()) {
                String labelReservados = esEnCurso
                        ? "⏳ Esperando recogida (" + reservados.size() + ")"
                        : "👥 Reservados (" + reservados.size() + ")";
                if (!aBordo.isEmpty()) { // Solo poner sub-header si ya hay un grupo anterior
                    agregarSubHeader(h.layoutPasajerosLista, labelReservados, "#1565C0", "#E3F2FD", d);
                }
                for (int i = 0; i < reservados.size(); i++) {
                    agregarFilaPasajeroDesdeReserva(h.layoutPasajerosLista,
                            reservados.get(i), esFinalizado, viajeId, idConductor,
                            totalMostrados, false);
                    totalMostrados++;
                }
            }

            // ── Actualizar contador y mensaje vacío ──────────────────────────
            if (h.txtContadorPasajeros != null) h.txtContadorPasajeros.setText(String.valueOf(totalMostrados));

            if (totalMostrados == 0 && h.txtSinPasajeros != null) {
                h.txtSinPasajeros.setVisibility(View.VISIBLE);
                if (esFinalizado) {
                    h.txtSinPasajeros.setText("No hubo pasajeros en este viaje");
                } else if (esEnCurso) {
                    h.txtSinPasajeros.setText("Sin pasajeros a bordo aún");
                } else {
                    h.txtSinPasajeros.setText("Sin pasajeros reservados aún");
                }
            }
        });
    }

    /** Agrega un sub-encabezado de grupo dentro de la lista de pasajeros. */
    private void agregarSubHeader(LinearLayout container, String texto,
                                  String colorTexto, String colorFondo, float d) {
        TextView tv = new TextView(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin    = (int)(4 * d);
        lp.bottomMargin = (int)(4 * d);
        tv.setLayoutParams(lp);
        tv.setText(texto);
        tv.setTextSize(11.5f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(Color.parseColor(colorTexto));
        tv.setPadding((int)(10*d), (int)(5*d), (int)(10*d), (int)(5*d));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(8 * d);
        bg.setColor(Color.parseColor(colorFondo));
        tv.setBackground(bg);
        container.addView(tv);
    }

    /** Extrae datos de un JSONObject de reserva y llama a agregarFilaPasajero. */
    private void agregarFilaPasajeroDesdeReserva(LinearLayout container, JSONObject res,
                                                 boolean esFinalizado, int viajeId,
                                                 int idConductor, int index, boolean aBordo) {
        int    idP      = -1;
        String nomP     = "";
        int    asientos = res.optInt("numeroAsientos", res.optInt("asientos", 1));
        String estRes   = res.optString("estado", "").toUpperCase().trim();

        // Extraer objeto pasajero/usuario
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

        // ── Fila contenedor ──────────────────────────────────────────────────
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
        // Fondo ligeramente diferente para pasajeros a bordo
        if (aBordo) {
            bgFila.setColor(Color.parseColor("#E0F7FA"));
            bgFila.setStroke((int)(1*d), Color.parseColor("#80DEEA"));
        } else {
            bgFila.setColor(index % 2 == 0 ? Color.parseColor("#F0FFFE") : Color.parseColor("#FFFFFF"));
            bgFila.setStroke((int)(1*d), Color.parseColor("#E0F2F1"));
        }
        fila.setBackground(bgFila);

        // ── Avatar ───────────────────────────────────────────────────────────
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

        // ── Columna de texto ─────────────────────────────────────────────────
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

        // Sub-fila: asientos + badge estado
        LinearLayout sub = new LinearLayout(context);
        sub.setOrientation(LinearLayout.HORIZONTAL);
        sub.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpSub = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpSub.topMargin = (int)(2*d);
        sub.setLayoutParams(lpSub);

        TextView tvAs = new TextView(context);
        tvAs.setText("💺 " + asientos);
        tvAs.setTextSize(11f);
        tvAs.setTextColor(Color.parseColor("#6B7280"));
        sub.addView(tvAs);

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

        // ── Botón calificar (solo viajes finalizados) ─────────────────────────
        if (esFinalizado && idPasajero > 0) {
            Button btnCal = new Button(context);
            LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                    (int)(38*d), (int)(30*d));
            lpBtn.leftMargin = p4;
            btnCal.setLayoutParams(lpBtn);
            btnCal.setText("⭐");
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
                        idConductor, true,
                        (pun, com) -> marcarCalificado(btnCal, fDens));
            });
            fila.addView(btnCal);
        }

        container.addView(fila);
    }

    private void marcarCalificado(Button btn, float d) {
        btn.setText("✅");
        btn.setEnabled(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(14*d);
        bg.setColor(Color.parseColor("#F0FFF4"));
        bg.setStroke((int)(1*d), Color.parseColor("#A7F3D0"));
        btn.setBackground(bg);
    }

    private String etiquetaBadge(String e) {
        switch (e.toUpperCase()) {
            case "ACTIVA": case "CONFIRMADA":      return "✓ Confirmada";
            case "PENDIENTE":                      return "⏳ Pendiente";
            case "EN_CURSO": case "INICIADO":      return "🚗 A bordo";
            case "ESPERANDO_RECOGIDA":             return "📍 Esperando";
            case "RECOGIDO":                       return "✓ Recogido";
            case "CANCELADO": case "CANCELADA":    return "✕ Cancelada";
            case "COMPLETADO": case "FINALIZADO":  return "🏁 Completada";
            default:                               return e;
        }
    }

    private int colorBadge(String e) {
        switch (e.toUpperCase()) {
            case "ACTIVA": case "CONFIRMADA":      return Color.parseColor("#2E7D32");
            case "PENDIENTE":                      return Color.parseColor("#F57F17");
            case "EN_CURSO": case "INICIADO":
            case "RECOGIDO":                       return Color.parseColor("#00838F");
            case "ESPERANDO_RECOGIDA":             return Color.parseColor("#E65100");
            case "CANCELADO": case "CANCELADA":    return Color.parseColor("#B71C1C");
            case "COMPLETADO": case "FINALIZADO":  return Color.parseColor("#1565C0");
            default:                               return Color.parseColor("#546E7A");
        }
    }

    // =========================================================================
    //  BTN CALIFICAR grande (zona de botones)
    // =========================================================================
    private void mostrarBtnCalificarSiPendiente(ViajeViewHolder h, int viajeId,
                                                int idCalificador, int idCalificado) {
        if (Boolean.TRUE.equals(cacheCalificado.get(viajeId))) {
            h.btnCalificar.setVisibility(View.GONE);
            return;
        }
        h.btnCalificar.setVisibility(View.VISIBLE);
        h.btnCalificar.setText("⭐ Calificar viaje");
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
                        if (idC <= 0) for (String k : new String[]{"id","idUsuarios","idUsuario"}) { int id = co.optInt(k,-1); if (id>0){idC=id;break;} }
                        for (String k : new String[]{"nombre","nombreCompleto","name"}) { String n=co.optString(k,""); if(!n.isEmpty()&&!n.equals("null")){nomC=n;break;} }
                    }
                    if (idC <= 0) { new Handler(Looper.getMainLooper()).post(()->Toast.makeText(context,"No se encontró el conductor",Toast.LENGTH_SHORT).show()); return; }
                    final int fId=idC; final String fNom=nomC.isEmpty()?"el conductor":nomC;
                    new Handler(Looper.getMainLooper()).post(()->abrirBottomSheetCalificacion(viajeId, idCalificador, fId, fNom, false));
                },
                err -> new Handler(Looper.getMainLooper()).post(()->Toast.makeText(context,"Error al obtener datos del viaje",Toast.LENGTH_SHORT).show()));
    }

    private void confirmarCancelar(int viajeId) {
        new AlertDialog.Builder(context).setTitle("Cancelar viaje").setMessage("¿Estás seguro de que quieres cancelar este viaje?")
                .setPositiveButton("Sí, cancelar", (d,w)->cambiarEstado(viajeId, Constantes.viajeCancelar((long)viajeId),"cancelado"))
                .setNegativeButton("No", null).show();
    }

    private void confirmarYFinalizar(int viajeId) {
        new AlertDialog.Builder(context).setTitle("Finalizar viaje").setMessage("¿Finalizar el viaje? Se liberarán todos los cupos.")
                .setPositiveButton("Finalizar",(d,w)->ejecutarFlujoFinalizar(viajeId))
                .setNegativeButton("Cancelar",null).show();
    }

    private void ejecutarFlujoFinalizar(int viajeId) {
        ConexionApi.getInstance(context).post(Constantes.viajePorId((long)viajeId)+"/pasajeros-bajaron",
                null, r1->paso2Finalizar(viajeId), err1->paso2Finalizar(viajeId));
    }

    private void paso2Finalizar(int viajeId) {
        ConexionApi.getInstance(context).post(Constantes.viajeFinalizar((long)viajeId), null,
                r2 -> new Handler(Looper.getMainLooper()).post(()->{
                    Toast.makeText(context,"✅ Viaje finalizado correctamente",Toast.LENGTH_SHORT).show();
                    notifyDataSetChanged();
                }),
                err2 -> new Handler(Looper.getMainLooper()).post(()->
                        Toast.makeText(context,"❌ Error al finalizar el viaje",Toast.LENGTH_LONG).show()));
    }

    private void paso3BuscarPasajero(int viajeId) { intentarEndpointReservas(viajeId, 0); }

    private void intentarEndpointReservas(int viajeId, int index) {
        String[] urls = {
                Constantes.BASE_URL + "/api/reservas/viaje/"   + viajeId,
                Constantes.BASE_URL + "/api/reservas?idViaje=" + viajeId,
                Constantes.BASE_URL + "/api/reservas?viajeId=" + viajeId,
                Constantes.BASE_URL + "/api/viajes/"           + viajeId,
        };
        if (index >= urls.length) { mostrarErrorCalificacion(); return; }
        ConexionApi.getInstance(context).getArrayNoCache(urls[index],
                r -> { if(r.length()==0 && index<urls.length-1) intentarEndpointReservas(viajeId,index+1); else procesarReservas(r,viajeId); },
                e -> ConexionApi.getInstance(context).getObjectNoCache(urls[index],
                        obj -> { JSONArray arr=extraerArrayDeObjeto(obj); if(arr!=null&&arr.length()>0) procesarReservas(arr,viajeId); else intentarEndpointReservas(viajeId,index+1); },
                        e2 -> intentarEndpointReservas(viajeId,index+1)));
    }

    private JSONArray extraerArrayDeObjeto(JSONObject obj) {
        if (obj==null) return null;
        for (String k:new String[]{"items","reservas","content","data","list","results","pasajeros","passengers","bookings"}) {
            JSONArray a=obj.optJSONArray(k); if(a!=null) return a;
        }
        Iterator<String> keys=obj.keys();
        while(keys.hasNext()){ Object val=obj.opt(keys.next()); if(val instanceof JSONArray) return (JSONArray)val; }
        return null;
    }

    private void procesarReservas(JSONArray reservas, int viajeId) {
        java.util.ArrayList<Integer> ids=new java.util.ArrayList<>();
        java.util.ArrayList<String> nombres=new java.util.ArrayList<>();
        if (reservas==null||reservas.length()==0) {
            new Handler(Looper.getMainLooper()).post(()->Toast.makeText(context,"Este viaje no tiene pasajeros para calificar",Toast.LENGTH_SHORT).show()); return;
        }
        for (int i=0;i<reservas.length();i++) {
            JSONObject res=reservas.optJSONObject(i); if(res==null) continue;
            String est=res.optString("estado","").toUpperCase();
            if("CANCELADO".equals(est)||"CANCELADA".equals(est)) continue;
            int idP=-1; String nomP="";
            JSONObject po=null;
            for (String k:new String[]{"pasajero","usuario","user","passenger"}) { JSONObject c=res.optJSONObject(k); if(c!=null){po=c;break;} }
            if (po!=null) {
                for (String k:new String[]{"id","idUsuarios","idUsuario","userId"}) { int id=po.optInt(k,-1); if(id>0){idP=id;break;} }
                for (String k:new String[]{"nombre","nombreCompleto","name","nombres"}) { String n=po.optString(k,""); if(!n.isEmpty()&&!n.equals("null")){nomP=n;break;} }
                if(nomP.isEmpty()){String n=po.optString("nombres",""),a=po.optString("apellidos",""); if(!n.isEmpty()||!a.isEmpty()) nomP=(n+" "+a).trim();}
            }
            if (idP<=0) for (String k:new String[]{"idUsuarios","idUsuario","idPasajero","pasajeroId"}) { int id=res.optInt(k,-1); if(id>0){idP=id;break;} }
            if (idP>0) { ids.add(idP); nombres.add(nomP.isEmpty()?"Pasajero":nomP); }
        }
        if (ids.isEmpty()) { new Handler(Looper.getMainLooper()).post(()->Toast.makeText(context,"No hay pasajeros activos para calificar",Toast.LENGTH_SHORT).show()); return; }
        final int idCal=new SessionManager(context).getIdUsuario();
        new Handler(Looper.getMainLooper()).postDelayed(()->mostrarCalificacionesEncadenadas(ids,nombres,idCal,viajeId,0),800);
    }

    private void mostrarCalificacionesEncadenadas(java.util.ArrayList<Integer> ids, java.util.ArrayList<String> nombres, int idCalificador, int viajeId, int indice) {
        if (indice>=ids.size()) return;
        int idP=ids.get(indice); String nomP=nombres.get(indice); int sig=indice+1;
        new CalificacionesManager(context).verificarCalificacion(viajeId, idCalificador, idP,
                new CalificacionesManager.OnVerificacionListener() {
                    @Override public void onDebeCalificar() {
                        abrirBottomSheetCalificacion(viajeId,idCalificador,idP,nomP,true);
                        new Handler(Looper.getMainLooper()).postDelayed(()->mostrarCalificacionesEncadenadas(ids,nombres,idCalificador,viajeId,sig),3000);
                    }
                    @Override public void onYaCalifico(int p,String e) { mostrarCalificacionesEncadenadas(ids,nombres,idCalificador,viajeId,sig); }
                });
    }

    private void abrirBottomSheetCalificacion(int viajeId, int idCalificador, int idCalificado, String nomCalificado, boolean esConductorCal) {
        Activity activity=resolveActivity(context);
        if (activity==null||activity.isFinishing()||activity.isDestroyed()) return;
        new CalificacionesManager(context).verificarCalificacion(viajeId, idCalificador, idCalificado,
                new CalificacionesManager.OnVerificacionListener() {
                    @Override public void onDebeCalificar() {
                        cacheCalificado.put(viajeId,false);
                        activity.runOnUiThread(()->CalificacionController.mostrarBottomSheetCalificar(
                                activity, viajeId, idCalificado, nomCalificado, idCalificador, esConductorCal,
                                (p,c)->{cacheCalificado.put(viajeId,true); activity.runOnUiThread(()->notifyDataSetChanged());}));
                    }
                    @Override public void onYaCalifico(int p,String e) {
                        cacheCalificado.put(viajeId,true);
                        activity.runOnUiThread(()->{Toast.makeText(context,"Ya calificaste este viaje "+e,Toast.LENGTH_SHORT).show(); notifyDataSetChanged();});
                    }
                });
    }

    private void mostrarErrorCalificacion() {
        new Handler(Looper.getMainLooper()).post(()->Toast.makeText(context,"No se pudo cargar la información del pasajero",Toast.LENGTH_LONG).show());
    }

    // =========================================================================
    //  HELPERS
    // =========================================================================
    private String etiquetaEstado(String e) {
        switch (e) {
            case EST_CREADO: case EST_DISPONIBLE: return "Publicado";
            case EST_PROGRAMADO:  return "Programado";
            case EST_EN_CURSO: case EST_INICIADO: return "En curso 🚗";
            case EST_FINALIZADO:  return "Finalizado 🏁";
            case EST_CANCELADO:   return "Cancelado ❌";
            default:              return e;
        }
    }

    private void cargarNombreConductor(int idConductor, ViajeViewHolder holder) {
        ConexionApi.getInstance(context).getObject(Constantes.usuarioDetalle((long)idConductor),
                resp -> {
                    String nom=extraerNombreDeJson(resp); if(nom.isEmpty()) nom="Conductor";
                    cacheConductores.put(idConductor,nom); final String nf=nom;
                    if(holder.itemView.isAttachedToWindow()) holder.itemView.post(()->{
                        holder.txtConductor.setText("👤 "+nf);
                        String tel=resp.optString("telefono",resp.optString("celular",""));
                        holder.txtTelefono.setText(tel.isEmpty()?"":"📞 "+tel);
                    });
                },
                err->ConexionApi.getInstance(context).getObject(Constantes.USUARIOS+"/"+idConductor,
                        resp2->{String n2=extraerNombreDeJson(resp2); if(n2.isEmpty()) n2="Conductor"; cacheConductores.put(idConductor,n2); final String nf2=n2;
                            if(holder.itemView.isAttachedToWindow()) holder.itemView.post(()->holder.txtConductor.setText("👤 "+nf2));},
                        err2->{if(holder.itemView.isAttachedToWindow()) holder.itemView.post(()->holder.txtConductor.setText("👤 Conductor"));}));
    }

    private String extraerNombreDeJson(JSONObject obj) {
        if(obj==null) return "";
        for(String k:new String[]{"nombre","nombreCompleto","name","fullName","nombreUsuario","displayName"}){String v=obj.optString(k,""); if(!v.isEmpty()&&!v.equals("null")) return v;}
        String n=obj.optString("nombres",""),a=obj.optString("apellidos",""); if(!n.isEmpty()||!a.isEmpty()) return (n+" "+a).trim();
        return "";
    }

    private void abrirDetalle(int viajeId) {
        Intent i=new Intent(context,DetalleViajeActivity.class); i.putExtra("ID_VIAJE",viajeId); context.startActivity(i);
    }

    private void cambiarEstado(int viajeId, String url, String accion) {
        ConexionApi.getInstance(context).post(url,null,
                r->{Toast.makeText(context,"Viaje "+accion+" ✅",Toast.LENGTH_SHORT).show(); notifyDataSetChanged();},
                e->Toast.makeText(context,"Error al "+accion+" ❌",Toast.LENGTH_SHORT).show());
    }

    @Override public int getItemCount() { return viajes.size(); }

    // =========================================================================
    //  VIEW HOLDER
    // =========================================================================

    private void mostrarErrorCargaPasajeros(ViajeViewHolder h) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (!h.itemView.isAttachedToWindow()) return;
            if (h.loaderPasajeros      != null) h.loaderPasajeros.setVisibility(View.GONE);
            if (h.txtContadorPasajeros != null) h.txtContadorPasajeros.setText("0");
            if (h.txtSinPasajeros      != null) {
                h.txtSinPasajeros.setVisibility(View.VISIBLE);
                h.txtSinPasajeros.setText("Sin pasajeros reservados aun");
            }
        });
    }

    static class ViajeViewHolder extends RecyclerView.ViewHolder {
        TextView     txtRuta, txtFecha, txtCupos, txtPrecio;
        TextView     txtConductor, txtTelefono, txtVehiculo, txtEstado;
        Button       btnAceptar, btnIniciar, btnFinalizar, btnCancelar, btnDetalle, btnCalificar;
        LinearLayout layoutSeccionPasajeros, layoutPasajerosLista;
        TextView     txtContadorPasajeros, txtSinPasajeros;
        ProgressBar  loaderPasajeros;

        ViajeViewHolder(@NonNull View v) {
            super(v);
            txtRuta                = v.findViewById(R.id.txtRuta);
            txtFecha               = v.findViewById(R.id.txtFecha);
            txtCupos               = v.findViewById(R.id.txtCupos);
            txtPrecio              = v.findViewById(R.id.txtPrecio);
            txtConductor           = v.findViewById(R.id.txtConductor);
            txtTelefono            = v.findViewById(R.id.txtTelefono);
            txtVehiculo            = v.findViewById(R.id.txtVehiculo);
            txtEstado              = v.findViewById(R.id.txtEstado);
            btnAceptar             = v.findViewById(R.id.btnAceptar);
            btnIniciar             = v.findViewById(R.id.btnIniciar);
            btnFinalizar           = v.findViewById(R.id.btnFinalizar);
            btnCancelar            = v.findViewById(R.id.btnCancelar);
            btnDetalle             = v.findViewById(R.id.btnDetalle);
            btnCalificar           = v.findViewById(R.id.btnCalificar);
            layoutSeccionPasajeros = v.findViewById(R.id.layoutSeccionPasajeros);
            layoutPasajerosLista   = v.findViewById(R.id.layoutPasajerosLista);
            txtContadorPasajeros   = v.findViewById(R.id.txtContadorPasajeros);
            txtSinPasajeros        = v.findViewById(R.id.txtSinPasajeros);
            loaderPasajeros        = v.findViewById(R.id.loaderPasajeros);
        }
    }
}