/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.classifier;

import java.util.Optional;

import edu.kit.kastel.sdq.lissa.ratlr.cache.Cache;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheKey;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheManager;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;
import edu.kit.kastel.sdq.lissa.ratlr.utils.KeyGenerator;

import dev.langchain4j.model.chat.ChatModel;

public class SimpleClassifier extends Classifier {

    private static final String DEFAULT_TEMPLATE =
            """
            Question: Here are two parts of software development artifacts.

            {source_type}: '''{source_content}'''

            {target_type}: '''{target_content}'''
            Are they related?

            Answer with 'yes' or 'no'.
            """;

    private final Cache cache;
    private final ChatLanguageModelProvider provider;

    private final ChatModel llm;
    private final String template;

    public SimpleClassifier(ModuleConfiguration configuration) {
        super(ChatLanguageModelProvider.threads(configuration));
        this.provider = new ChatLanguageModelProvider(configuration);
        this.template = configuration.argumentAsString("template", DEFAULT_TEMPLATE);
        this.cache = CacheManager.getDefaultInstance()
                .getCache(this.getClass().getSimpleName() + "_" + provider.modelName() + "_" + provider.seed());
        this.llm = provider.createChatModel();
    }

    private SimpleClassifier(int threads, Cache cache, ChatLanguageModelProvider provider, String template) {
        super(threads);
        this.cache = cache;
        this.provider = provider;
        this.template = template;
        this.llm = provider.createChatModel();
    }

    @Override
    protected final Classifier copyOf() {
        return new SimpleClassifier(threads, cache, provider, template);
    }

    @Override
    protected final Optional<ClassificationResult> classify(Element source, Element target) {
        String llmResponse = classifyIntern(source, target);

        String thinkEnd = "</think>";
        if (llmResponse.startsWith("<think>") && llmResponse.contains(thinkEnd)) {
            // Omit the thinking of models like deepseek-r1
            llmResponse = llmResponse
                    .substring(llmResponse.indexOf(thinkEnd) + thinkEnd.length())
                    .strip();
        }

        boolean isRelated = llmResponse.toLowerCase().contains("yes");
        if (isRelated) {
            return Optional.of(ClassificationResult.of(source, target));
        }

        return Optional.empty();
    }

    private String classifyIntern(Element source, Element target) {
        String request = template.replace("{source_type}", source.getType())
                .replace("{source_content}", source.getContent())
                .replace("{target_type}", target.getType())
                .replace("{target_content}", target.getContent());

        String key = KeyGenerator.generateKey(request);
        CacheKey cacheKey = new CacheKey(provider.modelName(), provider.seed(), CacheKey.Mode.CHAT, request, key);
        String cachedResponse = cache.get(cacheKey, String.class);
        if (cachedResponse != null) {
            return cachedResponse;
        } else {
            logger.info(
                    "Classifying ({}): {} and {}",
                    provider.modelName(),
                    source.getIdentifier(),
                    target.getIdentifier());
            String response = llm.chat(request);
            cache.put(cacheKey, response);
            return response;
        }
    }
}
