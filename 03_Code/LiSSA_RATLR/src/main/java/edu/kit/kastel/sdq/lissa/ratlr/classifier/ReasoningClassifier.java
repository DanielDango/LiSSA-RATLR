/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.classifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.kit.kastel.sdq.lissa.ratlr.cache.Cache;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheKey;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheManager;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;
import edu.kit.kastel.sdq.lissa.ratlr.utils.KeyGenerator;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * A classifier that uses a language model to reason about trace links between elements.
 * This classifier employs a chat-based approach to determine if elements are related,
 * using configurable prompts and caching to improve performance.
 */
public class ReasoningClassifier extends Classifier {
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
     * The prompt template used for classification requests.
     */
    private final String prompt;

    /**
     * Whether to use original artifacts instead of nested elements.
     */
    private final boolean useOriginalArtifacts;

    /**
     * Whether to include a system message in the chat.
     */
    private final boolean useSystemMessage;

    /**
     * Creates a new reasoning classifier with the specified configuration.
     *
     * @param configuration The module configuration containing classifier settings
     */
    public ReasoningClassifier(ModuleConfiguration configuration) {
        super(ChatLanguageModelProvider.threads(configuration));
        this.provider = new ChatLanguageModelProvider(configuration);
        this.cache = CacheManager.getDefaultInstance()
                .getCache(this.getClass().getSimpleName() + "_" + provider.modelName() + "_" + provider.seed());
        this.prompt = configuration.argumentAsStringByEnumIndex("prompt", 0, Prompt.values(), it -> it.promptTemplate);
        this.useOriginalArtifacts = configuration.argumentAsBoolean("use_original_artifacts", false);
        this.useSystemMessage = configuration.argumentAsBoolean("use_system_message", true);
        this.llm = this.provider.createChatModel();
    }

    /**
     * Creates a new reasoning classifier with the specified parameters.
     * This constructor is used internally for creating thread-local copies.
     *
     * @param threads The number of threads to use for parallel processing
     * @param cache The cache to use for storing classification results
     * @param provider The language model provider
     * @param prompt The prompt template to use
     * @param useOriginalArtifacts Whether to use original artifacts
     * @param useSystemMessage Whether to include a system message
     */
    private ReasoningClassifier(
            int threads,
            Cache cache,
            ChatLanguageModelProvider provider,
            String prompt,
            boolean useOriginalArtifacts,
            boolean useSystemMessage) {
        super(threads);
        this.cache = cache;
        this.provider = provider;
        this.prompt = prompt;
        this.useOriginalArtifacts = useOriginalArtifacts;
        this.useSystemMessage = useSystemMessage;
        this.llm = this.provider.createChatModel();
    }

    @Override
    protected final Classifier copyOf() {
        return new ReasoningClassifier(threads, cache, provider, prompt, useOriginalArtifacts, useSystemMessage);
    }

    /**
     * Classifies a pair of elements by using the language model to reason about their relationship.
     * The classification result is cached to avoid redundant LLM calls.
     *
     * @param source The source element
     * @param target The target element
     * @return A classification result if the elements are related, empty otherwise
     */
    @Override
    protected final Optional<ClassificationResult> classify(Element source, Element target) {
        var targetToConsider = target;
        if (useOriginalArtifacts) {
            while (targetToConsider.getParent() != null) {
                targetToConsider = targetToConsider.getParent();
            }
        }

        var sourceToConsider = source;
        /* TODO Maybe reactivate the sourceToConsider in the future ..
        if(useOriginalArtifacts){
            while (sourceToConsider.getParent() != null) {
                sourceToConsider = sourceToConsider.getParent();
            }
        }
        */

        String llmResponse = classifyIntern(sourceToConsider, targetToConsider);
        boolean isRelated = isRelated(llmResponse);
        if (isRelated) {
            return Optional.of(ClassificationResult.of(source, targetToConsider));
        }
        return Optional.empty();
    }

    /**
     * Determines if the language model's response indicates a trace link.
     * The response is expected to contain a trace tag with "yes" or "no".
     *
     * @param llmResponse The response from the language model
     * @return true if the response indicates a trace link, false otherwise
     */
    private boolean isRelated(String llmResponse) {
        Pattern pattern = Pattern.compile("<trace>(.*?)</trace>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(llmResponse);
        boolean related = false;
        if (matcher.find()) {
            related = matcher.group().toLowerCase().contains("yes");
        } else {
            logger.debug("No trace tag found in response: {}", llmResponse);
        }
        return related;
    }

    /**
     * Performs the actual classification using the language model.
     * The result is cached to avoid redundant LLM calls.
     *
     * @param source The source element
     * @param target The target element
     * @return The language model's response
     */
    private String classifyIntern(Element source, Element target) {
        List<ChatMessage> messages = new ArrayList<>();
        if (useSystemMessage)
            messages.add(new SystemMessage(
                    "Your job is to determine if there is a traceability link between two artifacts of a system."));

        String request = prompt.replace("{source_type}", source.getType())
                .replace("{source_content}", source.getContent())
                .replace("{target_type}", target.getType())
                .replace("{target_content}", target.getContent());
        messages.add(new UserMessage(request));

        // TODO Don't rely on messages.toString() as it is not stable
        String key = KeyGenerator.generateKey(messages.toString());
        CacheKey cacheKey =
                new CacheKey(provider.modelName(), provider.seed(), CacheKey.Mode.CHAT, messages.toString(), key);

        String cachedResponse = cache.get(cacheKey, String.class);
        if (cachedResponse != null) {
            return cachedResponse;
        } else {
            logger.info(
                    "Classifying ({}): {} and {}",
                    provider.modelName(),
                    source.getIdentifier(),
                    target.getIdentifier());
            ChatResponse response = llm.chat(messages);
            String responseText = response.aiMessage().text();
            cache.put(cacheKey, responseText);
            return responseText;
        }
    }

    /**
     * Defines the available prompt templates for classification.
     */
    private enum Prompt {
        /**
         * Basic prompt that asks for reasoning about trace links.
         */
        REASON_WITH_NAME(
                "Below are two artifacts from the same software system. Is there a traceability link between (1) and (2)? Give your reasoning and then answer with 'yes' or 'no' enclosed in <trace> </trace>.\n (1) {source_type}: '''{source_content}''' \n (2) {target_type}: '''{target_content}''' "),

        /**
         * Prompt that asks for conceivable trace links.
         */
        REASON_WITH_NAME_CONCEIVABLE(
                "Below are two artifacts from the same software system. Is there a conceivable traceability link between (1) and (2)? Give your reasoning and then answer with 'yes' or 'no' enclosed in <trace> </trace>.\n (1) {source_type}: '''{source_content}''' \n (2) {target_type}: '''{target_content}''' "),

        /**
         * Prompt that requires high certainty for positive responses.
         */
        REASON_WITH_NAME_YES_IF_CERTAIN(
                "Below are two artifacts from the same software system.\n Is there a traceability link between (1) and (2)? Give your reasoning and then answer with 'yes' or 'no' enclosed in <trace> </trace>. Only answer yes if you are absolutely certain.\n (1) {source_type}: '''{source_content}''' \n (2) {target_type}: '''{target_content}''' ");

        /**
         * The template string for this prompt.
         */
        private final String promptTemplate;

        /**
         * Creates a new prompt with the specified template.
         *
         * @param promptTemplate The template string for the prompt
         */
        Prompt(String promptTemplate) {
            this.promptTemplate = promptTemplate;
        }
    }
}
