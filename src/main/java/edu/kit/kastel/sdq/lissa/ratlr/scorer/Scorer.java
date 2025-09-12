/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.scorer;

import java.util.List;

import edu.kit.kastel.sdq.lissa.ratlr.classifier.ClassificationTask;

public interface Scorer {

    /**
     * Sequentially computes scores for a list of prompts and classification task examples
     * using the provided classifier.
     * Each prompt is evaluated against all examples, and the mean score amongst them is returned.
     * It utilizes caching to avoid redundant computations.
     *
     * @param prompts A list of prompts to evaluate.
     * @param examples A list of classification task examples.
     * @return A list of computed scores corresponding to each prompt.
     */
    List<Double> call(List<String> prompts, List<ClassificationTask> examples);

    /**
     * Placeholder for parallel call implementation.
     * Currently, it calls the sequential implementation.
     *
     * @param prompts A list of prompts to evaluate.
     * @param examples A list of classification task examples.
     * @param maximumThreads The number of threads to use for parallel processing.
     * @return A list of computed scores corresponding to each prompt.
     * @deprecated Use {@link #call(List, List)} instead. No parallel implementation is provided.
     */
    @Deprecated(forRemoval = true)
    List<Double> call(List<String> prompts, List<ClassificationTask> examples, int maximumThreads);
}
