package android.view;

public class ViewGroup extends View implements ViewParent {
    public void addView(View view) {
        view.setParent(this);
    }

    public void removeView(View view) {
        if (view.getParent() == this) {
            view.setParent(null);
        }
    }
}
