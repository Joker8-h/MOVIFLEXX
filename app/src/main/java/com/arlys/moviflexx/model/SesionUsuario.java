package com.arlys.moviflexx.model;

import android.content.Context;
import android.util.Log;

/**
 * Clase estática para mantener la sesión del usuario en memoria.
 * Se sincroniza automáticamente con SharedPreferences vía SessionManager.
 */
public class SesionUsuario {

    private static final String TAG = "SesionUsuario";

    private static int    idUsuario      = -1;
    private static int    idRol          = -1;
    private static String token          = null;
    private static SessionManager sessionManager = null;

    // ================= INICIALIZACIÓN =================

    public static void init(Context context) {
        if (sessionManager == null) {
            sessionManager = new SessionManager(context.getApplicationContext());
            cargarDesdePreferencias();
            Log.d(TAG, "✅ SesionUsuario inicializada");
        }
    }

    private static void cargarDesdePreferencias() {
        if (sessionManager != null && sessionManager.isLoggedIn()) {
            token     = sessionManager.getToken();
            idUsuario = sessionManager.getIdUsuario();
            idRol     = sessionManager.getIdRol();

            Log.d(TAG, "📦 Sesión cargada - Usuario: " + idUsuario +
                    " | Rol: " + idRol +
                    " | Token: " + (token != null ? "✓" : "✗"));
        }
    }

    // ================= ID USUARIO =================

    public static void setIdUsuario(int id) {
        idUsuario = id;
        Log.d(TAG, "Usuario ID actualizado: " + id);
    }

    public static int getIdUsuario() {
        if (idUsuario == -1 && sessionManager != null) {
            idUsuario = sessionManager.getIdUsuario();
        }
        return idUsuario;
    }

    public static int getId() {
        return getIdUsuario();
    }

    // ================= ROL =================

    public static void setIdRol(int rol) {
        idRol = rol;
        Log.d(TAG, "Rol ID actualizado: " + rol);
    }

    public static int getIdRol() {
        if (idRol == -1 && sessionManager != null) {
            idRol = sessionManager.getIdRol();
        }
        return idRol;
    }

    // ================= TOKEN =================

    public static void setToken(String t) {
        token = t;
        Log.d(TAG, "Token actualizado: " + (t != null ? "✓" : "✗"));
    }

    public static String getToken() {
        if (token == null && sessionManager != null) {
            token = sessionManager.getToken();
            Log.d(TAG, "Token cargado desde preferencias: " + (token != null ? "✓" : "✗"));
        }
        return token;
    }

    // ================= SESIÓN =================

    public static boolean estaLogueado() {
        boolean logueado = idUsuario > 0 && token != null;

        if (!logueado && sessionManager != null) {
            logueado = sessionManager.isLoggedIn();
            if (logueado) {
                cargarDesdePreferencias();
            }
        }

        return logueado;
    }

    /**
     * Limpia los campos estáticos en memoria.
     *
     * IMPORTANTE: NO llama a sessionManager.logout() para evitar recursión
     * infinita. El ciclo era:
     *   SessionManager.logout() → SesionUsuario.cerrarSesion()
     *     → sessionManager.logout() → SesionUsuario.cerrarSesion() → ∞ crash
     *
     * La limpieza de SharedPreferences la hace SessionManager.logout().
     * Este método solo limpia la memoria estática.
     */
    public static void cerrarSesion() {
        idUsuario = -1;
        idRol     = -1;
        token     = null;
        Log.d(TAG, "🚪 Sesión cerrada (memoria limpiada)");
    }

    // ================= DEBUG =================

    public static void logEstado() {
        Log.d(TAG, "═══════════════════════════════════════");
        Log.d(TAG, "Estado de SesionUsuario:");
        Log.d(TAG, "  Usuario ID: " + idUsuario);
        Log.d(TAG, "  Rol ID:     " + idRol);
        Log.d(TAG, "  Token:      " + (token != null ? "Presente" : "Ausente"));
        Log.d(TAG, "  Logueado:   " + estaLogueado());
        Log.d(TAG, "═══════════════════════════════════════");
    }
}