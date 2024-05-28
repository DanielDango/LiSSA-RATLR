package edu.kit.kastel.sdq.lissa.ratlr.artifactprovider;

import edu.kit.kastel.sdq.lissa.ratlr.RatlrConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Artifact;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Knowledge;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.*;

public class TextArtifactProvider extends ArtifactProvider {

    protected final File path;
    protected final Artifact.ArtifactType artifactType;
    protected final List<Artifact> artifacts;

    public TextArtifactProvider(RatlrConfiguration.ModuleConfiguration configuration) {
        this.path = new File(configuration.arguments().get("path"));
        if (!path.exists()) {
            throw new IllegalArgumentException("Path does not exist: " + path.getAbsolutePath());
        }
        this.artifactType = Artifact.ArtifactType.from(configuration.arguments().get("artifact_type"));
        this.artifacts = new ArrayList<>();
    }

    protected void loadFiles() {
        List<File> files = new ArrayList<>();
        if (this.path.isFile()) {
            files.add(this.path);
        } else {
            files.addAll(Arrays.asList(Objects.requireNonNull(this.path.listFiles())));
        }

        files.stream().map(File::toPath).forEach(it -> {
            if (Files.isRegularFile(it)) {
                try (Scanner scan = new Scanner(it.toFile()).useDelimiter("\\A")) {
                    String content = scan.next();
                    artifacts.add(new Artifact(it.getFileName().toString(), artifactType, content));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
    }

    @Override
    public List<Artifact> getArtifacts() {
        if (artifacts.isEmpty())
            this.loadFiles();
        var artifacts = new ArrayList<>(this.artifacts);
        artifacts.sort(Comparator.comparing(Knowledge::getIdentifier));
        return artifacts;
    }

    @Override
    public Artifact getArtifact(String identifier) {
        if (artifacts.isEmpty())
            this.loadFiles();
        return artifacts.stream()
                .filter(it -> it.getIdentifier().equals(identifier))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Artifact not found"));
    }
}
