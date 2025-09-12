/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.scorer;

import java.util.List;

import edu.kit.kastel.sdq.lissa.ratlr.resultaggregator.ResultAggregator;
import org.jetbrains.annotations.NotNull;

import edu.kit.kastel.sdq.lissa.ratlr.classifier.ClassificationTask;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.Classifier;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.utils.Pair;

/**
 * A scorer that assigns a score of 1.0 for correct classifications and 0.0 for incorrect ones.
 */
public class BinaryScorer extends AbstractScorer {

    protected static final double POSITIVE_CLASS_SCORE = 1.0;
    protected static final double NEGATIVE_CLASS_SCORE = 0.0;

    /**
     * Creates a new binary scorer instance with the given configuration.
     *
     * @param configuration The configuration for the scorer.
     */
    public BinaryScorer(ModuleConfiguration configuration, Classifier classifier, ResultAggregator aggregator) {
        super(configuration, classifier, aggregator);
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
        return computeBooleanScores(promptExamplesToCompute).stream()
                .map(score -> Boolean.TRUE.equals(score) ? POSITIVE_CLASS_SCORE : NEGATIVE_CLASS_SCORE)
                .toList();
    }

    /**
     * Computes boolean scores for a list of prompt-example pairs.
     * If and only if the results of the classifier match the labels of the examples, true is returned regardless of
     * true or false label.
     *
     * @param promptExamplesToCompute  A list of pairs containing prompts and classification task examples.
     * @return                         A list of boolean scores (true for correct classification, false otherwise).
     */
    @NotNull
    protected List<Boolean> computeBooleanScores(List<Pair<String, ClassificationTask>> promptExamplesToCompute) {
        return promptExamplesToCompute.stream()
                .map(this::classificationMatchesTruth)
                .toList();
    }

    /**
     * Checks if the classifier's prediction matches the label of the classification task example.
     *
     * @param promptExample  A pair containing the prompt and the classification task example.
     * @return               True if the prediction matches the label, false otherwise.
     */
    private boolean classificationMatchesTruth(Pair<String, ClassificationTask> promptExample) {
        String prompt = promptExample.first();
        ClassificationTask example = promptExample.second();
        classifier.setClassificationPrompt(prompt);
        return this.classify(example) == example.label();
    }
}
