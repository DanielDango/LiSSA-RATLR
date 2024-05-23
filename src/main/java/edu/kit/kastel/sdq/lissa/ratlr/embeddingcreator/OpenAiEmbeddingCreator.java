package edu.kit.kastel.sdq.lissa.ratlr.embeddingcreator;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import edu.kit.kastel.sdq.lissa.ratlr.RatlrConfiguration;

public class OpenAiEmbeddingCreator extends CachedEmbeddingCreator {

    public OpenAiEmbeddingCreator(RatlrConfiguration.ModuleConfiguration configuration) {
        super(configuration.arguments().getOrDefault("model", "text-embedding-ada-002"));
    }

    @Override
    protected EmbeddingModel createEmbeddingModel(String model) {
        String openAiOrganizationId = System.getenv("OPENAI_ORGANIZATION_ID");
        String openAiApiKey = System.getenv("OPENAI_API_KEY");
        if (openAiOrganizationId == null || openAiApiKey == null) {
            throw new IllegalStateException("OPENAI_ORGANIZATION_ID or OPENAI_API_KEY environment variable not set");
        }
        return new OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder().modelName(model).organizationId(openAiOrganizationId).apiKey(openAiApiKey).build();
    }
}
