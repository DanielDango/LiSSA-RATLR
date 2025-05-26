/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.classifier;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import edu.kit.kastel.sdq.lissa.ratlr.cache.Cache;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheKey;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheManager;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;
import edu.kit.kastel.sdq.lissa.ratlr.utils.KeyGenerator;

public class ReasoningClassifier extends Classifier {
    private final Cache cache;
    private final ChatLanguageModelProvider provider;

    private final ChatModel llm;
    private final String prompt;
    private final boolean useOriginalArtifacts;
    private final boolean useSystemMessage;

    public ReasoningClassifier(ModuleConfiguration configuration) {
        super(ChatLanguageModelProvider.supportsThreads(configuration) ? DEFAULT_THREAD_COUNT : 1);
        this.provider = new ChatLanguageModelProvider(configuration);
        this.cache = CacheManager.getDefaultInstance()
                .getCache(this.getClass().getSimpleName() + "_" + provider.modelName());
        this.prompt = configuration.argumentAsStringByEnumIndex("prompt", 0, Prompt.values(), it -> it.promptTemplate);
        this.useOriginalArtifacts = configuration.argumentAsBoolean("use_original_artifacts", false);
        this.useSystemMessage = configuration.argumentAsBoolean("use_system_message", true);
        this.llm = this.provider.createChatModel();
    }

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

    @Override
    protected final ClassificationResult classify(Element source, Element target) {
        List<Element> relatedTargets = new ArrayList<>();

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
            return ClassificationResult.of(source, targetToConsider);
        }
        return null;
    }

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

    private enum Prompt {
        REASON_WITH_NAME(
                "Below are two artifacts from the same software system. Is there a traceability link between (1) and (2)? Give your reasoning and then answer with 'yes' or 'no' enclosed in <trace> </trace>.\n (1) {source_type}: '''{source_content}''' \n (2) {target_type}: '''{target_content}''' "),

        REASON_WITH_NAME_CONCEIVABLE(
                "Below are two artifacts from the same software system. Is there a conceivable traceability link between (1) and (2)? Give your reasoning and then answer with 'yes' or 'no' enclosed in <trace> </trace>.\n (1) {source_type}: '''{source_content}''' \n (2) {target_type}: '''{target_content}''' "),

        REASON_WITH_NAME_YES_IF_CERTAIN(
                "Below are two artifacts from the same software system.\n Is there a traceability link between (1) and (2)? Give your reasoning and then answer with 'yes' or 'no' enclosed in <trace> </trace>. Only answer yes if you are absolutely certain.\n (1) {source_type}: '''{source_content}''' \n (2) {target_type}: '''{target_content}''' ");

        private final String promptTemplate;

        Prompt(String promptTemplate) {
            this.promptTemplate = promptTemplate;
        }
    }
}
