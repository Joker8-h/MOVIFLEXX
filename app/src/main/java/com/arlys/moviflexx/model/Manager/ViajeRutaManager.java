package com.arlys.moviflexx.model.Manager;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.arlys.moviflexx.model.ConexionBd;
import com.arlys.moviflexx.model.pojo.ViajeRuta;

public class ViajeRutaManager {
    private ConexionBd conexionBd;
    private SQLiteDatabase db;

    public ViajeRutaManager(Context context) {
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
     * Inserta un nuevo registro en la tabla viaje_ruta.
     */
    public long insertData(ViajeRuta viajeRuta) {
        openBdWr();
        ContentValues values = new ContentValues();
        values.put("idViajes", viajeRuta.getIdViajes());
        values.put("idRutas", viajeRuta.getIdRutas());

        long id = db.insert("viaje_ruta", null, values);
        closeBd();
        return id;
    }
}