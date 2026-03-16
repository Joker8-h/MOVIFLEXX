package com.arlys.moviflexx.model;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

public class SessionManager {

    private static final String PREF_NAME = "MoviFlexxPrefs";

    private static final String KEY_IS_LOGGED_IN         = "IS_LOGGED_IN";
    private static final String KEY_TOKEN                 = "token";
    private static final String KEY_NOMBRE                = "nombre";
    private static final String KEY_EMAIL                 = "email";
    private static final String KEY_TELEFONO              = "telefono";
    private static final String KEY_ID_ROL                = "idRol";
    private static final String KEY_ID_USUARIO            = "id_usuario";
    private static final String KEY_CALIFICACION_PROMEDIO = "calificacion_promedio";
    private static final String KEY_CALIFICACION_TOTAL    = "calificacion_total";
    private static final String KEY_FOTO_PERFIL           = "foto_perfil"; 
    private static final String KEY_PENDING_WELCOME       = "pending_welcome"; 

    private SharedPreferences        prefs;
    private SharedPreferences.Editor editor;
    private final Context            context;

    public SessionManager(Context context) {
        this.context = context.getApplicationContext();
        prefs  = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    // ================= LOGGED IN =================

    public void setLoggedIn(boolean loggedIn) {
        editor.putBoolean(KEY_IS_LOGGED_IN, loggedIn);
        editor.apply();
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    // ================= TOKEN =================

    public void saveToken(String token) {
        editor.putString(KEY_TOKEN, token);
        editor.apply();
        SesionUsuario.setToken(token);
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    // ================= USUARIO =================

    public void saveUser(String nombre, String email, String telefono, int idRol, int idUsuario) {
        editor.putString(KEY_NOMBRE,     nombre);
        editor.putString(KEY_EMAIL,      email);
        editor.putString(KEY_TELEFONO,   telefono);
        editor.putInt(KEY_ID_ROL,        idRol);
        editor.putInt(KEY_ID_USUARIO,    idUsuario);
        editor.apply();

        SesionUsuario.setIdUsuario(idUsuario);
        SesionUsuario.setIdRol(idRol);

        migrarVehiculoSiNecesario();
    }

    public String  getNombre()    { return prefs.getString(KEY_NOMBRE,   "Usuario"); }
    public String  getEmail()     { return prefs.getString(KEY_EMAIL,    ""); }
    public String  getTelefono()  { return prefs.getString(KEY_TELEFONO, ""); }
    public int     getIdRol()     { return prefs.getInt(KEY_ID_ROL,      -1); }
    public int     getIdUsuario() { return prefs.getInt(KEY_ID_USUARIO,  -1); }
    public boolean isConductor()  { return getIdRol() == 2; }

    // ================= GET USER DATA =================

    public Map<String, String> getUserData() {
        Map<String, String> user = new HashMap<>();
        user.put("nombre",    getNombre());
        user.put("email",     getEmail());
        user.put("telefono",  getTelefono());
        user.put("idUsuario", String.valueOf(getIdUsuario()));
        user.put("idRol",     String.valueOf(getIdRol()));
        return user;
    }

    // ================= ACTUALIZAR PERFIL =================

    public void actualizarPerfil(String nombre, String telefono) {
        editor.putString(KEY_NOMBRE,   nombre);
        editor.putString(KEY_TELEFONO, telefono);
        editor.apply();
    }

    // ================= CALIFICACIONES =================

    public void guardarCalificacion(double promedio, int total) {
        editor.putFloat(KEY_CALIFICACION_PROMEDIO, (float) promedio);
        editor.putInt(KEY_CALIFICACION_TOTAL, total);
        editor.apply();
    }

    public double getCalificacionPromedio() {
        return prefs.getFloat(KEY_CALIFICACION_PROMEDIO, 0f);
    }

    public int getCalificacionTotal() {
        return prefs.getInt(KEY_CALIFICACION_TOTAL, 0);
    }

    // ================= FOTO PERFIL (Cloudinary) =================

    public void saveFotoPerfil(String url) {
        editor.putString(KEY_FOTO_PERFIL, url == null ? "" : url);
        editor.apply();
    }

    public String getFotoPerfil() {
        return prefs.getString(KEY_FOTO_PERFIL, "");
    }

    // ================= BIENVENIDA (Asistente) =================

    public void setPendingWelcome(boolean pending) {
        editor.putBoolean(KEY_PENDING_WELCOME, pending);
        editor.apply();
    }

    public boolean isPendingWelcome() {
        return prefs.getBoolean(KEY_PENDING_WELCOME, false);
    }

    // ================= VEHÍCULO POR USUARIO =================

    private String vehiculoKey() {
        int id = getIdUsuario();
        if (id != -1) return "vehiculo_user_" + id;
        String token = getToken();
        if (token != null && token.length() > 8)
            return "vehiculo_token_" + token.substring(0, 8);
        return "vehiculo_user_default";
    }

    public void migrarVehiculoSiNecesario() {
        int id = getIdUsuario();
        if (id == -1) return;

        String keyCorrecta = "vehiculo_user_" + id;
        if (prefs.getBoolean(keyCorrecta + "_tiene", false)) return;

        String token = getToken();
        if (token != null && token.length() > 8) {
            String keyToken = "vehiculo_token_" + token.substring(0, 8);
            if (prefs.getBoolean(keyToken + "_tiene", false)) {
                copiarVehiculoKey(keyToken, keyCorrecta);
                return;
            }
        }

        String keyDefault = "vehiculo_user_default";
        if (prefs.getBoolean(keyDefault + "_tiene", false)) {
            copiarVehiculoKey(keyDefault, keyCorrecta);
        }
    }

    private void copiarVehiculoKey(String keyOrigen, String keyDestino) {
        editor.putBoolean(keyDestino + "_tiene",    true);
        editor.putInt(keyDestino     + "_id",        prefs.getInt(keyOrigen    + "_id",        -1));
        editor.putString(keyDestino  + "_modelo",    prefs.getString(keyOrigen + "_modelo",    ""));
        editor.putString(keyDestino  + "_placa",     prefs.getString(keyOrigen + "_placa",     ""));
        editor.putString(keyDestino  + "_capacidad", prefs.getString(keyOrigen + "_capacidad", ""));
        editor.remove(keyOrigen + "_tiene");
        editor.remove(keyOrigen + "_id");
        editor.remove(keyOrigen + "_modelo");
        editor.remove(keyOrigen + "_placa");
        editor.remove(keyOrigen + "_capacidad");
        editor.apply();
    }

    public void saveVehiculo(int idVehiculo, String modeloFull, String placa, String capacidad) {
        String key = vehiculoKey();
        editor.putBoolean(key + "_tiene",    true);
        editor.putInt(key    + "_id",        idVehiculo);
        editor.putString(key + "_modelo",    modeloFull);
        editor.putString(key + "_placa",     placa);
        editor.putString(key + "_capacidad", capacidad);
        editor.apply();
    }

    // ── Getters vehículo ──────────────────────────────────────────────────────
    public boolean tieneVehiculo()  { return prefs.getBoolean(vehiculoKey() + "_tiene",     false); }
    public int     getVehiculoId()  { return prefs.getInt(vehiculoKey()     + "_id",        -1);    }

    // ★ getIdVehiculo() — alias de getVehiculoId() para compatibilidad
    public int     getIdVehiculo()  { return getVehiculoId(); }

    public String  getVModelo()     { return prefs.getString(vehiculoKey()  + "_modelo",    "");    }
    public String  getVPlaca()      { return prefs.getString(vehiculoKey()  + "_placa",     "");    }
    public String  getVCapacidad()  { return prefs.getString(vehiculoKey()  + "_capacidad", "");    }

    public String getVehiculoNombre() { return prefs.getString(vehiculoKey() + "_modelo", "Vehículo"); }
    public String getVehiculoPlaca()  { return prefs.getString(vehiculoKey() + "_placa",  "---");      }

    // ================= CARGAR SESIÓN EN MEMORIA =================

    public void loadSessionToMemory() {
        if (isLoggedIn()) {
            SesionUsuario.setIdUsuario(getIdUsuario());
            SesionUsuario.setIdRol(getIdRol());
            SesionUsuario.setToken(getToken());
        }
    }

    // ================= LOGOUT =================

    public void logout() {
        prefs.edit().clear().apply();
        editor = prefs.edit();
        SesionUsuario.cerrarSesion();
    }

    public void logoutCompleto() {
        int idUsuario = getIdUsuario();
        logout();
        if (idUsuario != -1) {
            context.getSharedPreferences(
                    "moviflexx_favoritos_usuario_" + idUsuario,
                    Context.MODE_PRIVATE
            ).edit().clear().apply();
        }
    }
}