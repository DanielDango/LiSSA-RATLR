/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import edu.kit.kastel.sdq.lissa.cli.command.OptimizeCommand;
import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheKey;
import edu.kit.kastel.sdq.lissa.ratlr.classifier.Classifier;
import edu.kit.kastel.sdq.lissa.ratlr.promptmetric.Metric;
import edu.kit.kastel.sdq.lissa.ratlr.promptoptimizer.AbstractPromptOptimizer;
import edu.kit.kastel.sdq.lissa.ratlr.utils.Environment;
import edu.kit.kastel.sdq.lissa.ratlr.utils.Futures;
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
            .and()
            .resideOutsideOfPackage("..e2e..")
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

    /**
     * CacheKeys should only be created using the #of method of the CacheKey class.
     */
    @ArchTest
    static final ArchRule cacheKeysShouldBeCreatedUsingKeyGenerator = noClasses()
            .that()
            .haveNameNotMatching(CacheKey.class.getName())
            .should()
            .callConstructorWhere(new DescribedPredicate<JavaConstructorCall>("calls CacheKey constructor") {
                @Override
                public boolean test(JavaConstructorCall javaConstructorCall) {
                    return javaConstructorCall
                            .getTarget()
                            .getOwner()
                            .getFullName()
                            .equals(CacheKey.class.getName());
                }
            });

    /**
     * Prompts for classifiers should only be modified by optimizers or scorers. Otherwise, there will be
     * inconsistencies with the configuration file.
     */
    @ArchTest
    static final ArchRule classifierPromptsShouldOnlyBeModifiedByOptimizers = noClasses()
            .that()
            .areNotAssignableTo(AbstractPromptOptimizer.class)
            .and()
            .areNotAssignableTo(Metric.class)
            .should()
            .callMethod(Classifier.class, "setClassificationPrompt", String.class);

    /**
     * Only the {@link OptimizeCommand} should be allowed to overwrite the prompt used for evaluation to reflect the
     * modified prompt into the configuration.
     */
    @ArchTest
    static final ArchRule onlyOptimizationCommandShouldCallEvaluationWithPromptOverwrite = noClasses()
            .that()
            .areNotAssignableTo(OptimizeCommand.class)
            .should()
            .callConstructor(Evaluation.class, Path.class, String.class);

    /**
     * Futures should be opened with a logger.
     */
    @ArchTest
    static final ArchRule futuresShouldBeOpenedWithLogger = noClasses()
            .that()
            .doNotHaveFullyQualifiedName(Futures.class.getName())
            .should()
            .callMethod(Future.class, "get")
            .orShould()
            .callMethod(Future.class, "resultNow");
}
