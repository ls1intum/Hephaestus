package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static de.tum.cit.aet.hephaestus.architecture.ArchitectureTestConstants.BASE_PACKAGE;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import java.util.Collection;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;

class SpringCorrectnessArchitectureTest extends HephaestusArchitectureTest {

    @Test
    void shouldRequireTransactionalMethodsToBeProxyable() {
        ArchRule rule = methods()
            .that(transactionalMethods())
            .should()
            .notBePrivate()
            .andShould()
            .notBeStatic()
            .andShould()
            .notBeFinal()
            .andShould(beDeclaredInANonFinalClass())
            .because("class-based transaction proxies cannot advise private or final methods");

        rule.check(classes);
    }

    @Test
    void shouldRejectTransactionalSelfInvocation() {
        ArchRule rule = FreezingArchRule.freeze(
            methods()
                .should(notCallTransactionalMethodsOnSelf())
                .because("self-invocation bypasses Spring's transactional proxy")
        );

        rule.check(classes);
    }

    @Test
    void shouldRejectAutowiredFields() {
        ArchRule rule = fields()
            .should(notBeAutowired())
            .because("required collaborators must be explicit constructor dependencies");

        rule.check(classes);
    }

    @Test
    void shouldValidateApplicationRequestBodies() {
        ArchRule rule = FreezingArchRule.freeze(
            methods()
                .should(haveValidatedRequestBodies())
                .because("bean validation is not applied to a request body unless the parameter is @Valid")
        );

        rule.check(classes);
    }

    @Test
    void shouldRejectTransactionsDuringPostConstruct() {
        ArchRule rule = methods()
            .that()
            .areAnnotatedWith(PostConstruct.class)
            .should(notBeEffectivelyTransactional())
            .because("@PostConstruct runs before the bean's transactional proxy is available");

        rule.check(classes);
    }

    @Test
    void shouldReturnRepositoryCollectionsDirectly() {
        ArchRule rule = methods()
            .that()
            .areDeclaredInClassesThat()
            .areAssignableTo(Repository.class)
            .should(notReturnOptionalCollection())
            .because("Spring Data collection queries return an empty collection rather than null");

        rule.check(classes);
    }

    private static ArchCondition<JavaMethod> beDeclaredInANonFinalClass() {
        return new ArchCondition<>("be declared in a non-final class") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (method.getOwner().getModifiers().contains(JavaModifier.FINAL)) {
                    events.add(
                        SimpleConditionEvent.violated(method, method.getDescription() + " is declared in a final class")
                    );
                }
            }
        };
    }

    private static DescribedPredicate<JavaMethod> transactionalMethods() {
        return DescribedPredicate.describe(
            "covered by a method-level or type-level @Transactional annotation",
            SpringCorrectnessArchitectureTest::isEffectivelyTransactional
        );
    }

    private static ArchCondition<JavaField> notBeAutowired() {
        return new ArchCondition<>("not be annotated or meta-annotated with @Autowired") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                if (field.isAnnotatedWith(Autowired.class) || field.isMetaAnnotatedWith(Autowired.class)) {
                    events.add(SimpleConditionEvent.violated(field, field.getDescription() + " is field-injected"));
                }
            }
        };
    }

    private static ArchCondition<JavaMethod> notCallTransactionalMethodsOnSelf() {
        return new ArchCondition<>("not call a transactional method on the same class") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                method
                    .getMethodCallsFromSelf()
                    .stream()
                    .filter(call -> method.getOwner().getAllClassesSelfIsAssignableTo().contains(call.getTargetOwner()))
                    .filter(call ->
                        call
                            .getTarget()
                            .resolveMember()
                            .map(SpringCorrectnessArchitectureTest::isEffectivelyTransactional)
                            .orElse(false)
                    )
                    .forEach(call ->
                        events.add(
                            SimpleConditionEvent.violated(
                                method,
                                method.getDescription() + " calls transactional " + call.getTarget().getDescription()
                            )
                        )
                    );
            }
        };
    }

    private static ArchCondition<JavaMethod> haveValidatedRequestBodies() {
        return new ArchCondition<>("have @Valid on every @RequestBody parameter") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                method
                    .getParameters()
                    .stream()
                    .filter(parameter -> parameter.isAnnotatedWith(RequestBody.class))
                    .filter(parameter ->
                        parameter
                            .getType()
                            .getAllInvolvedRawTypes()
                            .stream()
                            .anyMatch(type -> type.getPackageName().startsWith(BASE_PACKAGE))
                    )
                    .filter(parameter -> !parameter.isAnnotatedWith(Valid.class))
                    .forEach(parameter ->
                        events.add(
                            SimpleConditionEvent.violated(
                                method,
                                parameter.getDescription() + " is @RequestBody but is not @Valid"
                            )
                        )
                    );
            }
        };
    }

    private static ArchCondition<JavaMethod> notBeEffectivelyTransactional() {
        return new ArchCondition<>("not be transactional") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (isEffectivelyTransactional(method)) {
                    events.add(SimpleConditionEvent.violated(method, method.getDescription() + " is transactional"));
                }
            }
        };
    }

    private static boolean isEffectivelyTransactional(JavaMethod method) {
        boolean methodAnnotated = method
            .getOwner()
            .getAllMethods()
            .stream()
            .filter(candidate -> candidate.getName().equals(method.getName()))
            .filter(candidate -> candidate.getRawParameterTypes().equals(method.getRawParameterTypes()))
            .anyMatch(
                candidate ->
                    candidate.isAnnotatedWith(Transactional.class) || candidate.isMetaAnnotatedWith(Transactional.class)
            );
        boolean ownerAnnotated = method
            .getOwner()
            .getAllClassesSelfIsAssignableTo()
            .stream()
            .anyMatch(
                owner -> owner.isAnnotatedWith(Transactional.class) || owner.isMetaAnnotatedWith(Transactional.class)
            );
        boolean proxyCandidate =
            !method.getModifiers().contains(JavaModifier.PRIVATE) &&
            !method.getModifiers().contains(JavaModifier.STATIC);
        return methodAnnotated || (ownerAnnotated && proxyCandidate);
    }

    private static ArchCondition<JavaMethod> notReturnOptionalCollection() {
        return new ArchCondition<>("not return Optional-wrapped collections") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                JavaType returnType = method.getReturnType();
                if (
                    returnType instanceof JavaParameterizedType parameterized &&
                    returnType.toErasure().isEquivalentTo(Optional.class) &&
                    parameterized
                        .getActualTypeArguments()
                        .stream()
                        .anyMatch(SpringCorrectnessArchitectureTest::isCollection)
                ) {
                    events.add(
                        SimpleConditionEvent.violated(
                            method,
                            method.getDescription() + " returns Optional-wrapped collection " + returnType.getName()
                        )
                    );
                }
            }
        };
    }

    private static boolean isCollection(JavaType type) {
        return type.toErasure().isAssignableTo(Collection.class);
    }
}
