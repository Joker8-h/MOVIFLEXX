package com.arlys.moviflexx.model.Manager;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.arlys.moviflexx.model.ConexionBd;
import com.arlys.moviflexx.model.pojo.DocumentosCatologo;

public class DocumentosCatalogoManager {
    private ConexionBd conexionBd;
    private SQLiteDatabase db;

    public DocumentosCatalogoManager(Context context) {
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
     * Inserta un nuevo registro en la tabla documentos_catalogo.
     */
    public long insertData(DocumentosCatologo documento) {
        openBdWr();
        ContentValues values = new ContentValues();
        values.put("nombre", documento.getNombre());
        values.put("aplica_e_p", documento.getAplicaEP());
        values.put("requiere_vigencia", documento.getRequiereVigencia());

        long id = db.insert("documentos_catalogo", null, values);
        closeBd();
        return id;
    }
}