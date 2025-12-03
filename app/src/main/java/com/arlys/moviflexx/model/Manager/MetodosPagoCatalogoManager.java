package com.arlys.moviflexx.model.Manager;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.arlys.moviflexx.model.ConexionBd;
import com.arlys.moviflexx.model.pojo.MetodosPagoCatalogo;

public class MetodosPagoCatalogoManager {
    private ConexionBd conexionBd;
    private SQLiteDatabase db;

    public MetodosPagoCatalogoManager(Context context) {
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
     * Inserta un nuevo registro en la tabla metodos_pago_catalogo.
     */
    public long insertData(MetodosPagoCatalogo metodo) {
        openBdWr();
        ContentValues values = new ContentValues();
        values.put("nombre", metodo.getNombre());
        values.put("tipo", metodo.getTipo());

        long id = db.insert("metodos_pago_catalogo", null, values);
        closeBd();
        return id;
    }
}