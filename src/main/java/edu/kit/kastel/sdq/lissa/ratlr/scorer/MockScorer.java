/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.scorer;

import java.util.Collections;
import java.util.List;

import edu.kit.kastel.sdq.lissa.ratlr.classifier.ClassificationTask;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.MockClassifier;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.context.ContextStore;
import edu.kit.kastel.sdq.lissa.ratlr.utils.Pair;

/**
 * A mock implementation of the AbstractScorer that returns a constant score of 1.0 for any input.
 * This class is primarily used for testing and demonstration purposes.
 */
public class MockScorer extends AbstractScorer {

    /**
     * Creates a new instance of MockScorer with an empty configuration and a MockClassifier.
     */
    public MockScorer() {
        // TODO dont pass null
        super(new ModuleConfiguration("", Collections.emptyMap()), new MockClassifier(new ContextStore()), null);
    }

    @Override
    protected List<Double> computeScores(List<Pair<String, ClassificationTask>> promptExamplesToCompute) {
        return Collections.nCopies(promptExamplesToCompute.size(), 0.0);
    }
}
