package com.rideflow.common.arch;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

/** Rule R2 of .claude/rules/architecture.md. Each service runs these via its ArchitectureTest. */
public final class RideflowArchRules {

    @ArchTest
    public static final ArchRule domain_is_framework_free = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "org.hibernate..",
                    "..application..", "..adapter..", "..infrastructure..");

    @ArchTest
    public static final ArchRule application_does_not_know_adapters = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..adapter..", "..infrastructure..", "jakarta.persistence..",
                    "org.springframework.amqp..", "org.springframework.web..");

    @ArchTest
    public static final ArchRule no_system_out = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    private RideflowArchRules() {
    }
}
