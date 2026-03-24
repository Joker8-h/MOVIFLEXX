package com.arlys.moviflexx;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.arlys.moviflexx.model.SesionUsuario;

/**
 * Clase Application personalizada.
 * Se ejecuta ANTES que cualquier Activity cuando la app arranca.
 * Rastrea la Activity visible para el asistente de voz Movi.
 */
public class MyApplication extends Application {

    private static final String TAG = "MoviFlexx";

    /** Activity actualmente visible (foreground). */
    private static Activity currentActivity = null;

    /** Flag para detectar si la app se acaba de abrir */
    public static boolean isAppJustOpened = true;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "🚀 Iniciando MoviFlexx Application");

        // Inicializar sistema de sesión
        SesionUsuario.init(this);

        // Rastrear Activity visible para el asistente de voz
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle b) {}
            @Override public void onActivityStarted(@NonNull Activity a) {}
            @Override public void onActivityResumed(@NonNull Activity a) { currentActivity = a; }
            @Override public void onActivityPaused(@NonNull Activity a)  {
                if (currentActivity == a) currentActivity = null;
            }
            @Override public void onActivityStopped(@NonNull Activity a) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) {}
            @Override public void onActivityDestroyed(@NonNull Activity a) {}
        });

        Log.d(TAG, "✅ Application inicializada correctamente");
    }

    /**
     * Obtiene la Activity actualmente en foreground.
     * Usado por VoiceAssistantManager para leer pantalla, ayuda contextual, etc.
     */
    public static Activity getCurrentActivity() {
        return currentActivity;
    }
}