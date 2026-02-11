package com.arlys.moviflexx.model;

public class Constantes {

    // ================= AUTH GOOGLE =================


    // ================= BASE =================
    public static final String BASE_URL =
            "https://backendmovi-production.up.railway.app";

    // ================= AUTH =================
    public static final String LOGIN =
            BASE_URL + "/api/auth/login";

    public static final String REGISTER =
            BASE_URL + "/api/auth/registro";

    public static final String USUARIO_POR_ID =
            BASE_URL + "/api/auth/"; // + id (perfil propio / conductor)

    // ================= AUTH GOOGLE =================
    public static final String LOGIN_GOOGLE =
            BASE_URL + "/api/auth/google";

    // ================= ROLES =================
    // (solo lectura, permitido)
    public static final String ROLES =
            BASE_URL + "/api/roles";

    public static final String ROL_POR_ID =
            BASE_URL + "/api/roles/"; // + id

    // ================= VEHICULOS =================
    public static final String VEHICULOS =
            BASE_URL + "/api/vehiculos";

    public static final String MIS_VEHICULOS =
            BASE_URL + "/api/vehiculos/mis-vehiculos";

    public static final String VEHICULO_ELIMINAR =
            BASE_URL + "/api/vehiculos/"; // + id

    // ================= RUTAS =================
    public static final String RUTAS =
            BASE_URL + "/api/rutas";

    public static final String MIS_RUTAS =
            BASE_URL + "/api/rutas/mis-rutas";

    public static final String RUTA_POR_ID =
            BASE_URL + "/api/rutas/"; // + id

    public static final String RUTA_PARADAS =
            BASE_URL + "/api/rutas/"; // + id + /paradas

    // ================= VIAJES =================
    public static final String VIAJES =
            BASE_URL + "/api/viajes";

    public static final String BUSCAR_VIAJES =
            BASE_URL + "/api/viajes/buscar";

    public static final String MIS_VIAJES =
            BASE_URL + "/api/viajes/mis-viajes";

    public static final String VIAJE_POR_ID =
            BASE_URL + "/api/viajes/"; // + id

    public static final String VIAJE_INICIAR =
            BASE_URL + "/api/viajes/"; // + id + /iniciar

    public static final String VIAJE_FINALIZAR =
            BASE_URL + "/api/viajes/"; // + id + /finalizar

    public static final String VIAJE_CANCELAR =
            BASE_URL + "/api/viajes/"; // + id + /cancelar

    // ================= RESERVAS =================
    public static final String RESERVAS =
            BASE_URL + "/api/reservas";

    public static final String MIS_RESERVAS =
            BASE_URL + "/api/reservas/mis-reservas";

    public static final String RESERVA_CANCELAR =
            BASE_URL + "/api/reservas/"; // + idViaje + /cancelar

    // ================= PAGOS =================
    public static final String PAGOS =
            BASE_URL + "/api/pagos";

    // ================= CHAT =================
    public static final String CHAT_CONVERSACIONES =
            BASE_URL + "/api/chat/conversaciones";

    public static final String CHAT_MENSAJES =
            BASE_URL + "/api/chat/mensajes";

    public static final String CHAT_MENSAJES_POR_CONVERSACION =
            BASE_URL + "/api/chat/conversaciones/"; // + id + /mensajes

    // ================= CALIFICACIONES =================
    public static final String CALIFICACIONES =
            BASE_URL + "/api/calificaciones";

    public static final String CALIFICACION_PROMEDIO =
            BASE_URL + "/api/calificaciones/"; // + idUsuario + /promedio

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

    public static final String PARADAS_POR_RUTA =
            BASE_URL + "/api/paradas/ruta/"; // + idRuta

    public static final String PARADA_POR_ID =
            BASE_URL + "/api/paradas/"; // + id

    // ================= VIAJE TRAMOS =================
    public static final String VIAJE_TRAMOS_POR_VIAJE =
            BASE_URL + "/api/viaje-tramos/viaje/"; // + idViaje

    public static final String VIAJE_TRAMO_ESPECIFICO =
            BASE_URL + "/api/viaje-tramos/"; // + idViaje/idInicio/idFin

    public static final String VIAJE_TRAMOS =
            BASE_URL + "/api/viaje-tramos";

    public static final String VIAJE_TRAMOS_GENERAR =
            BASE_URL + "/api/viaje-tramos/generar";

    public static final String VIAJE_TRAMOS_OCUPACION =
            BASE_URL + "/api/viaje-tramos/ocupacion";

    public static final String VIAJE_TRAMOS_DISPONIBILIDAD =
            BASE_URL + "/api/viaje-tramos/verificar-disponibilidad";

    // ================= DOCUMENTACION =================
    public static final String DOCUMENTOS_SUBIR =
            BASE_URL + "/api/documentacion/documentacion_subir";

    public static final String MIS_DOCUMENTOS =
            BASE_URL + "/api/documentacion/documentacion_mis";
}
