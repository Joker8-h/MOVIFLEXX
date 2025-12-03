package com.arlys.moviflexx.model.Manager;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.arlys.moviflexx.model.ConexionBd;
import com.arlys.moviflexx.model.pojo.Pagos;

public class PagosManager {
    private ConexionBd conexionBd;
    private SQLiteDatabase db;

    public PagosManager(Context context) {
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
     * Inserta un nuevo registro en la tabla pagos.
     */
    public long insertData(Pagos pago) {
        openBdWr();
        ContentValues values = new ContentValues();
        values.put("idMetodos_pago", pago.getIdMetodosPago());
        values.put("idViajes", pago.getIdViajes());
        values.put("idUsuarios", pago.getIdUsuarios());
        values.put("monto", pago.getMonto());
        values.put("fecha_pago", pago.getFechaPago());
        values.put("estado", pago.getEstado());

        long id = db.insert("pagos", null, values);
        closeBd();
        return id;
    }
}