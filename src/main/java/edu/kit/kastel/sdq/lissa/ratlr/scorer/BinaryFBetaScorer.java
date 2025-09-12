/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.scorer;

import java.util.ArrayList;
import java.util.List;

import edu.kit.kastel.sdq.lissa.ratlr.classifier.ClassificationTask;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.Classifier;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.resultaggregator.ResultAggregator;
import edu.kit.kastel.sdq.lissa.ratlr.utils.Pair;

public class BinaryFBetaScorer extends BinaryScorer {

    /**
     * The beta parameter for the F-beta score calculation.
     */
    private static final int DEFAULT_BETA = 1;

    private static final String BETA_KEY = "beta";
    private final int beta;

    /**
     * Creates a new binary scorer instance with the given configuration.
     *
     * @param configuration The configuration for the scorer.
     * @param classifier
     */
    public BinaryFBetaScorer(ModuleConfiguration configuration, Classifier classifier, ResultAggregator aggregator) {
        super(configuration, classifier, aggregator);
        this.beta = configuration.argumentAsInt(BETA_KEY, DEFAULT_BETA);
    }

    @Override
    public List<Double> call(List<String> prompts, List<ClassificationTask> examples) {
        List<Double> fBetaScores = new ArrayList<>();
        for (String prompt : prompts) {
            fBetaScores.add(computeFBetaScore(prompt, examples));
        }
        return fBetaScores;
    }

    private double computeFBetaScore(String prompt, List<ClassificationTask> examples) {
        List<Boolean> scores;
        List<Pair<String, ClassificationTask>> promptExamplesToCompute;
        int truePositive = 0;
        int falsePositive = 0;
        int falseNegative = 0;
        int trueNegative = 0;
        promptExamplesToCompute =
                examples.stream().map(example -> new Pair<>(prompt, example)).toList();
        scores = this.computeBooleanScores(promptExamplesToCompute);
        for (int i = 0; i < scores.size(); i++) {
            if (Boolean.TRUE.equals(scores.get(i))) {
                if (promptExamplesToCompute.get(i).second().label()) {
                    truePositive++;
                } else {
                    trueNegative++;
                }
            } else {
                if (promptExamplesToCompute.get(i).second().label()) {
                    falseNegative++;
                } else {
                    falsePositive++;
                }
            }
        }
        return fBeta(truePositive, falsePositive, falseNegative, this.beta);
    }

    @Override
    public List<Double> call(List<String> prompts, List<ClassificationTask> examples, int threads) {
        return call(prompts, examples);
    }

    private static double recall(int truePositive, int falseNegative) {
        return (double) truePositive / (truePositive + falseNegative);
    }

    private static double precision(int truePositive, int falsePositive) {
        return (double) truePositive / (truePositive + falsePositive);
    }

    private static double fBeta(double precision, double recall, int beta) {
        return ((1 + beta * beta) * precision * recall) / ((beta * beta * precision) + recall);
    }

    private static double fBeta(int truePositive, int falsePositive, int falseNegative, int beta) {
        double precision = precision(truePositive, falsePositive);
        double recall = recall(truePositive, falseNegative);
        return fBeta(precision, recall, beta);
    }
}
