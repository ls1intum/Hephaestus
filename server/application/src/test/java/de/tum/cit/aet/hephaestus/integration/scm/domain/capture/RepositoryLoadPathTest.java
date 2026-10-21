package de.tum.cit.aet.hephaestus.integration.scm.domain.capture;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RepositoryLoadPathTest extends BaseUnitTest {

    @Test
    void shouldReadTheHostTheFullPathAndTheRefWhenThePathNamesThem() {
        assertThat(RepositoryLoadPath.parse("repos/github.com/acme/api"))
                .contains(new RepositoryLoadPath("github.com", "acme/api", null));
        assertThat(RepositoryLoadPath.parse("repos/GitHub.com/acme/api@feature/x"))
                .contains(new RepositoryLoadPath("github.com", "acme/api", "feature/x"));
        assertThat(RepositoryLoadPath.parse("repos/github.com/acme/api@pull/42"))
                .contains(new RepositoryLoadPath("github.com", "acme/api", "pull/42"));
        assertThat(RepositoryLoadPath.parse("repos/gitlab.example.org/group/sub/proj@merge-request/7"))
                .contains(new RepositoryLoadPath("gitlab.example.org", "group/sub/proj", "merge-request/7"));
        assertThat(RepositoryLoadPath.parse("repos/github.com/acme/api/"))
                .map(RepositoryLoadPath::folderPath)
                .contains("repos/github.com/acme/api");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "repos/acme/api",
                "acme/api",
                "repos/github.com/acme/../api",
                "repos/github.com//api",
                "repos/github.com/acme/api@",
                "repos/github.com/acme/api@main branch",
                "repos/github.com/acme/a pi",
                "repos/github.com/acme/api;rm"
            })
    void shouldNameNoRepositoryWhenThePathIsMalformed(String path) {
        assertThat(RepositoryLoadPath.parse(path)).isEmpty();
    }
}
