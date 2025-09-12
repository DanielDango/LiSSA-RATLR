/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.scorer;

import java.util.List;

import edu.kit.kastel.sdq.lissa.ratlr.classifier.ClassificationTask;

public interface Scorer {
    List<Double> call(List<String> prompts, List<ClassificationTask> examples);

    List<Double> call(List<String> prompts, List<ClassificationTask> examples, int maximumThreads);
}
