package android.graphics;

public class Picture {
    public static int beginCount;
    public static int drawCount;
    public static float[] drawTranslationX = new float[8];
    public static float[] drawTranslationY = new float[8];

    public static void reset() {
        beginCount = 0;
        drawCount = 0;
        drawTranslationX = new float[8];
        drawTranslationY = new float[8];
    }

    public Canvas beginRecording(int width, int height) {
        beginCount++;
        return new Canvas();
    }

    public void endRecording() {
    }

    public void draw(Canvas canvas) {
        if (drawCount < drawTranslationX.length) {
            drawTranslationX[drawCount] = canvas.getTranslationX();
            drawTranslationY[drawCount] = canvas.getTranslationY();
        }

        drawCount++;
    }
}
