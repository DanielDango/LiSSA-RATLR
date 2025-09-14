/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.promptmetric;

import java.util.Collections;
import java.util.List;

import edu.kit.kastel.sdq.lissa.ratlr.classifier.ClassificationTask;

/**
 * A mock implementation of the Metric interface that returns a constant score of 0.0 for any input.
 * This class is primarily used for testing and demonstration purposes.
 */
public class MockMetric implements Metric {

    public MockMetric() {
        // No specific initialization required
    }

    @Override
    public List<Double> getMetric(List<String> prompts, List<ClassificationTask> examples) {
        return Collections.nCopies(prompts.size(), 0.0);
    }

    @Override
    public Double getMetric(String prompt, List<ClassificationTask> examples) {
        return 0.0;
    }
}
