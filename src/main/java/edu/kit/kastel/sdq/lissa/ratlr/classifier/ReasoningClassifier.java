package edu.kit.kastel.sdq.lissa.ratlr.classifier;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import edu.kit.kastel.sdq.lissa.ratlr.cache.Cache;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheManager;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class ReasoningClassifier extends Classifier {
    private final Cache cache;
    private final ChatLanguageModel llm;
    private final String prompt;
    private final boolean useOriginalArtifacts;
    private final boolean useSystemMessage;

    protected ReasoningClassifier(String model, int promptId, boolean useOriginalArtifacts, boolean useSystemMessage) {
        this.cache = CacheManager.getInstance().getCache(this.getClass().getSimpleName() + "_" + model);
        this.prompt = Prompt.values()[promptId].prompt;
        this.useOriginalArtifacts = useOriginalArtifacts;
        this.useSystemMessage = useSystemMessage;
        this.llm = createChatModel(model);
    }

    protected abstract ChatLanguageModel createChatModel(String model);

    @Override
    protected final ClassificationResult classify(Element source, List<Element> targets) {
        List<Element> relatedTargets = new ArrayList<>();

        for (var target : targets) {
            String llmResponse = classify(source, target);
            boolean isRelated = isRelated(llmResponse);
            if (isRelated) {
                relatedTargets.add(target);
            }
        }
        return new ClassificationResult(source, relatedTargets);
    }

    private boolean isRelated(String llmResponse) {
        Pattern pattern = Pattern.compile("<trace>(.*?)</trace>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(llmResponse);
        boolean related = false;
        if (matcher.find()) {
            related = matcher.group().toLowerCase().contains("yes");
        }
        return related;
    }

    private String classify(Element source, Element target) {
        List<ChatMessage> messages = new ArrayList<>();
        if (useSystemMessage)
            messages.add(new SystemMessage("Your job is to determine if there is a traceability link between two artifacts of a system."));

        String request = prompt.replace("{source_type}", source.getType())
                .replace("{source_content}", source.getContent())
                .replace("{target_type}", target.getType())
                .replace("{target_content}", target.getContent());
        messages.add(new UserMessage(request));

        String key = UUID.nameUUIDFromBytes(messages.toString().getBytes(StandardCharsets.UTF_8)).toString();
        String cachedResponse = cache.get(key, String.class);
        if (cachedResponse != null) {
            return cachedResponse;
        } else {
            logger.info("Classifying: {} and {}", source.getIdentifier(), target.getIdentifier());
            Response<AiMessage> response = llm.generate(messages);
            String responseText = response.content().text();
            cache.put(key, responseText);
            return responseText;
        }
    }

    private enum Prompt {
        REASON_WITH_NAME(
                "Below are two artifacts from the same software system. Is there a traceability link between (1) and (2)? Give your reasoning and then answer with 'yes' or 'no' enclosed in <trace> </trace>.\n (1) {source_type}: '''{source_content}''' \n (2) {target_type}: '''{target_content}''' ");

        private final String prompt;

        Prompt(String prompt) {
            this.prompt = prompt;
        }
    }
}
