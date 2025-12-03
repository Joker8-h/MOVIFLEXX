package com.arlys.moviflexx.model.Manager;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.arlys.moviflexx.model.ConexionBd;
import com.arlys.moviflexx.model.pojo.Calificaciones;

public class CalificacionesManager {
    private ConexionBd conexionBd;
    private SQLiteDatabase db;

    public CalificacionesManager(Context context) {
        conexionBd = new ConexionBd(context);
    }

    public void openBdWr() {
        db = conexionBd.getWritableDatabase();
    }

    public void openBdRd() {
        db = conexionBd.getReadableDatabase();
    };

    public void closeBd() {
        db.close();
    }

    /**
     * Inserta un nuevo registro en la tabla calificaciones.
     */
    public long insertData(Calificaciones calificacion) {
        openBdWr();
        ContentValues values = new ContentValues();
        values.put("idUsuarios", calificacion.getIdUsuarios());
        values.put("idViajes", calificacion.getIdViajes());
        values.put("puntuacion", calificacion.getPuntuacion());
        values.put("comentario", calificacion.getComentario());
        values.put("fecha_calificacion", calificacion.getFechaCalificacion());

        long id = db.insert("calificaciones", null, values);
        closeBd();
        return id;
    }
}