package com.financetracker.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

/**
 * Build-breaking rules for the decisions that are expensive to re-audit later (SR-02).
 *
 * SECURITY.md SR-02 describes tenant scoping as a {@code userId} parameter on every finder.
 * This application scopes differently: the tenant is applied to every transaction by {@code TenantAwareJpaTransactionManager}, and Row-Level Security enforces it in the database.
 * So the rules below protect that mechanism instead — nothing may open a database connection outside it, and nothing may replace the transaction manager that feeds it.
 */
@AnalyzeClasses(packages = "com.financetracker", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule repositoriesStayInsideTheirOwnFeature = classes()
            .that()
            .areAssignableTo(Repository.class)
            .should()
            .notBePublic()
            .because("a package-private repository cannot be called from another feature, "
                    + "which keeps cross-feature calls going through services (BACKEND_CONVENTIONS 3.1)");

    @ArchTest
    static final ArchRule onlyCommonTouchesTheDatabaseDirectly = noClasses()
            .that()
            .resideOutsideOfPackage("com.financetracker.common..")
            .should()
            .dependOnClassesThat()
            .haveNameMatching("jakarta\\.persistence\\.EntityManager"
                    + "|javax\\.sql\\.DataSource"
                    + "|org\\.springframework\\.jdbc\\.core\\..*")
            .because("a query run on a connection taken outside the transaction manager carries no tenant, "
                    + "so Row-Level Security would hide every row and the failure would look like missing data");

    @ArchTest
    static final ArchRule onlyTheTenantPackageDefinesATransactionManager = classes()
            .that()
            .areAssignableTo(PlatformTransactionManager.class)
            .should()
            .resideInAPackage("com.financetracker.common.tenant..")
            .because("a second transaction manager would begin transactions without applying the tenant");

    @ArchTest
    static final ArchRule controllersDoNotUseRepositories = noClasses()
            .that()
            .haveSimpleNameEndingWith("Controller")
            .should()
            .dependOnClassesThat()
            .areAssignableTo(Repository.class)
            .because("a controller calling a repository skips the service layer that owns the transaction boundary");

    @ArchTest
    static final ArchRule transactionsAreNotDeclaredOnControllers = noMethods()
            .that()
            .areDeclaredInClassesThat()
            .haveSimpleNameEndingWith("Controller")
            .should()
            .beAnnotatedWith(Transactional.class)
            .because("the transaction boundary belongs on the service method (BACKEND_CONVENTIONS 6.2)");

    @ArchTest
    static final ArchRule dependenciesAreInjectedThroughConstructors = noFields()
            .should()
            .beAnnotatedWith(Autowired.class)
            .because("explicit constructors keep dependencies visible in plain code (BACKEND_CONVENTIONS 2.1)");
}
