package com.arlys.moviflexx.controller;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import android.content.pm.PackageManager;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.ConexionApi;
import com.arlys.moviflexx.model.Constantes;
import com.arlys.moviflexx.model.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MisReservasActivity extends BaseActivity {

    private static final String TAG          = "MisReservas";
    private static final double RADIO_KM     = 15.0;
    private static final int    REQ_LOCATION = 2001;

    private static final String ESTADO_EN_CURSO   = "EN_CURSO";
    private static final String ESTADO_INICIADO   = "INICIADO";
    private static final String ESTADO_FINALIZADO = "FINALIZADO";
    private static final String ESTADO_CANCELADO  = "CANCELADO";

    private static final List<String> RESERVA_ACTIVA_ESTADOS = Arrays.asList(
            "ACTIVA", "CONFIRMADA", "PENDIENTE", "ESPERANDO_RECOGIDA", "RECOGIDO", "EN_CURSO"
    );

    private static final int[] COLORES_INT = {
            0xFF009B8D, 0xFFF59E0B, 0xFFEF4444, 0xFF3B82F6, 0xFF10B981
    };

    // ── UI ────────────────────────────────────────────────────────────────────
    private TextInputEditText editOrigen, editDestino;
    private MaterialButton    btnBuscar;
    private LinearLayout      layoutBuscando;
    private LinearLayout      layoutLabelResultados;
    private TextView          txtLabelResultados, txtContadorResultados;
    private LinearLayout      layoutResultadosViajes;
    private LinearLayout      layoutVacio;
    private TextView          txtVacio, txtVacioSub;
    private LinearLayout      layoutLabelMisReservas;
    private LinearLayout      layoutMisReservas;
    private ProgressBar       loaderMisReservas;

    private MaterialCardView  layoutBloqueoBusqueda;
    private TextView          txtBloqueoBusqueda;

    private LinearLayout      layoutSugerencias;

    // ── Estado ────────────────────────────────────────────────────────────────
    private SessionManager session;
    private double  latOrigen = 0, lngOrigen = 0;
    private double  latDestino = 0, lngDestino = 0;
    private boolean busquedaActiva = false;
    private boolean gpsListo       = false;

    private boolean tieneReservaActiva   = false;
    private int     idViajeReservaActiva = 0;

    private static final Map<String, String> CORRECTOR = new HashMap<String, String>() {{
        put("popyan","Popayán"); put("popayan","Popayán"); put("popayn","Popayán");
        put("cauca","Cauca"); put("caucca","Cauca");
        put("bolivar","Bolívar"); put("bolevar","Bolívar");
        put("yanaconas","Yanaconas"); put("yanakona","Yanaconas");
        put("torobajo","Toro Bajo"); put("el canton","El Cantón");
        put("canton","El Cantón"); put("campohermoso","Campo Hermoso");
        put("pomona","La Pomona"); put("berlin","Berlín");
        put("caldono","Caldono"); put("la estacion","La Estación");
        put("estacion","La Estación"); put("el tablon","El Tablón");
        put("tablon","El Tablón"); put("cementerio","Cementerio Central");
        put("puente el señor","Puente El Señor"); put("bello horizonte","Bello Horizonte");
        put("bellohorizonte","Bello Horizonte"); put("el empedrado","El Empedrado");
        put("empedrado","El Empedrado"); put("la esmeralda","La Esmeralda");
        put("esmeralda","La Esmeralda"); put("nueva esperanza","Nueva Esperanza");
        put("el cortijo","El Cortijo"); put("cortijo","El Cortijo");
        put("la floresta","La Floresta"); put("floresta","La Floresta");
        put("tierradentro","Tierradentro"); put("pandiguando","Pandiguando");
        put("el liceo","El Liceo"); put("liceo","El Liceo");
        put("universidades","Universidades"); put("unicauca","Unicauca");
        put("universidad del cauca","Unicauca"); put("el parque","Parque Caldas");
        put("parque caldas","Parque Caldas"); put("la pamba","La Pamba");
        put("la loma","La Loma"); put("el morro","El Morro");
    }};

    // =========================================================================
    //  LIFECYCLE
    // =========================================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(
                android.graphics.Color.parseColor("#0ABFA3"));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(R.layout.activity_mis_reservas);
        session = new SessionManager(this);
        initViews();
        verificarPermisosGPS();
        cargarMisReservas();

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> onBackPressed());
    }

    @Override protected void onResume() {
        super.onResume();
        cargarMisReservas();
    }

    // =========================================================================
    //  INIT
    // =========================================================================
    private void initViews() {
        editOrigen             = findViewById(R.id.edit_origen_pasajero);
        editDestino            = findViewById(R.id.edit_destino_pasajero);
        btnBuscar              = findViewById(R.id.btn_buscar_viajes);
        layoutBuscando         = findViewById(R.id.layout_buscando);
        layoutLabelResultados  = findViewById(R.id.layout_label_resultados);
        txtLabelResultados     = findViewById(R.id.txt_label_resultados);
        txtContadorResultados  = findViewById(R.id.txt_contador_resultados);
        layoutResultadosViajes = findViewById(R.id.layout_resultados_viajes);
        layoutVacio            = findViewById(R.id.layout_vacio);
        txtVacio               = findViewById(R.id.txt_vacio);
        txtVacioSub            = findViewById(R.id.txt_vacio_sub);
        layoutLabelMisReservas = findViewById(R.id.layout_label_mis_reservas);
        layoutMisReservas      = findViewById(R.id.layout_mis_reservas);
        loaderMisReservas      = findViewById(R.id.loader_mis_reservas);

        layoutBloqueoBusqueda = findViewById(R.id.layout_bloqueo_busqueda);
        txtBloqueoBusqueda    = findViewById(R.id.txt_bloqueo_busqueda);

        layoutSugerencias = findViewById(R.id.layout_sugerencias_destino);

        if (btnBuscar != null) btnBuscar.setOnClickListener(v -> iniciarBusqueda());

        if (editDestino != null) {
            editDestino.addTextChangedListener(new android.text.TextWatcher() {
                private final Handler autoH = new Handler(Looper.getMainLooper());
                private Runnable autoR;
                @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
                @Override public void afterTextChanged(android.text.Editable s){}
                @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                    if(autoR!=null)autoH.removeCallbacks(autoR);
                    String txt=s.toString().trim().toLowerCase();
                    if(txt.length()<2){ocultarSugerencias();return;}
                    autoR=()->{
                        String corr=CORRECTOR.get(txt);
                        if(corr!=null&&!corr.equalsIgnoreCase(s.toString().trim())){
                            runOnUiThread(()->{
                                editDestino.removeTextChangedListener(this);
                                editDestino.setText(corr);editDestino.setSelection(corr.length());
                                editDestino.addTextChangedListener(this);
                            });return;
                        }
                        if(txt.length()>=3)buscarSugerenciasNominatim(s.toString().trim());
                    };
                    autoH.postDelayed(autoR,400);
                }
            });
        }
    }

    private void buscarSugerenciasNominatim(String query){
        new Thread(()->{
            try{
                String url="https://nominatim.openstreetmap.org/search?q="+java.net.URLEncoder.encode(query+", Popayán, Colombia","UTF-8")+"&format=json&limit=5&countrycodes=co&accept-language=es";
                String resp=petHttp(url); JSONArray arr=new JSONArray(resp);
                List<String> sugs=new ArrayList<>();
                for(int i=0;i<arr.length();i++){String dn=arr.getJSONObject(i).optString("display_name","");if(!dn.isEmpty())sugs.add(dn.split(",")[0].trim());}
                runOnUiThread(()->mostrarSugerencias(sugs));
            }catch(Exception ignored){}
        }).start();
    }

    private void mostrarSugerencias(List<String> sugs){
        if(layoutSugerencias==null)return;
        layoutSugerencias.removeAllViews();
        if(sugs.isEmpty()){layoutSugerencias.setVisibility(View.GONE);return;}
        layoutSugerencias.setVisibility(View.VISIBLE);
        float d=getResources().getDisplayMetrics().density;
        for(String s:sugs){
            TextView tv=new TextView(this);tv.setText(""+s);tv.setTextSize(13f);tv.setTextColor(Color.parseColor("#004D40"));tv.setPadding((int)(12*d),(int)(10*d),(int)(12*d),(int)(10*d));tv.setClickable(true);tv.setFocusable(true);
            tv.setOnClickListener(v->{if(editDestino!=null){editDestino.setText(s);editDestino.setSelection(s.length());}layoutSugerencias.setVisibility(View.GONE);});
            layoutSugerencias.addView(tv);
            View sep=new View(this);sep.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,1));sep.setBackgroundColor(Color.parseColor("#E0E0E0"));layoutSugerencias.addView(sep);
        }
    }

    private void ocultarSugerencias(){if(layoutSugerencias!=null)layoutSugerencias.setVisibility(View.GONE);}

    // =========================================================================
    //  GPS
    // =========================================================================
    private void verificarPermisosGPS(){
        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQ_LOCATION);
        else activarGPS();
    }

    private void activarGPS(){
        android.location.LocationManager lm = (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) return;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        android.location.Location last = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
        if (last == null) last = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
        if (last != null) {
            latOrigen = last.getLatitude();
            lngOrigen = last.getLongitude();
            gpsListo = true;
            String dir = obtenerDireccion(latOrigen, lngOrigen);
            if (editOrigen != null) editOrigen.setText(dir);
            View gl = findViewById(R.id.layout_gps_status);
            if (gl != null) gl.setVisibility(View.GONE);
        }
        lm.requestSingleUpdate(android.location.LocationManager.GPS_PROVIDER, loc -> {
            latOrigen = loc.getLatitude();
            lngOrigen = loc.getLongitude();
            gpsListo = true;
            String dir = obtenerDireccion(latOrigen, lngOrigen);
            runOnUiThread(() -> {
                if (editOrigen != null) editOrigen.setText(dir);
                View gl = findViewById(R.id.layout_gps_status);
                if (gl != null) gl.setVisibility(View.GONE);
            });
        }, null);
    }

    @Override public void onRequestPermissionsResult(int req,@NonNull String[] p,@NonNull int[] g){
        super.onRequestPermissionsResult(req,p,g);
        if(req==REQ_LOCATION&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)activarGPS();
        else Toast.makeText(this,"⚠️ Sin permiso GPS — ingresa tu ubicación manualmente",Toast.LENGTH_LONG).show();
    }

    private String obtenerDireccion(double lat, double lng){
        try{
            Geocoder gc=new Geocoder(this, new Locale("es","CO"));
            List<Address> l=gc.getFromLocation(lat, lng, 1);
            if(l!=null&&!l.isEmpty()){String ln=l.get(0).getAddressLine(0);return ln!=null?ln:"Mi ubicación";}
        }catch(Exception e){Log.e(TAG,"Geocoder: "+e.getMessage());}
        return "Mi ubicación actual";
    }

    // =========================================================================
    //  MIS RESERVAS + LÓGICA DE BLOQUEO
    // =========================================================================
    private void cargarMisReservas(){
        if(loaderMisReservas!=null)loaderMisReservas.setVisibility(View.VISIBLE);
        ConexionApi.getInstance(this).getArray(Constantes.MIS_RESERVAS,
                response->{if(loaderMisReservas!=null)loaderMisReservas.setVisibility(View.GONE);runOnUiThread(()->procesarMisReservas(response));},
                error->{if(loaderMisReservas!=null)loaderMisReservas.setVisibility(View.GONE);runOnUiThread(()->{tieneReservaActiva=false;actualizarEstadoBusqueda();if(layoutLabelMisReservas!=null)layoutLabelMisReservas.setVisibility(View.GONE);});});
    }

    private void procesarMisReservas(JSONArray reservas){
        if(layoutMisReservas==null)return;
        layoutMisReservas.removeAllViews();
        tieneReservaActiva=false; idViajeReservaActiva=0;
        if(reservas==null||reservas.length()==0){
            if(layoutLabelMisReservas!=null)layoutLabelMisReservas.setVisibility(View.GONE);
            actualizarEstadoBusqueda();
            return;
        }
        int count=0;
        for(int i=0;i<reservas.length();i++){
            JSONObject r=reservas.optJSONObject(i); if(r==null) continue;
            String estadoReserva = r.optString("estado","").toUpperCase().trim();

            // ── Excluir reservas canceladas o completadas ──
            if("CANCELADA".equals(estadoReserva) || "CANCELADO".equals(estadoReserva)
                    || "COMPLETADO".equals(estadoReserva) || "FINALIZADO".equals(estadoReserva))
                continue;

            // ── NUEVO: verificar también el estado del viaje asociado ──
            JSONObject viajeObj = r.optJSONObject("viaje");
            if(viajeObj != null){
                String estadoViaje = viajeObj.optString("estado","").toUpperCase().trim();
                // Si el viaje ya finalizó, no mostrar la reserva en "activas"
                if("FINALIZADO".equals(estadoViaje) || "COMPLETADO".equals(estadoViaje)
                        || "CANCELADO".equals(estadoViaje) || "CANCELADA".equals(estadoViaje))
                    continue;
            }

            // ── Detectar reserva activa para bloquear búsqueda ──
            if(RESERVA_ACTIVA_ESTADOS.contains(estadoReserva)
                    || "RESERVADO".equals(estadoReserva)){
                // Solo bloquear si el viaje está activo (no finalizado)
                boolean viajeActivo = true;
                if(viajeObj != null){
                    String ev = viajeObj.optString("estado","").toUpperCase().trim();
                    viajeActivo = !("FINALIZADO".equals(ev) || "COMPLETADO".equals(ev)
                            || "CANCELADO".equals(ev));
                }
                if(viajeActivo){
                    tieneReservaActiva = true;
                    int idV = r.optInt("idViaje",0);
                    if(idV == 0 && viajeObj != null) idV = extraerIdViaje(viajeObj);
                    idViajeReservaActiva = idV;
                }
            }

            agregarCardMiReserva(r);
            count++;
        }
        if(layoutLabelMisReservas!=null)
            layoutLabelMisReservas.setVisibility(count>0 ? View.VISIBLE : View.GONE);
        actualizarEstadoBusqueda();
    }

    private void actualizarEstadoBusqueda(){
        if(tieneReservaActiva){
            if(btnBuscar!=null){btnBuscar.setEnabled(false);btnBuscar.setAlpha(0.5f);btnBuscar.setText("Ya tienes un viaje activo");}
            if(editDestino!=null){editDestino.setEnabled(false);editDestino.setAlpha(0.6f);}
            if(editOrigen!=null){editOrigen.setEnabled(false);editOrigen.setAlpha(0.6f);}
            if(layoutBloqueoBusqueda!=null){layoutBloqueoBusqueda.setVisibility(View.VISIBLE);}else{mostrarBannerBloqueo();}
            if(layoutResultadosViajes!=null)layoutResultadosViajes.removeAllViews();
            if(layoutLabelResultados!=null)layoutLabelResultados.setVisibility(View.GONE);
            if(layoutVacio!=null)layoutVacio.setVisibility(View.GONE);
        }else{
            if(btnBuscar!=null){btnBuscar.setEnabled(true);btnBuscar.setAlpha(1f);btnBuscar.setText("Buscar viajes disponibles");}
            if(editDestino!=null){editDestino.setEnabled(true);editDestino.setAlpha(1f);}
            if(editOrigen!=null){editOrigen.setEnabled(true);editOrigen.setAlpha(1f);}
            if(layoutBloqueoBusqueda!=null)layoutBloqueoBusqueda.setVisibility(View.GONE);
            View bd=findViewById(R.id.banner_bloqueo_dinamico);if(bd!=null)bd.setVisibility(View.GONE);
        }
    }

    private void mostrarBannerBloqueo(){
        View existente=findViewById(R.id.banner_bloqueo_dinamico);if(existente!=null){existente.setVisibility(View.VISIBLE);return;}
        LinearLayout raiz=findViewById(R.id.layout_root_mis_reservas);if(raiz==null)return;
        float d=getResources().getDisplayMetrics().density;
        MaterialCardView banner=new MaterialCardView(this);banner.setId(R.id.banner_bloqueo_dinamico);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);lp.setMargins((int)(16*d),(int)(8*d),(int)(16*d),(int)(8*d));banner.setLayoutParams(lp);banner.setRadius(14*d);banner.setCardElevation(3*d);banner.setCardBackgroundColor(Color.parseColor("#FFF3E0"));
        LinearLayout inner=new LinearLayout(this);inner.setOrientation(LinearLayout.VERTICAL);inner.setPadding((int)(16*d),(int)(12*d),(int)(16*d),(int)(12*d));
        TextView tv1=new TextView(this);tv1.setText("🔒 Búsqueda bloqueada");tv1.setTextSize(14f);tv1.setTypeface(null,Typeface.BOLD);tv1.setTextColor(Color.parseColor("#E65100"));inner.addView(tv1);
        TextView tv2=new TextView(this);tv2.setText("Ya tienes un viaje activo. Una vez que el conductor finalice el viaje podrás buscar uno nuevo.");tv2.setTextSize(13f);tv2.setTextColor(Color.parseColor("#BF360C"));LinearLayout.LayoutParams lpT=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);lpT.topMargin=(int)(4*d);tv2.setLayoutParams(lpT);inner.addView(tv2);
        if(idViajeReservaActiva>0){MaterialButton btnV=new MaterialButton(this);btnV.setText("Ver mi viaje activo →");btnV.setTextSize(13f);btnV.setTextColor(Color.WHITE);btnV.setBackgroundColor(Color.parseColor("#FF6F00"));btnV.setCornerRadius((int)(10*d));LinearLayout.LayoutParams lpB=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,(int)(40*d));lpB.topMargin=(int)(8*d);btnV.setLayoutParams(lpB);final int id=idViajeReservaActiva;btnV.setOnClickListener(v->abrirDetalle(id));inner.addView(btnV);}
        banner.addView(inner);raiz.addView(banner,0);
    }

    // =========================================================================
    //  CARD MI RESERVA
    // =========================================================================
    private void agregarCardMiReserva(JSONObject reserva) {
        float d = getResources().getDisplayMetrics().density;
        int p16=(int)(16*d), p12=(int)(12*d), p8=(int)(8*d), p4=(int)(4*d);

        int    idViaje   = extraerIdViaje(reserva);
        String origen    = "Origen";
        String destino   = "Destino";
        String conductor = "";
        String estado    = reserva.optString("estado", "ACTIVA");
        String fechaSal  = "";
        int    asientos  = reserva.optInt("numeroAsientos", reserva.optInt("asientos", 1));
        String nombrePar = reserva.optString("nombreParada", "");
        int    idConductorViaje = -1;

        JSONObject viajeObj = reserva.optJSONObject("viaje");
        if (viajeObj != null) {
            if (idViaje == 0) idViaje = extraerIdViaje(viajeObj);
            fechaSal = viajeObj.optString("fechaHoraSalida", "");

            JSONObject ruta = viajeObj.optJSONObject("ruta");
            if (ruta != null) {
                origen  = extraerOrigenDeRuta(ruta);
                destino = extraerDestinoDeRuta(ruta);
            }

            // Log completo del viaje para diagnóstico
            logViajeCompleto(viajeObj);

            // Extracción robusta del conductor
            JSONObject condObj = viajeObj.optJSONObject("conductor");
            if (condObj != null) {
                conductor = extractNombreCompleto(condObj);
                idConductorViaje = condObj.optInt("id",
                        condObj.optInt("idUsuarios",
                                condObj.optInt("idUsuario", -1)));
            }

            if (conductor.isEmpty()) {
                conductor = primeraNoVacia(
                        viajeObj.optString("nombreConductor",   ""),
                        viajeObj.optString("conductorNombre",   ""),
                        viajeObj.optString("conductor",         ""),
                        viajeObj.optString("nameConductor",     "")
                );
            }

            if (idConductorViaje <= 0) {
                idConductorViaje = viajeObj.optInt("idConductor",
                        viajeObj.optInt("conductorId",
                                viajeObj.optInt("idUsuarioConductor", -1)));
            }
        }

        if (conductor.isEmpty()) {
            conductor = primeraNoVacia(
                    reserva.optString("nombreConductor", ""),
                    reserva.optString("conductorNombre",  ""),
                    reserva.optString("conductor",        "")
            );
        }

        if (conductor.isEmpty()) {
            JSONObject condReserva = reserva.optJSONObject("conductor");
            if (condReserva != null) {
                conductor = extractNombreCompleto(condReserva);
                if (idConductorViaje <= 0)
                    idConductorViaje = condReserva.optInt("id",
                            condReserva.optInt("idUsuarios", -1));
            }
        }

        final String conductorFinal  = conductor;
        final int    idCondFinal     = idConductorViaje;
        final int    idViajeFinal    = idViaje;

        // ── Construir card ────────────────────────────────────────────────────
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpCard.setMargins(0, 0, 0, p12);
        card.setLayoutParams(lpCard);
        card.setRadius(18 * d); card.setCardElevation(5 * d);
        card.setCardBackgroundColor(Color.WHITE);
        card.setClickable(true); card.setFocusable(true);
        int cb = badgeColorReserva(estado);
        card.setStrokeColor(cb); card.setStrokeWidth((int)(2 * d));

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(p16, p16, p16, p16);

        // Fila estado + asientos
        LinearLayout filaE = new LinearLayout(this);
        filaE.setOrientation(LinearLayout.HORIZONTAL);
        filaE.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView tvE = new TextView(this);
        tvE.setText(etiquetaReserva(estado)); tvE.setTextSize(11f);
        tvE.setTextColor(Color.WHITE); tvE.setTypeface(null, Typeface.BOLD);
        tvE.setPadding(p8, p4, p8, p4);
        GradientDrawable bgE = new GradientDrawable();
        bgE.setShape(GradientDrawable.RECTANGLE); bgE.setCornerRadius(20 * d); bgE.setColor(cb);
        tvE.setBackground(bgE); filaE.addView(tvE);
        View esp = new View(this);
        esp.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        filaE.addView(esp);
        TextView tvA = new TextView(this);
        tvA.setText("" + asientos + (asientos == 1 ? " asiento" : " asientos"));
        tvA.setTextSize(11f); tvA.setTextColor(Color.parseColor("#1565C0"));
        tvA.setTypeface(null, Typeface.BOLD); tvA.setPadding(p8, p4, p8, p4);
        GradientDrawable bgA = new GradientDrawable();
        bgA.setShape(GradientDrawable.RECTANGLE); bgA.setCornerRadius(20 * d);
        bgA.setColor(Color.parseColor("#E3F2FD")); tvA.setBackground(bgA);
        filaE.addView(tvA); inner.addView(filaE);

        // Separador
        View sep1 = new View(this);
        LinearLayout.LayoutParams lps1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(1 * d));
        lps1.setMargins(0, p8, 0, p8); sep1.setLayoutParams(lps1);
        sep1.setBackgroundColor(Color.parseColor("#E0F2F1")); inner.addView(sep1);

        // Fila ruta origen → destino
        LinearLayout filaR = new LinearLayout(this);
        filaR.setOrientation(LinearLayout.HORIZONTAL);
        filaR.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout ind = new LinearLayout(this);
        ind.setOrientation(LinearLayout.VERTICAL);
        ind.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams lpI = new LinearLayout.LayoutParams((int)(18 * d), LinearLayout.LayoutParams.WRAP_CONTENT);
        lpI.setMargins(0, 0, p12, 0); ind.setLayoutParams(lpI);
        View cv = new View(this);
        cv.setLayoutParams(new LinearLayout.LayoutParams((int)(10 * d), (int)(10 * d)));
        GradientDrawable gv = new GradientDrawable(); gv.setShape(GradientDrawable.OVAL);
        gv.setColor(Color.parseColor("#4CAF50")); cv.setBackground(gv); ind.addView(cv);
        View lv = new View(this);
        LinearLayout.LayoutParams lpLv = new LinearLayout.LayoutParams((int)(2 * d), (int)(26 * d));
        lpLv.setMargins((int)(4 * d), (int)(2 * d), (int)(4 * d), (int)(2 * d));
        lv.setLayoutParams(lpLv); lv.setBackgroundColor(Color.parseColor("#B2DFDB")); ind.addView(lv);
        View rv = new View(this);
        rv.setLayoutParams(new LinearLayout.LayoutParams((int)(10 * d), (int)(10 * d)));
        GradientDrawable gr = new GradientDrawable(); gr.setShape(GradientDrawable.OVAL);
        gr.setColor(Color.parseColor("#EF5350")); rv.setBackground(gr); ind.addView(rv);
        filaR.addView(ind);

        LinearLayout colR = new LinearLayout(this);
        colR.setOrientation(LinearLayout.VERTICAL);
        colR.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView tvO = new TextView(this);
        tvO.setText(origen); tvO.setTextSize(14f); tvO.setTypeface(null, Typeface.BOLD);
        tvO.setTextColor(Color.parseColor("#004D40")); tvO.setMaxLines(2);
        tvO.setEllipsize(android.text.TextUtils.TruncateAt.END); colR.addView(tvO);
        if (!nombrePar.isEmpty() && !nombrePar.equals(destino)) {
            TextView tvP = new TextView(this);
            tvP.setText("Bajas en: " + nombrePar); tvP.setTextSize(12f);
            tvP.setTextColor(Color.parseColor("#0097A7"));
            LinearLayout.LayoutParams lpP = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpP.topMargin = (int)(10 * d); tvP.setLayoutParams(lpP); colR.addView(tvP);
        }
        TextView tvDes = new TextView(this);
        tvDes.setText(destino); tvDes.setTextSize(13f);
        tvDes.setTextColor(Color.parseColor("#546E7A")); tvDes.setMaxLines(2);
        tvDes.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lpD = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpD.topMargin = p12; tvDes.setLayoutParams(lpD); colR.addView(tvDes);
        filaR.addView(colR); inner.addView(filaR);

        // Separador
        View sep2 = new View(this);
        LinearLayout.LayoutParams lps2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(1 * d));
        lps2.setMargins(0, p8, 0, p8); sep2.setLayoutParams(lps2);
        sep2.setBackgroundColor(Color.parseColor("#E0F2F1")); inner.addView(sep2);


        TextView tvC = new TextView(this);
        tvC.setTag("tv_conductor_" + idViajeFinal);
        // Mostrar nombre si ya lo tenemos, o placeholder mientras carga
        tvC.setText(conductorFinal.isEmpty() ? "Cargando..." : "" + conductorFinal);
        tvC.setTextSize(12f); tvC.setTextColor(Color.parseColor("#00695C"));
        tvC.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvC.setMaxLines(1); tvC.setEllipsize(android.text.TextUtils.TruncateAt.END);




        if (!fechaSal.isEmpty()) {
            String fl = fechaSal.length() > 10 ? fechaSal.substring(0, 16).replace("T", " ") : fechaSal;
            TextView tvF = new TextView(this);
            tvF.setText("" + fl); tvF.setTextSize(11f);
            tvF.setTextColor(Color.parseColor("#90A4AE"));
            LinearLayout.LayoutParams lpF = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpF.topMargin = p4; tvF.setLayoutParams(lpF); inner.addView(tvF);
        }

        card.addView(inner);
        card.setOnClickListener(v -> abrirDetalle(idViajeFinal));
        if (layoutMisReservas != null) layoutMisReservas.addView(card);


        if (idViajeFinal > 0) {
            if (conductorFinal.isEmpty()) {
                cargarNombreConductorDesdeViaje(idViajeFinal, tvC);
            } else if (!conductorFinal.isEmpty()) {
                // Ya tenemos nombre, pero verificar si hay uno más completo
                tvC.setText("" + conductorFinal);
            }
        } else if (idCondFinal > 0 && conductorFinal.isEmpty()) {
            cargarNombreConductorPorId(idCondFinal, tvC);
        }
    }


    private void cargarNombreConductorPorId(int idConductor, TextView tvConductor) {
        if (idConductor <= 0) return;

        String[] endpoints = {
                Constantes.BASE_URL + "/api/auth/" + idConductor,
                Constantes.BASE_URL + "/api/usuarios/" + idConductor,
                Constantes.USUARIOS + "/" + idConductor,
                Constantes.authPorId((long) idConductor)
        };

        intentarCargaNombreDesdeEndpoints(endpoints, 0, idConductor, tvConductor);
    }

    private void intentarCargaNombreDesdeEndpoints(String[] endpoints, int idx,
                                                   int idConductor, TextView tvConductor) {
        if (idx >= endpoints.length) {
            runOnUiThread(() -> tvConductor.setText("Conductor #" + idConductor));
            return;
        }

        ConexionApi.getInstance(this).getObjectNoCache(endpoints[idx],
                perfil -> {
                    Log.d(TAG, "cargarNombreConductorPorId endpoint[" + idx + "]: "
                            + perfil.toString().substring(0, Math.min(300, perfil.toString().length())));

                    String nombre = extraerNombreDePerfilCompleto(perfil);

                    if (!nombre.isEmpty()) {
                        final String nomFinal = nombre;
                        runOnUiThread(() -> tvConductor.setText("" + nomFinal));
                    } else {
                        intentarCargaNombreDesdeEndpoints(endpoints, idx + 1, idConductor, tvConductor);
                    }
                },
                error -> {
                    int code = (error != null && error.networkResponse != null)
                            ? error.networkResponse.statusCode : 0;
                    Log.w(TAG, "cargarNombreConductorPorId endpoint[" + idx + "] falló código=" + code);
                    intentarCargaNombreDesdeEndpoints(endpoints, idx + 1, idConductor, tvConductor);
                }
        );
    }

    private String extraerNombreDePerfilCompleto(JSONObject perfil) {
        if (perfil == null) return "";

        // Campos directos
        for (String k : new String[]{"nombre", "nombreCompleto", "name", "fullName",
                "nombreUsuario", "displayName"}) {
            String v = perfil.optString(k, "");
            if (!v.isEmpty() && !v.equals("null")) return v;
        }

        // nombres + apellidos
        String n = perfil.optString("nombres", perfil.optString("primerNombre", ""));
        String a = perfil.optString("apellidos", perfil.optString("primerApellido", ""));
        if (!n.isEmpty() || !a.isEmpty()) return (n + " " + a).trim();

        // Sub-objetos anidados
        for (String sub : new String[]{"usuario", "persona", "perfil", "conductor", "data"}) {
            JSONObject obj = perfil.optJSONObject(sub);
            if (obj != null) {
                for (String k : new String[]{"nombre", "nombreCompleto", "name"}) {
                    String v = obj.optString(k, "");
                    if (!v.isEmpty() && !v.equals("null")) return v;
                }
                String sn = obj.optString("nombres", "");
                String sa = obj.optString("apellidos", "");
                if (!sn.isEmpty() || !sa.isEmpty()) return (sn + " " + sa).trim();
            }
        }
        return "";
    }


    private void cargarNombreConductorDesdeViaje(int idViaje, TextView tvConductor) {
        if (idViaje <= 0) return;

        String url = Constantes.BASE_URL + "/api/viajes/" + idViaje
                + "?t=" + System.currentTimeMillis(); // evitar caché

        ConexionApi.getInstance(this).getObjectNoCache(url,
                viajeObj -> {
                    logViajeCompleto(viajeObj);
                    String nombre = "";
                    int idCond = -1;

                    // Intento 1: objeto "conductor" dentro del viaje
                    JSONObject condObj = viajeObj.optJSONObject("conductor");
                    if (condObj != null) {
                        nombre = extractNombreCompleto(condObj);
                        if (nombre.isEmpty()) {
                            JSONObject u = condObj.optJSONObject("usuario");
                            if (u != null) nombre = extractNombreCompleto(u);
                        }
                        if (idCond <= 0)
                            idCond = condObj.optInt("id",
                                    condObj.optInt("idUsuarios",
                                            condObj.optInt("idUsuario", -1)));
                    }

                    // Intento 2: campos planos
                    if (nombre.isEmpty()) {
                        nombre = primeraNoVacia(
                                viajeObj.optString("nombreConductor", ""),
                                viajeObj.optString("conductorNombre", ""),
                                viajeObj.optString("conductor", "")
                        );
                    }

                    // Intento 3: buscar idConductor y cargar por endpoint
                    if (nombre.isEmpty()) {
                        if (idCond <= 0)
                            idCond = viajeObj.optInt("idConductor",
                                    viajeObj.optInt("conductorId",
                                            viajeObj.optInt("idUsuarioConductor", -1)));

                        // También revisar en vehículo
                        if (idCond <= 0) {
                            JSONObject veh = viajeObj.optJSONObject("vehiculo");
                            if (veh != null)
                                idCond = veh.optInt("idUsuario",
                                        veh.optInt("idConductor", -1));
                        }

                        if (idCond > 0) {
                            cargarNombreConductorPorId(idCond, tvConductor);
                            return;
                        }
                    }

                    if (!nombre.isEmpty()) {
                        final String nomFinal = nombre;
                        runOnUiThread(() -> tvConductor.setText("️ " + nomFinal));
                    } else {
                        runOnUiThread(() -> tvConductor.setText(" Conductor"));
                    }
                },
                error -> {
                    Log.w(TAG, "No se pudo cargar viaje id=" + idViaje);
                    runOnUiThread(() -> tvConductor.setText(" Conductor"));
                }
        );
    }

    private String extractNombreCompleto(JSONObject o) {
        if (o == null) return "";

        Log.d(TAG, "extractNombreCompleto → " + o.toString());

        // 1. Campos directos de nombre completo
        String nombre = primeraNoVacia(
                o.optString("nombre",         ""),
                o.optString("nombreCompleto", ""),
                o.optString("name",           ""),
                o.optString("fullName",       ""),
                o.optString("nombreUsuario",  ""),
                o.optString("displayName",    "")
        );
        if (!nombre.isEmpty()) return nombre;

        // 2. nombres + apellidos
        String n = primeraNoVacia(
                o.optString("nombres",       ""),
                o.optString("primerNombre",  ""),
                o.optString("firstName",     "")
        );
        String a = primeraNoVacia(
                o.optString("apellidos",     ""),
                o.optString("primerApellido",""),
                o.optString("lastName",      "")
        );
        if (!n.isEmpty() || !a.isEmpty()) return (n + " " + a).trim();

        // 3. Sub-objetos anidados
        for (String sub : new String[]{"usuario", "user", "persona", "perfil", "profile"}) {
            JSONObject p = o.optJSONObject(sub);
            if (p == null) continue;
            Log.d(TAG, "  sub-objeto [" + sub + "] → " + p.toString());
            String nom = extractNombreDeObjeto(p);
            if (!nom.isEmpty()) return nom;

            // Un nivel más profundo
            for (String sub2 : new String[]{"persona", "perfil", "profile"}) {
                JSONObject p2 = p.optJSONObject(sub2);
                if (p2 == null) continue;
                nom = extractNombreDeObjeto(p2);
                if (!nom.isEmpty()) return nom;
            }
        }

        return "";
    }

    /** Extrae nombre de un objeto plano sin recursión adicional. */
    private String extractNombreDeObjeto(JSONObject o) {
        if (o == null) return "";
        String nombre = primeraNoVacia(
                o.optString("nombre",         ""),
                o.optString("nombreCompleto", ""),
                o.optString("name",           ""),
                o.optString("fullName",       ""),
                o.optString("nombreUsuario",  ""),
                o.optString("displayName",    "")
        );
        if (!nombre.isEmpty()) return nombre;
        String n = primeraNoVacia(o.optString("nombres", ""), o.optString("primerNombre", ""));
        String a = primeraNoVacia(o.optString("apellidos", ""), o.optString("primerApellido", ""));
        return (n + " " + a).trim();
    }

    /** Log completo del JSON del viaje para diagnóstico. */
    private void logViajeCompleto(JSONObject viaje) {
        try {
            Log.d(TAG, "══════ VIAJE JSON ══════");
            Log.d(TAG, viaje.toString(2));
            Log.d(TAG, "═══════════════════════");
        } catch (Exception e) {
            Log.e(TAG, "logViajeCompleto: " + e.getMessage());
        }
    }

    /** Devuelve el primer String no nulo, no vacío y distinto de "null". */
    private String primeraNoVacia(String... valores) {
        for (String v : valores) {
            if (v != null && !v.isEmpty() && !v.equals("null")) return v;
        }
        return "";
    }

    private String etiquetaReserva(String e){
        switch(e.toUpperCase()){
            case "ACTIVA": case "CONFIRMADA":   return "✅ Confirmada";
            case "EN_CURSO": case "INICIADO":   return " En curso";
            case "ESPERANDO_RECOGIDA":          return "⏳ Esperando recogida";
            case "RECOGIDO":                    return "✅ ¡Te recogieron!";
            case "PENDIENTE":                   return "⏳ Pendiente";
            default:                            return " " + e;
        }
    }

    private int badgeColorReserva(String e){
        switch(e.toUpperCase()){
            case "ACTIVA": case "CONFIRMADA":   return Color.parseColor("#2E7D32");
            case "EN_CURSO": case "INICIADO":   return Color.parseColor("#00838F");
            case "ESPERANDO_RECOGIDA":          return Color.parseColor("#E65100");
            case "RECOGIDO":                    return Color.parseColor("#1565C0");
            case "PENDIENTE":                   return Color.parseColor("#F57F17");
            default:                            return Color.parseColor("#546E7A");
        }
    }

    // =========================================================================
    //  BÚSQUEDA
    // =========================================================================
    private void iniciarBusqueda(){
        if(tieneReservaActiva){Toast.makeText(this,"🔒 Ya tienes un viaje activo. Espera a que finalice para buscar otro.",Toast.LENGTH_LONG).show();return;}
        String txtO=editOrigen!=null&&editOrigen.getText()!=null?editOrigen.getText().toString().trim():"";
        String txtD=editDestino!=null&&editDestino.getText()!=null?editDestino.getText().toString().trim():"";
        if(txtO.isEmpty()&&!gpsListo){Toast.makeText(this,"⏳ Esperando GPS... o ingresa tu ubicación",Toast.LENGTH_SHORT).show();return;}
        ocultarSugerencias();busquedaActiva=true;
        if(btnBuscar!=null){btnBuscar.setEnabled(false);btnBuscar.setText("Buscando...");}
        if(layoutBuscando!=null)layoutBuscando.setVisibility(View.VISIBLE);
        if(layoutResultadosViajes!=null)layoutResultadosViajes.removeAllViews();
        if(layoutLabelResultados!=null)layoutLabelResultados.setVisibility(View.GONE);
        if(layoutVacio!=null)layoutVacio.setVisibility(View.GONE);

        boolean usarGps = gpsListo && (txtO.isEmpty() || txtO.startsWith("Mi ubicación"));

        new Thread(()->{
            try{
                if(!usarGps && !txtO.isEmpty()){
                    double[]c=geocodificar(txtO);latOrigen=c[0];lngOrigen=c[1];
                }
                if(!txtD.isEmpty()){
                    try{double[]c=geocodificar(txtD);latDestino=c[0];lngDestino=c[1];}
                    catch(Exception e){Log.w(TAG,"No se pudo geocodificar destino: "+e.getMessage());latDestino=0;lngDestino=0;}
                }else{latDestino=0;lngDestino=0;}
                runOnUiThread(()->buscarViajesEnCurso(txtO,txtD));
            }catch(Exception e){
                Log.e(TAG,"geocodificar origen: "+e.getMessage());
                runOnUiThread(()->buscarViajesEnCurso(txtO,txtD));
            }
        }).start();
    }

    private void buscarViajesEnCurso(String txtO, String txtD) {
        Log.d(TAG, "Buscando viajes. latOrigen="+latOrigen+" lngOrigen="+lngOrigen);
        ConexionApi.getInstance(this).getArray(Constantes.buscarViajes(),
                r -> {
                    Log.d(TAG, "buscarViajes() OK, total="+r.length());
                    JSONArray filtrado = filtrarParaPasajero(r);
                    procesarViajes(filtrado, txtO, txtD);
                },
                err -> {
                    ConexionApi.getInstance(this).getArray(Constantes.VIAJES,
                            r2 -> {
                                JSONArray filtrado = filtrarParaPasajero(r2);
                                procesarViajes(filtrado, txtO, txtD);
                            },
                            e2 -> runOnUiThread(() -> {
                                if(layoutBuscando!=null)layoutBuscando.setVisibility(View.GONE);
                                if(btnBuscar!=null){btnBuscar.setEnabled(true);btnBuscar.setText("Buscar viajes disponibles");}
                                busquedaActiva=false;
                                Toast.makeText(this,"Error conectando al servidor. Verifica tu conexión.",Toast.LENGTH_LONG).show();
                            }));
                });
    }

    private JSONArray filtrarParaPasajero(JSONArray viajes) {
        JSONArray res = new JSONArray();
        if (viajes == null) return res;

        boolean tengoGps     = latOrigen  != 0 && lngOrigen  != 0;
        boolean tengoDestino = latDestino != 0 && lngDestino != 0;

        for (int i = 0; i < viajes.length(); i++) {
            try {
                JSONObject v = viajes.getJSONObject(i);

                // 1. Filtro de estado
                String est = v.optString("estado", "").toUpperCase();
                if (!est.equals("EN_CURSO") && !est.equals("INICIADO") &&
                        !est.equals("DISPONIBLE") && !est.equals("PROGRAMADO") &&
                        !est.equals("CREADO")) continue;

                // 2. Filtro de cupos
                int cupos = v.optInt("cuposDisponibles", v.optInt("cupos", -1));
                if (cupos == 0) continue;

                // 3. Filtro geográfico
                if (tengoGps) {
                    double loR = 0, lgR = 0, ldR = 0, gdR = 0;

                    JSONObject ruta = v.optJSONObject("ruta");
                    if (ruta != null) {
                        loR = ruta.optDouble("latOrigen",  0);
                        lgR = ruta.optDouble("lngOrigen",  0);
                        ldR = ruta.optDouble("latDestino", 0);
                        gdR = ruta.optDouble("lngDestino", 0);
                    }
                    if (loR == 0) loR = v.optDouble("latOrigen",  0);
                    if (lgR == 0) lgR = v.optDouble("lngOrigen",  0);
                    if (ldR == 0) ldR = v.optDouble("latDestino", 0);
                    if (gdR == 0) gdR = v.optDouble("lngDestino", 0);

                    if (loR != 0 && lgR != 0) {
                        boolean origenCerca = distKm(latOrigen, lngOrigen, loR, lgR) <= RADIO_KM;
                        boolean rutaPasaCercaDeMi = false;
                        if (ldR != 0 && gdR != 0) {
                            rutaPasaCercaDeMi = distSegmento(latOrigen, lngOrigen,
                                    loR, lgR, ldR, gdR) <= RADIO_KM;
                        }

                        if (!origenCerca && !rutaPasaCercaDeMi) continue;

                        if (tengoDestino && ldR != 0 && gdR != 0) {
                            boolean destinoCerca = distKm(latDestino, lngDestino, ldR, gdR) <= RADIO_KM * 1.5;
                            boolean rutaPasaCercaDeDestino = distSegmento(latDestino, lngDestino,
                                    loR, lgR, ldR, gdR) <= RADIO_KM * 1.5;
                            if (!destinoCerca && !rutaPasaCercaDeDestino) continue;
                        }
                    }
                }

                res.put(v);

            } catch (Exception e) {
                Log.e(TAG, "filtrar viaje: " + e.getMessage());
            }
        }
        return res;
    }

    private double distSegmento(double pLat, double pLng,
                                double aLat, double aLng,
                                double bLat, double bLng) {
        double dx = bLat - aLat;
        double dy = bLng - aLng;
        if (dx == 0 && dy == 0) return distKm(pLat, pLng, aLat, aLng);
        double t = ((pLat - aLat) * dx + (pLng - aLng) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));
        return distKm(pLat, pLng, aLat + t * dx, aLng + t * dy);
    }

    // =========================================================================
    //  MOSTRAR RESULTADOS
    // =========================================================================
    private void procesarViajes(JSONArray viajes, String txtO, String txtD){
        runOnUiThread(()->{
            if(layoutBuscando!=null)layoutBuscando.setVisibility(View.GONE);
            if(btnBuscar!=null){btnBuscar.setEnabled(true);btnBuscar.setText("Buscar viajes disponibles");}
            busquedaActiva=false;
            if(layoutResultadosViajes!=null)layoutResultadosViajes.removeAllViews();

            if(viajes==null||viajes.length()==0){
                if(layoutLabelResultados!=null)layoutLabelResultados.setVisibility(View.GONE);
                if(layoutVacio!=null){
                    layoutVacio.setVisibility(View.VISIBLE);
                    if(txtVacio!=null)txtVacio.setText("😔 No encontramos conductores activos");
                    if(txtVacioSub!=null)txtVacioSub.setText(
                            gpsListo
                                    ? "No hay conductores con viaje EN CURSO cerca de ti ahora.\n\nEspera a que un conductor presione \"Iniciar viaje\"."
                                    : "No hay viajes disponibles cerca de ti ahora.\n\nBusca de nuevo en unos minutos."
                    );
                }
                return;
            }
            if(layoutVacio!=null)layoutVacio.setVisibility(View.GONE);
            if(layoutLabelResultados!=null)layoutLabelResultados.setVisibility(View.VISIBLE);
            if(txtLabelResultados!=null)txtLabelResultados.setText("Conductores activos cerca de ti");
            if(txtContadorResultados!=null)txtContadorResultados.setText(String.valueOf(viajes.length()));

            for(int i=0;i<viajes.length();i++){
                JSONObject v=viajes.optJSONObject(i);
                if(v!=null)agregarCardViaje(v,i);
            }
        });
    }

    // =========================================================================
    //  CARD VIAJE RESULTADO
    // =========================================================================
    private void agregarCardViaje(JSONObject viaje, int idx) {
        float d=getResources().getDisplayMetrics().density;
        int p16=(int)(16*d),p12=(int)(12*d),p8=(int)(8*d),p4=(int)(4*d);
        int    idV    = extraerIdViaje(viaje);

        int    cupos  = viaje.optInt("cuposDisponibles", viaje.optInt("cupos", 0));
        String fecha  = viaje.optString("fechaHoraSalida", viaje.optString("fecha", ""));
        String origen = "Origen", destino = "Destino", cond = "";

        JSONObject ruta = viaje.optJSONObject("ruta");
        if (ruta != null) { origen = extraerOrigenDeRuta(ruta); destino = extraerDestinoDeRuta(ruta); }
        else { origen = viaje.optString("origen", origen); destino = viaje.optString("destino", destino); }

        // Log completo del viaje para diagnóstico del conductor
        logViajeCompleto(viaje);

        // Extracción robusta del conductor en resultados de búsqueda
        JSONObject co = viaje.optJSONObject("conductor");
        if (co != null) cond = extractNombreCompleto(co);
        if (cond.isEmpty()) cond = primeraNoVacia(
                viaje.optString("nombreConductor", ""),
                viaje.optString("conductorNombre", ""),
                viaje.optString("conductor",       "")
        );



        final String condFinal = cond;
        final int    idVFinal  = idV;
        int col = COLORES_INT[idx % COLORES_INT.length];

        MaterialCardView card=new MaterialCardView(this);
        LinearLayout.LayoutParams lpC=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        lpC.setMargins(0,0,0,p12);card.setLayoutParams(lpC);card.setRadius(18*d);card.setCardElevation(6*d);
        card.setCardBackgroundColor(Color.WHITE);card.setClickable(true);card.setFocusable(true);
        card.setStrokeColor(col);card.setStrokeWidth((int)(2.5f*d));

        LinearLayout inner=new LinearLayout(this);inner.setOrientation(LinearLayout.HORIZONTAL);
        View barra=new View(this);
        LinearLayout.LayoutParams lpB=new LinearLayout.LayoutParams((int)(6*d),LinearLayout.LayoutParams.MATCH_PARENT);
        barra.setLayoutParams(lpB);barra.setBackgroundColor(col);inner.addView(barra);

        LinearLayout cont=new LinearLayout(this);cont.setOrientation(LinearLayout.VERTICAL);
        cont.setPadding(p16,p12,p16,p12);
        cont.setLayoutParams(new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));

        // Fila ruta
        LinearLayout filaTop=new LinearLayout(this);filaTop.setOrientation(LinearLayout.HORIZONTAL);filaTop.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout indR=new LinearLayout(this);indR.setOrientation(LinearLayout.VERTICAL);indR.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams lpI=new LinearLayout.LayoutParams((int)(20*d),LinearLayout.LayoutParams.WRAP_CONTENT);lpI.setMargins(0,0,p12,0);indR.setLayoutParams(lpI);
        View c1=new View(this);c1.setLayoutParams(new LinearLayout.LayoutParams((int)(10*d),(int)(10*d)));
        GradientDrawable g1=new GradientDrawable();g1.setShape(GradientDrawable.OVAL);g1.setColor(col);c1.setBackground(g1);indR.addView(c1);
        View ln=new View(this);LinearLayout.LayoutParams lpLn=new LinearLayout.LayoutParams((int)(2*d),(int)(28*d));lpLn.setMargins((int)(4*d),(int)(2*d),(int)(4*d),(int)(2*d));ln.setLayoutParams(lpLn);ln.setBackgroundColor(Color.parseColor("#B2DFDB"));indR.addView(ln);
        View c2=new View(this);c2.setLayoutParams(new LinearLayout.LayoutParams((int)(10*d),(int)(10*d)));
        GradientDrawable g2=new GradientDrawable();g2.setShape(GradientDrawable.OVAL);g2.setColor(Color.parseColor("#EF5350"));c2.setBackground(g2);indR.addView(c2);
        filaTop.addView(indR);

        LinearLayout colRt=new LinearLayout(this);colRt.setOrientation(LinearLayout.VERTICAL);
        colRt.setLayoutParams(new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));
        TextView tvOr=new TextView(this);tvOr.setText("🟢 "+origen);tvOr.setTextSize(13f);tvOr.setTypeface(null,Typeface.BOLD);tvOr.setTextColor(Color.parseColor("#004D40"));tvOr.setMaxLines(2);tvOr.setEllipsize(android.text.TextUtils.TruncateAt.END);colRt.addView(tvOr);
        TextView tvDe=new TextView(this);tvDe.setText("🔴 "+destino);tvDe.setTextSize(13f);tvDe.setTextColor(Color.parseColor("#546E7A"));tvDe.setMaxLines(2);tvDe.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lpDe=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);lpDe.topMargin=(int)(14*d);tvDe.setLayoutParams(lpDe);colRt.addView(tvDe);
        filaTop.addView(colRt);

        TextView tvEst=new TextView(this);tvEst.setText("ACTIVO");tvEst.setTextSize(10f);tvEst.setTextColor(Color.WHITE);tvEst.setTypeface(null,Typeface.BOLD);tvEst.setPadding(p8,p4,p8,p4);
        GradientDrawable bgEst=new GradientDrawable();bgEst.setShape(GradientDrawable.RECTANGLE);bgEst.setCornerRadius(20*d);bgEst.setColor(0xFF2E7D32);tvEst.setBackground(bgEst);
        LinearLayout.LayoutParams lpEst=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);lpEst.setMargins(p8,0,0,0);tvEst.setLayoutParams(lpEst);
        filaTop.addView(tvEst);cont.addView(filaTop);

        View div=new View(this);LinearLayout.LayoutParams lpDiv=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,(int)(1*d));lpDiv.setMargins(0,p12,0,p12);div.setLayoutParams(lpDiv);div.setBackgroundColor(Color.parseColor("#E0F2F1"));cont.addView(div);


        LinearLayout filaI=new LinearLayout(this);filaI.setOrientation(LinearLayout.HORIZONTAL);filaI.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView tvCd=new TextView(this);
        // Mostrar nombre si ya lo tenemos, o placeholder mientras carga
        tvCd.setText(condFinal.isEmpty() ? "Cargando..." : "" + condFinal);
        tvCd.setTextSize(12f);tvCd.setTextColor(Color.parseColor("#00695C"));
        tvCd.setLayoutParams(new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));
        tvCd.setMaxLines(1);tvCd.setEllipsize(android.text.TextUtils.TruncateAt.END);
        filaI.addView(tvCd);


        TextView tvCu=new TextView(this);tvCu.setText(cupos>0?""+cupos+" libres":"Sin cupos");tvCu.setTextSize(12f);tvCu.setTypeface(null,Typeface.BOLD);tvCu.setTextColor(cupos>0?Color.parseColor("#2E7D32"):Color.parseColor("#C62828"));tvCu.setPadding(p8,p4,p8,p4);
        GradientDrawable bgCu=new GradientDrawable();bgCu.setShape(GradientDrawable.RECTANGLE);bgCu.setCornerRadius(12*d);bgCu.setColor(cupos>0?Color.parseColor("#E8F5E9"):Color.parseColor("#FFEBEE"));tvCu.setBackground(bgCu);filaI.addView(tvCu);
        cont.addView(filaI);

        if(!fecha.isEmpty()){
            String fl=fecha.length()>10?fecha.substring(0,16).replace("T"," "):fecha;
            TextView tvF=new TextView(this);tvF.setText("Salida: "+fl);tvF.setTextSize(11f);tvF.setTextColor(Color.parseColor("#90A4AE"));
            LinearLayout.LayoutParams lpF=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);lpF.topMargin=p8;tvF.setLayoutParams(lpF);cont.addView(tvF);
        }

        inner.addView(cont);card.addView(inner);
        card.setOnClickListener(v -> abrirDetalle(idVFinal));
        if(layoutResultadosViajes!=null)layoutResultadosViajes.addView(card);

        // Fallback: siempre cargar por API si el nombre quedó vacío
        if (condFinal.isEmpty() && idV > 0) {
            cargarNombreConductorDesdeViaje(idV, tvCd);
        }
    }

    // =========================================================================
    //  HELPERS GENERALES
    // =========================================================================

    private String extraerOrigenDeRuta(JSONObject ruta) {
        if (ruta == null) return "Origen";
        for (String k : new String[]{"origen","puntoOrigen","inicio","nombreOrigen","lugarOrigen"}) {
            String v = ruta.optString(k, "").trim();
            if (!v.isEmpty() && !v.equals("null")) return v;
        }
        String nombre = ruta.optString("nombre", ruta.optString("descripcion", "")).trim();
        if (nombre.contains("→")) return nombre.split("→")[0].trim();
        if (nombre.contains("->")) return nombre.split("->")[0].trim();
        if (nombre.contains(" - ")) return nombre.split(" - ")[0].trim();
        return "Origen";
    }

    private String extraerDestinoDeRuta(JSONObject ruta) {
        if (ruta == null) return "Destino";
        for (String k : new String[]{"destino","puntoDestino","fin","nombreDestino","lugarDestino"}) {
            String v = ruta.optString(k, "").trim();
            if (!v.isEmpty() && !v.equals("null")) return v;
        }
        String nombre = ruta.optString("nombre", ruta.optString("descripcion", "")).trim();
        String[] partes = null;
        if (nombre.contains("→"))   partes = nombre.split("→",  2);
        else if (nombre.contains("->"))  partes = nombre.split("->", 2);
        else if (nombre.contains(" - ")) partes = nombre.split(" - ", 2);
        if (partes != null && partes.length > 1) return partes[1].trim();
        if (!nombre.isEmpty()) return nombre;
        return "Destino";
    }

    private int extraerIdViaje(JSONObject o){
        if(o==null)return 0;
        for(String k:new String[]{"idViajes","idViaje","id","viajeId"}){int v=o.optInt(k,0);if(v>0)return v;}
        JSONObject va=o.optJSONObject("viaje");
        if(va!=null)for(String k:new String[]{"idViajes","idViaje","id"}){int v=va.optInt(k,0);if(v>0)return v;}
        return 0;
    }

    private void abrirDetalle(int idViaje){
        if(idViaje==0){Toast.makeText(this,"No se puede abrir este viaje",Toast.LENGTH_SHORT).show();return;}
        Intent i=new Intent(this,DetalleViajeActivity.class);
        i.putExtra("ID_VIAJE",idViaje);
        startActivity(i);
    }

    private double[] geocodificar(String dir) throws Exception {
        String q=dir.toLowerCase().contains("popay")?dir:dir+", Popayán, Colombia";
        String url="https://nominatim.openstreetmap.org/search?q="+java.net.URLEncoder.encode(q,"UTF-8")+"&format=json&limit=1&countrycodes=co";
        JSONArray arr=new JSONArray(petHttp(url));
        if(arr.length()==0){
            url="https://nominatim.openstreetmap.org/search?q="+java.net.URLEncoder.encode(dir+", Colombia","UTF-8")+"&format=json&limit=1";
            arr=new JSONArray(petHttp(url));
        }
        if(arr.length()==0) throw new Exception("No encontrado: "+dir);
        JSONObject o=arr.getJSONObject(0);
        return new double[]{o.getDouble("lat"),o.getDouble("lon")};
    }

    private String petHttp(String urlStr) throws Exception {
        HttpURLConnection c=null;
        try{
            URL u=new URL(urlStr);
            c=(HttpURLConnection)u.openConnection();
            c.setRequestProperty("User-Agent","Moviflexx-App/1.0");
            c.setConnectTimeout(15000);c.setReadTimeout(15000);
            BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream()));
            StringBuilder sb=new StringBuilder();String l;
            while((l=r.readLine())!=null)sb.append(l);
            r.close();return sb.toString();
        }finally{if(c!=null)c.disconnect();}
    }

    private double distKm(double la1,double lo1,double la2,double lo2){
        double R=6371,dLa=Math.toRadians(la2-la1),dLo=Math.toRadians(lo2-lo1);
        double a=Math.sin(dLa/2)*Math.sin(dLa/2)+Math.cos(Math.toRadians(la1))*Math.cos(Math.toRadians(la2))*Math.sin(dLo/2)*Math.sin(dLo/2);
        return R*2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));
    }

    // Alias por compatibilidad
    private String extractNombre(JSONObject o){ return extractNombreCompleto(o); }
}