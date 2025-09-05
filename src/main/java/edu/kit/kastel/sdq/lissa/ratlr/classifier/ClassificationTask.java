/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.classifier;

import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;

public record ClassificationTask(
        /**
         * The source element in the trace link relationship.
         */
        Element source,

        /**
         * The target element in the trace link relationship.
         */
        Element target,
        /**
         * The label indicating whether a trace link exists between the source and target elements.
         */
        boolean label) {
    @Override
    public String toString() {
        return "ClassificationTask{" + "source="
                + source.getIdentifier() + ", target="
                + target.getIdentifier() + ", label="
                + label + '}';
    }
}
