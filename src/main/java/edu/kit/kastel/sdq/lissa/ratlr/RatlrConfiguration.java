package edu.kit.kastel.sdq.lissa.ratlr;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record RatlrConfiguration(@JsonProperty("source_artifact_provider") ModuleConfiguration sourceArtifactProvider,
                                 @JsonProperty("target_artifact_provider") ModuleConfiguration targetArtifactProvider,
                                 @JsonProperty("source_preprocessor") ModuleConfiguration sourcePreprocessor,
                                 @JsonProperty("target_preprocessor") ModuleConfiguration targetPreprocessor,
                                 @JsonProperty("embedding_creator") ModuleConfiguration embeddingCreator,
                                 @JsonProperty("source_store") ModuleConfiguration sourceStore, @JsonProperty("target_store") ModuleConfiguration targetStore,
                                 @JsonProperty("classifier") ModuleConfiguration classifier,
                                 @JsonProperty("result_aggregator") ModuleConfiguration resultAggregator) {

    public record ModuleConfiguration(@JsonProperty String name, @JsonProperty("args") Map<String, String> arguments) {
    }
}
