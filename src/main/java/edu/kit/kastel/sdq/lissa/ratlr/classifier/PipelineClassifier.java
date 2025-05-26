/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.classifier;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.ElementStore;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;
import edu.kit.kastel.sdq.lissa.ratlr.utils.Pair;

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
    public List<ClassificationResult> classify(ElementStore sourceStore, ElementStore targetStore) {

        List<ClassificationResult> results = new ArrayList<>();
        for (var source : sourceStore.getAllElements(true)) {

            List<Element> remainingTargets = new ArrayList<>(targetStore.findSimilar(source.second()));

            int layerNum = 0;
            for (List<Classifier> layer : classifiers) {
                logger.info("Invoking layer {} with {} classifiers", layerNum, layer.size());
                layerNum++;

                List<Element> layerResults = calculateRemainingTargets(source.first(), remainingTargets, layer);
                logger.info(
                        "Reduced targets for {} @ layer {} from {} to {}",
                        source.first().getIdentifier(),
                        layerNum,
                        remainingTargets.size(),
                        layerResults.size());

                remainingTargets = layerResults;
                if (remainingTargets.isEmpty()) {
                    logger.info("No remaining targets after layer {}, stopping classification.", layerNum);
                    break;
                }
            }

            for (Element target : remainingTargets) {
                ClassificationResult result = ClassificationResult.of(source.first(), target, 1.0);
                results.add(result);
            }
        }
        return results;
    }

    private List<Element> calculateRemainingTargets(
            Element source, List<Element> targets, List<Classifier> classifiers) {
        Map<Element, AtomicInteger> counter = new IdentityHashMap<>();
        for (Element target : targets) {
            counter.put(target, new AtomicInteger(0));
        }

        for (Classifier classifier : classifiers) {
            if (targets.isEmpty()) break;
            List<Element> classificationResults;
            if (classifier.threads <= 1) {
                classificationResults = targets.stream()
                        .map(t -> classifier.classify(source, t))
                        .filter(Objects::nonNull)
                        .map(ClassificationResult::target)
                        .toList();
            } else {
                List<Pair<Element, Element>> tasks = targets.stream()
                        .map(target -> new Pair<>(source, target))
                        .toList();
                classificationResults = classifier.parallelClassify(tasks).stream()
                        .filter(Objects::nonNull)
                        .map(ClassificationResult::target)
                        .toList();
            }
            for (var result : classificationResults) {
                counter.get(result).incrementAndGet();
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

    @Override
    protected ClassificationResult classify(Element source, Element targets) {
        // Not implemented, as this classifier does not classify single pairs directly.
        throw new UnsupportedOperationException("PipelineClassifier does not support single pair classification.");
    }
}
