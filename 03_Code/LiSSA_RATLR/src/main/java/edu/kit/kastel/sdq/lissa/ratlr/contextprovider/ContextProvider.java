/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.contextprovider;

import java.util.stream.Collectors;

import edu.kit.kastel.sdq.lissa.ratlr.elementstore.ElementStore;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;
import edu.kit.kastel.sdq.lissa.ratlr.utils.Pair;

/**
 * A utility class that provides context information for elements in the trace link analysis system.
 * Currently, this class focuses on providing sibling context (neighboring elements) for evaluation purposes.
 * The context is provided as a pair of strings representing the content before and after the target element.
 */
public final class ContextProvider {
    /**
     * An empty context pair containing empty strings for both before and after content.
     */
    private static final Pair<String, String> EMPTY_CONTEXT = new Pair<>("", "");

    private ContextProvider() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Provides the content of siblings of the given element.
     * The contents are concatenated into a before string and after string.
     * The method retrieves sibling elements from the element store and extracts
     * a specified number of elements before and after the target element.
     *
     * @param elementStore The store containing all elements
     * @param element The target element for which to get context
     * @param pre The number of sibling elements to include before the target element
     * @param post The number of sibling elements to include after the target element
     * @return A pair of strings containing the concatenated content of siblings before and after the target element.
     *         Returns {@link #EMPTY_CONTEXT} if pre and post are both 0 or if the element has no parent.
     */
    public static Pair<String, String> getNeighbors(ElementStore elementStore, Element element, int pre, int post) {
        if (pre <= 0 && post <= 0) return EMPTY_CONTEXT;

        if (element.getParent() == null) return EMPTY_CONTEXT;

        var siblings = elementStore.getElementsByParentId(element.getParent().getIdentifier()).stream()
                .map(Pair::first)
                .toList();
        // TODO ensure that they are sorted
        int index = siblings.indexOf(element);
        int preStart = Math.max(0, index - pre);
        int postEnd = Math.min(siblings.size(), index + post + 1);

        var preSiblings = siblings.subList(preStart, index);
        var postSiblings = siblings.subList(index + 1, postEnd);

        String preText = preSiblings.stream().map(Element::getContent).collect(Collectors.joining("\n"));
        String postText = postSiblings.stream().map(Element::getContent).collect(Collectors.joining("\n"));
        return new Pair<>(preText, postText);
    }
}
