package edu.kit.kastel.sdq.lissa.ratlr.preprocessor;

import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Artifact;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;

import java.util.List;

public class SingleArtifactPreprocessor extends Preprocessor {
    @Override
    public List<Element> preprocess(Artifact artifact) {
        return List.of(new Element(artifact.getIdentifier(), artifact.getType(), artifact.getContent(), 0, null, true));
    }
}
