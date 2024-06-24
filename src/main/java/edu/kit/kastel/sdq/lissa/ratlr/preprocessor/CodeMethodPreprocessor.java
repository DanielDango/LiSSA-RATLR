package edu.kit.kastel.sdq.lissa.ratlr.preprocessor;

import edu.kit.kastel.sdq.lissa.ratlr.Configuration;
import edu.kit.kastel.sdq.lissa.ratlr.cache.Cache;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheManager;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Artifact;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Element;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterJava;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Split at the beginning of a class (class declaration to first split) and beginning of each method (method declaration to second split)
 */
public class CodeMethodPreprocessor extends CachedPreprocessor {

    private final Language language;

    public CodeMethodPreprocessor(Configuration.ModuleConfiguration configuration) {
        this.language = Objects.requireNonNull(Language.valueOf(configuration.argumentAsString("language")));
    }

    @Override
    protected Cache createCache() {
        return CacheManager.getInstance().getCache(this.getClass().getSimpleName() + language);
    }

    @Override
    protected List<Element> preprocessIntern(Artifact artifact) {
        List<Element> elements = new ArrayList<>();
        Element artifactAsElement = new Element(artifact.getIdentifier(), artifact.getType(), artifact.getContent(), 0, null, false);
        elements.add(artifactAsElement);

        var newElements = switch (language) {
        case JAVA -> splitJava(artifactAsElement);
        };

        elements.addAll(newElements);
        return elements;
    }

    private List<Element> splitJava(Element javaFile) {
        List<Element> elements = new ArrayList<>();

        TSParser parser = new TSParser();
        parser.setLanguage(new TreeSitterJava());

        TSTree tree = parser.parseString(null, javaFile.getContent());
        var classBodies = parseClassBodies(tree.getRootNode());
        int classStart = 0;
        for (int i = 0; i < classBodies.size(); i++) {
            var classBody = classBodies.get(i);
            var text = javaFile.getContent().substring(classStart, classBody.getStartByte());
            var classElement = new Element(javaFile.getIdentifier() + SEPARATOR + i, "source code class definition", text, 1, javaFile, false);
            elements.add(classElement);

            int methodStart = classBody.getStartByte();
            var methods = parseMethods(classBody);
            for (int j = 0; j < methods.size(); j++) {
                var method = methods.get(j);
                var methodText = javaFile.getContent().substring(methodStart, method.getEndByte());
                var methodElement = new Element(classElement.getIdentifier() + SEPARATOR + j, "source code method", methodText, 2, classElement, true);
                elements.add(methodElement);
                methodStart = method.getEndByte();
            }
            classStart = classBody.getEndByte();
        }

        return elements;
    }

    private List<TSNode> parseMethods(TSNode node) {
        if (node.getType().equals("method_declaration"))
            return List.of(node);
        List<TSNode> methods = new ArrayList<>();
        for (int i = 0; i < node.getChildCount(); i++) {
            methods.addAll(parseMethods(node.getChild(i)));
        }
        return methods;
    }

    private List<TSNode> parseClassBodies(TSNode node) {
        if (node.getType().equals("class_body"))
            return List.of(node);
        List<TSNode> classBodies = new ArrayList<>();
        for (int i = 0; i < node.getChildCount(); i++) {
            classBodies.addAll(parseClassBodies(node.getChild(i)));
        }
        return classBodies;
    }
}
