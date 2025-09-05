/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.evaluator;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.kastel.sdq.lissa.ratlr.classifier.ClassificationTask;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.Classifier;
import edu.kit.kastel.sdq.lissa.ratlr.scorer.AbstractScorer;

/**
 * Abstract base class for evaluators in the LiSSA framework.
 * This class provides the foundation for implementing different evaluation strategies
 * for prompts in classification tasks.
 * TODO: What are evaluators exactly?
 */
public abstract class AbstractEvaluator {

    /**
     * Default seed for random number generation to ensure reproducibility.
     */
    protected static final int DEFAULT_SEED = 42069;

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * Evaluates a list of prompts using the provided classification tasks, classifier, and scorer.
     * TODO: name the method
     *
     * @param prompts A list of prompts to evaluate.
     * @param examples A list of classification task examples.
     * @param classifier The classifier instance to use for evaluation.
     * @param scorer The scorer instance to use for scoring the prompts.
     */
    public abstract List<Double> call(
            List<String> prompts, List<ClassificationTask> examples, Classifier classifier, AbstractScorer scorer);
}
