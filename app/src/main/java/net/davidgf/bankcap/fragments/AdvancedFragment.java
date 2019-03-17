package net.davidgf.bankcap.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import net.davidgf.bankcap.R;
import net.davidgf.bankcap.adapter.LogAdapter;
import net.davidgf.bankcap.interfaces.ILogCallback;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class AdvancedFragment extends Fragment implements ILogCallback {

    private RecyclerView logView;
    private LogAdapter logAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup viewGroup = (ViewGroup) inflater.inflate(R.layout.fragment_advanced, container,
                false);

        this.logView = viewGroup.findViewById(R.id.log_view);
        this.logView.setLayoutManager(new LinearLayoutManager(getActivity()));

        this.logAdapter = new LogAdapter(getContext());
        this.logView.setAdapter(this.logAdapter);

        writeLogString("AdvancedFragment's view created");
        return viewGroup;
    }

    @Override
    public void writeLogString(String content) {
        if (this.logAdapter == null) {
            return;
        }
        this.logAdapter.appendLog(content);
    }
}
