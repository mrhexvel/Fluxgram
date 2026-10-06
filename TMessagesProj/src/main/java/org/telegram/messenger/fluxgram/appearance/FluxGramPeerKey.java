package org.telegram.messenger.fluxgram.appearance;

import java.util.Objects;

public final class FluxGramPeerKey {
    public enum Type {
        USER("user"),
        GROUP("group"),
        CHANNEL("channel");

        private final String apiValue;

        Type(String apiValue) {
            this.apiValue = apiValue;
        }

        public String getApiValue() {
            return apiValue;
        }

        public static Type fromApiValue(String value) {
            for (Type type : values()) {
                if (type.apiValue.equals(value)) {
                    return type;
                }
            }

            return null;
        }
    }

    private final Type type;
    private final long id;

    public FluxGramPeerKey(Type type, long id) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }

        this.type = type;
        this.id = id;
    }

    public Type getType() {
        return type;
    }

    public long getId() {
        return id;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof FluxGramPeerKey)) {
            return false;
        }

        FluxGramPeerKey key = (FluxGramPeerKey) object;
        return id == key.id && type == key.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, id);
    }
}
