/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.classifier;

import org.jetbrains.annotations.NotNull;

import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;

/**
 * Represents a classification task for trace link prediction between two elements.
 * Each task consists of a source element, a target element, and a label indicating
 * whether a trace link exists between them.
 *
 * @param source The source element in the trace link relationship.
 * @param target The target element in the trace link relationship.
 * @param label  The label indicating whether a trace link exists between the source and target elements.
 */
public record ClassificationTask(Element source, Element target, boolean label) {
    @NotNull
    @Override
    public String toString() {
        return "ClassificationTask{" + "source="
                + source.getIdentifier() + ", target="
                + target.getIdentifier() + ", label="
                + label + '}';
    }
}
