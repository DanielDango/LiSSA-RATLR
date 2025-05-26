/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.classifier;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;

public class PipelineClassifier extends Classifier {
    private final List<List<Classifier>> classifiers;

    public PipelineClassifier(List<List<ModuleConfiguration>> configs) {
        super(1);
        this.classifiers = configs.stream()
                .map(it -> it.stream().map(Classifier::createClassifier).toList())
                .toList();
    }

    private PipelineClassifier(List<List<Classifier>> classifiers, int threads) {
        super(threads);
        this.classifiers = classifiers.stream().map(List::copyOf).toList();
    }

    @Override
    protected List<ClassificationResult> classify(Element source, List<Element> targets) {
        List<Element> remainingTargets = new ArrayList<>(targets);

        int layerNum = 0;
        for (List<Classifier> layer : classifiers) {
            logger.info("Invoking layer {} with {} classifiers", layerNum, layer.size());
            layerNum++;

            List<Element> layerResults = calculateRemainingTargets(source, remainingTargets, layer);
            logger.info(
                    "Reduced targets for {} @ layer {} from {} to {}",
                    source.getIdentifier(),
                    layerNum,
                    remainingTargets.size(),
                    layerResults.size());

            remainingTargets = layerResults;
            if (remainingTargets.isEmpty()) {
                logger.info("No remaining targets after layer {}, stopping classification.", layerNum);
                break;
            }
        }

        return remainingTargets.stream()
                .map(it -> ClassificationResult.of(source, it))
                .toList();
    }

    private List<Element> calculateRemainingTargets(
            Element source, List<Element> targets, List<Classifier> classifiers) {
        Map<Element, AtomicInteger> counter = new IdentityHashMap<>();
        for (Element target : targets) {
            counter.put(target, new AtomicInteger(0));
        }

        for (Classifier classifier : classifiers) {
            if (targets.isEmpty()) break;
            var classificationResults = classifier.classify(source, targets);
            for (var result : classificationResults) {
                counter.get(result.target()).incrementAndGet();
            }
        }

        List<Element> remainingTargetsAfterMajorityVote = new ArrayList<>();
        int majorityThreshold = (int) Math.ceil(classifiers.size() / 2.0);
        for (var entry : counter.entrySet()) {
            if (entry.getValue().get() >= majorityThreshold) {
                remainingTargetsAfterMajorityVote.add(entry.getKey());
            }
        }

        return remainingTargetsAfterMajorityVote;
    }

    @Override
    protected Classifier copyOf() {
        return new PipelineClassifier(classifiers, this.threads);
    }
}
