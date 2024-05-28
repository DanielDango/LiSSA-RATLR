package edu.kit.kastel.sdq.lissa.ratlr.artifactprovider;

import edu.kit.kastel.sdq.lissa.ratlr.RatlrConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Artifact;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Scanner;

public class RecursiveTextArtifactProvider extends TextArtifactProvider {

    private final String[] extensions;

    public RecursiveTextArtifactProvider(RatlrConfiguration.ModuleConfiguration configuration) {
        super(configuration);
        this.extensions = Objects.requireNonNull(configuration.arguments().get("extensions")).toLowerCase().split(",");
    }

    @Override
    protected void loadFiles() {
        try {
            Files.walk(this.path.toPath()).forEach(it -> {
                if (Files.isRegularFile(it) && hasCorrectExtension(it)) {
                    try (Scanner scan = new Scanner(it.toFile()).useDelimiter("\\A")) {
                        String content = scan.next();
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

    private boolean hasCorrectExtension(Path file) {
        String fileName = file.getFileName().toString().toLowerCase();
        for (String extension : extensions) {
            if (fileName.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }
}
