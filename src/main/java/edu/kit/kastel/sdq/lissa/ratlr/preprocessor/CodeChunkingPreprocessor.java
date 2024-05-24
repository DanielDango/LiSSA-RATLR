package edu.kit.kastel.sdq.lissa.ratlr.preprocessor;

import edu.kit.kastel.sdq.lissa.ratlr.RatlrConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.cache.Cache;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheManager;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Artifact;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CodeChunkingPreprocessor extends CachedPreprocessor {

    private final RecursiveSplitter.Language language;
    private final int chunkSize;

    public CodeChunkingPreprocessor(RatlrConfiguration.ModuleConfiguration configuration) {
        this.language = Objects.requireNonNull(RecursiveSplitter.Language.valueOf(configuration.arguments().get("language")));
        this.chunkSize = Integer.parseInt(Objects.requireNonNull(configuration.arguments().getOrDefault("chunk_size", "60")));
    }

    @Override
    protected Cache createCache() {
        return CacheManager.getInstance().getCache(this.getClass().getSimpleName() + language + chunkSize);
    }

    protected List<Element> preprocessIntern(Artifact artifact) {
        List<String> segments = RecursiveSplitter.fromLanguage(language, chunkSize).splitText(artifact.getContent());
        List<Element> elements = new ArrayList<>();

        Element artifactAsElement = new Element(artifact.getIdentifier(), artifact.getType(), artifact.getContent(), 0, null, false);
        elements.add(artifactAsElement);

        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i);
            Element segmentAsElement = new Element(artifact.getIdentifier() + SEPARATOR + i, artifact.getType(), segment, 1, artifact, true);
            elements.add(segmentAsElement);
        }

        return elements;
    }
}
