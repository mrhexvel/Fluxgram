package android.view;

public class View {
    private ViewParent parent;

    public ViewParent getParent() {
        return parent;
    }

    void setParent(ViewParent parent) {
        this.parent = parent;
    }
}
