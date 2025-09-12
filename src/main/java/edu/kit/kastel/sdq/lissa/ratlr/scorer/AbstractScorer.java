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
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.resultaggregator.ResultAggregator;
import edu.kit.kastel.sdq.lissa.ratlr.utils.Pair;

/**
 * Abstract base class for scorers in the LiSSA framework.
 * This class provides the foundation for implementing different scoring strategies
 * for evaluating prompts in classification tasks.
 */
public abstract class AbstractScorer implements Scorer {

    private static final String CONFIDENCE_THRESHOLD_KEY = "confidence_threshold";
    private static final double DEFAULT_CONFIDENCE_THRESHOLD = 1.0;

    private final Map<String, Double> cache;
    private final double confidenceThreshold;
    /**
     * The classifier used for scoring.
     */
    protected final Classifier classifier;

    private final ResultAggregator aggregator;

    /**
     * Creates a new scorer with the specified configuration.
     * The configuration can include parameters such as confidence threshold.
     *
     * @param configuration The configuration for the scorer.
     */
    protected AbstractScorer(ModuleConfiguration configuration, Classifier classifier, ResultAggregator aggregator) {
        this.cache = new HashMap<>();
        this.classifier = classifier;
        this.aggregator = aggregator;
        this.confidenceThreshold =
                configuration.argumentAsDouble(CONFIDENCE_THRESHOLD_KEY, DEFAULT_CONFIDENCE_THRESHOLD);
    }

    @Override
    public List<Double> call(List<String> prompts, List<ClassificationTask> examples) {
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
        List<Double> computedScores = computeScores(promptsExsToCompute);
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

    @Override
    public List<Double> call(List<String> prompts, List<ClassificationTask> examples, int maximumThreads) {
        return call(prompts, examples);
    }

    /**
     * Computes scores for a list of prompt-example pairs using the provided classifier.
     * This method must be implemented by concrete scorer implementations to define
     * their specific scoring logic.
     *
     * @param promptExamplesToCompute A list of pairs, each containing a prompt and a classification task example.
     * @return A list of computed scores corresponding to each prompt-example pair.
     */
    protected abstract List<Double> computeScores(List<Pair<String, ClassificationTask>> promptExamplesToCompute);

    /**
     * Classifies a single classification task using the provided classifier.
     * The classification is considered positive if the confidence of the result
     * meets or exceeds a predefined threshold (currently set to 1.0).
     *
     * @param task The classification task to classify.
     * @return true if the classification is positive, false otherwise.
     */
    protected boolean classify(ClassificationTask task) {
        Optional<ClassificationResult> result = classifier.classify(task);
        if (result.isPresent()) {
            double confidence = result.get().confidence();
            return confidence >= confidenceThreshold;
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
