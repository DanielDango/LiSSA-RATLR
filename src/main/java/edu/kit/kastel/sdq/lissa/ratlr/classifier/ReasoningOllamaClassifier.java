package edu.kit.kastel.sdq.lissa.ratlr.classifier;

import dev.langchain4j.model.chat.ChatLanguageModel;
import edu.kit.kastel.sdq.lissa.ratlr.RatlrConfiguration;

public class ReasoningOllamaClassifier extends ReasoningClassifier {
    public ReasoningOllamaClassifier(RatlrConfiguration.ModuleConfiguration configuration) {
        super(configuration, configuration.arguments().getOrDefault("model", "llama3:8b"));
    }

    @Override
    protected ChatLanguageModel createChatModel(String model) {
        return ChatModels.createOllamaChatModel(model);
    }
}
