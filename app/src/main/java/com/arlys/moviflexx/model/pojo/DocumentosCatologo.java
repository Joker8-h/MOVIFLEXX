package com.arlys.moviflexx.model.pojo;

public class DocumentosCatologo {private int idDocumentoCatalogo;
    private String nombre;
    private String aplicaEP; // Usamos String para ENUM
    private int requiereVigencia; // Usamos int para TINYINT(1)

    public void DocumentosCatalogo(int idDocumentoCatalogo, String nombre, String aplicaEP, int requiereVigencia) {
        this.idDocumentoCatalogo = idDocumentoCatalogo;
        this.nombre = nombre;
        this.aplicaEP = aplicaEP;
        this.requiereVigencia = requiereVigencia;
    }

    public void setIdDocumentoCatalogo(int idDocumentoCatalogo) {
        this.idDocumentoCatalogo = idDocumentoCatalogo;
    }
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }
    public void setAplicaEP(String aplicaEP) {
        this.aplicaEP = aplicaEP;
    }
    public void setRequiereVigencia(int requiereVigencia) {
        this.requiereVigencia = requiereVigencia;
    }

    public int getIdDocumentoCatalogo() {
        return idDocumentoCatalogo;
    }
    public String getNombre() {
        return nombre;
    }
    public String getAplicaEP() {
        return aplicaEP;
    }
    public int getRequiereVigencia() {
        return requiereVigencia;
    }
}
