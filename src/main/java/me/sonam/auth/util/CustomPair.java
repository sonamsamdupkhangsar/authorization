package me.sonam.auth.util;

import java.util.Objects;

/**
 * A generic immutable pair of two elements.
 * Similar to org.springframework.data.util.Pair.
 *
 * @param <S> the type of the first element (first/key)
 * @param <T> the type of the second element (second/value)
 */
public final class CustomPair<S, T> {

    private  S first;
    private  T second;

    /**
     * Creates a new Pair for the given elements.
     * @param first the first element, must not be null.
     * @param second the second element, must not be null.
     */
    public CustomPair(S first, T second) {
        this.first = Objects.requireNonNull(first, "First element must not be null");
        this.second = Objects.requireNonNull(second, "Second element must not be null");
    }

    public CustomPair() {
    }


    /**
     * Returns the first element of the Pair.
     * @return the first element.
     */
    public S getFirst() {
        return first;
    }

    /**
     * Returns the second element of the Pair.
     * @return the second element.
     */
    public T getSecond() {
        return second;
    }

    /**
     * Factory method for creating a new Pair.
     * @param first the first element.
     * @param second the second element.
     * @param <S> the type of the first element.
     * @param <T> the type of the second element.
     * @return a new Pair instance.
     */
    public static <S, T> CustomPair<S, T> of(S first, T second) {
        return new CustomPair<>(first, second);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomPair<?, ?> that = (CustomPair<?, ?>) o;
        return Objects.equals(first, that.first) &&
                Objects.equals(second, that.second);
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second);
    }

    @Override
    public String toString() {
        return "CustomPair{" +
                "first=" + first +
                ", second=" + second +
                '}';
    }
}
