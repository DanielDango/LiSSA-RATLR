/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.embeddingcreator;

import java.util.List;

import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;

public class MockEmbeddingCreator extends EmbeddingCreator {
    @Override
    public List<float[]> calculateEmbeddings(List<Element> elements) {
        return elements.stream().map(it -> new float[] {0}).toList();
    }
}
