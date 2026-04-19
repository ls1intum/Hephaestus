package de.tum.in.www1.hephaestus.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * Registers custom PostgreSQL SQL functions with Hibernate so they can be
 * invoked from JPQL / HQL via {@code function('name', args...)}.
 *
 * <p>Discovered via Java {@link java.util.ServiceLoader} — registered in
 * {@code META-INF/services/org.hibernate.boot.model.FunctionContributor}.
 *
 * <h3>Registered functions</h3>
 * <ul>
 *   <li>{@code de_fold_name(text) -> text}: DIN 5007-2 German name folding
 *       (ö→oe, ä→ae, ü→ue, ß→ss) plus lowercasing. Backed by the SQL function
 *       of the same name and a functional index on {@code "user"(de_fold_name(name))}
 *       created in changelog {@code 1776600000000_changelog.xml}.</li>
 * </ul>
 */
public class HephaestusFunctionContributor implements FunctionContributor {

    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        functionContributions
            .getFunctionRegistry()
            .registerPattern(
                "de_fold_name",
                "de_fold_name(?1)",
                functionContributions.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.STRING)
            );
    }
}
