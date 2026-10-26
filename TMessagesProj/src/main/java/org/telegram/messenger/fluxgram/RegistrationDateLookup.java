package org.telegram.messenger.fluxgram;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class RegistrationDateLookup {
    private static final String ASSET_PATH = "fluxgram/registration/id_date.json";
    private static final int MAX_ANCHORS = 512;

    private static boolean loaded;
    private static long[][] anchors;

    public enum Kind {
        EXACT,
        APPROXIMATE,
        AFTER
    }

    public static final class Result {
        public final Kind kind;
        public final long timestamp;

        private Result(Kind kind, long timestamp) {
            this.kind = kind;
            this.timestamp = timestamp;
        }
    }

    public static Result estimate(long userId) {
        if (userId <= 0) {
            return null;
        }

        long[][] values = getAnchors();
        if (values.length == 0) {
            return null;
        }
        if (userId <= values[0][0]) {
            return new Result(Kind.EXACT, values[0][1]);
        }

        for (int index = 1; index < values.length; index++) {
            long[] previous = values[index - 1];
            long[] next = values[index];
            if (userId <= next[0]) {
                double progress = (double) (userId - previous[0]) / (next[0] - previous[0]);
                long timestamp = Math.round(previous[1] + progress * (next[1] - previous[1]));
                return new Result(Kind.APPROXIMATE, timestamp);
            }
        }

        return new Result(Kind.AFTER, values[values.length - 1][1]);
    }

    private static synchronized long[][] getAnchors() {
        if (loaded) {
            return anchors;
        }

        loaded = true;
        anchors = loadAnchors();
        return anchors;
    }

    private static long[][] loadAnchors() {
        try (InputStream input = ApplicationLoader.applicationContext.getAssets().open(ASSET_PATH);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }

            JSONObject root = new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
            JSONArray data = root.getJSONArray("data");
            if (data.length() < 2 || data.length() > MAX_ANCHORS) {
                return new long[0][0];
            }

            long[][] result = new long[data.length()][2];
            long previousId = 0;
            for (int index = 0; index < data.length(); index++) {
                JSONObject value = data.getJSONObject(index);
                long id = value.getLong("id");
                long date = value.getLong("date");
                if (id <= previousId || date <= 0) {
                    return new long[0][0];
                }

                result[index][0] = id;
                result[index][1] = date;
                previousId = id;
            }
            return result;
        } catch (Exception ignore) {
            return new long[0][0];
        }
    }

    private RegistrationDateLookup() {
    }
}
