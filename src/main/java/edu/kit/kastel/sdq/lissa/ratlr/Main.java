package edu.kit.kastel.sdq.lissa.ratlr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.kit.kastel.sdq.lissa.ratlr.artifactprovider.ArtifactProvider;
import edu.kit.kastel.sdq.lissa.ratlr.preprocessor.Preprocessor;

import java.util.Collection;

public class Main {
    public static void main(String[] args) throws JsonProcessingException {
        RatlrConfiguration configuration = new ObjectMapper().readValue("config.json", RatlrConfiguration.class);

        ArtifactProvider sourceArtifactProvider = ArtifactProvider.createArtifactProvider(configuration.sourceArtifactProvider());
        ArtifactProvider targetArtifactProvider = ArtifactProvider.createArtifactProvider(configuration.targetArtifactProvider());

        Preprocessor sourcePreprocessor = Preprocessor.createPreprocessor(configuration.sourcePreprocessor());
        Preprocessor targetPreprocessor = Preprocessor.createPreprocessor(configuration.targetPreprocessor());

        // RUN
        var sourceArtifacts = sourceArtifactProvider.getArtifacts();
        var targetArtifacts = targetArtifactProvider.getArtifacts();
        var sourceElements = sourceArtifacts.stream().map(sourcePreprocessor::preprocess).flatMap(Collection::stream).toList();
        var targetElements = targetArtifacts.stream().map(targetPreprocessor::preprocess).flatMap(Collection::stream).toList();

    }
}
