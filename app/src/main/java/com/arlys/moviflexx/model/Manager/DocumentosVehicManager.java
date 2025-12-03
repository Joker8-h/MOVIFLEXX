package com.arlys.moviflexx.model.Manager;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.arlys.moviflexx.model.ConexionBd;
import com.arlys.moviflexx.model.pojo.DocumentosVehic;

public class DocumentosVehicManager {
    private ConexionBd conexionBd;
    private SQLiteDatabase db;

    public DocumentosVehicManager(Context context) {
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
     * Inserta un nuevo registro en la tabla documentos_vehic.
     */
    public long insertData(DocumentosVehic docVehic) {
        openBdWr();
        ContentValues values = new ContentValues();
        values.put("idDocumento_catalogo", docVehic.getIdDocumentoCatalogo());
        values.put("idVehiculos", docVehic.getIdVehiculos());
        values.put("url_archivo", docVehic.getUrlArchivo());
        values.put("fecha_vencimiento", docVehic.getFechaVencimiento());
        values.put("estado", docVehic.getEstado());
        values.put("estado_final", docVehic.getEstadoFinal());
        values.put("fecha_subida", docVehic.getFechaSubida());

        long id = db.insert("documentos_vehic", null, values);
        closeBd();
        return id;
    }
}