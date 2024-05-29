package edu.kit.kastel.sdq.lissa.ratlr.postprocessor;

import edu.kit.kastel.sdq.lissa.ratlr.knowledge.TraceLink;

import java.util.Set;

public class IdentityPostprocessor extends TraceLinkIdPostprocessor {
    @Override
    public Set<TraceLink> postprocess(Set<TraceLink> traceLinks) {
        return traceLinks;
    }
}
