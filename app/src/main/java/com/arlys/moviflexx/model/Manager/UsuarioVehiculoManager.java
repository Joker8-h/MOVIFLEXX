package com.arlys.moviflexx.model.Manager;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.arlys.moviflexx.model.ConexionBd;
import com.arlys.moviflexx.model.pojo.UsuarioVehiculo;

public class UsuarioVehiculoManager {
    private ConexionBd conexionBd;
    private SQLiteDatabase db;

    public UsuarioVehiculoManager(Context context) {
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
     * Inserta un nuevo registro en la tabla usuario_vehiculo.
     */
    public long insertData(UsuarioVehiculo usuarioVehiculo) {
        openBdWr();
        ContentValues values = new ContentValues();
        values.put("idUsuarios", usuarioVehiculo.getIdUsuarios());
        values.put("idVehiculos", usuarioVehiculo.getIdVehiculos());

        long id = db.insert("usuario_vehiculo", null, values);
        closeBd();
        return id;
    }
}