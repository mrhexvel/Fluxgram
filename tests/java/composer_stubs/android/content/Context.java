package android.content;

import android.hardware.SensorManager;

public class Context {
    public static final String SENSOR_SERVICE = "sensor";

    public Object getSystemService(String name) {
        return SENSOR_SERVICE.equals(name) ? SensorManager.getInstance() : null;
    }
}
