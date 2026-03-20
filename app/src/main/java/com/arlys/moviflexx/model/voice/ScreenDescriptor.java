package com.arlys.moviflexx.model.voice;

/**
 * Interfaz que cada Activity debe implementar para que el asistente Movi
 * pueda describir la pantalla actual, leer su contenido y explicar
 * las opciones disponibles al usuario con discapacidad visual.
 */
public interface ScreenDescriptor {

    /**
     * Nombre corto de la pantalla. Ejemplo: "Búsqueda de Rutas"
     */
    String getNombrePantalla();

    /**
     * Descripción detallada del contenido visible en la pantalla.
     * Ejemplo: "Hay un campo para escribir el destino y un botón para buscar viajes."
     */
    String getDescripcionPantalla();

    /**
     * Lista de opciones/comandos que el usuario puede usar en esta pantalla.
     * Ejemplo: "Puedes decir: buscar viaje, escuchar rutas frecuentes o volver atrás."
     */
    String getOpcionesPantalla();
}
