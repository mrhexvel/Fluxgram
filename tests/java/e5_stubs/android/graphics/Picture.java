package android.graphics;

public class Picture {
    public static int beginCount;
    public static int drawCount;

    public static void reset() {
        beginCount = 0;
        drawCount = 0;
    }

    public Canvas beginRecording(int width, int height) {
        beginCount++;
        return new Canvas();
    }

    public void endRecording() {
    }

    public void draw(Canvas canvas) {
        drawCount++;
    }
}
