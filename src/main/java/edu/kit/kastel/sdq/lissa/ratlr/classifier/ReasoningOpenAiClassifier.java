package edu.kit.kastel.sdq.lissa.ratlr.classifier;

import dev.langchain4j.model.chat.ChatLanguageModel;
import edu.kit.kastel.sdq.lissa.ratlr.Configuration;
import edu.kit.kastel.sdq.lissa.ratlr.cache.Cache;

public class ReasoningOpenAiClassifier extends ReasoningClassifier {
    public ReasoningOpenAiClassifier(Configuration.ModuleConfiguration configuration) {
        super(configuration, configuration.argumentAsString("model", "gpt-4o-mini"));
    }

    protected ReasoningOpenAiClassifier(Cache cache, String model, ChatLanguageModel llm, String prompt, boolean useOriginalArtifacts,
            boolean useSystemMessage) {
        super(cache, model, llm, prompt, useOriginalArtifacts, useSystemMessage);
    }

    @Override
    protected ChatLanguageModel createChatModel(String model) {
        return ChatModels.createOpenAiChatModel(model);
    }

    @Override
    protected ReasoningClassifier copyOf(Cache cache, String model, ChatLanguageModel llm, String prompt, boolean useOriginalArtifacts,
            boolean useSystemMessage) {
        return new ReasoningOpenAiClassifier(cache, model, llm, prompt, useOriginalArtifacts, useSystemMessage);
    }
}
