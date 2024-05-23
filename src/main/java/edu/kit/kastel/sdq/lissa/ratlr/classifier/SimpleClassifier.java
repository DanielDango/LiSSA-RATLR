package edu.kit.kastel.sdq.lissa.ratlr.classifier;

import dev.langchain4j.model.chat.ChatLanguageModel;
import edu.kit.kastel.sdq.lissa.ratlr.cache.Cache;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheManager;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

abstract class SimpleClassifier extends Classifier {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final String TEMPLATE = """
            Question: Here are two parts of software development artifacts. \n
            {source_type}: '''{source_content}''' \n
            {target_type}: '''{target_content}'''
            Are they related? \n
            Answer with 'yes' or 'no'.
            """;

    private final Cache cache;
    private final ChatLanguageModel llm;

    protected SimpleClassifier(String model) {
        this.cache = CacheManager.getInstance().getCache(this.getClass().getSimpleName() + "_" + model);
        this.llm = createChatModel(model);
    }

    protected abstract ChatLanguageModel createChatModel(String model);

    @Override
    protected final ClassificationResult classify(Element source, List<Element> targets) {
        List<Element> relatedTargets = new ArrayList<>();

        for (var target : targets) {
            String llmResponse = classify(source, target);
            boolean isRelated = llmResponse.toLowerCase().contains("yes");
            if (isRelated) {
                relatedTargets.add(target);
            }
        }
        return new ClassificationResult(source, relatedTargets);
    }

    private String classify(Element source, Element target) {
        String key = UUID.nameUUIDFromBytes((source.getContent() + target.getContent()).getBytes()).toString();
        String cachedResponse = cache.get(key, String.class);
        if (cachedResponse != null) {
            return cachedResponse;
        } else {
            logger.info("Classifying: {} and {}", source.getIdentifier(), target.getIdentifier());
            String response = llm.generate(TEMPLATE.replace("{source_type}", source.getType())
                    .replace("{source_content}", source.getContent())
                    .replace("{target_type}", target.getType())
                    .replace("{target_content}", target.getContent()));
            cache.put(key, response);
            return response;
        }
    }

}
