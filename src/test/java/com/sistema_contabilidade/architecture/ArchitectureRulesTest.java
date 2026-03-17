package com.sistema_contabilidade.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "com.sistema_contabilidade",
    importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class ArchitectureRulesTest {

  @ArchTest
  static final ArchRule classes_with_controller_suffix_should_be_in_controller_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("Controller")
          .should()
          .resideInAPackage("..controller..");

  @ArchTest
  static final ArchRule classes_with_service_suffix_should_be_in_service_package =
      classes().that().haveSimpleNameEndingWith("Service").should().resideInAPackage("..service..");

  @ArchTest
  static final ArchRule classes_with_repository_suffix_should_be_in_repository_package =
      classes()
          .that()
          .haveSimpleNameEndingWith("Repository")
          .should()
          .resideInAPackage("..repository..");

  @ArchTest
  static final ArchRule layered_dependencies_should_follow_controller_service_repository =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .layer("Controller")
          .definedBy("com.sistema_contabilidade..controller..")
          .layer("Service")
          .definedBy("com.sistema_contabilidade..service..")
          .layer("Repository")
          .definedBy("com.sistema_contabilidade..repository..")
          .whereLayer("Controller")
          .mayOnlyAccessLayers("Service", "Repository")
          .whereLayer("Service")
          .mayOnlyAccessLayers("Repository")
          .whereLayer("Repository")
          .mayNotAccessAnyLayer();
}
