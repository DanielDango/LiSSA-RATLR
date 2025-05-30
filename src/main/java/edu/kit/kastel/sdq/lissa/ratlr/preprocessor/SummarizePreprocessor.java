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
 * Preprocessor that summarizes artifacts using a language model.
 * It takes a template for the summary and processes each artifact to generate a summary.
 * The available keys in the template are:
 * <ul>
 *     <li>{type} - The type of the artifact (e.g., "source code", "requirement")</li>
 *     <li>{content} - The content of the artifact</li>
 * </ul>
 */
public class SummarizePreprocessor extends Preprocessor {
    private final String template;
    private final ChatLanguageModelProvider provider;
    private final int threads;
    private final Cache cache;

    public SummarizePreprocessor(ModuleConfiguration moduleConfiguration) {
        this.template = moduleConfiguration.argumentAsString("template");
        this.provider = new ChatLanguageModelProvider(moduleConfiguration);
        this.threads = ChatLanguageModelProvider.threads(moduleConfiguration);
        this.cache = CacheManager.getDefaultInstance()
                .getCache(this.getClass().getSimpleName() + "_" + provider.modelName() + "_" + provider.seed());
    }

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
