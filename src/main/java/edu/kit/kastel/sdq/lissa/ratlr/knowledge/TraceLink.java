/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.knowledge;

import org.jetbrains.annotations.NotNull;

/**
 * Represents a trace link between two {@link Element Elements} in the LiSSA framework.
 * A trace link establishes a relationship between a source element and a target element,
 * indicating a connection or dependency between them.
 *
 * @param sourceId The unique identifier of the source element
 * @param targetId The unique identifier of the target element
 */
public record TraceLink(String sourceId, String targetId) implements Comparable<TraceLink> {
    @Override
    public int compareTo(@NotNull TraceLink o) {
        return this.sourceId.compareTo(o.sourceId) != 0
                ? this.sourceId.compareTo(o.sourceId)
                : this.targetId.compareTo(o.targetId);
    }
}
