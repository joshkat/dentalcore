package com.dentalcore.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the modular-monolith boundaries: a module's `internal` package may
 * only be used by that module itself. Cross-module communication must go
 * through `api` packages or `shared`.
 */
class ModuleBoundaryTest {

    private static final String[] MODULES = {
            "auth", "users", "patients", "providers", "appointments", "treatmentplans",
            "procedures", "clinicalnotes", "insurance", "billing", "documents", "reporting",
            "audit", "reminders", "forms"
    };

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.dentalcore");
    }

    @Test
    void moduleInternalsAreNotAccessedByOtherModules() {
        for (String module : MODULES) {
            noClasses()
                    .that().resideOutsideOfPackage("com.dentalcore." + module + "..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.dentalcore." + module + ".internal..")
                    .as("No class outside '%s' may depend on '%s.internal'".formatted(module, module))
                    .check(classes);
        }
    }

    @Test
    void sharedDependsOnNoBusinessModule() {
        for (String module : MODULES) {
            noClasses()
                    .that().resideInAPackage("com.dentalcore.shared..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.dentalcore." + module + "..")
                    .check(classes);
        }
    }

    @Test
    void entitiesAreNeverReferencedByControllers() {
        noClasses()
                .that().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat()
                .areAnnotatedWith(jakarta.persistence.Entity.class)
                .check(classes);
    }

    @Test
    void controllersResideInWebPackages() {
        classes()
                .that().haveSimpleNameEndingWith("Controller")
                .should().resideInAPackage("..internal.web..")
                .check(classes);
    }
}
