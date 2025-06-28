/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.preprocessor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import edu.kit.kastel.sdq.lissa.ratlr.cache.Cache;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheKey;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheManager;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.ChatLanguageModelProvider;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Artifact;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;
import edu.kit.kastel.sdq.lissa.ratlr.utils.KeyGenerator;

import dev.langchain4j.model.chat.ChatModel;

/**
 * A preprocessor that generates summaries of artifacts using a language model.
 * This preprocessor is part of the "summarize" type in the preprocessor hierarchy.
 * It:
 * <ul>
 *     <li>Uses a configurable template to format summary requests</li>
 *     <li>Supports parallel processing with multiple threads</li>
 *     <li>Caches summaries to avoid redundant processing</li>
 *     <li>Creates elements with granularity level 0</li>
 *     <li>Marks all elements for comparison (compare=true)</li>
 * </ul>
 *
 * The template for summary requests can use the following placeholders:
 * <ul>
 *     <li>{type} - The type of the artifact (e.g., "source code", "requirement")</li>
 *     <li>{content} - The content of the artifact to be summarized</li>
 * </ul>
 *
 * Configuration options:
 * <ul>
 *     <li>template: The template string for formatting summary requests</li>
 *     <li>model: The language model to use for summarization</li>
 *     <li>seed: Random seed for reproducible results</li>
 * </ul>
 *
 */
public class SummarizePreprocessor extends Preprocessor {
    /** The template string for formatting summary requests */
    private final String template;
    /** The provider for chat language models */
    private final ChatLanguageModelProvider provider;
    /** Number of threads to use for parallel processing */
    private final int threads;
    /** Cache for storing and retrieving summaries */
    private final Cache cache;

    /**
     * Creates a new summarize preprocessor with the specified configuration.
     *
     * @param moduleConfiguration The module configuration containing template and model settings
     */
    public SummarizePreprocessor(ModuleConfiguration moduleConfiguration) {
        this.template = moduleConfiguration.argumentAsString("template");
        this.provider = new ChatLanguageModelProvider(moduleConfiguration);
        this.threads = ChatLanguageModelProvider.threads(moduleConfiguration);
        this.cache = CacheManager.getDefaultInstance()
                .getCache(this.getClass().getSimpleName() + "_" + provider.modelName() + "_" + provider.seed());
    }

    /**
     * Preprocesses a list of artifacts by generating summaries for each one.
     * This method:
     * <ol>
     *     <li>Formats summary requests using the template</li>
     *     <li>Creates a thread pool for parallel processing</li>
     *     <li>Processes requests in parallel using the language model</li>
     *     <li>Caches and retrieves summaries as needed</li>
     *     <li>Creates elements with the generated summaries</li>
     * </ol>
     *
     * The method handles parallel processing efficiently:
     * <ul>
     *     <li>Uses a thread pool with the configured number of threads</li>
     *     <li>Creates a new model instance per thread when using multiple threads</li>
     *     <li>Shares a single model instance when using one thread</li>
     * </ul>
     *
     * @param artifacts The list of artifacts to summarize
     * @return A list of elements containing the summaries
     * @throws IllegalStateException if summarization fails
     */
    @Override
    public List<Element> preprocess(List<Artifact> artifacts) {
        List<Element> elements = new ArrayList<>();

        List<String> requests = new ArrayList<>();

        for (Artifact artifact : artifacts) {
            String request = template.replace("{type}", artifact.getType()).replace("{content}", artifact.getContent());
            requests.add(request);
        }

        ExecutorService executorService =
                threads > 1 ? Executors.newFixedThreadPool(threads) : Executors.newSingleThreadExecutor();

        var llmInstance = provider.createChatModel();
        List<Callable<String>> tasks = new ArrayList<>();
        for (String request : requests) {
            tasks.add(() -> {
                String key = KeyGenerator.generateKey(request);
                CacheKey cacheKey =
                        new CacheKey(provider.modelName(), provider.seed(), CacheKey.Mode.CHAT, request, key);

                String cachedResponse = cache.get(cacheKey, String.class);
                if (cachedResponse != null) {
                    return cachedResponse;
                }

                ChatModel chatModel = threads > 1 ? provider.createChatModel() : llmInstance;
                String response = chatModel.chat(request);
                cache.put(cacheKey, response);
                return response;
            });
        }

        try {
            logger.info("Summarizing {} artifacts with {} threads", artifacts.size(), threads);
            var summaries = executorService.invokeAll(tasks);
            for (int i = 0; i < artifacts.size(); i++) {
                Artifact artifact = artifacts.get(i);
                String summary = summaries.get(i).get();
                Element element = new Element(
                        artifact.getIdentifier(),
                        "Summary of '%s'".formatted(artifact.getType()),
                        summary,
                        0,
                        null,
                        true);
                elements.add(element);
            }
        } catch (InterruptedException e) {
            logger.error("Summarization interrupted", e);
            Thread.currentThread().interrupt();
            return elements;
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getMessage(), e);
        } finally {
            executorService.shutdown();
        }

        return elements;
    }
}
