package com.caverock.androidsvg;

import android.graphics.Canvas;
import android.graphics.RectF;

public final class SVG {
    public static int calls;
    public static String lastXml;
    public static float lastDocumentWidth;
    public static float lastDocumentHeight;

    private final String xml;

    private SVG(String xml) {
        this.xml = xml;
    }

    public static SVG getFromString(String xml) throws SVGParseException {
        calls++;
        lastXml = xml;

        if (xml.contains("parse-error")) {
            throw new SVGParseException();
        }
        if (xml.contains("out-of-memory")) {
            throw new OutOfMemoryError();
        }
        if (xml.contains("stack-overflow")) {
            throw new StackOverflowError();
        }

        return new SVG(xml);
    }

    public void setDocumentWidth(float width) {
        lastDocumentWidth = width;
    }

    public void setDocumentHeight(float height) {
        lastDocumentHeight = height;
    }

    public void renderToCanvas(Canvas canvas, RectF viewport) {
        if (xml.contains("render-error")) {
            throw new IllegalStateException();
        }
        if (!xml.contains("transparent")) {
            canvas.markVisible();
        }
    }
}
