package edu.kit.kastel.sdq.lissa.ratlr.elementstore;

import edu.kit.kastel.sdq.lissa.ratlr.RatlrConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;
import edu.kit.kastel.sdq.lissa.ratlr.utils.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ElementStore {

    private final Map<String, Pair<Element, float[]>> idToElementWithEmbedding;
    private final List<Pair<Element, float[]>> elementsWithEmbedding;
    private final int maxResults;

    public ElementStore(RatlrConfiguration.ModuleConfiguration configuration, List<Element> elements, List<float[]> embeddings) {
        if (elements.size() != embeddings.size()) {
            throw new IllegalArgumentException("The number of elements and embeddings must be equal.");
        }

        this.maxResults = Integer.parseInt(configuration.arguments().getOrDefault("max_results", "10"));

        elementsWithEmbedding = new ArrayList<>();
        idToElementWithEmbedding = new HashMap<>();
        for (int i = 0; i < elements.size(); i++) {
            var element = elements.get(i);
            var embedding = embeddings.get(i);
            var pair = new Pair<>(element, embedding);
            elementsWithEmbedding.add(pair);
            idToElementWithEmbedding.put(element.getIdentifier(), pair);
        }
    }

    public List<Element> findSimilar(float[] queryVector) {
        return findSimilarWithDistances(queryVector).stream().map(Pair::first).toList();
    }

    public List<Pair<Element, Float>> findSimilarWithDistances(float[] queryVector) {
        return findSimilarWithDistancesByCosineSimilarity(queryVector);
    }

    private List<Pair<Element, Float>> findSimilarWithDistancesByCosineSimilarity(float[] queryVector) {
        var elements = getAllElements(true);
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

    public Pair<Element, float[]> getById(String id) {
        var element = idToElementWithEmbedding.get(id);
        if (element == null) {
            return null;
        }
        return new Pair<>(element.first(), Arrays.copyOf(element.second(), element.second().length));
    }

    public List<Pair<Element, float[]>> getElementsByParentId(String parentId) {
        List<Pair<Element, float[]>> elements = new ArrayList<>();
        for (Pair<Element, float[]> element : elementsWithEmbedding) {
            if (element.first().getParent() != null && element.first().getParent().getIdentifier().equals(parentId)) {
                elements.add(new Pair<>(element.first(), Arrays.copyOf(element.second(), element.second().length)));
            }
        }
        return elements;
    }

    public List<Pair<Element, float[]>> getAllElements(boolean onlyCompare) {
        List<Pair<Element, float[]>> elements = new ArrayList<>();
        for (Pair<Element, float[]> element : elementsWithEmbedding) {
            if (!onlyCompare || element.first().isCompare()) {
                elements.add(new Pair<>(element.first(), Arrays.copyOf(element.second(), element.second().length)));
            }
        }
        return elements;
    }
}
