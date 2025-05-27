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

        List<Pair<Element, Element>> tasks = new ArrayList<>();

        for (var source : sourceStore.getAllElements(true)) {
            var targetCandidates = targetStore.findSimilar(source.second());
            for (Element target : targetCandidates) {
                tasks.add(new Pair<>(source.first(), target));
            }
        }

        int layerNum = 0;
        for (List<Classifier> layer : classifiers) {
            logger.info("Invoking layer {} with {} classifiers and {} tasks", layerNum, layer.size(), tasks.size());
            layerNum++;

            List<Pair<Element, Element>> layerResults = calculateRemainingTargets(tasks, layer);
            logger.info("Reduced targets @ layer {} from {} to {}", layerNum, tasks.size(), layerResults.size());

            tasks = layerResults;
            if (tasks.isEmpty()) {
                logger.info("No remaining targets after layer {}, stopping classification.", layerNum);
                break;
            }
        }

        for (Pair<Element, Element> sourceXtarget : tasks) {
            ClassificationResult result = ClassificationResult.of(sourceXtarget.first(), sourceXtarget.second(), 1.0);
            results.add(result);
        }

        return results;
    }

    private List<Pair<Element, Element>> calculateRemainingTargets(
            List<Pair<Element, Element>> tasks, List<Classifier> classifiers) {

        Map<Pair<Element, Element>, AtomicInteger> counter = new LinkedHashMap<>();
        for (var task : tasks) {
            counter.put(task, new AtomicInteger(0));
        }

        for (Classifier classifier : classifiers) {
            if (tasks.isEmpty()) break;
            List<ClassificationResult> classificationResults;
            if (classifier.threads <= 1) {
                classificationResults = tasks.stream()
                        .map(e -> classifier.classify(e.first(), e.second()))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .toList();
            } else {
                classificationResults = classifier.parallelClassify(tasks).stream()
                        .filter(Objects::nonNull)
                        .toList();
            }
            for (var result : classificationResults) {
                counter.get(new Pair<>(result.source(), result.target())).incrementAndGet();
            }
        }

        List<Pair<Element, Element>> remainingTargetsAfterMajorityVote = new ArrayList<>();
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
    protected Optional<ClassificationResult> classify(Element source, Element targets) {
        // Not implemented, as this classifier does not classify single pairs directly.
        throw new UnsupportedOperationException("PipelineClassifier does not support single pair classification.");
    }
}
