package org.telegram.messenger.fluxgram.appearance;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

public final class AppearanceHttpClient implements AppearanceRepository.Network {
    private static final int TIMEOUT_MS = 10000;
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private final String baseUrl;
    private final AppearanceApiParser parser;

    public AppearanceHttpClient(String baseUrl, AppearanceApiParser parser) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (!normalized.isEmpty()) {
            URI uri = URI.create(normalized);

            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("appearance API URL must use HTTPS");
            }
        }

        this.baseUrl = normalized;
        this.parser = parser;
    }

    @Override
    public boolean isEnabled() {
        return !baseUrl.isEmpty();
    }

    @Override
    public List<AppearanceApiDto.Result> fetch(List<FluxGramPeerKey> peers) throws Exception {
        if (!isEnabled()) {
            throw new IllegalStateException("appearance API is disabled");
        }
        if (peers.isEmpty() || peers.size() > 100) {
            throw new IllegalArgumentException("batch size must be between 1 and 100");
        }

        if (peers.size() == 1) {
            FluxGramPeerKey peer = peers.get(0);
            String path = "/v1/profiles/" + peer.getType().getApiValue() + "/" + peer.getId();
            return Collections.singletonList(parser.parseSingle(request("GET", path, null)));
        }

        byte[] body = parser.createBatchRequest(peers).getBytes(StandardCharsets.UTF_8);
        return parser.parseBatch(request("POST", "/v1/profiles:batch", body));
    }

    private String request(String method, String path, byte[] body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();

        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");

            if (body != null) {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(body.length);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body);
                }
            }

            int status = connection.getResponseCode();

            if (status < 200 || status >= 300) {
                throw new IllegalStateException("appearance API returned HTTP " + status);
            }

            try (InputStream input = connection.getInputStream()) {
                return read(input);
            }
        } finally {
            connection.disconnect();
        }
    }

    private String read(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;

        while ((count = input.read(buffer)) != -1) {
            total += count;

            if (total > MAX_RESPONSE_BYTES) {
                throw new IllegalStateException("appearance API response is too large");
            }

            output.write(buffer, 0, count);
        }

        return output.toString(StandardCharsets.UTF_8.name());
    }
}
