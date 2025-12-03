package com.arlys.moviflexx.model.Manager;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.arlys.moviflexx.model.ConexionBd;
import com.arlys.moviflexx.model.pojo.Viajes;

public class ViajesManager {
    private ConexionBd conexionBd;
    private SQLiteDatabase db;

    public ViajesManager(Context context) {
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
     * Inserta un nuevo registro en la tabla viajes.
     */
    public long insertData(Viajes viaje) {
        openBdWr();
        ContentValues values = new ContentValues();
        values.put("idVehiculos", viaje.getIdVehiculos());
        values.put("origen", viaje.getOrigen());
        values.put("destino", viaje.getDestino());
        values.put("fecha_hora_salida", viaje.getFechaHoraSalida());
        values.put("cupos_totales", viaje.getCuposTotales());
        values.put("cupos_disponibles", viaje.getCuposDisponibles());
        values.put("estado", viaje.getEstado());

        long id = db.insert("viajes", null, values);
        closeBd();
        return id;
    }
}