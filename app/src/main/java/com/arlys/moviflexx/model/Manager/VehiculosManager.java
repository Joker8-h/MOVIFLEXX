package com.arlys.moviflexx.model.Manager;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.arlys.moviflexx.model.ConexionBd;
import com.arlys.moviflexx.model.pojo.Vehiculos;

public class VehiculosManager {
    private ConexionBd conexionBd;
    private SQLiteDatabase db;

    public VehiculosManager(Context context) {
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
     * Inserta un nuevo registro en la tabla vehiculos.
     */
    public long insertData(Vehiculos vehiculo) {
        openBdWr();
        ContentValues values = new ContentValues();
        values.put("tipoVehicular", vehiculo.getTipoVehicular());
        values.put("placa", vehiculo.getPlaca());
        values.put("modelo", vehiculo.getModelo());
        values.put("color", vehiculo.getColor());

        long id = db.insert("vehiculos", null, values);
        closeBd();
        return id;
    }
}