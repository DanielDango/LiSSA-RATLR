package edu.kit.kastel.sdq.lissa.ratlr.preprocessor;

import edu.kit.kastel.sdq.lissa.ratlr.Configuration;
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

    public CodeChunkingPreprocessor(Configuration.ModuleConfiguration configuration) {
        this.language = Objects.requireNonNull(RecursiveSplitter.Language.valueOf(configuration.argumentAsString("language")));
        this.chunkSize = configuration.argumentAsInt("chunk_size", 60);
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
            Element segmentAsElement = new Element(artifact.getIdentifier() + SEPARATOR + i, artifact.getType(), segment, 1, artifactAsElement, true);
            elements.add(segmentAsElement);
        }

        return elements;
    }
}
