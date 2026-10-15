package com.rutarescue.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One generic RecyclerView adapter for all simple lists (generics + a small interface).
 * Each screen passes the row layout and a Binder that fills one row.
 *
 * @param <T> the type of item shown in each row
 */
public final class SimpleListAdapter<T> extends RecyclerView.Adapter<SimpleListAdapter.Holder> {

    /** Fills one row view with one item. */
    public interface Binder<T> {
        void bind(View row, T item, int position);
    }

    private final int layoutRes;
    private final Binder<T> binder;
    private final List<T> items = new ArrayList<>();

    public SimpleListAdapter(int layoutRes, Binder<T> binder) {
        this.layoutRes = layoutRes;
        this.binder = binder;
    }

    public void submit(List<T> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    public List<T> getItems() {
        return Collections.unmodifiableList(items);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        binder.bind(holder.itemView, items.get(position), position);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        Holder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
