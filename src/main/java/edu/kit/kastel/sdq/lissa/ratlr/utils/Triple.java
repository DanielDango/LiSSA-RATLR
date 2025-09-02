/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.utils;

/**
 * A utility record class representing an immutable triple of values.
 * @param <F> The type of the first value in the triple
 * @param <S> The type of the second value in the triple
 * @param <T> The type of the third value in the triple
 */
public record Triple<F, S, T>(F first, S second, T third) {}
