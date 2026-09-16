package com.arlys.moviflexx.controller.domiflex;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.arlys.moviflexx.R;
import com.arlys.moviflexx.model.pojo.Producto;

import java.util.List;

public class ProductoAdapter extends RecyclerView.Adapter<ProductoAdapter.VH> {
    public interface OnAdd { void onAdd(Producto p); }
    private List<Producto> list; private OnAdd listener;
    public ProductoAdapter(List<Producto> list, OnAdd l){ this.list=list; this.listener=l; }
    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int v){ return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_producto, p, false));}
    @Override public void onBindViewHolder(@NonNull VH h, int pos){
        Producto p = list.get(pos);
        h.tvNombre.setText(p.getNombre());
        h.tvPrecio.setText("$"+p.getPrecio());
        h.tvCat.setText(p.getCategoria());
        h.btnAdd.setOnClickListener(v->listener.onAdd(p));
    }
    @Override public int getItemCount(){ return list.size();}
    static class VH extends RecyclerView.ViewHolder{ TextView tvNombre, tvPrecio, tvCat; Button btnAdd; VH(View v){ super(v); tvNombre=v.findViewById(R.id.tvNombre); tvPrecio=v.findViewById(R.id.tvPrecio); tvCat=v.findViewById(R.id.tvCategoria); btnAdd=v.findViewById(R.id.btnAdd);}}
}
