package edu.kit.kastel.sdq.lissa.ratlr;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static org.junit.jupiter.api.Assertions.*;

@AnalyzeClasses(packages = "edu.kit.kastel.sdq.lissa.ratlr")
class ArchitectureTest {

    @ArchTest
    static final ArchRule no_getenv = noClasses().that()
            .haveNameNotMatching(Environment.class.getName())
            .should()
            .callMethod(System.class, "getenv")
            .orShould()
            .callMethod(System.class, "getenv", String.class);

}
