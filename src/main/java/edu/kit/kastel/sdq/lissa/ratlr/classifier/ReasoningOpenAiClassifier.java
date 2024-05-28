package edu.kit.kastel.sdq.lissa.ratlr.classifier;

import dev.langchain4j.model.chat.ChatLanguageModel;
import edu.kit.kastel.sdq.lissa.ratlr.Configuration;

public class ReasoningOpenAiClassifier extends ReasoningClassifier {
    public ReasoningOpenAiClassifier(Configuration.ModuleConfiguration configuration) {
        super(configuration, configuration.argumentAsString("model", "gpt-3.5-turbo-0125"));
    }

    @Override
    protected ChatLanguageModel createChatModel(String model) {
        return ChatModels.createOpenAiChatModel(model);
    }
}
