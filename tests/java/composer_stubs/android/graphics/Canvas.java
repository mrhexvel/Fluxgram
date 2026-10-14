package android.graphics;

public class Canvas {
    private int saves;
    private float translationX;
    private float translationY;
    private final float[] savedTranslationX = new float[32];
    private final float[] savedTranslationY = new float[32];

    public int save() {
        savedTranslationX[saves] = translationX;
        savedTranslationY[saves] = translationY;
        saves++;
        return saves;
    }

    public void restoreToCount(int count) {
        int index = count - 1;
        translationX = savedTranslationX[index];
        translationY = savedTranslationY[index];
        saves = index;
    }

    public void translate(float x, float y) {
        translationX += x;
        translationY += y;
    }

    public float getTranslationX() {
        return translationX;
    }

    public float getTranslationY() {
        return translationY;
    }

    public boolean clipRect(int left, int top, int right, int bottom) {
        return true;
    }
}
