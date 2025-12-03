package com.arlys.moviflexx.navigation;


import android.content.Context;
import android.content.Intent;

public class AppNavigation {

    public static void navigateToHome(Context context) {
        // Intent para ir a HomeScreen
        Intent intent = new Intent(context, com.arlys.moviflexx.ui.screen.HomeScreen.class);
        context.startActivity(intent);
    }

    public static void navigateToProfile(Context context) {
        // Aquí iría la navegación al perfil
        // Intent intent = new Intent(context, ProfileScreen.class);
        // context.startActivity(intent);
    }

    public static void navigateToHistory(Context context) {
        // Navegación al historial
    }

    // Más métodos de navegación...
}
