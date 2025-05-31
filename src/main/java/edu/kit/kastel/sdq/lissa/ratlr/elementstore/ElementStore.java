/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.elementstore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;
import edu.kit.kastel.sdq.lissa.ratlr.utils.Pair;

/**
 * A store for elements and their embeddings in the LiSSA framework.
 * This class manages a collection of elements and their associated vector embeddings,
 * providing functionality for similarity search and element retrieval as part of
 * LiSSA's trace link analysis approach.
 *
 * The store can operate in two distinct roles within the LiSSA pipeline:
 * <ul>
 *     <li><b>Target Store</b> (similarityRetriever = true):
 *         <ul>
 *             <li>Used to store target elements that will be searched for similarity in LiSSA's classification phase</li>
 *             <li>Supports finding similar elements using vector similarity for LiSSA's similarity-based matching</li>
 *             <li>Limited to returning a maximum number of results (configurable)</li>
 *             <li>Cannot retrieve all elements at once</li>
 *         </ul>
 *     </li>
 *     <li><b>Source Store</b> (similarityRetriever = false):
 *         <ul>
 *             <li>Used to store source elements that will be used as queries in LiSSA's classification phase</li>
 *             <li>Does not support similarity search as it's not needed for source elements</li>
 *             <li>Can retrieve all elements at once for LiSSA's batch processing</li>
 *             <li>Supports filtering elements by comparison flag for LiSSA's selective analysis</li>
 *         </ul>
 *     </li>
 * </ul>
 */
public class ElementStore {

    /**
     * Special value for the maximum number of results that indicates no limit.
     * Only applicable for target stores (similarityRetriever = true) in LiSSA's similarity search.
     */
    public static final String MAX_RESULTS_INFINITY_ARGUMENT = "infinity";

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
     * Maximum number of results to return in similarity search.
     * -1 indicates source store mode (no similarity search).
     * Positive values indicate target store mode with a limit on results for LiSSA's similarity matching.
     */
    private final int maxResults;

    /**
     * Creates a new element store for the LiSSA framework.
     *
     * @param configuration The configuration of the module
     * @param similarityRetriever Whether this store should be a target store (true) or source store (false).
     *                           Target stores support similarity search but limit results.
     *                           Source stores allow retrieving all elements but don't support similarity search.
     * @throws IllegalArgumentException If max_results is less than 1 in target store mode
     */
    public ElementStore(ModuleConfiguration configuration, boolean similarityRetriever) {
        if (similarityRetriever) {
            final String maxResultsKey = "max_results";
            boolean isInfinity = configuration.hasArgument(maxResultsKey)
                    && configuration.argumentAsString(maxResultsKey).equalsIgnoreCase(MAX_RESULTS_INFINITY_ARGUMENT);

            if (isInfinity) {
                this.maxResults = Integer.MAX_VALUE;
            } else {
                this.maxResults = configuration.argumentAsInt(maxResultsKey, 10);
                if (maxResults < 1) {
                    throw new IllegalArgumentException("The maximum number of results must be greater than 0.");
                }
            }
        } else {
            this.maxResults = -1;
        }

        elementsWithEmbedding = new ArrayList<>();
        idToElementWithEmbedding = new HashMap<>();
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
     * Finds elements similar to the given query vector as part of LiSSA's similarity matching.
     * Only available in target store mode.
     *
     * @param queryVector The vector to find similar elements for
     * @return List of similar elements, sorted by similarity
     * @throws IllegalStateException If this is a source store (similarityRetriever = false)
     */
    public final List<Element> findSimilar(float[] queryVector) {
        return findSimilarWithDistances(queryVector).stream().map(Pair::first).toList();
    }

    /**
     * Finds elements similar to the given query vector, including their similarity scores.
     * Used by LiSSA for similarity-based matching in the classification phase.
     * Only available in target store mode.
     *
     * @param queryVector The vector to find similar elements for
     * @return List of pairs containing similar elements and their similarity scores
     * @throws IllegalStateException If this is a source store (similarityRetriever = false)
     */
    public List<Pair<Element, Float>> findSimilarWithDistances(float[] queryVector) {
        if (maxResults < 0) {
            throw new IllegalStateException("You should set retriever to true to activate this feature.");
        }
        return findSimilarWithDistancesByCosineSimilarity(queryVector);
    }

    private List<Pair<Element, Float>> findSimilarWithDistancesByCosineSimilarity(float[] queryVector) {
        var elements = getAllElementsIntern(true);
        List<Pair<Element, Float>> similarElements = new ArrayList<>();
        for (var element : elements) {
            float[] elementVector = element.second();
            float similarity = cosineSimilarity(queryVector, elementVector);
            similarElements.add(new Pair<>(element.first(), similarity));
        }
        similarElements.sort((a, b) -> Float.compare(b.second(), a.second()));
        return similarElements.subList(0, Math.min(maxResults, similarElements.size()));
    }

    private float cosineSimilarity(float[] queryVector, float[] elementVector) {
        if (queryVector.length != elementVector.length) {
            throw new IllegalArgumentException("The length of the query vector and the element vector must be equal.");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < queryVector.length; i++) {
            dotProduct += queryVector[i] * elementVector[i];
            normA += Math.pow(queryVector[i], 2);
            normB += Math.pow(elementVector[i], 2);
        }
        return (float) (dotProduct / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    /**
     * Retrieves an element and its embedding by its identifier.
     * Available in both source and target store modes for LiSSA's element lookup.
     *
     * @param id The identifier of the element to retrieve
     * @return A pair containing the element and its embedding, or null if not found
     */
    public Pair<Element, float[]> getById(String id) {
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
     * Retrieves all elements in the store for LiSSA's batch processing.
     * Only available in source store mode.
     *
     * @param onlyCompare If true, only returns elements marked for comparison
     * @return List of pairs containing elements and their embeddings
     * @throws IllegalStateException If this is a target store (similarityRetriever = true)
     */
    public List<Pair<Element, float[]>> getAllElements(boolean onlyCompare) {
        if (maxResults > 0) {
            throw new IllegalStateException("You should set retriever to false to activate this feature.");
        }
        return getAllElementsIntern(onlyCompare);
    }

    /**
     * Internal method to retrieve all elements.
     * Available in both source and target store modes for LiSSA's internal processing.
     *
     * @param onlyCompare If true, only returns elements marked for comparison
     * @return List of pairs containing elements and their embeddings
     */
    private List<Pair<Element, float[]>> getAllElementsIntern(boolean onlyCompare) {
        List<Pair<Element, float[]>> elements = new ArrayList<>();
        for (Pair<Element, float[]> element : elementsWithEmbedding) {
            if (!onlyCompare || element.first().isCompare()) {
                elements.add(new Pair<>(element.first(), Arrays.copyOf(element.second(), element.second().length)));
            }
        }
        return elements;
    }
}
