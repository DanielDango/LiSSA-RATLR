package edu.kit.kastel.sdq.lissa.ratlr.artifactprovider;

import edu.kit.kastel.sdq.lissa.ratlr.RatlrConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Artifact;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class TextArtifactProvider extends ArtifactProvider {

    private final File path;
    private final Artifact.ArtifactType artifactType;
    private final List<Artifact> artifacts;

    public TextArtifactProvider(RatlrConfiguration.ModuleConfiguration configuration) {
        this.path = new File(configuration.arguments().get("path"));
        if (!path.exists()) {
            throw new IllegalArgumentException("Path does not exist");
        }
        this.artifactType = Artifact.ArtifactType.from(configuration.arguments().get("artifact_type"));
        this.artifacts = new ArrayList<>();
        this.loadFiles();
    }

    private void loadFiles() {
        try {
            Files.walk(path.toPath(), FileVisitOption.FOLLOW_LINKS).forEach(it -> {
                if (Files.isRegularFile(it)) {
                    try {
                        String content = Files.readString(it);
                        artifacts.add(new Artifact(it.getFileName().toString(), artifactType, content));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public List<Artifact> getArtifacts() {
        return new ArrayList<>(artifacts);
    }

    @Override
    public Artifact getArtifact(String identifier) {
        return artifacts.stream()
                .filter(it -> it.getIdentifier().equals(identifier))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Artifact not found"));
    }
}
