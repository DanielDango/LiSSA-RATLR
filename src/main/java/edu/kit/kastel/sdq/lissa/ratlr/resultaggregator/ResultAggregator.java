package edu.kit.kastel.sdq.lissa.ratlr.resultaggregator;

import edu.kit.kastel.sdq.lissa.ratlr.RatlrConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.Classifier;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.TraceLink;

import java.util.List;
import java.util.Set;

public abstract class ResultAggregator {
    public abstract Set<TraceLink> aggregate(List<Classifier.ClassificationResult> classificationResults);

    public static ResultAggregator createResultAggregator(RatlrConfiguration.ModuleConfiguration configuration) {
        return switch (configuration.name()) {
        case "any_connection" -> new AnyResultAggregator(configuration);
        default -> throw new IllegalStateException("Unexpected value: " + configuration.name());
        };
    }
}
