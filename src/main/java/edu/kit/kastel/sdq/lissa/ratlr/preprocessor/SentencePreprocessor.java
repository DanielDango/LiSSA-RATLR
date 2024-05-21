package edu.kit.kastel.sdq.lissa.ratlr.preprocessor;

import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import edu.kit.kastel.sdq.lissa.ratlr.RatlrConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.cache.Cache;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheManager;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Artifact;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SentencePreprocessor extends Preprocessor {

    private final Cache cache;

    public SentencePreprocessor(RatlrConfiguration.ModuleConfiguration configuration) {
        this.cache = CacheManager.getInstance().getCache(this.getClass().getSimpleName());
    }

    @Override
    public List<Element> preprocess(Artifact artifact) {
        String data = artifact.getContent() + artifact.getContent();
        String key = UUID.nameUUIDFromBytes(data.getBytes(StandardCharsets.UTF_8)).toString();

        Preprocessed cachedPreprocessed = cache.get(key, Preprocessed.class);
        if (cachedPreprocessed != null) {
            return cachedPreprocessed.elements();
        }

        Preprocessed preprocessed = preprocessIntern(artifact);
        cache.put(key, preprocessed);
        return preprocessed.elements();
    }

    private Preprocessed preprocessIntern(Artifact artifact) {
        DocumentBySentenceSplitter splitter = new DocumentBySentenceSplitter(Integer.MAX_VALUE, 0);
        String[] sentences = splitter.split(artifact.getContent());
        List<Element> elements = new ArrayList<>();
        for (int i = 0; i < sentences.length; i++) {
            String sentence = sentences[i];
            Element sentenceAsElement = new Element(artifact.getIdentifier() + SEPARATOR + i, artifact.getType(), sentence, 1, artifact, true);
            elements.add(sentenceAsElement);
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
