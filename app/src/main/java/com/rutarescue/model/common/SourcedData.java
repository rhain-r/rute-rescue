package com.rutarescue.model.common;

/**
 * Wraps a value together with where it came from and how fresh it is, so the screens can
 * always show "source + timestamp" next to the data (DOCX section 11).
 *
 * @param <T> the wrapped data type
 */
public final class SourcedData<T> {

    private final T data;
    private final DataFreshness freshness;
    private final long retrievedAt;
    private final String source;

    public SourcedData(T data, DataFreshness freshness, long retrievedAt, String source) {
        this.data = data;
        this.freshness = freshness == null ? DataFreshness.CACHED : freshness;
        this.retrievedAt = retrievedAt;
        this.source = source == null ? "" : source;
    }

    public T getData() {
        return data;
    }

    public DataFreshness getFreshness() {
        return freshness;
    }

    /** When the data was last confirmed by its source (epoch milliseconds, 0 = unknown). */
    public long getRetrievedAt() {
        return retrievedAt;
    }

    public String getSource() {
        return source;
    }
}
