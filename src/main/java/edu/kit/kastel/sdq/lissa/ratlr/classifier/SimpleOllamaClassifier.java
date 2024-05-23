package edu.kit.kastel.sdq.lissa.ratlr.classifier;

import dev.langchain4j.model.ollama.OllamaChatModel;
import edu.kit.kastel.sdq.lissa.ratlr.RatlrConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.cache.Cache;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheManager;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;
import edu.kit.kastel.sdq.lissa.ratlr.utils.OllamaUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SimpleOllamaClassifier extends Classifier {

    private static final String TEMPLATE = """
            Question: Here are two parts of software development artifacts. \n
            {source_type}: '''{source_content}''' \n
            {target_type}: '''{target_content}'''
            Are they related? \n
            Answer with 'yes' or 'no'.
            """;

    private final Cache cache;
    private final OllamaChatModel ollama;

    public SimpleOllamaClassifier(RatlrConfiguration.ModuleConfiguration configuration) {
        super();
        var model = configuration.arguments().getOrDefault("model", "llama3:8b");
        this.cache = CacheManager.getInstance().getCache(this.getClass().getSimpleName() + "_" + model);

        String host = System.getenv("OLLAMA_HOST");
        String user = System.getenv("OLLAMA_USER");
        String password = System.getenv("OLLAMA_PASSWORD");

        ollama = OllamaChatModel.builder().baseUrl(host).modelName(model).timeout(Duration.ofMinutes(5)).temperature(0.0).build();
        if (user != null && password != null) {
            OllamaUtils.setAuthForOllama(ollama, host, Duration.ofMinutes(5), user, password);
        }
    }

    @Override
    protected ClassificationResult classify(Element source, List<Element> targets) {
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
            String response = ollama.generate(TEMPLATE.replace("{source_type}", source.getType())
                    .replace("{source_content}", source.getContent())
                    .replace("{target_type}", target.getType())
                    .replace("{target_content}", target.getContent()));
            cache.put(key, response);
            return response;
        }
    }
}
