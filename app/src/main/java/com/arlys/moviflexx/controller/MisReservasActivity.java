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
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MisReservasActivity extends AppCompatActivity {

    private static final String TAG          = "MisReservas";
    // ✅ FIX: Radio aumentado de 5 a 15 km para no excluir viajes válidos
    private static final double RADIO_KM     = 15.0;
    private static final int    REQ_LOCATION = 2001;
    private static final String OSRM_URL     = "https://osrm-popayan-production.up.railway.app";

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

    // ── Mapa ──────────────────────────────────────────────────────────────────
    private MapView              mapBusqueda;
    private LinearLayout         cardMapaBusqueda;
    private MyLocationNewOverlay myLocationOverlay;
    private Marker               marcadorOrigen, marcadorDestino;
    private final List<Polyline> rutasEnMapa = new ArrayList<>();

    // ── Estado ────────────────────────────────────────────────────────────────
    private SessionManager session;
    private double  latOrigen = 0, lngOrigen = 0;
    private double  latDestino = 0, lngDestino = 0;
    private boolean busquedaActiva = false;
    private boolean gpsListo       = false;

    private boolean tieneReservaActiva   = false;
    private int     idViajeReservaActiva = 0;

    private final List<ViajeConRuta> viajesConRuta = new ArrayList<>();

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
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_mis_reservas);
        session = new SessionManager(this);
        initViews();
        configurarMapa();
        verificarPermisosGPS();
        cargarMisReservas();
    }

    @Override protected void onResume() {
        super.onResume();
        if (mapBusqueda != null) mapBusqueda.onResume();
        cargarMisReservas();
    }

    @Override protected void onPause() {
        super.onPause();
        if (mapBusqueda != null) mapBusqueda.onPause();
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
        mapBusqueda            = findViewById(R.id.map_busqueda);
        cardMapaBusqueda       = findViewById(R.id.card_mapa_busqueda);

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
            TextView tv=new TextView(this);tv.setText("📍 "+s);tv.setTextSize(13f);tv.setTextColor(Color.parseColor("#004D40"));tv.setPadding((int)(12*d),(int)(10*d),(int)(12*d),(int)(10*d));tv.setClickable(true);tv.setFocusable(true);
            tv.setOnClickListener(v->{if(editDestino!=null){editDestino.setText(s);editDestino.setSelection(s.length());}layoutSugerencias.setVisibility(View.GONE);});
            layoutSugerencias.addView(tv);
            View sep=new View(this);sep.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,1));sep.setBackgroundColor(Color.parseColor("#E0E0E0"));layoutSugerencias.addView(sep);
        }
    }

    private void ocultarSugerencias(){if(layoutSugerencias!=null)layoutSugerencias.setVisibility(View.GONE);}

    // =========================================================================
    //  MAPA
    // =========================================================================
    private void configurarMapa(){
        if(mapBusqueda==null)return;
        mapBusqueda.setTileSource(TileSourceFactory.MAPNIK);mapBusqueda.setMultiTouchControls(true);mapBusqueda.setBuiltInZoomControls(false);mapBusqueda.getController().setZoom(14.0);mapBusqueda.setMinZoomLevel(5.0);mapBusqueda.setMaxZoomLevel(20.0);mapBusqueda.setFlingEnabled(true);mapBusqueda.setHorizontalMapRepetitionEnabled(false);mapBusqueda.setVerticalMapRepetitionEnabled(false);mapBusqueda.setUseDataConnection(true);mapBusqueda.getController().setCenter(new GeoPoint(2.4448,-76.6147));
        Configuration.getInstance().setOsmdroidTileCache(new File(getCacheDir(),"osmdroid_tiles"));Configuration.getInstance().setTileFileSystemCacheMaxBytes(100L*1024*1024);
        if(cardMapaBusqueda!=null)cardMapaBusqueda.setVisibility(View.GONE);
    }

    // =========================================================================
    //  GPS
    // =========================================================================
    private void verificarPermisosGPS(){
        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQ_LOCATION);
        else activarGPS();
    }

    private void activarGPS(){
        if(mapBusqueda==null)return;
        myLocationOverlay=new MyLocationNewOverlay(new GpsMyLocationProvider(this),mapBusqueda);myLocationOverlay.enableMyLocation();myLocationOverlay.enableFollowLocation();mapBusqueda.getOverlays().add(myLocationOverlay);
        myLocationOverlay.runOnFirstFix(()->runOnUiThread(()->{
            GeoPoint pos=myLocationOverlay.getMyLocation();if(pos==null)return;
            latOrigen=pos.getLatitude();lngOrigen=pos.getLongitude();gpsListo=true;
            String dir=obtenerDireccion(pos);if(editOrigen!=null)editOrigen.setText(dir);
            myLocationOverlay.disableFollowLocation();mapBusqueda.getController().animateTo(pos);mapBusqueda.getController().setZoom(15.0);
            agregarMarcadorOrigen(pos,dir);
            View gl=findViewById(R.id.layout_gps_status);if(gl!=null)gl.setVisibility(View.GONE);
        }));
    }

    @Override public void onRequestPermissionsResult(int req,@NonNull String[] p,@NonNull int[] g){
        super.onRequestPermissionsResult(req,p,g);
        if(req==REQ_LOCATION&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)activarGPS();
        else Toast.makeText(this,"⚠️ Sin permiso GPS — ingresa tu ubicación manualmente",Toast.LENGTH_LONG).show();
    }

    private String obtenerDireccion(GeoPoint punto){
        try{Geocoder gc=new Geocoder(this,new Locale("es","CO"));List<Address> l=gc.getFromLocation(punto.getLatitude(),punto.getLongitude(),1);if(l!=null&&!l.isEmpty()){String ln=l.get(0).getAddressLine(0);return ln!=null?ln:"Mi ubicación";}}catch(Exception e){Log.e(TAG,"Geocoder: "+e.getMessage());}return"Mi ubicación actual";
    }

    // =========================================================================
    //  MARCADORES
    // =========================================================================
    private void agregarMarcadorOrigen(GeoPoint p,String titulo){if(mapBusqueda==null)return;if(marcadorOrigen!=null)mapBusqueda.getOverlays().remove(marcadorOrigen);marcadorOrigen=crearMarcador(p,"📍 Tú",titulo,COLORES_INT[0],"A");mapBusqueda.getOverlays().add(marcadorOrigen);mapBusqueda.invalidate();}
    private void agregarMarcadorDestino(GeoPoint p,String titulo){if(mapBusqueda==null)return;if(marcadorDestino!=null)mapBusqueda.getOverlays().remove(marcadorDestino);marcadorDestino=crearMarcador(p,"🏁 Destino",titulo,0xFFEF4444,"B");mapBusqueda.getOverlays().add(marcadorDestino);mapBusqueda.invalidate();}
    private Marker crearMarcador(GeoPoint punto,String titulo,String snippet,int color,String letra){Marker m=new Marker(mapBusqueda);m.setPosition(punto);m.setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM);m.setTitle(titulo);m.setSnippet(snippet);int sz=96;Bitmap bmp=Bitmap.createBitmap(sz,sz,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(bmp);Paint ps=new Paint(Paint.ANTI_ALIAS_FLAG);ps.setColor(Color.argb(80,0,0,0));c.drawCircle(sz/2f+3,sz/2f+5,sz/2f-6,ps);Paint pc=new Paint(Paint.ANTI_ALIAS_FLAG);pc.setColor(color);c.drawCircle(sz/2f,sz/2f-4,sz/2f-8,pc);Paint pb=new Paint(Paint.ANTI_ALIAS_FLAG);pb.setColor(Color.WHITE);pb.setStyle(Paint.Style.STROKE);pb.setStrokeWidth(4f);c.drawCircle(sz/2f,sz/2f-4,sz/2f-8,pb);Paint pt=new Paint(Paint.ANTI_ALIAS_FLAG);pt.setColor(Color.WHITE);pt.setTextSize(36f);pt.setTypeface(Typeface.DEFAULT_BOLD);pt.setTextAlign(Paint.Align.CENTER);c.drawText(letra,sz/2f,sz/2f+9,pt);m.setIcon(new BitmapDrawable(getResources(),bmp));return m;}

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
        tieneReservaActiva=false;idViajeReservaActiva=0;
        if(reservas==null||reservas.length()==0){if(layoutLabelMisReservas!=null)layoutLabelMisReservas.setVisibility(View.GONE);actualizarEstadoBusqueda();return;}
        int count=0;
        for(int i=0;i<reservas.length();i++){
            JSONObject r=reservas.optJSONObject(i);if(r==null)continue;
            String estado=r.optString("estado","").toUpperCase();
            if(estado.equals("CANCELADA")||estado.equals("CANCELADO")||estado.equals("COMPLETADO")||estado.equals("FINALIZADO"))continue;
            if(RESERVA_ACTIVA_ESTADOS.contains(estado)){tieneReservaActiva=true;int idV=r.optInt("idViaje",0);if(idV==0){JSONObject vo=r.optJSONObject("viaje");if(vo!=null)idV=extraerIdViaje(vo);}idViajeReservaActiva=idV;}
            agregarCardMiReserva(r);count++;
        }
        if(layoutLabelMisReservas!=null)layoutLabelMisReservas.setVisibility(count>0?View.VISIBLE:View.GONE);
        actualizarEstadoBusqueda();
    }

    private void actualizarEstadoBusqueda(){
        if(tieneReservaActiva){
            if(btnBuscar!=null){btnBuscar.setEnabled(false);btnBuscar.setAlpha(0.5f);btnBuscar.setText("🔒 Ya tienes un viaje activo");}
            if(editDestino!=null){editDestino.setEnabled(false);editDestino.setAlpha(0.6f);}
            if(editOrigen!=null){editOrigen.setEnabled(false);editOrigen.setAlpha(0.6f);}
            if(layoutBloqueoBusqueda!=null){layoutBloqueoBusqueda.setVisibility(View.VISIBLE);}else{mostrarBannerBloqueo();}
            if(layoutResultadosViajes!=null)layoutResultadosViajes.removeAllViews();
            if(layoutLabelResultados!=null)layoutLabelResultados.setVisibility(View.GONE);
            if(layoutVacio!=null)layoutVacio.setVisibility(View.GONE);
            if(cardMapaBusqueda!=null)cardMapaBusqueda.setVisibility(View.GONE);
        }else{
            if(btnBuscar!=null){btnBuscar.setEnabled(true);btnBuscar.setAlpha(1f);btnBuscar.setText("🔍  Buscar viajes disponibles");}
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
    private void agregarCardMiReserva(JSONObject reserva){
        float d=getResources().getDisplayMetrics().density;int p16=(int)(16*d),p12=(int)(12*d),p8=(int)(8*d),p4=(int)(4*d);
        int idViaje=extraerIdViaje(reserva);String origen="Origen",destino="Destino",conductor="Conductor";double precio=0;String estado=reserva.optString("estado","ACTIVA");String fechaSal="";int asientos=reserva.optInt("numeroAsientos",reserva.optInt("asientos",1));String nombrePar=reserva.optString("nombreParada","");
        JSONObject viajeObj=reserva.optJSONObject("viaje");
        if(viajeObj!=null){if(idViaje==0)idViaje=extraerIdViaje(viajeObj);precio=viajeObj.optDouble("precio",0);fechaSal=viajeObj.optString("fechaHoraSalida","");JSONObject ruta=viajeObj.optJSONObject("ruta");if(ruta!=null){origen=extraerOrigenDeRuta(ruta);destino=extraerDestinoDeRuta(ruta);}JSONObject cond=viajeObj.optJSONObject("conductor");if(cond!=null)conductor=extractNombre(cond);}
        MaterialCardView card=new MaterialCardView(this);LinearLayout.LayoutParams lpCard=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);lpCard.setMargins(0,0,0,p12);card.setLayoutParams(lpCard);card.setRadius(18*d);card.setCardElevation(5*d);card.setCardBackgroundColor(Color.WHITE);card.setClickable(true);card.setFocusable(true);int cb=badgeColorReserva(estado);card.setStrokeColor(cb);card.setStrokeWidth((int)(2*d));
        LinearLayout inner=new LinearLayout(this);inner.setOrientation(LinearLayout.VERTICAL);inner.setPadding(p16,p16,p16,p16);
        LinearLayout filaE=new LinearLayout(this);filaE.setOrientation(LinearLayout.HORIZONTAL);filaE.setGravity(android.view.Gravity.CENTER_VERTICAL);TextView tvE=new TextView(this);tvE.setText(etiquetaReserva(estado));tvE.setTextSize(11f);tvE.setTextColor(Color.WHITE);tvE.setTypeface(null,Typeface.BOLD);tvE.setPadding(p8,p4,p8,p4);GradientDrawable bgE=new GradientDrawable();bgE.setShape(GradientDrawable.RECTANGLE);bgE.setCornerRadius(20*d);bgE.setColor(cb);tvE.setBackground(bgE);filaE.addView(tvE);View esp=new View(this);esp.setLayoutParams(new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));filaE.addView(esp);TextView tvA=new TextView(this);tvA.setText("💺 "+asientos+(asientos==1?" asiento":" asientos"));tvA.setTextSize(11f);tvA.setTextColor(Color.parseColor("#1565C0"));tvA.setTypeface(null,Typeface.BOLD);tvA.setPadding(p8,p4,p8,p4);GradientDrawable bgA=new GradientDrawable();bgA.setShape(GradientDrawable.RECTANGLE);bgA.setCornerRadius(20*d);bgA.setColor(Color.parseColor("#E3F2FD"));tvA.setBackground(bgA);filaE.addView(tvA);inner.addView(filaE);
        View sep1=new View(this);LinearLayout.LayoutParams lps1=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,(int)(1*d));lps1.setMargins(0,p8,0,p8);sep1.setLayoutParams(lps1);sep1.setBackgroundColor(Color.parseColor("#E0F2F1"));inner.addView(sep1);
        LinearLayout filaR=new LinearLayout(this);filaR.setOrientation(LinearLayout.HORIZONTAL);filaR.setGravity(android.view.Gravity.CENTER_VERTICAL);LinearLayout ind=new LinearLayout(this);ind.setOrientation(LinearLayout.VERTICAL);ind.setGravity(android.view.Gravity.CENTER_HORIZONTAL);LinearLayout.LayoutParams lpI=new LinearLayout.LayoutParams((int)(18*d),LinearLayout.LayoutParams.WRAP_CONTENT);lpI.setMargins(0,0,p12,0);ind.setLayoutParams(lpI);View cv=new View(this);cv.setLayoutParams(new LinearLayout.LayoutParams((int)(10*d),(int)(10*d)));GradientDrawable gv=new GradientDrawable();gv.setShape(GradientDrawable.OVAL);gv.setColor(Color.parseColor("#4CAF50"));cv.setBackground(gv);ind.addView(cv);View lv=new View(this);LinearLayout.LayoutParams lpLv=new LinearLayout.LayoutParams((int)(2*d),(int)(26*d));lpLv.setMargins((int)(4*d),(int)(2*d),(int)(4*d),(int)(2*d));lv.setLayoutParams(lpLv);lv.setBackgroundColor(Color.parseColor("#B2DFDB"));ind.addView(lv);View rv=new View(this);rv.setLayoutParams(new LinearLayout.LayoutParams((int)(10*d),(int)(10*d)));GradientDrawable gr=new GradientDrawable();gr.setShape(GradientDrawable.OVAL);gr.setColor(Color.parseColor("#EF5350"));rv.setBackground(gr);ind.addView(rv);filaR.addView(ind);
        LinearLayout colR=new LinearLayout(this);colR.setOrientation(LinearLayout.VERTICAL);colR.setLayoutParams(new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));TextView tvO=new TextView(this);tvO.setText(origen);tvO.setTextSize(14f);tvO.setTypeface(null,Typeface.BOLD);tvO.setTextColor(Color.parseColor("#004D40"));tvO.setMaxLines(2);tvO.setEllipsize(android.text.TextUtils.TruncateAt.END);colR.addView(tvO);if(!nombrePar.isEmpty()&&!nombrePar.equals(destino)){TextView tvP=new TextView(this);tvP.setText("🚏 Bajas en: "+nombrePar);tvP.setTextSize(12f);tvP.setTextColor(Color.parseColor("#0097A7"));LinearLayout.LayoutParams lpP=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);lpP.topMargin=(int)(10*d);tvP.setLayoutParams(lpP);colR.addView(tvP);}TextView tvDes=new TextView(this);tvDes.setText(destino);tvDes.setTextSize(13f);tvDes.setTextColor(Color.parseColor("#546E7A"));tvDes.setMaxLines(2);tvDes.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams lpD=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);lpD.topMargin=p12;tvDes.setLayoutParams(lpD);colR.addView(tvDes);filaR.addView(colR);inner.addView(filaR);
        View sep2=new View(this);LinearLayout.LayoutParams lps2=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,(int)(1*d));lps2.setMargins(0,p8,0,p8);sep2.setLayoutParams(lps2);sep2.setBackgroundColor(Color.parseColor("#E0F2F1"));inner.addView(sep2);
        LinearLayout filaI=new LinearLayout(this);filaI.setOrientation(LinearLayout.HORIZONTAL);filaI.setGravity(android.view.Gravity.CENTER_VERTICAL);TextView tvC=new TextView(this);tvC.setText("🚗 "+(conductor.isEmpty()?"Conductor":conductor));tvC.setTextSize(12f);tvC.setTextColor(Color.parseColor("#00695C"));tvC.setLayoutParams(new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));tvC.setMaxLines(1);tvC.setEllipsize(android.text.TextUtils.TruncateAt.END);filaI.addView(tvC);
        if(precio>0){TextView tvP2=new TextView(this);tvP2.setText("💵 $"+String.format("%.0f",precio));tvP2.setTextSize(13f);tvP2.setTypeface(null,Typeface.BOLD);tvP2.setTextColor(Color.parseColor("#FF6F00"));LinearLayout.LayoutParams lpP2=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);lpP2.setMargins(p8,0,0,0);tvP2.setLayoutParams(lpP2);filaI.addView(tvP2);}inner.addView(filaI);
        if(!fechaSal.isEmpty()){String fl=fechaSal.length()>10?fechaSal.substring(0,16).replace("T"," "):fechaSal;TextView tvF=new TextView(this);tvF.setText("🕐 "+fl);tvF.setTextSize(11f);tvF.setTextColor(Color.parseColor("#90A4AE"));LinearLayout.LayoutParams lpF=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);lpF.topMargin=p4;tvF.setLayoutParams(lpF);inner.addView(tvF);}
        card.addView(inner);final int idF=idViaje;card.setOnClickListener(v->abrirDetalle(idF));
        if(layoutMisReservas!=null)layoutMisReservas.addView(card);
    }

    private String etiquetaReserva(String e){switch(e.toUpperCase()){case"ACTIVA":case"CONFIRMADA":return"✅ Confirmada";case"EN_CURSO":case"INICIADO":return"🚗 En curso";case"ESPERANDO_RECOGIDA":return"⏳ Esperando recogida";case"RECOGIDO":return"🚗 ¡Te recogieron!";case"PENDIENTE":return"⏳ Pendiente";default:return"📌 "+e;}}
    private int badgeColorReserva(String e){switch(e.toUpperCase()){case"ACTIVA":case"CONFIRMADA":return Color.parseColor("#2E7D32");case"EN_CURSO":case"INICIADO":return Color.parseColor("#00838F");case"ESPERANDO_RECOGIDA":return Color.parseColor("#E65100");case"RECOGIDO":return Color.parseColor("#1565C0");case"PENDIENTE":return Color.parseColor("#F57F17");default:return Color.parseColor("#546E7A");}}

    // =========================================================================
    //  BÚSQUEDA — CORREGIDA
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

        // ✅ FIX: si el GPS ya tiene coords y el campo origen está vacío o dice "Mi ubicación", usar GPS directamente
        boolean usarGps = gpsListo && (txtO.isEmpty() || txtO.startsWith("Mi ubicación"));

        new Thread(()->{
            try{
                if(!usarGps && !txtO.isEmpty()){
                    double[]c=geocodificar(txtO);latOrigen=c[0];lngOrigen=c[1];
                }
                // Si usarGps ya tenemos latOrigen/lngOrigen del GPS

                if(!txtD.isEmpty()){
                    try{double[]c=geocodificar(txtD);latDestino=c[0];lngDestino=c[1];}
                    catch(Exception e){Log.w(TAG,"No se pudo geocodificar destino: "+e.getMessage());latDestino=0;lngDestino=0;}
                }else{latDestino=0;lngDestino=0;}

                runOnUiThread(()->buscarViajesEnCurso(txtO,txtD));
            }catch(Exception e){
                Log.e(TAG,"geocodificar origen: "+e.getMessage());
                // ✅ FIX: Si falla la geocodificación del origen, buscar sin filtro de coords
                runOnUiThread(()->buscarViajesEnCurso(txtO,txtD));
            }
        }).start();
    }

    /**
     * ✅ FIX PRINCIPAL:
     * Intenta múltiples endpoints en cascada.
     * El filtrado de estado EN_CURSO se hace localmente en filtrarParaPasajero().
     */
    private void buscarViajesEnCurso(String txtO, String txtD) {
        Log.d(TAG, "Buscando viajes. latOrigen="+latOrigen+" lngOrigen="+lngOrigen);

        // Intento 1: /api/viajes/buscar
        ConexionApi.getInstance(this).getArray(Constantes.buscarViajes(),
                r -> {
                    Log.d(TAG, "buscarViajes() OK, total="+r.length());
                    JSONArray filtrado = filtrarParaPasajero(r);
                    Log.d(TAG, "Después de filtro: "+filtrado.length());
                    procesarViajes(filtrado, txtO, txtD);
                },
                err -> {
                    Log.w(TAG, "buscarViajes() falló, intentando VIAJES base...");
                    // Intento 2: /api/viajes
                    ConexionApi.getInstance(this).getArray(Constantes.VIAJES,
                            r2 -> {
                                Log.d(TAG, "VIAJES OK, total="+r2.length());
                                JSONArray filtrado = filtrarParaPasajero(r2);
                                Log.d(TAG, "Después de filtro: "+filtrado.length());
                                procesarViajes(filtrado, txtO, txtD);
                            },
                            e2 -> {
                                Log.e(TAG, "Ambos endpoints fallaron");
                                runOnUiThread(() -> {
                                    if(layoutBuscando!=null)layoutBuscando.setVisibility(View.GONE);
                                    if(btnBuscar!=null){btnBuscar.setEnabled(true);btnBuscar.setText("🔍  Buscar viajes disponibles");}
                                    busquedaActiva=false;
                                    Toast.makeText(this,"Error conectando al servidor. Verifica tu conexión.",Toast.LENGTH_LONG).show();
                                });
                            });
                });
    }

    /**
     * ✅ FILTRO CORREGIDO:
     * - Solo filtra por estado EN_CURSO / INICIADO
     * - Solo filtra por cupos disponibles
     * - El filtro de distancia es OPCIONAL: solo aplica si tenemos coords válidas
     *   Y si el radio es amplio (15 km). Si no hay GPS, muestra todos los viajes activos.
     * - NO lanza excepción si las coords son 0 — simplemente omite el filtro geográfico.
     */
    private JSONArray filtrarParaPasajero(JSONArray viajes) {
        JSONArray res = new JSONArray();
        if (viajes == null) return res;

        boolean tengoGps = latOrigen != 0 && lngOrigen != 0;

        for (int i = 0; i < viajes.length(); i++) {
            try {
                JSONObject v = viajes.getJSONObject(i);
                String est = v.optString("estado", "").toUpperCase();

                // ✅ Solo viajes EN_CURSO o INICIADO
                if (!est.equals(ESTADO_EN_CURSO) && !est.equals(ESTADO_INICIADO)) continue;

                // ✅ Solo con cupos disponibles
                int cupos = v.optInt("cuposDisponibles", v.optInt("cupos", -1));
                // Si cupos=-1 el campo no existe, lo mostramos igual (el backend es inconsistente)
                if (cupos == 0) continue;

                // ✅ Filtro geográfico SOLO si tenemos GPS
                if (tengoGps) {
                    double loR = 0, lgR = 0, ldR = 0, gdR = 0;
                    JSONObject ruta = v.optJSONObject("ruta");
                    if (ruta != null) {
                        loR = ruta.optDouble("latOrigen", 0);
                        lgR = ruta.optDouble("lngOrigen", 0);
                        ldR = ruta.optDouble("latDestino", 0);
                        gdR = ruta.optDouble("lngDestino", 0);
                    }
                    if (loR == 0) loR = v.optDouble("latOrigen", 0);
                    if (lgR == 0) lgR = v.optDouble("lngOrigen", 0);
                    if (ldR == 0) ldR = v.optDouble("latDestino", 0);
                    if (gdR == 0) gdR = v.optDouble("lngDestino", 0);

                    // ✅ Si las coords del viaje son 0, lo incluimos igualmente
                    // (el conductor puede haber publicado sin coords exactas)
                    if (loR != 0 && lgR != 0) {
                        boolean origenCerca = distKm(latOrigen, lngOrigen, loR, lgR) <= RADIO_KM;
                        boolean rutaPasa   = ldR != 0 && distKm(latOrigen, lngOrigen, ldR, gdR) <= RADIO_KM * 2;
                        if (!origenCerca && !rutaPasa) {
                            Log.d(TAG, "Viaje excluido por distancia: id=" + v.optInt("idViajes", v.optInt("id", 0)));
                            continue;
                        }
                    }
                    // Si las coords del viaje son 0 → lo incluimos siempre
                }
                // Si no tenemos GPS → mostramos todos los EN_CURSO sin filtro geográfico

                res.put(v);
            } catch (Exception e) {
                Log.e(TAG, "filtrar viaje: " + e.getMessage());
            }
        }
        return res;
    }

    // =========================================================================
    //  MOSTRAR RESULTADOS
    // =========================================================================
    private void procesarViajes(JSONArray viajes,String txtO,String txtD){
        runOnUiThread(()->{
            if(layoutBuscando!=null)layoutBuscando.setVisibility(View.GONE);
            if(btnBuscar!=null){btnBuscar.setEnabled(true);btnBuscar.setText("🔍  Buscar viajes disponibles");}
            busquedaActiva=false;if(layoutResultadosViajes!=null)layoutResultadosViajes.removeAllViews();viajesConRuta.clear();
            if(viajes==null||viajes.length()==0){
                if(layoutLabelResultados!=null)layoutLabelResultados.setVisibility(View.GONE);
                if(layoutVacio!=null){
                    layoutVacio.setVisibility(View.VISIBLE);
                    if(txtVacio!=null)txtVacio.setText("😔 No encontramos conductores activos");
                    if(txtVacioSub!=null)txtVacioSub.setText(
                            gpsListo
                                    ? "No hay conductores con viaje EN CURSO cerca de ti ahora.\n\nEspera a que un conductor presione \"Iniciar viaje\"."
                                    : "No hay conductores con viaje EN CURSO en este momento.\n\nEspera a que un conductor presione \"Iniciar viaje\"."
                    );
                }
                if(cardMapaBusqueda!=null)cardMapaBusqueda.setVisibility(View.GONE);
                return;
            }
            if(layoutVacio!=null)layoutVacio.setVisibility(View.GONE);
            if(layoutLabelResultados!=null)layoutLabelResultados.setVisibility(View.VISIBLE);
            if(txtLabelResultados!=null)txtLabelResultados.setText("Conductores activos cerca de ti");
            if(txtContadorResultados!=null)txtContadorResultados.setText(String.valueOf(viajes.length()));
            if(cardMapaBusqueda!=null)cardMapaBusqueda.setVisibility(View.VISIBLE);
            limpiarMapa();

            GeoPoint pO=latOrigen!=0?new GeoPoint(latOrigen,lngOrigen):new GeoPoint(2.4448,-76.6147);
            agregarMarcadorOrigen(pO,txtO.isEmpty()?"Mi ubicación":txtO);
            if(latDestino!=0&&lngDestino!=0)agregarMarcadorDestino(new GeoPoint(latDestino,lngDestino),txtD);

            for(int i=0;i<viajes.length();i++){
                JSONObject v=viajes.optJSONObject(i);if(v==null)continue;
                agregarCardViaje(v,i);
                double lo=0,go=0,ld=0,gd=0;
                JSONObject ruta=v.optJSONObject("ruta");
                if(ruta!=null){lo=ruta.optDouble("latOrigen",0);go=ruta.optDouble("lngOrigen",0);ld=ruta.optDouble("latDestino",0);gd=ruta.optDouble("lngDestino",0);}
                if(lo==0)lo=v.optDouble("latOrigen",0);if(go==0)go=v.optDouble("lngOrigen",0);
                if(ld==0)ld=v.optDouble("latDestino",0);if(gd==0)gd=v.optDouble("lngDestino",0);
                if(lo!=0&&go!=0&&ld!=0&&gd!=0){final double flo=lo,fgo=go,fld=ld,fgd=gd;final int idx=i;new Thread(()->dibujarRuta(flo,fgo,fld,fgd,idx)).start();}
            }
            ajustarVista(pO);
        });
    }

    // =========================================================================
    //  CARD VIAJE RESULTADO
    // =========================================================================
    private void agregarCardViaje(JSONObject viaje,int idx){
        float d=getResources().getDisplayMetrics().density;int p16=(int)(16*d),p12=(int)(12*d),p8=(int)(8*d),p4=(int)(4*d);
        int idV=extraerIdViaje(viaje);double precio=viaje.optDouble("precio",0);int cupos=viaje.optInt("cuposDisponibles",viaje.optInt("cupos",0));
        String fecha=viaje.optString("fechaHoraSalida",viaje.optString("fecha",""));
        String origen="Origen",destino="Destino",cond="Conductor";
        JSONObject ruta=viaje.optJSONObject("ruta");
        if(ruta!=null){origen=extraerOrigenDeRuta(ruta);destino=extraerDestinoDeRuta(ruta);}
        else{origen=viaje.optString("origen",origen);destino=viaje.optString("destino",destino);}
        JSONObject co=viaje.optJSONObject("conductor");
        if(co!=null){cond=extractNombre(co);if(cond.isEmpty()){JSONObject u=co.optJSONObject("usuario");if(u!=null)cond=extractNombre(u);}}
        if(cond.isEmpty())cond=viaje.optString("nombreConductor","Conductor");

        int col=COLORES_INT[idx%COLORES_INT.length];
        MaterialCardView card=new MaterialCardView(this);LinearLayout.LayoutParams lpC=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);lpC.setMargins(0,0,0,p12);card.setLayoutParams(lpC);card.setRadius(18*d);card.setCardElevation(6*d);card.setCardBackgroundColor(Color.WHITE);card.setClickable(true);card.setFocusable(true);card.setStrokeColor(col);card.setStrokeWidth((int)(2.5f*d));
        LinearLayout inner=new LinearLayout(this);inner.setOrientation(LinearLayout.HORIZONTAL);View barra=new View(this);LinearLayout.LayoutParams lpB=new LinearLayout.LayoutParams((int)(6*d),LinearLayout.LayoutParams.MATCH_PARENT);barra.setLayoutParams(lpB);barra.setBackgroundColor(col);inner.addView(barra);
        LinearLayout cont=new LinearLayout(this);cont.setOrientation(LinearLayout.VERTICAL);cont.setPadding(p16,p12,p16,p12);cont.setLayoutParams(new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));
        LinearLayout filaTop=new LinearLayout(this);filaTop.setOrientation(LinearLayout.HORIZONTAL);filaTop.setGravity(android.view.Gravity.CENTER_VERTICAL);LinearLayout indR=new LinearLayout(this);indR.setOrientation(LinearLayout.VERTICAL);indR.setGravity(android.view.Gravity.CENTER_HORIZONTAL);LinearLayout.LayoutParams lpI=new LinearLayout.LayoutParams((int)(20*d),LinearLayout.LayoutParams.WRAP_CONTENT);lpI.setMargins(0,0,p12,0);indR.setLayoutParams(lpI);View c1=new View(this);c1.setLayoutParams(new LinearLayout.LayoutParams((int)(10*d),(int)(10*d)));GradientDrawable g1=new GradientDrawable();g1.setShape(GradientDrawable.OVAL);g1.setColor(col);c1.setBackground(g1);indR.addView(c1);View ln=new View(this);LinearLayout.LayoutParams lpLn=new LinearLayout.LayoutParams((int)(2*d),(int)(28*d));lpLn.setMargins((int)(4*d),(int)(2*d),(int)(4*d),(int)(2*d));ln.setLayoutParams(lpLn);ln.setBackgroundColor(Color.parseColor("#B2DFDB"));indR.addView(ln);View c2=new View(this);c2.setLayoutParams(new LinearLayout.LayoutParams((int)(10*d),(int)(10*d)));GradientDrawable g2=new GradientDrawable();g2.setShape(GradientDrawable.OVAL);g2.setColor(Color.parseColor("#EF5350"));c2.setBackground(g2);indR.addView(c2);filaTop.addView(indR);
        LinearLayout colRt=new LinearLayout(this);colRt.setOrientation(LinearLayout.VERTICAL);colRt.setLayoutParams(new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));TextView tvOr=new TextView(this);tvOr.setText("🟢 "+origen);tvOr.setTextSize(13f);tvOr.setTypeface(null,Typeface.BOLD);tvOr.setTextColor(Color.parseColor("#004D40"));tvOr.setMaxLines(2);tvOr.setEllipsize(android.text.TextUtils.TruncateAt.END);colRt.addView(tvOr);TextView tvDe=new TextView(this);tvDe.setText("🔴 "+destino);tvDe.setTextSize(13f);tvDe.setTextColor(Color.parseColor("#546E7A"));tvDe.setMaxLines(2);tvDe.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams lpDe=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);lpDe.topMargin=(int)(14*d);tvDe.setLayoutParams(lpDe);colRt.addView(tvDe);filaTop.addView(colRt);
        TextView tvEst=new TextView(this);tvEst.setText("🚗 ACTIVO");tvEst.setTextSize(10f);tvEst.setTextColor(Color.WHITE);tvEst.setTypeface(null,Typeface.BOLD);tvEst.setPadding(p8,p4,p8,p4);GradientDrawable bgEst=new GradientDrawable();bgEst.setShape(GradientDrawable.RECTANGLE);bgEst.setCornerRadius(20*d);bgEst.setColor(0xFF2E7D32);tvEst.setBackground(bgEst);LinearLayout.LayoutParams lpEst=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);lpEst.setMargins(p8,0,0,0);tvEst.setLayoutParams(lpEst);filaTop.addView(tvEst);cont.addView(filaTop);
        View div=new View(this);LinearLayout.LayoutParams lpDiv=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,(int)(1*d));lpDiv.setMargins(0,p12,0,p12);div.setLayoutParams(lpDiv);div.setBackgroundColor(Color.parseColor("#E0F2F1"));cont.addView(div);
        LinearLayout filaI=new LinearLayout(this);filaI.setOrientation(LinearLayout.HORIZONTAL);filaI.setGravity(android.view.Gravity.CENTER_VERTICAL);TextView tvCd=new TextView(this);tvCd.setText("🚗 "+cond);tvCd.setTextSize(12f);tvCd.setTextColor(Color.parseColor("#00695C"));tvCd.setLayoutParams(new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));tvCd.setMaxLines(1);tvCd.setEllipsize(android.text.TextUtils.TruncateAt.END);filaI.addView(tvCd);
        if(precio>0){TextView tvPr=new TextView(this);tvPr.setText("💵 $"+String.format("%.0f",precio));tvPr.setTextSize(13f);tvPr.setTypeface(null,Typeface.BOLD);tvPr.setTextColor(Color.parseColor("#FF6F00"));LinearLayout.LayoutParams lpPr=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);lpPr.setMargins(p8,0,p8,0);tvPr.setLayoutParams(lpPr);filaI.addView(tvPr);}
        TextView tvCu=new TextView(this);tvCu.setText(cupos>0?"💺 "+cupos+" libres":"💺 Sin cupos");tvCu.setTextSize(12f);tvCu.setTypeface(null,Typeface.BOLD);tvCu.setTextColor(cupos>0?Color.parseColor("#2E7D32"):Color.parseColor("#C62828"));tvCu.setPadding(p8,p4,p8,p4);GradientDrawable bgCu=new GradientDrawable();bgCu.setShape(GradientDrawable.RECTANGLE);bgCu.setCornerRadius(12*d);bgCu.setColor(cupos>0?Color.parseColor("#E8F5E9"):Color.parseColor("#FFEBEE"));tvCu.setBackground(bgCu);filaI.addView(tvCu);cont.addView(filaI);
        if(!fecha.isEmpty()){String fl=fecha.length()>10?fecha.substring(0,16).replace("T"," "):fecha;TextView tvF=new TextView(this);tvF.setText("🕐 Salida: "+fl);tvF.setTextSize(11f);tvF.setTextColor(Color.parseColor("#90A4AE"));LinearLayout.LayoutParams lpF=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);lpF.topMargin=p8;tvF.setLayoutParams(lpF);cont.addView(tvF);}
        inner.addView(cont);card.addView(inner);final int idF=idV;card.setOnClickListener(v->abrirDetalle(idF));
        if(layoutResultadosViajes!=null)layoutResultadosViajes.addView(card);
    }

    // =========================================================================
    //  MAPA — RUTAS
    // =========================================================================
    private void dibujarRuta(double lo,double go,double ld,double gd,int idx){
        try{
            ArrayList<GeoPoint>pts=null;
            try{pts=parsearOsrm(petHttp(OSRM_URL+"/route/v1/driving/"+go+","+lo+";"+gd+","+ld+"?overview=full&geometries=geojson"));}
            catch(Exception e1){try{pts=parsearOsrm(petHttp("https://router.project-osrm.org/route/v1/driving/"+go+","+lo+";"+gd+","+ld+"?overview=full&geometries=geojson"));}catch(Exception e2){Log.w(TAG,"OSRM falló idx="+idx);}}
            if(pts==null||pts.isEmpty()){pts=new ArrayList<>();pts.add(new GeoPoint(lo,go));pts.add(new GeoPoint(ld,gd));}
            ViajeConRuta vcr=new ViajeConRuta();vcr.puntos=pts;vcr.origen=new GeoPoint(lo,go);vcr.destino=new GeoPoint(ld,gd);
            synchronized(viajesConRuta){viajesConRuta.add(vcr);}
            final ArrayList<GeoPoint>pf=pts;
            runOnUiThread(()->{
                if(mapBusqueda==null)return;
                int c=COLORES_INT[idx%COLORES_INT.length];
                Polyline s=new Polyline(mapBusqueda);s.setPoints(pf);s.setColor(Color.argb(50,0,0,0));s.setWidth(18f);
                Polyline b=new Polyline(mapBusqueda);b.setPoints(pf);b.setColor(Color.WHITE);b.setWidth(14f);
                Polyline l=new Polyline(mapBusqueda);l.setPoints(pf);l.setColor(c);l.setWidth(9f);
                mapBusqueda.getOverlays().add(s);mapBusqueda.getOverlays().add(b);mapBusqueda.getOverlays().add(l);
                synchronized(rutasEnMapa){rutasEnMapa.add(s);rutasEnMapa.add(b);rutasEnMapa.add(l);}
                rePinearMarcadores();mapBusqueda.invalidate();ajustarVistaTodas();
            });
        }catch(Exception e){Log.e(TAG,"dibujarRuta "+idx+": "+e.getMessage());}
    }

    private ArrayList<GeoPoint> parsearOsrm(String json)throws Exception{if(json==null||json.isEmpty())return null;JSONObject res=new JSONObject(json);if(!"Ok".equals(res.optString("code")))return null;JSONArray routes=res.optJSONArray("routes");if(routes==null||routes.length()==0)return null;JSONObject geo=routes.getJSONObject(0).optJSONObject("geometry");if(geo==null)return null;JSONArray coords=geo.optJSONArray("coordinates");if(coords==null)return null;ArrayList<GeoPoint>p=new ArrayList<>();for(int i=0;i<coords.length();i++){JSONArray c=coords.getJSONArray(i);p.add(new GeoPoint(c.getDouble(1),c.getDouble(0)));}return p;}
    private void limpiarMapa(){if(mapBusqueda==null)return;synchronized(rutasEnMapa){mapBusqueda.getOverlays().removeAll(rutasEnMapa);rutasEnMapa.clear();}List<Overlay>rm=new ArrayList<>();for(Overlay o:mapBusqueda.getOverlays())if(o instanceof Marker)rm.add(o);mapBusqueda.getOverlays().removeAll(rm);mapBusqueda.invalidate();}
    private void rePinearMarcadores(){if(mapBusqueda==null)return;if(marcadorOrigen!=null){mapBusqueda.getOverlays().remove(marcadorOrigen);mapBusqueda.getOverlays().add(marcadorOrigen);}if(marcadorDestino!=null){mapBusqueda.getOverlays().remove(marcadorDestino);mapBusqueda.getOverlays().add(marcadorDestino);}}
    private void ajustarVista(GeoPoint o){if(mapBusqueda==null)return;mapBusqueda.post(()->{try{mapBusqueda.getController().animateTo(o);mapBusqueda.getController().setZoom(14.0);}catch(Exception ignored){}});}
    private void ajustarVistaTodas(){if(mapBusqueda==null)return;synchronized(viajesConRuta){if(viajesConRuta.isEmpty())return;}mapBusqueda.post(()->{try{double mn1=Double.MAX_VALUE,mx1=-Double.MAX_VALUE,mn2=Double.MAX_VALUE,mx2=-Double.MAX_VALUE;mn1=Math.min(mn1,latOrigen!=0?latOrigen:2.4448);mx1=Math.max(mx1,latOrigen!=0?latOrigen:2.4448);mn2=Math.min(mn2,lngOrigen!=0?lngOrigen:-76.6147);mx2=Math.max(mx2,lngOrigen!=0?lngOrigen:-76.6147);synchronized(viajesConRuta){for(ViajeConRuta v:viajesConRuta){if(v.puntos==null)continue;for(GeoPoint p:v.puntos){mn1=Math.min(mn1,p.getLatitude());mx1=Math.max(mx1,p.getLatitude());mn2=Math.min(mn2,p.getLongitude());mx2=Math.max(mx2,p.getLongitude());}}}double pL=Math.max((mx1-mn1)*0.2,0.008),pG=Math.max((mx2-mn2)*0.2,0.008);mapBusqueda.zoomToBoundingBox(new BoundingBox(mx1+pL,mx2+pG,mn1-pL,mn2-pG),true,80);}catch(Exception ignored){}});}

    // =========================================================================
    //  HELPERS — extracción robusta de origen/destino (igual que DetalleViaje)
    // =========================================================================

    /**
     * Extrae el nombre de origen de un objeto ruta.
     * Soporta campo directo "origen" o parseando el campo "nombre" con formato "A → B".
     */
    private String extraerOrigenDeRuta(JSONObject ruta) {
        if (ruta == null) return "Origen";
        // Intento 1: campo directo
        for (String k : new String[]{"origen","puntoOrigen","inicio","nombreOrigen","lugarOrigen"}) {
            String v = ruta.optString(k, "").trim();
            if (!v.isEmpty() && !v.equals("null")) return v;
        }
        // Intento 2: parsear campo "nombre" = "Origen → Destino"
        String nombre = ruta.optString("nombre", ruta.optString("descripcion", "")).trim();
        if (nombre.contains("→")) return nombre.split("→")[0].trim();
        if (nombre.contains("->")) return nombre.split("->")[0].trim();
        if (nombre.contains(" - ")) return nombre.split(" - ")[0].trim();
        return "Origen";
    }

    /**
     * Extrae el nombre de destino de un objeto ruta.
     */
    private String extraerDestinoDeRuta(JSONObject ruta) {
        if (ruta == null) return "Destino";
        // Intento 1: campo directo
        for (String k : new String[]{"destino","puntoDestino","fin","nombreDestino","lugarDestino"}) {
            String v = ruta.optString(k, "").trim();
            if (!v.isEmpty() && !v.equals("null")) return v;
        }
        // Intento 2: parsear campo "nombre" = "Origen → Destino"
        String nombre = ruta.optString("nombre", ruta.optString("descripcion", "")).trim();
        String[] partes = null;
        if (nombre.contains("→"))  partes = nombre.split("→",  2);
        else if (nombre.contains("->")) partes = nombre.split("->", 2);
        else if (nombre.contains(" - ")) partes = nombre.split(" - ", 2);
        if (partes != null && partes.length > 1) return partes[1].trim();
        // Intento 3: si el nombre no tiene separador, úsalo completo como destino
        if (!nombre.isEmpty()) return nombre;
        return "Destino";
    }

    private int extraerIdViaje(JSONObject o){if(o==null)return 0;for(String k:new String[]{"idViajes","idViaje","id","viajeId"}){int v=o.optInt(k,0);if(v>0)return v;}JSONObject va=o.optJSONObject("viaje");if(va!=null)for(String k:new String[]{"idViajes","idViaje","id"}){int v=va.optInt(k,0);if(v>0)return v;}return 0;}
    private void abrirDetalle(int idViaje){if(idViaje==0){Toast.makeText(this,"No se puede abrir este viaje",Toast.LENGTH_SHORT).show();return;}Intent i=new Intent(this,DetalleViajeActivity.class);i.putExtra("ID_VIAJE",idViaje);startActivity(i);}
    private double[] geocodificar(String dir)throws Exception{String q=dir.toLowerCase().contains("popay")?dir:dir+", Popayán, Colombia";String url="https://nominatim.openstreetmap.org/search?q="+java.net.URLEncoder.encode(q,"UTF-8")+"&format=json&limit=1&countrycodes=co";JSONArray arr=new JSONArray(petHttp(url));if(arr.length()==0){url="https://nominatim.openstreetmap.org/search?q="+java.net.URLEncoder.encode(dir+", Colombia","UTF-8")+"&format=json&limit=1";arr=new JSONArray(petHttp(url));}if(arr.length()==0)throw new Exception("No encontrado: "+dir);JSONObject o=arr.getJSONObject(0);return new double[]{o.getDouble("lat"),o.getDouble("lon")};}
    private String petHttp(String urlStr)throws Exception{HttpURLConnection c=null;try{URL u=new URL(urlStr);c=(HttpURLConnection)u.openConnection();c.setRequestProperty("User-Agent","Moviflexx-App/1.0");c.setConnectTimeout(15000);c.setReadTimeout(15000);BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream()));StringBuilder sb=new StringBuilder();String l;while((l=r.readLine())!=null)sb.append(l);r.close();return sb.toString();}finally{if(c!=null)c.disconnect();}}
    private double distKm(double la1,double lo1,double la2,double lo2){double R=6371,dLa=Math.toRadians(la2-la1),dLo=Math.toRadians(lo2-lo1);double a=Math.sin(dLa/2)*Math.sin(dLa/2)+Math.cos(Math.toRadians(la1))*Math.cos(Math.toRadians(la2))*Math.sin(dLo/2)*Math.sin(dLo/2);return R*2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));}
    private String extractNombre(JSONObject o){if(o==null)return"";for(String k:new String[]{"nombre","nombreCompleto","name","fullName","nombreUsuario","displayName","nombres"}){String v=o.optString(k,"");if(!v.isEmpty()&&!v.equals("null"))return v;}String n=o.optString("nombres",""),a=o.optString("apellidos","");if(!n.isEmpty()||!a.isEmpty())return(n+" "+a).trim();return"";}

    private static class ViajeConRuta{ArrayList<GeoPoint>puntos;GeoPoint origen,destino;}
}