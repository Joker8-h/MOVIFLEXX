package com.arlys.moviflexx.model;

import android.content.Context;
import android.util.Log;

/**
 * Clase estática para mantener la sesión del usuario en memoria.
 * Se sincroniza automáticamente con SharedPreferences vía SessionManager.
 */
public class SesionUsuario {

    private static final String TAG = "SesionUsuario";

    private static int idUsuario = -1;
    private static int idRol = -1;
    private static String token = null;
    private static SessionManager sessionManager = null;

    // ================= INICIALIZACIÓN =================

    /**
     * Inicializa SesionUsuario cargando datos desde SharedPreferences.
     * DEBE llamarse al iniciar la app (en Application o MainActivity).
     */
    public static void init(Context context) {
        if (sessionManager == null) {
            sessionManager = new SessionManager(context.getApplicationContext());
            cargarDesdePreferencias();
            Log.d(TAG, "✅ SesionUsuario inicializada");
        }
    }

    /**
     * Carga los datos de sesión desde SharedPreferences a memoria.
     */
    private static void cargarDesdePreferencias() {
        if (sessionManager != null && sessionManager.isLoggedIn()) {
            token = sessionManager.getToken();
            idUsuario = sessionManager.getIdUsuario();
            idRol = sessionManager.getIdRol();

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
        // Si no hay valor en memoria, intentar cargar
        if (idUsuario == -1 && sessionManager != null) {
            idUsuario = sessionManager.getIdUsuario();
        }
        return idUsuario;
    }

    // Alias para compatibilidad con código existente
    public static int getId() {
        return getIdUsuario();
    }

    // ================= ROL =================

    public static void setIdRol(int rol) {
        idRol = rol;
        Log.d(TAG, "Rol ID actualizado: " + rol);
    }

    public static int getIdRol() {
        // Si no hay valor en memoria, intentar cargar
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
        // Si no hay token en memoria, intentar cargar desde preferencias
        if (token == null && sessionManager != null) {
            token = sessionManager.getToken();
            Log.d(TAG, "Token cargado desde preferencias: " + (token != null ? "✓" : "✗"));
        }
        return token;
    }

    // ================= SESIÓN =================

    public static boolean estaLogueado() {
        boolean logueado = idUsuario > 0 && token != null;

        // Double check con SessionManager
        if (!logueado && sessionManager != null) {
            logueado = sessionManager.isLoggedIn();
            if (logueado) {
                // Si está logueado pero no tenemos datos, recargar
                cargarDesdePreferencias();
            }
        }

        return logueado;
    }

    public static void cerrarSesion() {
        idUsuario = -1;
        idRol = -1;
        token = null;

        if (sessionManager != null) {
            sessionManager.logout();
        }

        Log.d(TAG, "🚪 Sesión cerrada");
    }

    // ================= DEBUG =================

    public static void logEstado() {
        Log.d(TAG, "═══════════════════════════════════════");
        Log.d(TAG, "Estado de SesionUsuario:");
        Log.d(TAG, "  Usuario ID: " + idUsuario);
        Log.d(TAG, "  Rol ID: " + idRol);
        Log.d(TAG, "  Token: " + (token != null ? "Presente" : "Ausente"));
        Log.d(TAG, "  Logueado: " + estaLogueado());
        Log.d(TAG, "═══════════════════════════════════════");
    }
}