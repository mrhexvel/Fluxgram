package android.graphics;

public class Canvas {
    private int saves;
    private float translationX;
    private float translationY;

    public int save() {
        return saves++;
    }

    public void restoreToCount(int count) {
        saves = count;
        translationX = 0;
        translationY = 0;
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
