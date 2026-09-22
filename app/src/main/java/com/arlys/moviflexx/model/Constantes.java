package com.arlys.moviflexx.model;

public class Constantes {
    // DomiFlex BD - migrada de Moviflexx (viajes) a domicilios
    public static String NAME_BD = "DomiFlexBd";
    public static int VERSION_BD = 2;

    // Roles DomiFlex (Fase 1)
    public static String ROL_ADMIN = "ADMIN";
    public static String ROL_REPARTIDOR = "REPARTIDOR";
    public static String ROL_CLIENTE = "CLIENTE";
    public static String ROL_COMERCIO = "COMERCIO";

    // Estados Pedido (Fase 2) - reemplaza EstadoViaje
    public static String PEDIDO_CREADO = "CREADO";
    public static String PEDIDO_ASIGNADO = "ASIGNADO";
    public static String PEDIDO_RECOGIENDO = "RECOGIENDO";
    public static String PEDIDO_EN_CAMINO = "EN_CAMINO";
    public static String PEDIDO_ENTREGADO = "ENTREGADO";
    public static String PEDIDO_CANCELADO = "CANCELADO";

    // Tipos Negocio (Fase 3)
    public static String TIPO_COMIDA = "COMIDA";
    public static String TIPO_FARMACIA = "FARMACIA";
    public static String TIPO_SUPERMERCADO = "SUPERMERCADO";
    public static String TIPO_TIENDA = "TIENDA";
    public static String TIPO_PAQUETERIA = "PAQUETERIA";
    public static String TIPO_OTRO = "OTRO";

    // API Backend DomiFlex
    public static String BASE_URL = "https://domiflex-backend-production.up.railway.app/api/";
    public static String BASE_URL_LOCAL = "http://10.0.2.2:3000/api/";

    // Complementos - IAs y mapas
    public static String OSRM_BASE_URL = "https://domiflex-osrm-production.up.railway.app";
    public static String OPTIMIZER_URL = "https://route-optimizer-production-7e60.up.railway.app";
    public static String IA_PLACA_URL = "https://ia-placa-production.up.railway.app";
    public static String IA_FACIAL_URL = "https://domiflex-facial-production.up.railway.app";
    public static String IA_OBJETOS_URL = "https://ia-objetos-production.up.railway.app";
}
