package android.hardware;

public final class SensorManager {
    public static final int SENSOR_DELAY_UI = 2;
    private static final SensorManager INSTANCE = new SensorManager();
    private final Sensor gravity = new Sensor(Sensor.TYPE_GRAVITY);
    private final Sensor accelerometer = new Sensor(Sensor.TYPE_ACCELEROMETER);
    private SensorEventListener listener;
    private Sensor registeredSensor;
    public boolean gravityAvailable = true;
    public boolean accelerometerAvailable = true;
    public boolean registerSuccess = true;
    public boolean gravityRegisterSuccess = true;
    public boolean failUnregister;
    public int registerCalls;
    public int unregisterCalls;

    public static SensorManager getInstance() {
        return INSTANCE;
    }

    public Sensor getDefaultSensor(int type) {
        if (type == Sensor.TYPE_GRAVITY) {
            return gravityAvailable ? gravity : null;
        }

        if (type == Sensor.TYPE_ACCELEROMETER) {
            return accelerometerAvailable ? accelerometer : null;
        }

        return null;
    }

    public boolean registerListener(SensorEventListener value, Sensor sensor, int delay) {
        registerCalls++;

        if (!registerSuccess || sensor.getType() == Sensor.TYPE_GRAVITY && !gravityRegisterSuccess) {
            return false;
        }

        listener = value;
        registeredSensor = sensor;
        return true;
    }

    public void unregisterListener(SensorEventListener value, Sensor sensor) {
        unregisterCalls++;

        if (failUnregister) {
            failUnregister = false;
            throw new IllegalStateException("intentional unregister failure");
        }

        if (listener == value && registeredSensor == sensor) {
            listener = null;
            registeredSensor = null;
        }
    }

    public void dispatch(long timestamp, int accuracy, float x, float y, float z) {
        if (listener == null || registeredSensor == null) {
            return;
        }

        SensorEvent event = new SensorEvent();
        event.sensor = registeredSensor;
        event.timestamp = timestamp;
        event.accuracy = accuracy;
        event.values = new float[] {x, y, z};
        listener.onSensorChanged(event);
    }
}
