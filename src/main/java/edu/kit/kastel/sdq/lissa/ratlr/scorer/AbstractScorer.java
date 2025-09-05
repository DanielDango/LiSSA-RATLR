/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.scorer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import edu.kit.kastel.sdq.lissa.ratlr.classifier.ClassificationResult;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.ClassificationTask;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.Classifier;
import edu.kit.kastel.sdq.lissa.ratlr.utils.Pair;

/**
 * Abstract base class for scorers in the LiSSA framework.
 * This class provides the foundation for implementing different scoring strategies
 * for evaluating prompts in classification tasks.
 * TODO: factory
 */
public abstract class AbstractScorer {

    private final Map<String, Double> cache;

    protected AbstractScorer() {
        this.cache = new HashMap<>();
    }

    /**
     * Sequentially computes scores for a list of prompts and classification task examples
     * using the provided classifier.
     * Each prompt is evaluated against all examples, and the mean score amongst them is returned.
     * It utilizes caching to avoid redundant computations. <br>
     * TODO: use persistent file cache instead
     *
     * @param classifier The classifier instance to use for scoring.
     * @param prompts A list of prompts to evaluate.
     * @param examples A list of classification task examples.
     * @return A list of computed scores corresponding to each prompt.
     */
    public List<Double> sequentialCall(Classifier classifier, List<String> prompts, List<ClassificationTask> examples) {
        Map<String, List<Double>> cachedScores = new HashMap<>();
        for (String prompt : prompts) {
            cachedScores.put(prompt, new ArrayList<>());
        }

        List<Pair<String, ClassificationTask>> promptsExsToCompute = new ArrayList<>();
        for (ClassificationTask example : examples) {
            for (String prompt : prompts) {
                String key = example + "-" + prompt;
                if (cache.containsKey(key)) {
                    cachedScores.get(prompt).add(cache.get(key));
                } else {
                    promptsExsToCompute.add(new Pair<>(prompt, example));
                }
            }
        }
        List<Double> computedScores = computeScores(classifier, promptsExsToCompute);
        for (int i = 0; i < promptsExsToCompute.size(); i++) {
            String prompt = promptsExsToCompute.get(i).first();
            ClassificationTask example = promptsExsToCompute.get(i).second();
            String key = example + "-" + prompt;
            cache.put(key, computedScores.get(i));
            cachedScores.get(prompt).add(computedScores.get(i));
        }
        List<Double> meanScores = new ArrayList<>();
        for (String prompt : prompts) {
            meanScores.add(mean(cachedScores.get(prompt)));
        }
        return meanScores;
    }

    /**
     * Placeholder for parallel call implementation.
     * Currently, it calls the sequential implementation. <br>
     * Todo: implement parallel call?
     *
     * @param classifier The classifier instance to use for scoring.
     * @param prompts A list of prompts to evaluate.
     * @param examples A list of classification task examples.
     * @param threads The number of threads to use for parallel processing.
     * @return A list of computed scores corresponding to each prompt.
     */
    public List<Double> parallelCall(
            Classifier classifier, List<String> prompts, List<ClassificationTask> examples, int threads) {
        return sequentialCall(classifier, prompts, examples);
    }

    /**
     * Computes scores for a list of prompt-example pairs using the provided classifier.
     * This method must be implemented by concrete scorer implementations to define
     * their specific scoring logic.
     *
     * @param classifier The classifier instance to use for scoring.
     * @param promptExamplesToCompute A list of pairs, each containing a prompt and a classification task example.
     * @return A list of computed scores corresponding to each prompt-example pair.
     */
    protected abstract List<Double> computeScores(
            Classifier classifier, List<Pair<String, ClassificationTask>> promptExamplesToCompute);

    /**
     * Classifies a single classification task using the provided classifier.
     * The classification is considered positive if the confidence of the result
     * meets or exceeds a predefined threshold (currently set to 1.0).
     *
     * @param classifier The classifier instance to use for classification.
     * @param task The classification task to classify.
     * @return true if the classification is positive, false otherwise.
     */
    protected boolean classify(Classifier classifier, ClassificationTask task) {
        Optional<ClassificationResult> result = classifier.classify(task);
        if (result.isPresent()) {
            double confidence = result.get().confidence();
            // Todo: what should be the threshold?
            return confidence >= 1.0;
        } else {
            return false;
        }
    }

    /**
     * Computes the arithmetic mean of a list of doubles.
     * @param list A list of doubles
     * @return The mean of the list, or 0.0 if the list is empty
     */
    private static double mean(List<Double> list) {
        return list.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
}
