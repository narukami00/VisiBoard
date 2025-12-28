package com.visiboard.app.ui.admin;

import android.content.Context;
import android.widget.TextView;

import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;
import com.visiboard.app.R;

import java.util.List;

public class CustomMarkerView extends MarkerView {

    private final TextView tvContent;
    private final List<String> labels;

    public CustomMarkerView(Context context, int layoutResource, List<String> labels) {
        super(context, layoutResource);
        tvContent = findViewById(R.id.tvContent);
        this.labels = labels;
    }

    // callbacks everytime the MarkerView is redrawn, can be used to update the
    // content (user-interface)
    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        String label = "";
        int index = (int) e.getX();
        if (labels != null && index >= 0 && index < labels.size()) {
            label = labels.get(index) + "\n";
        }
        
        if (e instanceof com.github.mikephil.charting.data.BarEntry) {
             tvContent.setText(label + "Count: " + (int)e.getY());
        } else {
             tvContent.setText(label + "Users: " + (int)e.getY());
        }

        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        return new MPPointF(-(getWidth() / 2), -getHeight());
    }
}
