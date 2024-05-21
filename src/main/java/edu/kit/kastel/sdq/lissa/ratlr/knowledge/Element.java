package edu.kit.kastel.sdq.lissa.ratlr.knowledge;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public final class Element extends Knowledge {
    @JsonProperty
    private final int granularity;
    private transient Knowledge parent;
    @JsonProperty
    private final String parentId;
    @JsonProperty
    private final boolean compare;

    @JsonCreator
    public Element(String identifier, String type, String content, int granularity, Knowledge parent, boolean compare) {
        super(identifier, type, content);
        this.granularity = granularity;
        this.parentId = parent == null ? null : parent.getIdentifier();
        this.parent = parent;
        this.compare = compare;
    }

    public void init(Map<String, ? extends Knowledge> otherKnowledge) {
        if (parentId != null) {
            parent = otherKnowledge.get(parentId);
        }
    }

    public int getGranularity() {
        return granularity;
    }

    public Knowledge getParent() {
        return parent;
    }

    public boolean isCompare() {
        return compare;
    }
}
