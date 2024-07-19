package edu.kit.kastel.sdq.lissa.ratlr.classifier;

import dev.langchain4j.model.chat.ChatLanguageModel;
import edu.kit.kastel.sdq.lissa.ratlr.Configuration;
import edu.kit.kastel.sdq.lissa.ratlr.cache.Cache;

public class SimpleOpenAiClassifier extends SimpleClassifier {
    public SimpleOpenAiClassifier(Configuration.ModuleConfiguration configuration) {
        super(configuration, configuration.argumentAsString("model", "gpt-4o-mini"));
    }

    public SimpleOpenAiClassifier(Cache cache, String model, ChatLanguageModel llm, String template) {
        super(cache, model, llm, template);
    }

    @Override
    protected ChatLanguageModel createChatModel(String model) {
        return ChatModels.createOpenAiChatModel(model);
    }

    @Override
    protected SimpleClassifier copyOf(Cache cache, String model, ChatLanguageModel llm, String template) {
        return new SimpleOpenAiClassifier(cache, model, llm, template);
    }
}
