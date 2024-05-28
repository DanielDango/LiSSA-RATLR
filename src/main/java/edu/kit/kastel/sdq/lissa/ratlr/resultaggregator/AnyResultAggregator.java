package edu.kit.kastel.sdq.lissa.ratlr.resultaggregator;

import edu.kit.kastel.sdq.lissa.ratlr.Configuration;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.Classifier;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.TraceLink;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AnyResultAggregator extends ResultAggregator {
    private final int sourceGranularity;
    private final int targetGranularity;

    public AnyResultAggregator(Configuration.ModuleConfiguration configuration) {
        this.sourceGranularity = configuration.argumentAsInt("source_granularity", 0);
        this.targetGranularity = configuration.argumentAsInt("target_granularity", 0);
    }

    @Override
    public Set<TraceLink> aggregate(List<Classifier.ClassificationResult> classificationResults) {
        Set<TraceLink> traceLinks = new LinkedHashSet<>();
        for (var result : classificationResults) {
            var source = result.source();
            while (source.getGranularity() > this.sourceGranularity) {
                var parent = source.getParent();
                if (!(parent instanceof Element parentAsElement))
                    throw new IllegalStateException("Parent is not an element");
                source = parentAsElement;
            }

            for (var candidate : result.targets()) {
                var target = candidate;
                while (target.getGranularity() > this.targetGranularity) {
                    var parent = target.getParent();
                    if (!(parent instanceof Element parentAsElement))
                        throw new IllegalStateException("Parent is not an element");
                    target = parentAsElement;
                }
                traceLinks.add(new TraceLink(source.getIdentifier(), target.getIdentifier()));
            }
        }
        return traceLinks;
    }
}
