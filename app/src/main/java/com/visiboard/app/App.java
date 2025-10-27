package com.visiboard.app;

import android.app.Application;
import org.maplibre.android.MapLibre;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        MapLibre.getInstance(this);
        // init logging, analytics (opt-in later), or singletons
    }
}
