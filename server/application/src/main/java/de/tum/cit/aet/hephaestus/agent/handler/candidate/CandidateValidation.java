package de.tum.cit.aet.hephaestus.agent.handler.candidate;

import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.*;

import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.practices.spi.ClaimedCoverage;
import de.tum.cit.aet.hephaestus.practices.spi.CoverageOmission;
import de.tum.cit.aet.hephaestus.practices.spi.DutyBasis;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceCitation;
import de.tum.cit.aet.hephaestus.practices.spi.HostCoverage;
import de.tum.cit.aet.hephaestus.practices.spi.InapplicabilityWarrant;
import de.tum.cit.aet.hephaestus.practices.spi.IntroducedBasis;
import de.tum.cit.aet.hephaestus.practices.spi.OpenQuestion;
import de.tum.cit.aet.hephaestus.practices.spi.ReadReceipt;
import de.tum.cit.aet.hephaestus.practices.spi.ResultExample;
import de.tum.cit.aet.hephaestus.practices.spi.SearchWarrant;
import de.tum.cit.aet.hephaestus.practices.spi.StateBasis;
import de.tum.cit.aet.hephaestus.practices.spi.WorsenedBasis;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.cfg.defs.MaxDef;
import org.hibernate.validator.cfg.defs.PatternDef;
import org.hibernate.validator.cfg.defs.PositiveDef;
import org.hibernate.validator.cfg.defs.PositiveOrZeroDef;
import org.hibernate.validator.cfg.defs.SizeDef;

/** Candidate bounds apply at the untrusted boundary, independently of stored domain values. */
final class CandidateValidation {
    private CandidateValidation() {}

    static Validator create() {
        var configuration = Validation.byProvider(HibernateValidator.class).configure();
        var mapping = configuration.createConstraintMapping();
        mapping.type(SourceKind.class)
                .field("value")
                .constraint(new SizeDef().min(1).max(WARRANT_FIELD_CHARS));
        mapping.type(EvidenceCitation.class)
                .field("sourceKind")
                .valid()
                .field("sourcePath")
                .constraint(new SizeDef().min(1).max(PATH_CHARS))
                .field("path")
                .constraint(new SizeDef().min(1).max(PATH_CHARS))
                .field("revision")
                .constraint(new SizeDef().min(1).max(VERSION_CHARS))
                .field("startLine")
                .constraint(new PositiveDef())
                .field("endLine")
                .constraint(new PositiveDef())
                .field("quote")
                .constraint(new SizeDef().min(1).max(QUOTE_CHARS));
        mapping.type(ResultExample.class)
                .field("citation")
                .valid()
                .field("note")
                .constraint(new SizeDef().min(1).max(EXAMPLE_NOTE_CHARS));
        mapping.type(OpenQuestion.class)
                .field("citation")
                .valid()
                .field("openQuestion")
                .constraint(new SizeDef().min(1).max(OPEN_QUESTION_CHARS))
                .field("wouldSettleIt")
                .constraint(new SizeDef().min(1).max(OPEN_QUESTION_CHARS));
        mapping.type(SearchWarrant.class)
                .field("consulted")
                .constraint(new SizeDef().min(1).max(CONSULTED_SOURCES))
                .valid()
                .field("lookedFor")
                .constraint(new SizeDef().min(1).max(WARRANT_FIELD_CHARS))
                .field("boundary")
                .constraint(new SizeDef().min(1).max(WARRANT_FIELD_CHARS));
        mapping.type(InapplicabilityWarrant.class)
                .field("consulted")
                .constraint(new SizeDef().min(1).max(CONSULTED_SOURCES))
                .valid()
                .field("prerequisite")
                .constraint(new SizeDef().min(1).max(WARRANT_FIELD_CHARS))
                .field("ruledOutBy")
                .constraint(new SizeDef().min(1).max(WARRANT_FIELD_CHARS));
        mapping.type(IntroducedBasis.class)
                .field("head")
                .constraint(new PositiveOrZeroDef())
                .constraint(new MaxDef().value(MAX_COUNT))
                .field("explanation")
                .constraint(new SizeDef().min(1).max(PROBLEM_EXPLANATION_CHARS));
        mapping.type(WorsenedBasis.class)
                .field("head")
                .constraint(new PositiveOrZeroDef())
                .constraint(new MaxDef().value(MAX_COUNT))
                .field("base")
                .constraint(new PositiveOrZeroDef())
                .constraint(new MaxDef().value(MAX_COUNT))
                .field("explanation")
                .constraint(new SizeDef().min(1).max(PROBLEM_EXPLANATION_CHARS));
        mapping.type(DutyBasis.class)
                .field("event")
                .constraint(new PositiveOrZeroDef())
                .constraint(new MaxDef().value(MAX_COUNT))
                .field("criterionQuote")
                .constraint(new SizeDef().min(1).max(QUOTE_CHARS))
                .field("explanation")
                .constraint(new SizeDef().min(1).max(PROBLEM_EXPLANATION_CHARS));
        mapping.type(StateBasis.class)
                .field("explanation")
                .constraint(new SizeDef().min(1).max(PROBLEM_EXPLANATION_CHARS));
        mapping.type(ReadReceipt.class)
                .field("sourceKind")
                .valid()
                .field("sourcePath")
                .constraint(new SizeDef().min(1).max(PATH_CHARS))
                .field("path")
                .constraint(new SizeDef().min(1).max(PATH_CHARS))
                .field("version")
                .constraint(new SizeDef().min(1).max(VERSION_CHARS))
                .field("contentDigest")
                .constraint(new PatternDef().regexp("[0-9a-f]{64}"))
                .field("startLine")
                .constraint(new PositiveDef())
                .field("endLine")
                .constraint(new PositiveDef());
        mapping.type(CoverageOmission.class)
                .field("what")
                .constraint(new SizeDef().min(1).max(SUMMARY_CHARS))
                .constraint(new PatternDef().regexp("[^\\r\\n]*"))
                .field("count")
                .constraint(new PositiveOrZeroDef())
                .constraint(new MaxDef().value(MAX_COUNT))
                .field("items")
                .constraint(new SizeDef().max(OMISSION_ITEMS))
                .containerElementType()
                .constraint(new SizeDef().min(1).max(WARRANT_FIELD_CHARS));
        mapping.type(HostCoverage.class)
                .field("reads")
                .constraint(new PositiveOrZeroDef())
                .constraint(new MaxDef().value(MAX_COUNT))
                .field("truncatedReads")
                .constraint(new PositiveOrZeroDef())
                .constraint(new MaxDef().value(MAX_COUNT))
                .field("failedReads")
                .constraint(new PositiveOrZeroDef())
                .constraint(new MaxDef().value(MAX_COUNT))
                .field("receipts")
                .constraint(new SizeDef().max(RECEIPTS))
                .valid()
                .field("omitted")
                .constraint(new SizeDef().max(OMISSIONS))
                .valid();
        mapping.type(ClaimedCoverage.class)
                .field("selected")
                .constraint(new PositiveOrZeroDef())
                .constraint(new MaxDef().value(MAX_COUNT))
                .field("checked")
                .constraint(new PositiveOrZeroDef())
                .constraint(new MaxDef().value(MAX_COUNT))
                .field("total")
                .constraint(new PositiveOrZeroDef())
                .constraint(new MaxDef().value(MAX_COUNT))
                .field("omitted")
                .constraint(new SizeDef().max(OMISSIONS))
                .valid();
        return configuration.addMapping(mapping).buildValidatorFactory().getValidator();
    }
}
