package edu.kit.kastel.sdq.lissa.ratlr;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kit.kastel.sdq.lissa.ratlr.artifactprovider.ArtifactProvider;
import edu.kit.kastel.sdq.lissa.ratlr.embeddingcreator.EmbeddingCreator;
import edu.kit.kastel.sdq.lissa.ratlr.preprocessor.Preprocessor;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class Main {
    public static void main(String[] args) throws IOException {
        RatlrConfiguration configuration = new ObjectMapper().readValue(new File("config.json"), RatlrConfiguration.class);

        ArtifactProvider sourceArtifactProvider = ArtifactProvider.createArtifactProvider(configuration.sourceArtifactProvider());
        ArtifactProvider targetArtifactProvider = ArtifactProvider.createArtifactProvider(configuration.targetArtifactProvider());

        Preprocessor sourcePreprocessor = Preprocessor.createPreprocessor(configuration.sourcePreprocessor());
        Preprocessor targetPreprocessor = Preprocessor.createPreprocessor(configuration.targetPreprocessor());

        EmbeddingCreator embeddingCreator = EmbeddingCreator.createEmbeddingCreator(configuration.embeddingCreator());

        // RUN
        var sourceArtifacts = sourceArtifactProvider.getArtifacts();
        var targetArtifacts = targetArtifactProvider.getArtifacts();
        var sourceElements = sourceArtifacts.stream().map(sourcePreprocessor::preprocess).flatMap(Collection::stream).toList();
        var targetElements = targetArtifacts.stream().map(targetPreprocessor::preprocess).flatMap(Collection::stream).toList();

        var sourceEmbeddings = embeddingCreator.calculateEmbeddings(sourceElements);
        var targetEmbeddings = embeddingCreator.calculateEmbeddings(targetElements);

    }
}
