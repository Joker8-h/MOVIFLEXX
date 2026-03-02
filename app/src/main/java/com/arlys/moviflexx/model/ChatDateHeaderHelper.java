package com.arlys.moviflexx.model;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;


public class ChatDateHeaderHelper {

    private static final String[] DIAS = {
            "", "Domingo", "Lunes", "Martes", "Miércoles",
            "Jueves", "Viernes", "Sábado"
    };

    private static final String[] MESES = {
            "enero","febrero","marzo","abril","mayo","junio",
            "julio","agosto","septiembre","octubre","noviembre","diciembre"
    };

    /**
     * Intenta parsear la fecha desde varios formatos posibles de la API.
     * Devuelve null si no puede parsear.
     */
    public static Date parsearFecha(String fechaStr) {
        if (fechaStr == null || fechaStr.isEmpty()) return null;

        // Si viene como timestamp numérico (milisegundos)
        try {
            long ts = Long.parseLong(fechaStr);
            return new Date(ts);
        } catch (NumberFormatException ignored) {}

        // Formatos ISO y comunes
        String[] formatos = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd"
        };

        for (String fmt : formatos) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(fmt, Locale.getDefault());
                sdf.setLenient(false);
                return sdf.parse(fechaStr);
            } catch (ParseException ignored) {}
        }

        return null;
    }

    /**
     * Devuelve el encabezado de fecha estilo WhatsApp.
     * Ej: "Hoy", "Ayer", "Lunes", "15 de enero de 2025"
     */
    public static String formatearHeaderFecha(Date fecha) {
        if (fecha == null) return "";

        Calendar hoy    = Calendar.getInstance();
        Calendar msgCal = Calendar.getInstance();
        msgCal.setTime(fecha);

        // Normalizar a medianoche para comparar solo días
        int anioHoy  = hoy.get(Calendar.YEAR);
        int diaHoy   = hoy.get(Calendar.DAY_OF_YEAR);
        int anioMsg  = msgCal.get(Calendar.YEAR);
        int diaMsg   = msgCal.get(Calendar.DAY_OF_YEAR);

        if (anioHoy == anioMsg && diaHoy == diaMsg) {
            return "Hoy";
        }

        Calendar ayer = Calendar.getInstance();
        ayer.add(Calendar.DAY_OF_YEAR, -1);
        if (anioHoy == anioMsg && ayer.get(Calendar.DAY_OF_YEAR) == diaMsg) {
            return "Ayer";
        }

        // Misma semana (últimos 7 días)
        Calendar haceSiete = Calendar.getInstance();
        haceSiete.add(Calendar.DAY_OF_YEAR, -6);
        if (!fecha.before(limpiarHora(haceSiete).getTime())) {
            int diaSemana = msgCal.get(Calendar.DAY_OF_WEEK); // 1=Domingo … 7=Sábado
            return DIAS[diaSemana];
        }

        // Más antiguo: "15 de enero de 2025"
        int dia  = msgCal.get(Calendar.DAY_OF_MONTH);
        int mes  = msgCal.get(Calendar.MONTH);       // 0-indexed
        int anio = msgCal.get(Calendar.YEAR);
        return dia + " de " + MESES[mes] + " de " + anio;
    }

    /**
     * Hora del mensaje: "14:32"
     */
    public static String formatearHora(Date fecha) {
        if (fecha == null) return "";
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(fecha);
    }

    /**
     * Hora desde string (shortcut directo desde Mensaje.getFechaEnvio())
     */
    public static String formatearHora(String fechaStr) {
        return formatearHora(parsearFecha(fechaStr));
    }

    /**
     * Compara si dos fechas son del mismo día calendario.
     * Útil para saber cuándo insertar un header de fecha en el adapter.
     */
    public static boolean mismoDia(Date a, Date b) {
        if (a == null || b == null) return false;
        Calendar ca = Calendar.getInstance(); ca.setTime(a);
        Calendar cb = Calendar.getInstance(); cb.setTime(b);
        return ca.get(Calendar.YEAR)         == cb.get(Calendar.YEAR)
                && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR);
    }

    private static Calendar limpiarHora(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }
}