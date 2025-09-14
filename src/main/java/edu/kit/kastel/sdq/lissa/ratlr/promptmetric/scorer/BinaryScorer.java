/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.promptmetric.scorer;

import java.util.ArrayList;
import java.util.List;

import edu.kit.kastel.sdq.lissa.ratlr.classifier.ClassificationResult;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.ClassificationTask;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;

/**
 * This scorer assigns a fixed score for correct and incorrect classifications.
 * The presence of a classification result indicates a correct classification.
 * If no result is provided, the classification is considered incorrect.
 */
public class BinaryScorer implements Scorer {

    private static final double DEFAULT_CORRECT_CLASSIFICATION_SCORE = 1.0;
    private static final String CORRECT_CLASSIFICATION_SCORE_CONFIGURATION_KEY = "correct_classification_score";
    private static final double DEFAULT_INCORRECT_CLASSIFICATION_SCORE = 0.0;
    private static final String INCORRECT_CLASSIFICATION_SCORE_CONFIGURATION_KEY = "incorrect_classification_score";

    private final double correctClassificationScore;
    private final double incorrectClassificationScore;

    public BinaryScorer(ModuleConfiguration configuration) {
        this.correctClassificationScore = configuration.argumentAsDouble(
                CORRECT_CLASSIFICATION_SCORE_CONFIGURATION_KEY, DEFAULT_CORRECT_CLASSIFICATION_SCORE);
        this.incorrectClassificationScore = configuration.argumentAsDouble(
                INCORRECT_CLASSIFICATION_SCORE_CONFIGURATION_KEY, DEFAULT_INCORRECT_CLASSIFICATION_SCORE);
    }

    public BinaryScorer() {
        this.correctClassificationScore = DEFAULT_CORRECT_CLASSIFICATION_SCORE;
        this.incorrectClassificationScore = DEFAULT_INCORRECT_CLASSIFICATION_SCORE;
    }

    /**
     * Scores a list of classification tasks against their corresponding classification results.
     * Depending on the presence of a result, each task is scored accordingly with the {@link #score(ClassificationTask, ClassificationResult)}
     * or {@link #score(ClassificationTask)} methods.
     *
     * @param tasks   The list of classification tasks to be scored
     * @param results The list of classification results corresponding to the tasks. May contain null values
     * @return A list of scores for each classification task
     * @throws IllegalArgumentException if the sizes of tasks and results lists do not match
     */
    @Override
    public List<Double> score(List<ClassificationTask> tasks, List<ClassificationResult> results) {
        if (tasks.size() != results.size()) {
            throw new IllegalArgumentException("Tasks and results lists must have the same size.");
        }
        List<Double> scores = new ArrayList<>(tasks.size());
        for (int i = 0; i < tasks.size(); i++) {
            scores.add(score(tasks.get(i), results.get(i)));
        }
        return scores;
    }

    /**
     * Scores the classification task based on the presence of a result. If a result is provided, not null and the task
     * is labeled as true, it is considered a correct classification and receives the correctClassificationScore.
     *
     * @param task   The classification task to be scored
     * @param result The classification result, which may be null
     * @return       The score for the classification task
     */
    @Override
    public double score(ClassificationTask task, ClassificationResult result) {
        if (result == null) {
            return score(task);
        }
        return task.label() ? correctClassificationScore : incorrectClassificationScore;
    }

    /**
     * Scores the classification task when no result is provided. If the task is labeled as false, it is considered a
     * correct classification and receives the correctClassificationScore.
     *
     * @param task The classification task to be scored
     * @return     The score for the classification task
     */
    @Override
    public double score(ClassificationTask task) {
        return task.label() ? incorrectClassificationScore : correctClassificationScore;
    }
}
