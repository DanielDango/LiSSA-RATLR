package edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer;

import dev.langchain4j.model.chat.ChatModel;
import edu.kit.kastel.sdq.lissa.ratlr.cache.Cache;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheKey;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheManager;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.ChatLanguageModelProvider;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.ElementStore;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;
import edu.kit.kastel.sdq.lissa.ratlr.utils.KeyGenerator;

public class SimpleOptimizer extends AbstractPromptOptimizer {

    /**
     * The default template for classification requests.
     * This template presents two artifacts and asks if they are related.
     */
    private static final String DEFAULT_TEMPLATE =
            """
                    Optimize the following prompt to achieve better classification results for traceability link recovery.
                    Traceability links are to be found in the domain of {source_type} to {target_type}.
                    Do not modify the input and output formats specified by the original prompt.
                    Enclose your optimized prompt with <prompt> </prompt> brackets.
                    The original prompt is provided below:
                    '''{original_prompt}'''
                    """;

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
     * Creates a new simple optimizer with the specified configuration.
     *
     * @param configuration The module configuration containing optimizer settings
     */
    public SimpleOptimizer(ModuleConfiguration configuration) {
        super(ChatLanguageModelProvider.threads(configuration));
        this.provider = new ChatLanguageModelProvider(configuration);
        this.template = configuration.argumentAsString("optimization_template", DEFAULT_TEMPLATE);
        this.cache = CacheManager.getDefaultInstance()
                .getCache(this.getClass().getSimpleName() + "_" + provider.modelName() + "_" + provider.seed());
        this.llm = provider.createChatModel();
    }

    private SimpleOptimizer(int threads, Cache cache, ChatLanguageModelProvider provider, String template) {
        super(threads);
        this.cache = cache;
        this.provider = provider;
        this.template = template;
        this.llm = provider.createChatModel();
    }

    @Override
    public String optimize(ElementStore sourceStore, ElementStore targetStore, String prompt) {
        Element source = sourceStore.getAllElements(true).get(0).first();
        Element target = targetStore.findSimilar(sourceStore.getAllElements(true).get(0).second()).get(0);
        String request = template
                .replace("{source_type}", source.getType())
                .replace("{target_type}", target.getType())
                .replace("{original_prompt}", prompt);

        String key = KeyGenerator.generateKey(request);
        CacheKey cacheKey = new CacheKey(provider.modelName(), provider.seed(), CacheKey.Mode.CHAT, request, key);
        String cachedResponse = cache.get(cacheKey, String.class);
        if (cachedResponse != null) {
            return cachedResponse;
        }
        else {
            logger.info("Optiming ({}): {}",
                    provider.modelName(),
                    prompt);
            String response = llm.chat(request);
            cache.put(cacheKey, response);
            return response;
        }
    }

    @Override
    protected AbstractPromptOptimizer copyOf(AbstractPromptOptimizer original) {
        return new SimpleOptimizer(threads, cache, provider, template);
    }
}
