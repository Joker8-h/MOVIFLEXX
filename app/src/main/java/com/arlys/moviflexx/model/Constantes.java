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

    /**
     * POST /api/usuarios/{idUsuario}/fcm-token
     * Guarda o actualiza el token FCM del usuario en la BD.
     * Se llama al hacer login y cuando Firebase rota el token (onNewToken).
     */
    public static String fcmTokenUsuario(long idUsuario) {
        return BASE_URL + "/api/usuarios/" + idUsuario + "/fcm-token";
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

    public static String pagoPorId(long idPago) {
        return BASE_URL + "/api/pagos/" + idPago;
    }

    public static String pagoDeUsuarioEnViaje(long idViaje, long idUsuario) {
        return BASE_URL + "/api/pagos/viaje/" + idViaje + "/usuario/" + idUsuario;
    }

    public static String pagosPorViaje(long idViaje) {
        return BASE_URL + "/api/pagos/viaje/" + idViaje;
    }

    public static String pagosDeViaje(long idViaje) {
        return pagosPorViaje(idViaje);
    }

    public static String pagoConfirmarPasajero(long idPago) {
        return BASE_URL + "/api/pagos/confirmarPasajero/" + idPago;
    }

    public static String pagoConfirmarConductor(long idPago) {
        return BASE_URL + "/api/pagos/confirmarConductor/" + idPago;
    }


    // ================= CHAT =================
    public static final String CHAT_CONVERSACIONES =
            BASE_URL + "/api/chat/conversaciones";

    public static String chatConversacionesPorUsuario(int idUsuario) {
        return BASE_URL + "/api/chat/conversaciones";
    }

    public static final String CHAT_MENSAJES =
            BASE_URL + "/api/chat/mensajes";

    public static String chatMarcarLeido(Long idMensaje) {
        return BASE_URL + "/api/chat/mensajes/" + idMensaje + "/leido";
    }

    public static String chatMensajesPorConversacion(Long idConversacion) {
        return BASE_URL + "/api/chat/conversaciones/" + idConversacion + "/mensajes";
    }


    // ================= CALIFICACIONES =================
    public static final String CALIFICACIONES =
            BASE_URL + "/api/calificaciones";

    public static String calificacionPromedio(Long idUsuario) {
        return BASE_URL + "/api/calificaciones/" + idUsuario + "/promedio";
    }

    public static String calificacionesPorUsuario(Long idUsuario) {
        return BASE_URL + "/api/calificaciones/" + idUsuario;
    }

    public static String calificacionPorId(Long idCalificacion) {
        return BASE_URL + "/api/calificaciones/" + idCalificacion;
    }

    public static final String CALIFICACIONES_TOP_CONDUCTORES =
            BASE_URL + "/api/calificaciones/top-conductores";

    public static final String CALIFICACIONES_TOP_VIAJEROS =
            BASE_URL + "/api/calificaciones/top-viajeros";


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
    public static String misNotificaciones(int idUsuario) {
        return BASE_URL + "/api/notificaciones/usuario/" + idUsuario;
    }

    public static String notificacionesCount(int idUsuario) {
        return BASE_URL + "/api/notificaciones/usuario/" + idUsuario + "/count";
    }

    public static String notificacionMarcarLeida(long idNotificacion) {
        return BASE_URL + "/api/notificaciones/" + idNotificacion + "/leida";
    }

    public static String notificacionesMarcarTodas(int idUsuario) {
        return BASE_URL + "/api/notificaciones/usuario/" + idUsuario + "/leer-todas";
    }

    /**
     * POST /api/notificaciones/push
     * El cliente Android envía el token FCM del destinatario y el backend
     * llama a la API de Firebase para entregar la notificación push.
     *
     * Body esperado:
     * {
     *   "token":   "<fcmToken del destinatario>",
     *   "titulo":  "Nombre del remitente",
     *   "mensaje": "Texto del mensaje",
     *   "cuerpo":  "Texto del mensaje",   ← alias de mensaje
     *   "tipo":    "MENSAJE"
     * }
     */
    public static final String PUSH_ENVIAR =
            BASE_URL + "/api/notificaciones/push";

    public static String pagoConfirmacion(long idPago) {
        return PAGOS + "/" + idPago + "/confirmacion";
    }

    @Deprecated
    public static final String MIS_NOTIFICACIONES =
            BASE_URL + "/api/notificaciones/mis-notificaciones";

    @Deprecated
    public static final String NOTIFICACIONES_MARCAR_TODAS =
            BASE_URL + "/api/notificaciones/marcar-todas-leidas";

}