/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.elementstore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.elementstore.strategy.RetrievalStrategy;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;
import edu.kit.kastel.sdq.lissa.ratlr.utils.Pair;

/**
 * A store for elements and their embeddings in the LiSSA framework.
 * This class manages a collection of elements and their associated vector embeddings,
 * providing functionality for similarity search and element retrieval as part of
 * LiSSA's trace link analysis approach.
 * <p>
 * The store can operate in two distinct roles within the LiSSA pipeline:
 * <ul>
 *     <li>{@link SourceElementStore}</li>
 *     <li>{@link TargetElementStore}</li>
 * </ul>
 */
public abstract class ElementStore {

    /**
     * Maps element identifiers to their corresponding elements and embeddings.
     * Used by LiSSA to maintain the relationship between elements and their vector representations.
     */
    private final Map<String, Pair<Element, float[]>> idToElementWithEmbedding;

    /**
     * List of all elements and their embeddings.
     * Used by LiSSA to maintain the order and full set of elements for processing.
     */
    private final List<Pair<Element, float[]>> elementsWithEmbedding;

    /**
     * Creates a new element store for the LiSSA framework.
     *
     * @param configuration The configuration of the module
     * @param similarityRetriever Whether this store should be a target store (true) or source store (false).
     *                           Target stores support similarity search but limit results.
     *                           Source stores allow retrieving all elements but don't support similarity search.
     * @throws IllegalArgumentException If max_results is less than 1 in target store mode
     */
    //TODO: Nur im Target Store
    public ElementStore(ModuleConfiguration configuration, boolean similarityRetriever) {
        if (!similarityRetriever && !"custom".equals(configuration.name())) {
            RetrievalStrategy.logger.error(
                    "The element store is created in source store mode, but the retrieval strategy is not set to \"custom\". This is likely a configuration error as source stores do not use retrieval strategies.");
        }

        elementsWithEmbedding = new ArrayList<>();
        idToElementWithEmbedding = new HashMap<>();
    }

    /**
     * Creates a new element store with the provided content.
     * This constructor is used for initializing the store with existing elements and their embeddings.
     *
     * @param content List of pairs containing elements and their embeddings
     */
    protected ElementStore(List<Pair<Element, float[]>> content) {

        elementsWithEmbedding = new ArrayList<>();
        idToElementWithEmbedding = new HashMap<>();
        List<Element> elements = new ArrayList<>();
        List<float[]> embeddings = new ArrayList<>();
        for (var pair : content) {
            var element = pair.first();
            var embedding = pair.second();
            elements.add(element);
            embeddings.add(Arrays.copyOf(embedding, embedding.length));
        }
        setup(elements, embeddings);
    }

    /**
     * Initializes the element store with elements and their embeddings for LiSSA's processing.
     *
     * @param elements List of elements to store
     * @param embeddings List of embeddings corresponding to the elements
     * @throws IllegalStateException If the store is already initialized
     * @throws IllegalArgumentException If the number of elements and embeddings don't match
     */
    public void setup(List<Element> elements, List<float[]> embeddings) {
        if (!elementsWithEmbedding.isEmpty() || !idToElementWithEmbedding.isEmpty()) {
            throw new IllegalStateException("The element store is already set up.");
        }

        if (elements.size() != embeddings.size()) {
            throw new IllegalArgumentException("The number of elements and embeddings must be equal.");
        }

        for (int i = 0; i < elements.size(); i++) {
            var element = elements.get(i);
            var embedding = embeddings.get(i);
            var pair = new Pair<>(element, embedding);
            elementsWithEmbedding.add(pair);
            idToElementWithEmbedding.put(element.getIdentifier(), pair);
        }
    }

    /**
     * Retrieves an element and its embedding by its identifier.
     * Available in both source and target store modes for LiSSA's element lookup.
     *
     * @param id The identifier of the element to retrieve
     * @return A pair containing the element and its embedding, or null if not found
     */
    public @Nullable Pair<Element, float[]> getById(String id) {
        var element = idToElementWithEmbedding.get(id);
        if (element == null) {
            return null;
        }
        return new Pair<>(element.first(), Arrays.copyOf(element.second(), element.second().length));
    }

    /**
     * Retrieves all elements that have a specific parent element.
     * Available in both source and target store modes for LiSSA's hierarchical analysis.
     *
     * @param parentId The identifier of the parent element
     * @return List of pairs containing elements and their embeddings
     */
    public List<Pair<Element, float[]>> getElementsByParentId(String parentId) {
        List<Pair<Element, float[]>> elements = new ArrayList<>();
        for (Pair<Element, float[]> element : elementsWithEmbedding) {
            if (element.first().getParent() != null
                    && element.first().getParent().getIdentifier().equals(parentId)) {
                elements.add(new Pair<>(element.first(), Arrays.copyOf(element.second(), element.second().length)));
            }
        }
        return elements;
    }

    /**
     * Internal method to retrieve all elements.
     * Available in both source and target store modes for LiSSA's internal processing.
     *
     * @param onlyCompare If true, only returns elements marked for comparison
     * @return List of pairs containing elements and their embeddings
     */
    protected List<Pair<Element, float[]>> getAllElementsIntern(boolean onlyCompare) {
        List<Pair<Element, float[]>> elements = new ArrayList<>();
        for (Pair<Element, float[]> element : elementsWithEmbedding) {
            if (!onlyCompare || element.first().isCompare()) {
                elements.add(new Pair<>(element.first(), Arrays.copyOf(element.second(), element.second().length)));
            }
        }
        return elements;
    }

    protected int size() {
        return elementsWithEmbedding.size();
    }
}
