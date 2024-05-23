package edu.kit.kastel.sdq.lissa.ratlr.classifier;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import edu.kit.kastel.sdq.lissa.ratlr.RatlrConfiguration;

public class SimpleOpenAiClassifier extends SimpleClassifier {
    public SimpleOpenAiClassifier(RatlrConfiguration.ModuleConfiguration configuration) {
        super(configuration.arguments().getOrDefault("model", "gpt-3.5-turbo-0125"));
    }

    @Override
    protected ChatLanguageModel createChatModel(String model) {
        String openAiOrganizationId = System.getenv("OPENAI_ORGANIZATION_ID");
        String openAiApiKey = System.getenv("OPENAI_API_KEY");
        if (openAiOrganizationId == null || openAiApiKey == null) {
            throw new IllegalStateException("OPENAI_ORGANIZATION_ID or OPENAI_API_KEY environment variable not set");
        }
        return new OpenAiChatModel.OpenAiChatModelBuilder().modelName(model).organizationId(openAiOrganizationId).apiKey(openAiApiKey).temperature(0.0).build();
    }
}
