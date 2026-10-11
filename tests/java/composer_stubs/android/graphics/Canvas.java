package android.graphics;

public class Canvas {
    private int saves;

    public int save() {
        return saves++;
    }

    public void restoreToCount(int count) {
        saves = count;
    }

    public boolean clipRect(int left, int top, int right, int bottom) {
        return true;
    }
}
