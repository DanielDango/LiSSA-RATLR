/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.preprocessor;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import edu.kit.kastel.mcse.ardoco.tlr.models.connectors.generators.architecture.uml.parser.UmlComponent;
import edu.kit.kastel.mcse.ardoco.tlr.models.connectors.generators.architecture.uml.parser.UmlInterface;
import edu.kit.kastel.mcse.ardoco.tlr.models.connectors.generators.architecture.uml.parser.UmlModel;
import edu.kit.kastel.mcse.ardoco.tlr.models.connectors.generators.architecture.uml.parser.UmlModelRoot;
import edu.kit.kastel.mcse.ardoco.tlr.models.connectors.generators.architecture.uml.parser.xmlelements.OwnedOperation;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Artifact;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;

/**
 * A preprocessor that extracts information from UML model artifacts.
 * This preprocessor creates a hierarchical structure of elements where:
 * <ul>
 *     <li>The root element represents the entire UML model (granularity level 0)</li>
 *     <li>Child elements represent components and interfaces (granularity level 1)</li>
 * </ul>
 *
 * The preprocessor can extract various types of information from UML models:
 * <ul>
 *     <li>Component and interface definitions</li>
 *     <li>Component usages (dependencies on other components)</li>
 *     <li>Component and interface operations</li>
 *     <li>Interface realizations by components</li>
 * </ul>
 *
 * Configuration options:
 * <ul>
 *     <li>includeUsages: Whether to include component usages in the extracted information (default: true)</li>
 *     <li>includeOperations: Whether to include operations of components and interfaces (default: true)</li>
 *     <li>includeInterfaceRealizations: Whether to include interface realizations by components (default: true)</li>
 * </ul>
 *
 * Each element in the hierarchy:
 * <ul>
 *     <li>Has a unique identifier combining the model ID, element index, and UML element ID</li>
 *     <li>Maintains the same type as the source artifact</li>
 *     <li>Contains a structured representation of the UML element's properties</li>
 *     <li>Has a granularity level based on its position in the hierarchy</li>
 *     <li>Is marked for comparison only if it's a component element</li>
 * </ul>
 */
public class ModelUMLPreprocessor extends Preprocessor {
    /** Whether to include component usages in the extracted information */
    private final boolean includeUsages;
    /** Whether to include operations of components and interfaces */
    private final boolean includeOperations;
    /** Whether to include interface realizations by components */
    private final boolean includeInterfaceRealizations;

    /**
     * Creates a new UML model preprocessor with the specified configuration.
     *
     * @param configuration The module configuration containing extraction settings
     */
    public ModelUMLPreprocessor(ModuleConfiguration configuration) {
        this.includeUsages = configuration.argumentAsBoolean("includeUsages", true);
        this.includeOperations = configuration.argumentAsBoolean("includeOperations", true);
        this.includeInterfaceRealizations = configuration.argumentAsBoolean("includeInterfaceRealizations", true);
    }

    /**
     * Preprocesses a list of UML model artifacts by extracting their components and interfaces.
     * For each artifact, this method:
     * <ol>
     *     <li>Creates an element representing the entire UML model</li>
     *     <li>Parses the XML content to create a UML model</li>
     *     <li>Extracts components and their properties</li>
     *     <li>Extracts interfaces and their properties</li>
     *     <li>Creates elements for each component and interface</li>
     * </ol>
     *
     * @param artifacts The list of UML model artifacts to preprocess
     * @return A list of elements containing both the original models and their components/interfaces
     */
    @Override
    public List<Element> preprocess(List<Artifact> artifacts) {
        List<Element> elements = new ArrayList<>();

        for (Artifact artifact : artifacts) {
            Element artifactAsElement =
                    new Element(artifact.getIdentifier(), artifact.getType(), artifact.getContent(), 0, null, false);
            elements.add(artifactAsElement);

            String xml = artifact.getContent();
            UmlModelRoot umlModel = new UmlModel(new ByteArrayInputStream(xml.getBytes())).getModel();

            AtomicInteger counter = new AtomicInteger(0);
            for (UmlComponent umlComponent : umlModel.getComponents()) {
                this.addComponent(counter, umlComponent, artifactAsElement, elements);
            }

            for (UmlInterface umlInterface : umlModel.getInterfaces()) {
                this.addInterface(counter, umlInterface, artifactAsElement, elements);
            }
        }
        return elements;
    }

    /**
     * Adds an interface element to the list of elements.
     * This method:
     * <ol>
     *     <li>Creates a structured representation of the interface's properties</li>
     *     <li>Optionally includes the interface's operations</li>
     *     <li>Creates a new element with the interface information</li>
     *     <li>Links the element to the model element</li>
     * </ol>
     *
     * @param counter A counter for generating unique element indices
     * @param umlInterface The UML interface to process
     * @param artifactAsElement The model element to link to
     * @param elements The list to add the new element to
     */
    private void addInterface(
            AtomicInteger counter, UmlInterface umlInterface, Element artifactAsElement, List<Element> elements) {
        Set<String> representation = new LinkedHashSet<>();
        representation.add("Type: " + umlInterface.getType() + ", Name: " + umlInterface.getName());
        if (includeOperations) {
            for (OwnedOperation operation : umlInterface.getOperations()) {
                representation.add("\nOperation: " + operation.getName());
            }
        }
        String content = String.join("", representation);
        String identifier = artifactAsElement.getIdentifier()
                + SEPARATOR
                + counter.getAndIncrement()
                + SEPARATOR
                + umlInterface.getId();
        Element resultingElement =
                new Element(identifier, artifactAsElement.getType(), content, 1, artifactAsElement, false);
        elements.add(resultingElement);
    }

    /**
     * Adds a component element to the list of elements.
     * This method:
     * <ol>
     *     <li>Creates a structured representation of the component's properties</li>
     *     <li>Optionally includes interface realizations</li>
     *     <li>Optionally includes operations from provided interfaces</li>
     *     <li>Optionally includes component usages</li>
     *     <li>Creates a new element with the component information</li>
     *     <li>Links the element to the model element</li>
     * </ol>
     *
     * @param counter A counter for generating unique element indices
     * @param component The UML component to process
     * @param artifactAsElement The model element to link to
     * @param elements The list to add the new element to
     */
    private void addComponent(
            AtomicInteger counter, UmlComponent component, Element artifactAsElement, List<Element> elements) {
        Set<String> representation = new LinkedHashSet<>();
        representation.add("Type: " + component.getType() + ", Name: " + component.getName());
        if (includeInterfaceRealizations) {
            for (UmlInterface umlInterface : component.getProvided()) {
                representation.add("\nInterface Realization: " + umlInterface.getName());
            }
        }
        if (includeOperations) {
            for (OwnedOperation operation : component.getProvided().stream()
                    .flatMap(it -> it.getOperations().stream())
                    .toList()) {
                representation.add("\nOperation: " + operation.getName());
            }
        }
        if (includeUsages) {
            for (UmlInterface usedComponent : component.getRequired()) {
                representation.add("\nUses: " + usedComponent.getName());
            }
        }

        String content = String.join("", representation);
        String identifier = artifactAsElement.getIdentifier()
                + SEPARATOR
                + counter.getAndIncrement()
                + SEPARATOR
                + component.getId();
        Element resultingElement =
                new Element(identifier, artifactAsElement.getType(), content, 1, artifactAsElement, true);
        elements.add(resultingElement);
    }
}
