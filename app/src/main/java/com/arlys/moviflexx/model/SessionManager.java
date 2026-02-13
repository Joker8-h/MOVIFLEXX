package com.arlys.moviflexx.model;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

public class SessionManager {

    private static final String PREF_NAME = "MoviFlexxPrefs";

    private static final String KEY_IS_LOGGED_IN = "IS_LOGGED_IN";
    private static final String KEY_TOKEN        = "token";
    private static final String KEY_NOMBRE       = "nombre";
    private static final String KEY_EMAIL        = "email";
    private static final String KEY_TELEFONO     = "telefono";
    private static final String KEY_ID_ROL       = "idRol";
    private static final String KEY_ID_USUARIO   = "id_usuario";

    private SharedPreferences        prefs;
    private SharedPreferences.Editor editor;

    public SessionManager(Context context) {
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

    public void logout() {
        editor.clear();
        editor.apply();
        SesionUsuario.cerrarSesion();
    }
}