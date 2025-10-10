/* Licensed under MIT 2025. */
package edu.kit.kastel.sdq.lissa.ratlr;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import edu.kit.kastel.sdq.lissa.ratlr.cache.CacheKey;
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
    static final ArchRule NoDirectEnvironmentAccess = noClasses()
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
    static final ArchRule OnlyKeyGeneratorAllowedForUUID = noClasses()
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

    @Test
    void allPackagesShouldHavePackageInfo() {
        JavaClasses classes = new ClassFileImporter().importPackages("edu.kit.kastel.sdq.lissa");

        Set<String> packages = new HashSet<>();
        Set<String> packagesWithPackageInfo = new HashSet<>();

        for (JavaClass clazz : classes) {
            String pkg = clazz.getPackageName();
            packages.add(pkg);

            if (clazz.getSimpleName().equals("package-info")) {
                packagesWithPackageInfo.add(pkg);
                Assertions.assertTrue(
                        clazz.isAnnotatedWith(NullMarked.class),
                        "package-info.java in package " + pkg + " must be annotated with @NullMarked");
            }
        }

        // Now check which packages are missing package-info
        Set<String> packagesMissingPackageInfo = packages.stream()
                .filter(pkg -> !packagesWithPackageInfo.contains(pkg))
                .collect(Collectors.toSet());

        Assertions.assertTrue(
                packagesMissingPackageInfo.isEmpty(),
                "Package info missing in packages: " + packagesMissingPackageInfo);
    }
}
