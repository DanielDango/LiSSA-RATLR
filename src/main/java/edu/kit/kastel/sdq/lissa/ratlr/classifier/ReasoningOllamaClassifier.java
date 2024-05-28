package edu.kit.kastel.sdq.lissa.ratlr.classifier;

import dev.langchain4j.model.chat.ChatLanguageModel;
import edu.kit.kastel.sdq.lissa.ratlr.Configuration;

public class ReasoningOllamaClassifier extends ReasoningClassifier {
    public ReasoningOllamaClassifier(Configuration.ModuleConfiguration configuration) {
        super(configuration, configuration.argumentAsString("model", "llama3:8b"));
    }

    @Override
    protected ChatLanguageModel createChatModel(String model) {
        return ChatModels.createOllamaChatModel(model);
    }
}
