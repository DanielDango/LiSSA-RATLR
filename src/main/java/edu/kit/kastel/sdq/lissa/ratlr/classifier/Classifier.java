/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.classifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
        var tasks = createClassificationTasks(sourceStore, targetStore);

        if (threads <= 1) {
            return sequentialClassify(tasks);
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
                        var result = copy.classify(pair.first(), pair.second());
                        logger.debug(
                                "Classified (P) {} with {}: {}",
                                pair.first().getIdentifier(),
                                pair.second().getIdentifier(),
                                result);
                        result.ifPresent(results::add);
                    }
                }
            });
        }

        logger.info("Waiting for classification to finish. Tasks in queue: {}", taskQueue.size());

        for (Thread worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                logger.error("Worker thread interrupted.", e);
                Thread.currentThread().interrupt();
            }
        }

        List<ClassificationResult> resultList = new ArrayList<>(results);
        logger.info("Finished parallel classification with {} results.", resultList.size());
        return resultList;
    }

    private List<ClassificationResult> sequentialClassify(List<Pair<Element, Element>> tasks) {
        List<ClassificationResult> results = new ArrayList<>();
        for (var task : tasks) {
            var result = classify(task.first(), task.second());
            logger.debug(
                    "Classified {} with {}: {}",
                    task.first().getIdentifier(),
                    task.second().getIdentifier(),
                    result);
            result.ifPresent(results::add);
        }
        logger.info("Finished sequential classification with {} results.", results.size());
        return results;
    }

    /**
     * Classifies a pair of elements.
     *
     * @param source the source element
     * @param target the target element
     * @return a classification result, empty if classified as unrelated/no trace link
     */
    protected abstract Optional<ClassificationResult> classify(Element source, Element target);

    protected abstract Classifier copyOf();

    protected static List<Pair<Element, Element>> createClassificationTasks(
            ElementStore sourceStore, ElementStore targetStore) {
        List<Pair<Element, Element>> tasks = new ArrayList<>();

        for (var source : sourceStore.getAllElements(true)) {
            var targetCandidates = targetStore.findSimilar(source.second());
            for (Element target : targetCandidates) {
                tasks.add(new Pair<>(source.first(), target));
            }
        }
        return tasks;
    }

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
