/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.evaluator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import edu.kit.kastel.sdq.lissa.ratlr.classifier.ClassificationTask;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.Classifier;
import edu.kit.kastel.sdq.lissa.ratlr.scorer.AbstractScorer;

/**
 * An evaluator that performs a brute-force evaluation of all provided prompts
 */
public class BruteForceEvaluator extends AbstractEvaluator {

    private final int evaluationBudget;
    private final Random random;

    public BruteForceEvaluator(int evaluationBudget) {
        this.evaluationBudget = evaluationBudget;
        this.random = new Random(DEFAULT_SEED);
    }

    /**
     * Evaluates all provided prompts using the given classifier and scorer.
     * It selects a subset of examples based on the evaluation budget and the number of prompts.
     * Each prompt is evaluated against all remaining examples, and the mean score amongst them is returned
     */
    @Override
    public List<Double> call(
            List<String> prompts,
            List<ClassificationTask> classificationTasks,
            Classifier classifier,
            AbstractScorer scorer) {
        int sampleSize = Math.min(classificationTasks.size(), (this.evaluationBudget / prompts.size()));
        List<ClassificationTask> classificationExamples = new ArrayList<>(classificationTasks);
        Collections.shuffle(classificationExamples, this.random);
        classificationExamples = classificationExamples.subList(0, sampleSize);
        return scorer.sequentialCall(classifier, prompts, classificationExamples);
    }
}
