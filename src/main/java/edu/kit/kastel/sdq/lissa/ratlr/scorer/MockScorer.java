/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.scorer;

import java.util.Collections;
import java.util.List;

import edu.kit.kastel.sdq.lissa.ratlr.classifier.ClassificationTask;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.Classifier;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.utils.Pair;

/**
 * A mock implementation of the AbstractScorer that returns a constant score of 1.0 for any input.
 * This class is primarily used for testing and demonstration purposes.
 */
public class MockScorer extends AbstractScorer {

    /**
     * Creates a new instance of MockScorer with an empty configuration.
     */
    public MockScorer() {
        super(new ModuleConfiguration("", Collections.emptyMap()));
    }

    @Override
    protected List<Double> computeScores(
            Classifier classifier, List<Pair<String, ClassificationTask>> promptExamplesToCompute) {
        return Collections.nCopies(promptExamplesToCompute.size(), 1.0);
    }
}
