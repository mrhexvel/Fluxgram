package org.telegram.messenger.plugins;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import org.telegram.messenger.ApplicationLoader;
import org.fluxgram.ComposerMotionSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashSet;

final class ComposerMotionSensors {
    interface Client {
        void onMotion(ComposerMotionSnapshot snapshot);
    }

    private static final LinkedHashSet<Client> clients = new LinkedHashSet<>();
    private static SensorManager manager;
    private static Sensor sensor;
    private static int source;
    private static boolean registered;

    private static final SensorEventListener listener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (!registered || event == null || event.sensor != sensor
                    || event.values == null || event.values.length < 3) {
                return;
            }

            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                return;
            }

            ComposerMotionSnapshot snapshot = new ComposerMotionSnapshot(
                    source, event.timestamp, event.accuracy, x, y, z);

            for (Client client : new ArrayList<>(clients)) {
                client.onMotion(snapshot);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private ComposerMotionSensors() {
    }

    static boolean setEnabled(Client client, boolean enabled) {
        if (!enabled) {
            clients.remove(client);

            if (clients.isEmpty()) {
                stop();
            }

            return true;
        }

        if (clients.contains(client)) {
            return registered;
        }

        clients.add(client);

        if (!registered && !start()) {
            clients.remove(client);
            return false;
        }

        return true;
    }

    private static boolean start() {
        Context context = ApplicationLoader.applicationContext;

        if (context == null) {
            return false;
        }

        Object service = context.getSystemService(Context.SENSOR_SERVICE);

        if (!(service instanceof SensorManager)) {
            return false;
        }

        manager = (SensorManager) service;
        Sensor gravity = manager.getDefaultSensor(Sensor.TYPE_GRAVITY);

        if (register(gravity, ComposerMotionSnapshot.SOURCE_GRAVITY)) {
            return true;
        }

        Sensor accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if (register(accelerometer, ComposerMotionSnapshot.SOURCE_ACCELEROMETER)) {
            return true;
        }

        manager = null;
        return false;
    }

    private static boolean register(Sensor candidate, int candidateSource) {
        if (candidate == null) {
            return false;
        }

        try {
            if (!manager.registerListener(listener, candidate, SensorManager.SENSOR_DELAY_UI)) {
                return false;
            }
        } catch (RuntimeException error) {
            Log.e("FluxGramComposer", "Motion sensor registration failed", error);
            return false;
        }

        sensor = candidate;
        source = candidateSource;
        registered = true;
        return true;
    }

    private static void stop() {
        try {
            if (manager != null && registered) {
                manager.unregisterListener(listener, sensor);
            }
        } catch (RuntimeException error) {
            Log.e("FluxGramComposer", "Motion sensor cleanup failed", error);
        } finally {
            manager = null;
            sensor = null;
            source = 0;
            registered = false;
        }
    }
}
