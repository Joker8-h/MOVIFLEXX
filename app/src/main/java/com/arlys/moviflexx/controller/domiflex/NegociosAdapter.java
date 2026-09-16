package com.arlys.moviflexx.controller.domiflex;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.pojo.Negocios;

import java.util.List;

public class NegociosAdapter extends RecyclerView.Adapter<NegociosAdapter.VH> {
    public interface OnClick { void onClick(Negocios n); }
    private List<Negocios> list;
    private OnClick listener;
    public NegociosAdapter(List<Negocios> list, OnClick l) { this.list = list; this.listener = l; }
    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_negocio, parent, false);
        return new VH(v);
    }
    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        Negocios n = list.get(pos);
        h.tvNombre.setText(n.getNombre());
        h.tvTipo.setText(n.getTipo() + " • " + n.getDireccion());
        h.itemView.setOnClickListener(v -> listener.onClick(n));
    }
    @Override public int getItemCount() { return list.size(); }
    static class VH extends RecyclerView.ViewHolder {
        TextView tvNombre, tvTipo;
        VH(View v) { super(v); tvNombre = v.findViewById(R.id.tvNombre); tvTipo = v.findViewById(R.id.tvTipo); }
    }
}
