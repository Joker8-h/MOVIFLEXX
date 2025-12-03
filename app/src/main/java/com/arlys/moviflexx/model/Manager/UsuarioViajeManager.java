package com.arlys.moviflexx.model.Manager;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.arlys.moviflexx.model.ConexionBd;
import com.arlys.moviflexx.model.pojo.UsuarioViaje;

public class UsuarioViajeManager {
    private ConexionBd conexionBd;
    private SQLiteDatabase db;

    public UsuarioViajeManager(Context context) {
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
     * Inserta un nuevo registro en la tabla usuario_viaje.
     */
    public long insertData(UsuarioViaje usuarioViaje) {
        openBdWr();
        ContentValues values = new ContentValues();
        values.put("idUsuarios", usuarioViaje.getIdUsuarios());
        values.put("idViajes", usuarioViaje.getIdViajes());
        values.put("idTarifas", usuarioViaje.getIdTarifas());
        values.put("rol_en_viaje", usuarioViaje.getRolEnViaje());
        values.put("distancia_recorrida", usuarioViaje.getDistanciaRecorrida());
        values.put("precio_final", usuarioViaje.getPrecioFinal());

        long id = db.insert("usuario_viaje", null, values);
        closeBd();
        return id;
    }
}