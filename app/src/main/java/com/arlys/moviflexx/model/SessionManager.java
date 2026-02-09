package com.arlys.moviflexx.model;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {

    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;
    private static final String PREF_NAME = "MoviFlexxPrefs";

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    // ================= TOKEN =================
    public void saveToken(String token) {
        editor.putString("token", token);
        editor.apply();
    }

    public String getToken() {
        return prefs.getString("token", null);
    }

    public boolean isLoggedIn() {
        return getToken() != null;
    }

    // ================= USUARIO =================
    public void saveUser(String nombre, String email, String telefono, int idRol, int idUsuario) {
        editor.putString("nombre", nombre);
        editor.putString("email", email);
        editor.putString("telefono", telefono);
        editor.putInt("idRol", idRol);
        editor.putInt("id_usuario", idUsuario); // 🔥 CLAVE
        editor.apply();
    }

    public String getNombre() { return prefs.getString("nombre", "Usuario"); }
    public String getEmail() { return prefs.getString("email", ""); }
    public String getTelefono() { return prefs.getString("telefono", ""); }
    public int getIdRol() { return prefs.getInt("idRol", -1); }
    public int getIdUsuario() { return prefs.getInt("id_usuario", -1); }

    public boolean isConductor() {
        return getIdRol() == 2;
    }

    // ================= VEHÍCULO (POR USUARIO) =================

    private String vehiculoKey() {
        return "vehiculo_user_" + getIdUsuario();
    }

    public void saveVehiculo(int idVehiculo, String modeloFull, String placa, String capacidad) {
        editor.putBoolean(vehiculoKey() + "_tiene", true);
        editor.putInt(vehiculoKey() + "_id", idVehiculo);
        editor.putString(vehiculoKey() + "_modelo", modeloFull);
        editor.putString(vehiculoKey() + "_placa", placa);
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

    // ================= LOGOUT =================
    public void logout() {
        editor.remove("nombre");
        editor.remove("email");
        editor.remove("telefono");
        editor.remove("idRol");
        editor.remove("token");
        editor.remove("id_usuario");
        editor.apply();
    }
}
