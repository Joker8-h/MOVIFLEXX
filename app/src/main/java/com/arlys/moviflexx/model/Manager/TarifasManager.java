package com.arlys.moviflexx.model.Manager;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.arlys.moviflexx.model.ConexionBd;
import com.arlys.moviflexx.model.pojo.Tarifas;

public class TarifasManager {
    private ConexionBd conexionBd;
    private SQLiteDatabase db;

    public TarifasManager(Context context) {
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
     * Inserta un nuevo registro en la tabla tarifas.
     */
    public long insertData(Tarifas tarifa) {
        openBdWr();
        ContentValues values = new ContentValues();
        values.put("costo_base", tarifa.getCostoBase());
        values.put("costo_por_km", tarifa.getCostoPorKm());
        values.put("fecha_vigencia", tarifa.getFechaVigencia());

        long id = db.insert("tarifas", null, values);
        closeBd();
        return id;
    }
}