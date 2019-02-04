package fr.dechriste.iot.airboatcontroller;

import android.app.Application;

import timber.log.Timber;

public class AirboatControllerApp extends Application {

    @Override public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }
    }
}
