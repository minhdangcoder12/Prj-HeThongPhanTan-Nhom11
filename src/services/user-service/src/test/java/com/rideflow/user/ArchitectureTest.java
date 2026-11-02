package com.rideflow.user;

import com.rideflow.common.arch.RideflowArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

@AnalyzeClasses(packages = "com.rideflow.user", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchTests rideflow_rules = ArchTests.in(RideflowArchRules.class);
}
