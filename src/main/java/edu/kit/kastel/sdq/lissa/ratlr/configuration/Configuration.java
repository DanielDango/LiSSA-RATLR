/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.configuration;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import edu.kit.kastel.sdq.lissa.ratlr.classifier.Classifier;
import edu.kit.kastel.sdq.lissa.ratlr.utils.KeyGenerator;

import io.soabase.recordbuilder.core.RecordBuilder;

@RecordBuilder()
public record Configuration(
        @JsonProperty("cache_dir") String cacheDir,
        @JsonProperty("gold_standard_configuration") GoldStandardConfiguration goldStandardConfiguration,
        @JsonProperty("source_artifact_provider") ModuleConfiguration sourceArtifactProvider,
        @JsonProperty("target_artifact_provider") ModuleConfiguration targetArtifactProvider,
        @JsonProperty("source_preprocessor") ModuleConfiguration sourcePreprocessor,
        @JsonProperty("target_preprocessor") ModuleConfiguration targetPreprocessor,
        @JsonProperty("embedding_creator") ModuleConfiguration embeddingCreator,
        @JsonProperty("source_store") ModuleConfiguration sourceStore,
        @JsonProperty("target_store") ModuleConfiguration targetStore,
        @JsonProperty("classifier") ModuleConfiguration classifier,
        @JsonProperty("classifiers") List<List<ModuleConfiguration>> classifiers,
        @JsonProperty("result_aggregator") ModuleConfiguration resultAggregator,
        @JsonProperty("tracelinkid_postprocessor") ModuleConfiguration traceLinkIdPostprocessor)
        implements ConfigurationBuilder.With {

    public String serializeAndDestroyConfiguration() throws UncheckedIOException {
        sourceArtifactProvider.finalizeForSerialization();
        targetArtifactProvider.finalizeForSerialization();
        sourcePreprocessor.finalizeForSerialization();
        targetPreprocessor.finalizeForSerialization();
        embeddingCreator.finalizeForSerialization();
        sourceStore.finalizeForSerialization();
        targetStore.finalizeForSerialization();
        if (classifier != null) {
            classifier.finalizeForSerialization();
        }
        if (classifiers != null) {
            for (var group : classifiers) {
                for (var classifier : group) {
                    classifier.finalizeForSerialization();
                }
            }
        }
        resultAggregator.finalizeForSerialization();
        if (traceLinkIdPostprocessor != null) {
            traceLinkIdPostprocessor.finalizeForSerialization();
        }

        try {
            return new ObjectMapper()
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    .writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String toString() {
        return "Configuration{" + "sourceArtifactProvider="
                + sourceArtifactProvider + ", targetArtifactProvider="
                + targetArtifactProvider + ", sourcePreprocessor="
                + sourcePreprocessor + ", targetPreprocessor="
                + targetPreprocessor + ", embeddingCreator="
                + embeddingCreator + ", sourceStore="
                + sourceStore + ", targetStore="
                + targetStore + ", classifier="
                + classifier + ", classifiers="
                + classifiers + ", resultAggregator="
                + resultAggregator + ", traceLinkIdPostprocessor="
                + traceLinkIdPostprocessor + '}';
    }

    public String getConfigurationIdentifierForFile(String prefix) {
        return Objects.requireNonNull(prefix) + "_" + KeyGenerator.generateKey(this.toString());
    }

    public Classifier createClassifier() {
        if ((classifier == null) == (classifiers == null)) {
            throw new IllegalStateException("Either 'classifier' or 'classifiers' must be set, but not both.");
        }

        return classifier != null
                ? Classifier.createClassifier(classifier)
                : Classifier.createMultiStageClassifier(classifiers);
    }
}
