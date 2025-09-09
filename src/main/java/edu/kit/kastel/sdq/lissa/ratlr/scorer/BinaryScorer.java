/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.scorer;

import java.util.List;

import edu.kit.kastel.sdq.lissa.ratlr.classifier.ClassificationTask;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.Classifier;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.utils.Pair;

/**
 * A scorer that assigns a score of 1.0 for correct classifications and 0.0 for incorrect ones.
 */
public class BinaryScorer extends AbstractScorer {

    /**
     * Creates a new binary scorer instance with the given configuration.
     *
     * @param configuration The configuration for the scorer.
     */
    public BinaryScorer(ModuleConfiguration configuration, Classifier classifier) {
        super(configuration, classifier);
    }

    /**
     * Computes scores for a list of prompt-example pairs.
     * Each score is 1.0 if the classifier's prediction matches the example's label, otherwise 0.0.
     *
     * @param promptExamplesToCompute   A list of pairs containing prompts and classification task examples.
     * @return                          A list of computed scores (1.0 or 0.0).
     */
    @Override
    protected List<Double> computeScores(List<Pair<String, ClassificationTask>> promptExamplesToCompute) {
        return promptExamplesToCompute.stream().map(this::computeSingleScore).toList();
    }

    /**
     * Computes the score for a single prompt-example pair.
     * The score is 1.0 if the classifier's prediction matches the example's label, otherwise 0.0.
     *
     * @param promptExample  A pair containing the prompt and the classification task example.
     * @return               The computed score (1.0 or 0.0).
     */
    private Double computeSingleScore(Pair<String, ClassificationTask> promptExample) {
        String prompt = promptExample.first();
        ClassificationTask example = promptExample.second();
        classifier.setClassificationPrompt(prompt);
        return super.classify(example) == example.label() ? 1.0 : 0.0;
    }
}
