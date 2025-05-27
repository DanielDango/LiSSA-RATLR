/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.classifier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.ElementStore;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;
import edu.kit.kastel.sdq.lissa.ratlr.utils.Pair;

public abstract class Classifier {
    public static final String CONFIG_NAME_SEPARATOR = "_";

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());
    protected final int threads;

    protected Classifier(int threads) {
        this.threads = Math.max(1, threads);
    }

    public List<ClassificationResult> classify(ElementStore sourceStore, ElementStore targetStore) {
        if (threads <= 1) {
            return sequentialClassify(sourceStore, targetStore);
        }

        // Parallel classification requires a list of tasks
        List<Pair<Element, Element>> tasks = new ArrayList<>();
        for (var query : sourceStore.getAllElements(true)) {
            var targetCandidates = targetStore.findSimilar(query.second());
            for (Element target : targetCandidates) {
                tasks.add(new Pair<>(query.first(), target));
            }
        }
        return parallelClassify(tasks);
    }

    protected final List<ClassificationResult> parallelClassify(List<Pair<Element, Element>> tasks) {
        ConcurrentLinkedQueue<ClassificationResult> results = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Pair<Element, Element>> taskQueue = new ConcurrentLinkedQueue<>(tasks);

        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = Thread.ofVirtual().start(new Runnable() {
                private final Classifier copy = copyOf();

                @Override
                public void run() {
                    while (!taskQueue.isEmpty()) {
                        Pair<Element, Element> pair = taskQueue.poll();
                        if (pair == null) {
                            return;
                        }
                        ClassificationResult result = copy.classify(pair.first(), pair.second());
                        logger.debug(
                                "Classified (P) {} with {}: {}",
                                pair.first().getIdentifier(),
                                pair.second().getIdentifier(),
                                result);
                        if (result != null) results.add(result);
                    }
                }
            });
        }

        logger.info("Waiting for classification to finish. Tasks in queue: {}", taskQueue.size());

        for (Thread worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Worker thread interrupted.", e);
            }
        }

        List<ClassificationResult> resultList = new ArrayList<>(results);
        logger.info("Finished parallel classification with {} results.", resultList.size());
        return resultList;
    }

    private List<ClassificationResult> sequentialClassify(ElementStore sourceStore, ElementStore targetStore) {
        List<ClassificationResult> results = new ArrayList<>();
        for (var query : sourceStore.getAllElements(true)) {
            var targetCandidates = targetStore.findSimilar(query.second());
            for (Element target : targetCandidates) {
                ClassificationResult result = classify(query.first(), target);
                logger.debug(
                        "Classified {} with {}: {}", query.first().getIdentifier(), target.getIdentifier(), result);
                results.add(result);
            }
        }
        logger.info("Finished sequential classification with {} results.", results.size());
        return results;
    }

    protected abstract ClassificationResult classify(Element source, Element target);

    protected abstract Classifier copyOf();

    public static Classifier createClassifier(ModuleConfiguration configuration) {
        return switch (configuration.name().split(CONFIG_NAME_SEPARATOR)[0]) {
            case "mock" -> new MockClassifier();
            case "simple" -> new SimpleClassifier(configuration);
            case "reasoning" -> new ReasoningClassifier(configuration);
            default -> throw new IllegalStateException("Unexpected value: " + configuration.name());
        };
    }

    public static Classifier createMultiStageClassifier(List<List<ModuleConfiguration>> configs) {
        return new PipelineClassifier(configs);
    }
}
