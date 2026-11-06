package pe.cruzroja.convocatorias.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.*;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages="pe.cruzroja.convocatorias",importOptions=ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest static final ArchRule domain_is_framework_free=classes().that().resideInAPackage("..domain..")
        .should().onlyDependOnClassesThat().resideInAnyPackage("java..","..domain..");
    @ArchTest static final ArchRule application_depends_inward=classes().that().resideInAPackage("..application..")
        .should().onlyDependOnClassesThat().resideInAnyPackage("java..","..application..","..domain..");
    @ArchTest static final ArchRule jdbc_only_in_infrastructure=noClasses().that().resideOutsideOfPackage("..infrastructure..")
        .should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..","java.sql..","javax.sql..");
    @ArchTest static final ArchRule web_uses_no_persistence=noClasses().that().resideInAPackage("..web..")
        .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..","org.springframework.transaction..","org.springframework.security..");
    @ArchTest static final ArchRule controllers_have_no_jdbc=noClasses().that().haveSimpleNameEndingWith("Controller")
        .should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..");
    @ArchTest static final ArchRule context_modules_have_no_cycles=slices().matching("pe.cruzroja.convocatorias.(*)..").should().beFreeOfCycles();
    @ArchTest static final ArchRule modules_do_not_import_each_others_implementations=noClasses().that().resideInAnyPackage("..membership.application..","..operations.application..","..administration.application..","..communications.application..")
        .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..","..web..","..bootstrap..");
    @ArchTest static final ArchRule shared_kernel_has_no_business_module_dependencies=noClasses().that().resideInAPackage("..shared..")
        .should().dependOnClassesThat().resideInAnyPackage("..membership..","..operations..","..administration..","..communications..","..bootstrap..");
    @ArchTest static final ArchRule membership_integrates_through_shared_contracts=noClasses().that().resideInAPackage("..membership..")
        .should().dependOnClassesThat().resideInAnyPackage("..operations..","..administration..","..communications..");
    @ArchTest static final ArchRule operations_integrates_through_shared_contracts=noClasses().that().resideInAPackage("..operations..")
        .should().dependOnClassesThat().resideInAnyPackage("..membership..","..administration..","..communications..");
    @ArchTest static final ArchRule administration_integrates_through_shared_contracts=noClasses().that().resideInAPackage("..administration..")
        .should().dependOnClassesThat().resideInAnyPackage("..membership..","..operations..","..communications..");
    @ArchTest static final ArchRule communications_integrates_through_shared_contracts=noClasses().that().resideInAPackage("..communications..")
        .should().dependOnClassesThat().resideInAnyPackage("..membership..","..operations..","..administration..");
}
