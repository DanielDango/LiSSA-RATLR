/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.utils;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.kit.kastel.sdq.lissa.ratlr.cache.Cache;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheKey;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.ChatLanguageModelProvider;

import dev.langchain4j.model.chat.ChatModel;

public class ChatLanguageModelUtils {

    private static final Logger logger = LoggerFactory.getLogger(ChatLanguageModelUtils.class);

    private ChatLanguageModelUtils() {
        throw new IllegalAccessError("Utility class");
    }

    /**
     * Sends multiple requests to the language model and caches the responses.
     * Todo: The number of requests is currently used as part of the request string for caching.
     * Todo: Maybe langchain4j provides a better way to handle multiple responses?
     *
     * @param request The request to send to the language model
     * @param provider The chat language model provider
     * @param llm The chat model instance
     * @param cache The cache instance to use for caching responses
     * @param numberOfRequests The number of requests to send to the language model
     * @return A list of replies from the language model
     */
    public static List<String> nCachedRequest(
            String request, ChatLanguageModelProvider provider, ChatModel llm, Cache cache, int numberOfRequests) {
        CacheKey cacheKey = CacheKey.of(
                provider.modelName(), provider.seed(), CacheKey.Mode.CHAT, numberOfRequests + " results: \n" + request);
        List<String> responses = cache.get(cacheKey, List.class);
        if (responses == null || responses.size() < numberOfRequests) {
            responses = new ArrayList<>();
            logger.info("Optimizing ({}) with {} requests", provider.modelName(), numberOfRequests);
            for (int i = 1; i <= numberOfRequests; i++) {
                responses.add(llm.chat(request));
            }
            cache.put(cacheKey, responses);
        }
        logger.debug("Responses: {}", responses);
        return responses;
    }

    /**
     * A wrapper for sending a single cached request to the language model using the
     * {@link #nCachedRequest(String, ChatLanguageModelProvider, ChatModel, Cache, int)} method.
     *
     * @param request The request to send to the language model
     * @param provider The chat language model provider
     * @param llm The chat model instance
     * @param cache The cache instance to use for caching responses
     * @return The reply from the language model
     */
    public static String cachedRequest(String request, ChatLanguageModelProvider provider, ChatModel llm, Cache cache) {
        return nCachedRequest(request, provider, llm, cache, 1).getFirst();
    }
}
