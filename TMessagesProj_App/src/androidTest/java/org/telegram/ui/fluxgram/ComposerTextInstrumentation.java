package org.telegram.ui.fluxgram;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Picture;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.graphics.Typeface;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.PixelCopy;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import org.telegram.messenger.plugins.api.ComposerTextCandidate;
import org.telegram.messenger.plugins.api.ComposerTextLayoutSnapshot;
import org.telegram.messenger.plugins.api.ComposerTextPresentation;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.EditTextCaption;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class ComposerTextInstrumentation extends Instrumentation {
    private ProbeField field;
    private AlertDialog dialog;
    private int cases;

    private static final class ProbeField extends EditTextCaption {
        ComposerNativeTextCapture capture;
        ComposerTextPresentation presentation;
        private final Consumer<Canvas> nativeDraw = canvas -> super.onDraw(canvas);

        ProbeField(Activity activity) {
            super(activity, null);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (capture != null && presentation != null) {
                capture.draw(canvas, nativeDraw, presentation);
            } else {
                super.onDraw(canvas);
            }
        }
    }

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        int resultCode = Activity.RESULT_OK;

        try {
            Intent intent = new Intent(Intent.ACTION_MAIN)
                    .setComponent(new ComponentName(getTargetContext().getPackageName(), "org.telegram.ui.LaunchActivity"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Activity activity = startActivitySync(intent);
            onUi(() -> {
                field = new ProbeField(activity);
                field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                field.setTextSize(TypedValue.COMPLEX_UNIT_PX, 48);
                field.setTextColor(Color.WHITE);
                field.setBackgroundColor(Color.BLACK);
                field.setCursorColor(Color.MAGENTA);
                field.setLineColors(0, 0, 0);
                field.setPadding(12, 12, 12, 12);
                field.setGravity(Gravity.BOTTOM | Gravity.LEFT);
                field.setIncludeFontPadding(false);
                field.setSingleLine(false);
                field.setShowSoftInputOnFocus(false);
                field.setFocusableInTouchMode(true);
                dialog = new AlertDialog.Builder(activity).setTitle("Composer text host verification")
                        .setView(field, 64).show();
                field.requestFocus();
            });
            waitForIdleSync();
            Thread.sleep(200);

            onUi(() -> {
                identityAndSuppressionCase();
                staleAndFallbackCase();
                imeCase();
                replayFailureCase();
                layoutSnapshotCase();
            });

            if (Build.VERSION.SDK_INT >= 26) {
                hardwareCase();
            }

            result.putString("stream", "Composer text host PASS: " + cases + " cases\n");
        } catch (Throwable error) {
            result.putString("stream", "Composer text host FAIL: " + Log.getStackTraceString(error));
            resultCode = Activity.RESULT_CANCELED;
        } finally {
            onUi(() -> {
                if (dialog != null) {
                    dialog.dismiss();
                }
            });
        }

        finish(resultCode, result);
    }

    private void identityAndSuppressionCase() {
        setText("HH");
        Editable editable = field.getText();
        UnderlineSpan underline = new UnderlineSpan();
        editable.setSpan(underline, 0, editable.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | Spanned.SPAN_COMPOSING);
        Bitmap original = software();
        ComposerNativeTextCapture capture = ComposerNativeTextCapture.capture(
                field, new ComposerTextCandidate(4, 1, 2), "HH");
        require(capture != null && capture.getRevision() == 4 && capture.getStart() == 1
                && capture.getEnd() == 2, "capture identity");
        field.capture = capture;
        field.presentation = new ComposerTextPresentation(0, 0, 1);
        Bitmap restored = software();
        compare(original, restored, 0);
        field.presentation = new ComposerTextPresentation(0, 0, 0);
        Bitmap suppressed = software();
        require(different(original, suppressed) > 20, "selected text was not suppressed");
        require(field.getText() == editable && editable.toString().equals("HH"), "draw mutated Editable");
        require(editable.getSpanStart(underline) == 0 && editable.getSpanEnd(underline) == 2
                && (editable.getSpanFlags(underline) & Spanned.SPAN_COMPOSING) != 0,
                "draw mutated composing span");
        clearCapture();
        original.recycle();
        restored.recycle();
        suppressed.recycle();
        cases++;
    }

    private void staleAndFallbackCase() {
        setText("H");
        ComposerNativeTextCapture capture = ComposerNativeTextCapture.capture(
                field, new ComposerTextCandidate(5, 0, 1), "H");
        require(capture != null, "basic capture rejected");
        field.setSelection(0);
        require(!capture.isValid(), "selection change retained capture");
        setText("H");
        capture = ComposerNativeTextCapture.capture(field, new ComposerTextCandidate(5, 0, 1), "other");
        require(capture == null, "mismatched text revision was captured");
        capture = ComposerNativeTextCapture.capture(field, new ComposerTextCandidate(5, 0, 1), "H");
        require(capture != null, "replacement capture rejected");
        setText("HH");
        require(!capture.isValid(), "text revision stayed valid after change");
        field.setSelection(0, 2);
        require(ComposerNativeTextCapture.capture(field, new ComposerTextCandidate(6, 0, 2), "HH") == null,
                "selection overlap did not fall back");
        setText("H");
        field.getText().setSpan(new StyleSpan(Typeface.BOLD), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        require(ComposerNativeTextCapture.capture(field, new ComposerTextCandidate(7, 0, 1), "H") == null,
                "styled range did not fall back");
        cases++;
    }

    private void replayFailureCase() {
        setText("H");
        ComposerNativeTextCapture capture = ComposerNativeTextCapture.capture(
                field, new ComposerTextCandidate(10, 0, 1), "H");
        require(capture != null, "failure capture rejected");
        Bitmap image = Bitmap.createBitmap(field.getWidth(), field.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image) {
            @Override
            public void drawPicture(Picture picture) {
                throw new IllegalStateException("Intentional replay failure");
            }
        };
        AtomicInteger nativeDraws = new AtomicInteger();
        capture.draw(canvas, ignored -> nativeDraws.incrementAndGet(),
                new ComposerTextPresentation(0, 0, 1));
        require(nativeDraws.get() == 1 && !capture.isValid(), "replay fallback was not single-pass native");
        image.recycle();
        cases++;
    }

    private void layoutSnapshotCase() {
        setText("ab\n\u05d0\u05d1");
        ComposerTextLayoutSnapshot snapshot = ComposerTextLayoutSnapshots.capture(
                field, 11, "ab\n\u05d0\u05d1");
        require(snapshot != null && snapshot.revision == 11 && snapshot.getLineCount() == 2,
                "layout snapshot lines");
        require(!snapshot.getLine(0).rtl && snapshot.getLine(1).rtl,
                "layout snapshot directions");
        field.getText().setSpan(new StyleSpan(Typeface.BOLD), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        snapshot = ComposerTextLayoutSnapshots.capture(field, 12, "ab\n\u05d0\u05d1");
        require(snapshot != null && snapshot.unsupportedSpans, "unsupported span was not reported");
        cases++;
    }

    private void imeCase() {
        setText("");
        InputConnection input = field.onCreateInputConnection(new EditorInfo());
        require(input != null && input.setComposingText("H", 1), "IME connection");
        layoutField();
        Editable editable = field.getText();
        int composingStart = BaseInputConnection.getComposingSpanStart(editable);
        ComposerNativeTextCapture capture = ComposerNativeTextCapture.capture(
                field, new ComposerTextCandidate(8, 0, 1), "H");
        require(capture != null, "composing capture rejected");
        field.capture = capture;
        field.presentation = new ComposerTextPresentation(0, 0, 0.5f);
        Bitmap frame = software();
        frame.recycle();
        require(field.getText() == editable && BaseInputConnection.getComposingSpanStart(editable) == composingStart,
                "capture changed IME state");
        require(input.setComposingText("HH", 1) && editable.toString().equals("HH"), "IME replacement failed");
        require(!capture.isValid(), "IME replacement retained stale capture");
        require(input.finishComposingText() && input.deleteSurroundingText(1, 0), "IME cleanup failed");
        clearCapture();
        cases++;
    }

    private void hardwareCase() throws Exception {
        onUi(() -> {
            setText("HH");
            field.capture = ComposerNativeTextCapture.capture(field, new ComposerTextCandidate(9, 1, 2), "HH");
            require(field.capture != null, "hardware capture rejected");
            field.presentation = new ComposerTextPresentation(0, 0, 1);
            field.invalidate();
        });
        Bitmap original = hardware(false);
        Bitmap replay = hardware(true);
        compare(original, replay, 3);
        original.recycle();
        replay.recycle();
        onUi(this::clearCapture);
        cases++;
    }

    private void setText(String text) {
        clearCapture();
        field.setText(text);
        field.setSelection(text.length());
        layoutField();
    }

    private void layoutField() {
        field.measure(View.MeasureSpec.makeMeasureSpec(field.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(field.getHeight(), View.MeasureSpec.EXACTLY));
        field.layout(field.getLeft(), field.getTop(), field.getRight(), field.getBottom());
    }

    private void clearCapture() {
        field.capture = null;
        field.presentation = null;
    }

    private Bitmap software() {
        Bitmap image = Bitmap.createBitmap(field.getWidth(), field.getHeight(), Bitmap.Config.ARGB_8888);
        field.draw(new Canvas(image));
        return image;
    }

    private Bitmap hardware(boolean replay) throws Exception {
        onUi(() -> {
            if (!replay) {
                ComposerNativeTextCapture capture = field.capture;
                ComposerTextPresentation presentation = field.presentation;
                clearCapture();
                field.setTag(new Object[]{capture, presentation});
            } else {
                Object[] state = (Object[]) field.getTag();
                field.capture = (ComposerNativeTextCapture) state[0];
                field.presentation = (ComposerTextPresentation) state[1];
                field.setTag(null);
            }

            field.invalidate();
        });
        waitForIdleSync();
        Thread.sleep(70);
        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger status = new AtomicInteger(-1);
        Bitmap image = Bitmap.createBitmap(field.getWidth(), field.getHeight(), Bitmap.Config.ARGB_8888);
        onUi(() -> {
            int[] position = new int[2];
            field.getLocationInWindow(position);
            Rect rect = new Rect(position[0], position[1], position[0] + field.getWidth(), position[1] + field.getHeight());
            PixelCopy.request(dialog.getWindow(), rect, image, value -> {
                status.set(value);
                done.countDown();
            }, new Handler(Looper.getMainLooper()));
        });
        require(done.await(5, TimeUnit.SECONDS) && status.get() == PixelCopy.SUCCESS,
                "PixelCopy failed: " + status.get());
        return image;
    }

    private void onUi(Runnable action) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        runOnMainSync(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                failure.set(error);
            }
        });

        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    private static int different(Bitmap first, Bitmap second) {
        int count = 0;

        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getPixel(x, y) != second.getPixel(x, y)) {
                    count++;
                }
            }
        }

        return count;
    }

    private static void compare(Bitmap first, Bitmap second, int tolerance) {
        require(first.getWidth() == second.getWidth() && first.getHeight() == second.getHeight(), "bitmap size");

        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                int a = first.getPixel(x, y);
                int b = second.getPixel(x, y);
                require(Math.abs(Color.red(a) - Color.red(b)) <= tolerance
                        && Math.abs(Color.green(a) - Color.green(b)) <= tolerance
                        && Math.abs(Color.blue(a) - Color.blue(b)) <= tolerance,
                        "pixel mismatch at " + x + "," + y);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
