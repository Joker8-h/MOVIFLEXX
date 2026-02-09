package com.arlys.moviflexx.model;

public class SesionUsuario {

    private static int idUsuario;
    private static int idRol;
    private static String token;

    // ================= ID USUARIO =================
    public static void setIdUsuario(int id) {
        idUsuario = id;
    }

    public static int getIdUsuario() {
        return idUsuario;
    }

    // 🔥 ALIAS PARA NO ROMPER EL CÓDIGO
    public static int getId() {
        return idUsuario;
    }

    // ================= ROL =================
    public static void setIdRol(int rol) {
        idRol = rol;
    }

    public static int getIdRol() {
        return idRol;
    }

    // ================= TOKEN =================
    public static void setToken(String t) {
        token = t;
    }

    public static String getToken() {
        return token;
    }

    // ================= SESIÓN =================
    public static boolean estaLogueado() {
        return idUsuario > 0;
    }

    public static void cerrarSesion() {
        idUsuario = 0;
        idRol = 0;
        token = null;
    }
}
