package net.davidgf.bankcap.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import net.davidgf.bankcap.R;

import java.util.LinkedList;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.ViewHolder> {

    private final Context context;
    private final LinkedList<String> logItems = new LinkedList<>();

    public LogAdapter(Context context) {
        this.context = context;
    }

    public void appendLog(String content) {
        logItems.add(content);
        this.notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(context).inflate(R.layout.adapter_item_log, parent,
                false);
        return new LogAdapter.ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.logRow.setText(logItems.get(position));
    }

    @Override
    public int getItemCount() {
        return logItems.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private TextView logRow;

        ViewHolder(View view) {
            super(view);
            this.logRow = view.findViewById(R.id.log_row);
        }
    }
}
