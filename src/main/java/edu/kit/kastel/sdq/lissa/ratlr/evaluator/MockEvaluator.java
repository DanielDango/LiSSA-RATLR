/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.evaluator;

import java.util.Collections;
import java.util.List;

import edu.kit.kastel.sdq.lissa.ratlr.classifier.ClassificationTask;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.scorer.AbstractScorer;

public class MockEvaluator extends AbstractEvaluator {

    public MockEvaluator() {
        super(new ModuleConfiguration("", Collections.emptyMap()));
    }

    @Override
    public List<Double> call(List<String> prompts, List<ClassificationTask> examples, AbstractScorer scorer) {
        return Collections.nCopies(prompts.size(), 1.0);
    }
}
