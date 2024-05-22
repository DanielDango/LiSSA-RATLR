package edu.kit.kastel.sdq.lissa.ratlr.preprocessor;

import edu.kit.kastel.sdq.lissa.ratlr.RatlrConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.cache.Cache;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheManager;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Artifact;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CodeChunkingPreprocessor extends Preprocessor {

    private final Cache cache;
    private final RecursiveSplitter.Language language;
    private final int chunkSize;

    public CodeChunkingPreprocessor(RatlrConfiguration.ModuleConfiguration configuration) {
        this.language = Objects.requireNonNull(RecursiveSplitter.Language.valueOf(configuration.arguments().get("language")));
        this.chunkSize = Integer.parseInt(Objects.requireNonNull(configuration.arguments().getOrDefault("chunkSize", "60")));
        this.cache = CacheManager.getInstance().getCache(this.getClass().getSimpleName() + language + chunkSize);
    }

    @Override
    public List<Element> preprocess(Artifact artifact) {
        String key = UUID.nameUUIDFromBytes(artifact.getContent().getBytes(StandardCharsets.UTF_8)).toString();

        Preprocessed cachedPreprocessed = cache.get(key, Preprocessed.class);
        if (cachedPreprocessed != null) {
            return cachedPreprocessed.elements();
        }

        Preprocessed preprocessed = preprocessIntern(artifact);
        cache.put(key, preprocessed);
        return preprocessed.elements();
    }

    private Preprocessed preprocessIntern(Artifact artifact) {
        List<String> segments = RecursiveSplitter.fromLanguage(language, chunkSize).splitText(artifact.getContent());
        List<Element> elements = new ArrayList<>();

        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i);
            Element segmentAsElement = new Element(artifact.getIdentifier() + SEPARATOR + i, artifact.getType(), segment, 1, artifact, true);
            elements.add(segmentAsElement);
        }

        return new Preprocessed(elements);
    }

    private record Preprocessed(List<Element> elements) {
        public List<Element> elements() {
            this.elements.forEach(it -> it.init(elements.stream().collect(Collectors.toMap(Element::getIdentifier, Function.identity()))));
            return elements;
        }
    }
}
