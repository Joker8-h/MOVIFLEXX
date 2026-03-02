package com.arlys.moviflexx.model;

public class Constantes {

    // ================= BASE BACKEND PRINCIPAL =================
    public static final String BASE_URL =
            "https://backendmovi-production-c657.up.railway.app";


    // ================= AUTH =================
    public static final String LOGIN =
            BASE_URL + "/api/auth/login";

    public static final String REGISTER =
            BASE_URL + "/api/auth/registro";

    public static final String LOGIN_GOOGLE =
            BASE_URL + "/api/auth/google";

    public static String authPorId(Long idUsuario) {
        return BASE_URL + "/api/auth/" + idUsuario;
    }


    // ================= USUARIOS =================
    public static final String USUARIOS =
            BASE_URL + "/api/usuarios";

    public static String vehiculoPorId(Long idVehiculo) {
        return BASE_URL + "/api/vehiculos/" + idVehiculo;
    }

    public static String usuarioDetalle(Long idUsuario) {
        return BASE_URL + "/api/usuarios/" + idUsuario;
    }


    // ================= ROLES =================
    public static final String ROLES =
            BASE_URL + "/api/roles";

    public static String rolPorId(Long idRol) {
        return BASE_URL + "/api/roles/" + idRol;
    }


    // ================= VEHICULOS =================
    public static final String VEHICULOS =
            BASE_URL + "/api/vehiculos";

    public static final String MIS_VEHICULOS =
            BASE_URL + "/api/vehiculos/mis-vehiculos";

    public static String vehiculoEliminar(Long idVehiculo) {
        return BASE_URL + "/api/vehiculos/" + idVehiculo;
    }


    // ================= RUTAS =================
    public static final String RUTAS =
            BASE_URL + "/api/rutas";

    public static final String MIS_RUTAS =
            BASE_URL + "/api/rutas/mis-rutas";

    public static String rutaPorId(Long idRuta) {
        return BASE_URL + "/api/rutas/" + idRuta;
    }

    public static String rutaParadas(Long idRuta) {
        return BASE_URL + "/api/rutas/" + idRuta + "/paradas";
    }


    // ================= VIAJES =================
    public static final String VIAJES =
            BASE_URL + "/api/viajes";

    public static String buscarViajes(String origen, String destino, String fecha) {
        return BASE_URL + "/api/viajes/buscar?origen=" + origen +
                "&destino=" + destino +
                "&fecha=" + fecha;
    }

    public static final String MIS_VIAJES =
            BASE_URL + "/api/viajes/mis-viajes";

    public static String buscarViajes() {
        return BASE_URL + "/api/viajes/buscar";
    }

    public static String viajePorId(Long idViaje) {
        return BASE_URL + "/api/viajes/" + idViaje;
    }

    public static String viajeIniciar(Long idViaje) {
        return BASE_URL + "/api/viajes/" + idViaje + "/iniciar";
    }

    public static String viajeFinalizar(Long idViaje) {
        return BASE_URL + "/api/viajes/" + idViaje + "/finalizar";
    }

    public static String viajeCancelar(Long idViaje) {
        return BASE_URL + "/api/viajes/" + idViaje + "/cancelar";
    }

    public static String viajeReservasDetalle(Long idViaje) {
        return BASE_URL + "/api/viajes/" + idViaje + "/reservas-detalle";
    }


    // ================= RESERVAS =================
    public static final String RESERVAS =
            BASE_URL + "/api/reservas";

    public static final String MIS_RESERVAS =
            BASE_URL + "/api/reservas/mis-reservas";

    public static String reservaCancelar(Long idReserva) {
        return BASE_URL + "/api/reservas/" + idReserva + "/cancelar";
    }


    // ================= PAGOS =================
    public static final String PAGOS =
            BASE_URL + "/api/pagos";


    // ================= CHAT =================
    // Rutas reales del backend (Node/Express con verificarToken middleware)

    public static final String CHAT_CONVERSACIONES =
            BASE_URL + "/api/chat/conversaciones";

    /**
     * El backend ya filtra por token JWT automáticamente (usa verificarToken).
     * NO necesita idUsuario en la URL — el middleware lo extrae del token.
     * Usar siempre CHAT_CONVERSACIONES directamente.
     */
    public static String chatConversacionesPorUsuario(int idUsuario) {
        // El backend filtra por token, no por parámetro — ignoramos idUsuario
        return BASE_URL + "/api/chat/conversaciones";
    }

    public static final String CHAT_MENSAJES =
            BASE_URL + "/api/chat/mensajes";

    public static String chatMensajesPorConversacion(Long idConversacion) {
        return BASE_URL + "/api/chat/conversaciones/" + idConversacion + "/mensajes";
    }

    /**
     * ⚠️ El backend NO tiene endpoint para marcar mensajes individuales como leídos.
     * Estas rutas no existen — no usar hasta que el backend las implemente.
     * Por ahora se omiten para evitar los 404.
     */
    // chatMarcarLeido y chatMensajeMarcarLeido eliminados (404 en backend)


    // ================= CALIFICACIONES =================
    public static final String CALIFICACIONES =
            BASE_URL + "/api/calificaciones";

    public static String calificacionPromedio(Long idUsuario) {
        return BASE_URL + "/api/calificaciones/" + idUsuario + "/promedio";
    }


    // ================= SUSCRIPCIONES =================
    public static final String PLANES_SUSCRIPCION =
            BASE_URL + "/api/suscripciones/planes";

    public static final String SUSCRIBIRSE_PLAN =
            BASE_URL + "/api/suscripciones/suscribirse";

    public static final String MI_SUSCRIPCION =
            BASE_URL + "/api/suscripciones/mi-suscripcion";


    // ================= PARADAS =================
    public static final String PARADAS =
            BASE_URL + "/api/paradas";

    public static String paradasPorRuta(Long idRuta) {
        return BASE_URL + "/api/paradas/ruta/" + idRuta;
    }

    public static String paradaPorId(Long idParada) {
        return BASE_URL + "/api/paradas/" + idParada;
    }


    // ================= VIAJE TRAMOS =================
    public static final String VIAJE_TRAMOS =
            BASE_URL + "/api/viaje-tramos";

    public static final String VIAJE_TRAMOS_GENERAR =
            BASE_URL + "/api/viaje-tramos/generar";

    public static final String VIAJE_TRAMOS_OCUPACION =
            BASE_URL + "/api/viaje-tramos/ocupacion";

    public static final String VIAJE_TRAMOS_DISPONIBILIDAD =
            BASE_URL + "/api/viaje-tramos/verificar-disponibilidad";

    public static String viajeTramosPorViaje(Long idViaje) {
        return BASE_URL + "/api/viaje-tramos/viaje/" + idViaje;
    }

    public static String viajeTramoEspecifico(Long idViaje, Long idInicio, Long idFin) {
        return BASE_URL + "/api/viaje-tramos/" + idViaje + "/" + idInicio + "/" + idFin;
    }


    // ================= DOCUMENTACION =================
    public static final String DOCUMENTOS_SUBIR =
            BASE_URL + "/api/documentacion/documentacion_subir";

    public static final String MIS_DOCUMENTOS =
            BASE_URL + "/api/documentacion/documentacion_mis";


    // ================= NOTIFICACIONES =================
    // Rutas reales del backend Node/Express:
    //   GET    /api/notificaciones/usuario/:idUsuario
    //   GET    /api/notificaciones/usuario/:idUsuario/count
    //   PATCH  /api/notificaciones/:id/leida
    //   PATCH  /api/notificaciones/usuario/:idUsuario/leer-todas
    //   DELETE /api/notificaciones/:id

    /**
     * Obtener todas las notificaciones de un usuario.
     * Requiere el ID del usuario en la URL (el backend NO usa token para filtrar).
     */
    public static String misNotificaciones(int idUsuario) {
        return BASE_URL + "/api/notificaciones/usuario/" + idUsuario;
    }

    /**
     * Contador de notificaciones no leídas.
     */
    public static String notificacionesCount(int idUsuario) {
        return BASE_URL + "/api/notificaciones/usuario/" + idUsuario + "/count";
    }

    /**
     * Marcar una notificación como leída.
     * PATCH /api/notificaciones/:id/leida
     */
    public static String notificacionMarcarLeida(long idNotificacion) {
        return BASE_URL + "/api/notificaciones/" + idNotificacion + "/leida";
    }

    /**
     * Marcar todas las notificaciones de un usuario como leídas.
     * PATCH /api/notificaciones/usuario/:idUsuario/leer-todas
     */
    public static String notificacionesMarcarTodas(int idUsuario) {
        return BASE_URL + "/api/notificaciones/usuario/" + idUsuario + "/leer-todas";
    }

    /**
     * @deprecated Usar misNotificaciones(idUsuario) — esta URL devuelve 404
     */
    @Deprecated
    public static final String MIS_NOTIFICACIONES =
            BASE_URL + "/api/notificaciones/mis-notificaciones";

    /**
     * @deprecated Usar notificacionesMarcarTodas(idUsuario) — esta URL devuelve 404
     */
    @Deprecated
    public static final String NOTIFICACIONES_MARCAR_TODAS =
            BASE_URL + "/api/notificaciones/marcar-todas-leidas";

}