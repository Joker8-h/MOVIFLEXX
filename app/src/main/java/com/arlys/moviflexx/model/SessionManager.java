package com.arlys.moviflexx.model;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

public class SessionManager {

    private static final String PREF_NAME = "MoviFlexxPrefs";

    private static final String KEY_IS_LOGGED_IN          = "IS_LOGGED_IN";
    private static final String KEY_TOKEN                  = "token";
    private static final String KEY_NOMBRE                 = "nombre";
    private static final String KEY_EMAIL                  = "email";
    private static final String KEY_TELEFONO               = "telefono";
    private static final String KEY_ID_ROL                 = "idRol";
    private static final String KEY_ID_USUARIO             = "id_usuario";
    private static final String KEY_CALIFICACION_PROMEDIO  = "calificacion_promedio";
    private static final String KEY_CALIFICACION_TOTAL     = "calificacion_total";

    private SharedPreferences        prefs;
    private SharedPreferences.Editor editor;
    private final Context            context;  // ← guardamos context para limpiar favoritos en logoutCompleto

    public SessionManager(Context context) {
        this.context = context.getApplicationContext();
        prefs  = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
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
    }

    public String getNombre() {
        return prefs.getString(KEY_NOMBRE, "Usuario");
    }

    public String getEmail() {
        return prefs.getString(KEY_EMAIL, "");
    }

    public String getTelefono() {
        return prefs.getString(KEY_TELEFONO, "");
    }

    public int getIdRol() {
        return prefs.getInt(KEY_ID_ROL, -1);
    }

    public int getIdUsuario() {
        return prefs.getInt(KEY_ID_USUARIO, -1);
    }

    public boolean isConductor() {
        return getIdRol() == 2;
    }

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

    /**
     * Actualiza solo nombre y teléfono en la sesión local.
     * El email NO se puede modificar desde el perfil.
     */
    public void actualizarPerfil(String nombre, String telefono) {
        editor.putString(KEY_NOMBRE,   nombre);
        editor.putString(KEY_TELEFONO, telefono);
        editor.apply();
    }

    // ================= CALIFICACIONES =================

    /**
     * Guarda el promedio y total de calificaciones del usuario en sesión local.
     * Se llama después de calificar un viaje o al cargar el perfil.
     */
    public void guardarCalificacion(double promedio, int total) {
        editor.putFloat(KEY_CALIFICACION_PROMEDIO, (float) promedio);
        editor.putInt(KEY_CALIFICACION_TOTAL, total);
        editor.apply();
    }

    /**
     * Retorna el promedio de calificaciones guardado localmente.
     * 0.0 si el usuario no tiene calificaciones aún.
     */
    public double getCalificacionPromedio() {
        return prefs.getFloat(KEY_CALIFICACION_PROMEDIO, 0f);
    }

    /**
     * Retorna el total de calificaciones recibidas guardado localmente.
     */
    public int getCalificacionTotal() {
        return prefs.getInt(KEY_CALIFICACION_TOTAL, 0);
    }

    // ================= VEHÍCULO POR USUARIO =================

    /** Clave base única por usuario para aislar vehículos entre conductores. */
    private String vehiculoKey() {
        return "vehiculo_user_" + getIdUsuario();
    }

    public void saveVehiculo(int idVehiculo, String modeloFull, String placa, String capacidad) {
        editor.putBoolean(vehiculoKey() + "_tiene",    true);
        editor.putInt(vehiculoKey()    + "_id",        idVehiculo);
        editor.putString(vehiculoKey() + "_modelo",    modeloFull);
        editor.putString(vehiculoKey() + "_placa",     placa);
        editor.putString(vehiculoKey() + "_capacidad", capacidad);
        editor.apply();
    }

    public boolean tieneVehiculo() {
        return prefs.getBoolean(vehiculoKey() + "_tiene", false);
    }

    public int getVehiculoId() {
        return prefs.getInt(vehiculoKey() + "_id", -1);
    }

    public String getVModelo() {
        return prefs.getString(vehiculoKey() + "_modelo", "");
    }

    public String getVPlaca() {
        return prefs.getString(vehiculoKey() + "_placa", "");
    }

    public String getVCapacidad() {
        return prefs.getString(vehiculoKey() + "_capacidad", "");
    }

    // ─── Aliases usados en PublicarViaje para mostrar info del vehículo ───

    /**
     * Retorna el nombre completo del vehículo (marca + modelo).
     * Alias de getVModelo() para mayor claridad en PublicarViaje.
     */
    public String getVehiculoNombre() {
        return prefs.getString(vehiculoKey() + "_modelo", "Vehículo");
    }

    /**
     * Retorna la placa del vehículo registrado.
     * Alias de getVPlaca() para mayor claridad en PublicarViaje.
     */
    public String getVehiculoPlaca() {
        return prefs.getString(vehiculoKey() + "_placa", "---");
    }

    // ================= CARGAR SESIÓN EN MEMORIA =================

    public void loadSessionToMemory() {
        if (isLoggedIn()) {
            SesionUsuario.setIdUsuario(getIdUsuario());
            SesionUsuario.setIdRol(getIdRol());
            SesionUsuario.setToken(getToken());
        }
    }

    // ================= LOGOUT =================

    /**
     * Cierra la sesión del usuario actual.
     * Los favoritos NO se borran — se conservan por si el usuario
     * vuelve a iniciar sesión. Cada usuario tiene sus propios favoritos
     * en SharedPreferences "moviflexx_favoritos_usuario_<idUsuario>".
     */
    public void logout() {
        editor.clear();
        editor.apply();
        SesionUsuario.cerrarSesion();
    }

    /**
     * Logout completo: borra sesión Y favoritos del usuario actual.
     * Usar solo si el usuario pide explícitamente "eliminar mis datos".
     */
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