/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import edu.kit.kastel.sdq.lissa.ratlr.utils.Environment;
import edu.kit.kastel.sdq.lissa.ratlr.utils.KeyGenerator;

/**
 * Architecture tests for the LiSSA framework using ArchUnit.
 * <p>
 * This class defines architectural rules to enforce:
 * <ul>
 *   <li>Centralized environment variable access</li>
 *   <li>Centralized UUID generation</li>
 *   <li>Functional programming practices (avoid forEach for side effects)</li>
 * </ul>
 * These rules help maintain code quality, consistency, and architectural integrity.
 */
@AnalyzeClasses(packages = "edu.kit.kastel.sdq.lissa")
class ArchitectureTest {

    /**
     * Rule that enforces environment variable access restrictions.
     * <p>
     * Only the {@link Environment} utility class may call {@code System.getenv()}.
     * All other classes must use the {@link Environment} class for environment variable access.
     */
    @ArchTest
    static final ArchRule no_getenv = noClasses()
            .that()
            .haveNameNotMatching(Environment.class.getName())
            .should()
            .callMethod(System.class, "getenv")
            .orShould()
            .callMethod(System.class, "getenv", String.class);

    /**
     * Rule that enforces UUID generation restrictions.
     * <p>
     * Only the {@link KeyGenerator} utility class may access {@link UUID}.
     * All other classes must use the {@link KeyGenerator} for UUID generation.
     */
    @ArchTest
    static final ArchRule no_uuid_outside_key_generator = noClasses()
            .that()
            .haveNameNotMatching(KeyGenerator.class.getName())
            .should()
            .accessClassesThat()
            .haveNameMatching(UUID.class.getName());

    /**
     * Rule that enforces functional programming practices.
     * <p>
     * Discourages the use of {@code forEach} and {@code forEachOrdered} on streams and lists,
     * as these are typically used for side effects. Prefer functional operations instead.
     */
    @ArchTest
    static final ArchRule noForEachInCollectionsOrStream = noClasses()
            .should()
            .callMethod(Stream.class, "forEach", Consumer.class)
            .orShould()
            .callMethod(Stream.class, "forEachOrdered", Consumer.class)
            .orShould()
            .callMethod(List.class, "forEach", Consumer.class)
            .orShould()
            .callMethod(List.class, "forEachOrdered", Consumer.class)
            .because("Lambdas should be functional. ForEach is typically used for side-effects.");
}
