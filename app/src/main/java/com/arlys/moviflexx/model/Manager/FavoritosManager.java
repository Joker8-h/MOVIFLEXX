package com.arlys.moviflexx.model.Manager;

import android.content.Context;
import android.content.SharedPreferences;

import com.arlys.moviflexx.model.SessionManager;

/**
 * FavoritosManager — v2 (corregido)
 *
 * PROBLEMA ANTERIOR:
 *   Las claves se guardaban como "fav_<idConversacion>" en un SharedPreferences
 *   global ("moviflexx_favoritos"). Esto hacía que todos los usuarios del dispositivo
 *   compartieran los mismos favoritos.
 *
 * SOLUCIÓN:
 *   Cada usuario tiene su propio "archivo" de preferencias:
 *   "moviflexx_favoritos_usuario_<idUsuario>"
 *   Así, el conductor y el pasajero nunca comparten favoritos.
 */
public class FavoritosManager {

    // Prefijo del archivo de preferencias — se añade el ID del usuario
    private static final String PREFS_PREFIX = "moviflexx_favoritos_usuario_";
    private static final String KEY_PREFIX   = "fav_";

    private final SharedPreferences prefs;

    /**
     * Constructor que aísla los favoritos por usuario.
     * Usa SessionManager para obtener el ID del usuario logueado.
     */
    public FavoritosManager(Context context) {
        SessionManager session = new SessionManager(context);
        int idUsuario = session.getIdUsuario();  // ← clave del aislamiento

        // Cada usuario tiene su propio archivo de preferencias
        String prefsName = PREFS_PREFIX + idUsuario;
        this.prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
    }

    /**
     * Alterna el estado de favorito para una conversación.
     * @return true si ahora ES favorito, false si se quitó.
     */
    public boolean toggleFavorito(long idConversacion) {
        String key = KEY_PREFIX + idConversacion;
        boolean eraFavorito = prefs.getBoolean(key, false);
        prefs.edit().putBoolean(key, !eraFavorito).apply();
        return !eraFavorito;
    }

    /**
     * Comprueba si una conversación está marcada como favorita
     * por el usuario actualmente logueado.
     */
    public boolean esFavorito(long idConversacion) {
        return prefs.getBoolean(KEY_PREFIX + idConversacion, false);
    }

    /**
     * Quita el favorito explícitamente.
     */
    public void quitarFavorito(long idConversacion) {
        prefs.edit().remove(KEY_PREFIX + idConversacion).apply();
    }

    /**
     * Limpia todos los favoritos del usuario actual.
     * Útil si implementas "cerrar sesión".
     */
    public void limpiarTodos() {
        prefs.edit().clear().apply();
    }
}