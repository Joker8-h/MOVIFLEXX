package com.arlys.moviflexx;

import android.app.Application;
import android.util.Log;
import com.arlys.moviflexx.model.SesionUsuario;

/**
 * Clase Application personalizada.
 * Se ejecuta ANTES que cualquier Activity cuando la app arranca.
 */
public class MyApplication extends Application {

    private static final String TAG = "MoviFlexx";

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "🚀 Iniciando MoviFlexx Application");

        // Inicializar sistema de sesión
        SesionUsuario.init(this);

        Log.d(TAG, "✅ Application inicializada correctamente");
    }
}