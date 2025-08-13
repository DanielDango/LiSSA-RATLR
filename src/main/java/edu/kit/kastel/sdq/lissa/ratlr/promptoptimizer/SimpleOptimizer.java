/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer;

import edu.kit.kastel.sdq.lissa.ratlr.cache.Cache;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheKey;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheManager;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.ChatLanguageModelProvider;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.ElementStore;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;

import dev.langchain4j.model.chat.ChatModel;

public class SimpleOptimizer extends AbstractPromptOptimizer {

    /**
     * The default template for classification requests.
     * This template presents two artifacts and asks if they are related.
     */
    public static final String DEFAULT_OPTIMIZATION_TEMPLATE =
            """
                    Optimize the following prompt to achieve better classification results for traceability link recovery.
                    Traceability links are to be found in the domain of {source_type} to {target_type}.
                    Do not modify the input and output formats specified by the original prompt.
                    Enclose your optimized prompt with"""
                    + PROMPT_START
                    + PROMPT_END
                    + """
                    brackets.
                    The original prompt is provided below:
                    '''{original_prompt}'''
                    """;

    public static final String ORIGINAL_PROMPT_KEY = "{original_prompt}";

    private final Cache cache;

    /**
     * Provider for the language model used in classification.
     */
    private final ChatLanguageModelProvider provider;

    /**
     * The language model instance used for classification.
     */
    private final ChatModel llm;

    /**
     * The template used for classification requests.
     */
    private final String template;

    /**
     * The prompt used for optimization.
     * This is the initial prompt that will be optimized.
     */
    private final String optimizationPrompt;
    /**
     * Creates a new simple optimizer with the specified configuration.
     *
     * @param configuration The module configuration containing optimizer settings
     */
    public SimpleOptimizer(ModuleConfiguration configuration) {
        super(ChatLanguageModelProvider.threads(configuration));
        this.provider = new ChatLanguageModelProvider(configuration);
        this.optimizationPrompt = configuration.argumentAsString(PROMPT_KEY, "");
        this.template = configuration.argumentAsString("optimization_template", DEFAULT_OPTIMIZATION_TEMPLATE);
        this.cache = CacheManager.getDefaultInstance()
                .getCache(this.getClass().getSimpleName() + "_" + provider.modelName() + "_" + provider.seed());
        this.llm = provider.createChatModel();
    }

    private SimpleOptimizer(
            int threads, Cache cache, ChatLanguageModelProvider provider, String optimizationPrompt, String template) {
        super(threads);
        this.cache = cache;
        this.provider = provider;
        this.optimizationPrompt = optimizationPrompt;
        this.template = template;
        this.llm = provider.createChatModel();
    }

    @Override
    public String optimize(ElementStore sourceStore, ElementStore targetStore) {
        Element source = sourceStore.getAllElements(true).getFirst().first();
        Element target = targetStore
                .findSimilar(sourceStore.getAllElements(true).getFirst())
                .getFirst();
        String request = template.replace("{source_type}", source.getType())
                .replace("{target_type}", target.getType())
                .replace(ORIGINAL_PROMPT_KEY, optimizationPrompt);

        CacheKey cacheKey = CacheKey.of(provider.modelName(), provider.seed(), CacheKey.Mode.CHAT, request);
        String response = cache.get(cacheKey, String.class);
        if (response == null) {
            logger.info("Optimizing ({}):", provider.modelName());
            response = llm.chat(request);
            cache.put(cacheKey, response);
        }
        response = extractPromptFromResponse(response);
        return response;
    }

    @Override
    protected AbstractPromptOptimizer copyOf(AbstractPromptOptimizer original) {
        return new SimpleOptimizer(threads, cache, provider, optimizationPrompt, template);
    }
}
