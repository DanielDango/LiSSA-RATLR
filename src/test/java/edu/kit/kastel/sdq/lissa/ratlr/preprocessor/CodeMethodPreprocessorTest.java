/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr.preprocessor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.junit.jupiter.api.Test;

import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Artifact;

/**
 * Tests for the {@link CodeMethodPreprocessor}.
 */
class CodeMethodPreprocessorTest {

    /**
     * Tests the preprocessing of a single class (as an example this class is used).
     */
    @Test
    void preprocessSelf() throws IOException {
        CodeMethodPreprocessor codeMethodPreprocessor =
                new CodeMethodPreprocessor(new ModuleConfiguration(null, Map.of("language", "JAVA")));
        String thisClassContent = new Scanner(new File(
                        "src/test/java/edu/kit/kastel/sdq/lissa/ratlr/preprocessor/CodeMethodPreprocessorTest.java"))
                .useDelimiter("\\A")
                .next();
        var elements = codeMethodPreprocessor.preprocess(
                List.of(new Artifact("CodeMethodPreprocessorTest.java", "JAVA", thisClassContent)));
        assertEquals(3, elements.size());
        var head = elements.get(1);
        assertEquals("CodeMethodPreprocessorTest.java$0", head.getIdentifier());
        assertTrue(head.getContent().contains("package edu.kit.kastel.sdq.lissa.ratlr.preprocessor;"));

        var firstMethod = elements.get(2);
        assertEquals("CodeMethodPreprocessorTest.java$0$0", firstMethod.getIdentifier());
        assertTrue(firstMethod.getContent().contains("@Test"));
        assertTrue(firstMethod.getContent().contains("preprocessSelf"));
    }
}
