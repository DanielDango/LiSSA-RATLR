/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.preprocessor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.langchain4j.model.chat.ChatModel;
import edu.kit.kastel.sdq.lissa.ratlr.cache.Cache;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheKey;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheManager;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.ChatLanguageModelProvider;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Artifact;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;
import edu.kit.kastel.sdq.lissa.ratlr.utils.KeyGenerator;

public class SummarizePreprocessor extends Preprocessor {

    private static final String DEFAULT_TEMPLATE =
            """
Describe how the following {type} supports functionality that may be relevant to a use case.\s
Focus on how it manages data, handles user inputs or system events, enforces validation or business rules, and interacts with other components.\s
Highlight any parts of the code that could enable or partially implement steps in a user or system interaction flow, such as displaying information, processing changes, or handling errors.

{type}
{content}
""";

    private final String template;
    private final ChatLanguageModelProvider provider;
    private final int threads;
    private final Cache cache;

    public SummarizePreprocessor(ModuleConfiguration moduleConfiguration) {
        this.template = moduleConfiguration.argumentAsString("template", DEFAULT_TEMPLATE);
        this.provider = new ChatLanguageModelProvider(moduleConfiguration);
        this.threads = ChatLanguageModelProvider.threads(moduleConfiguration);
        this.cache = CacheManager.getDefaultInstance()
                .getCache(this.getClass().getSimpleName() + "_" + provider.modelName());
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
                Element element = new Element(artifact.getIdentifier(), artifact.getType(), summary, 0, null, true);
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
