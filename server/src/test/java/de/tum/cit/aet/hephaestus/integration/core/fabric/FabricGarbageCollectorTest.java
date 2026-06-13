package de.tum.cit.aet.hephaestus.integration.core.fabric;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class FabricGarbageCollectorTest extends BaseUnitTest {

    @TempDir
    Path root;

    private final JsonMapper mapper = JsonMapper.builder().build();
    private FabricLayout layout;
    private ContentAddressedStore cas;
    private FabricGarbageCollector gc;

    @BeforeEach
    void setUp() {
        layout = new FabricLayout(root.toString());
        cas = new ContentAddressedStore(layout);
        gc = new FabricGarbageCollector(layout, cas, mapper, 30);
    }

    /** Write a job manifest referencing the given blob shas and return the job directory. */
    private Path writeJob(String jobId, String... shas) throws Exception {
        var manifest = mapper.createObjectNode();
        manifest.put("jobId", jobId);
        var entries = manifest.putArray("entries");
        for (String sha : shas) {
            entries.addObject().put("sha256", sha);
        }
        Path dir = layout.jobDir(jobId);
        Files.createDirectories(dir);
        Files.write(dir.resolve("manifest.json"), mapper.writeValueAsBytes(manifest));
        return dir;
    }

    @Test
    void pruneExpiredJobs_removesDirsOlderThanCutoffOnly() throws Exception {
        Path recent = writeJob("recent", "aa");
        Path old = writeJob("old", "bb");
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(Duration.ofDays(60))));

        int pruned = gc.pruneExpiredJobs(Instant.now().minus(Duration.ofDays(30)));

        assertThat(pruned).isEqualTo(1);
        assertThat(recent).exists();
        assertThat(old).doesNotExist();
    }

    @Test
    void referencedShas_collectsEveryShaFromSurvivingManifests() throws Exception {
        writeJob("a", "1111", "2222");
        writeJob("b", "3333");

        assertThat(gc.referencedShas()).containsExactlyInAnyOrder("1111", "2222", "3333");
    }

    @Test
    void collect_endToEnd_keepsReferencedBlobsAndSweepsOrphans() throws Exception {
        String keep = cas.put("keep".getBytes(StandardCharsets.UTF_8));
        String expired = cas.put("expired".getBytes(StandardCharsets.UTF_8));
        String orphan = cas.put("orphan".getBytes(StandardCharsets.UTF_8));

        writeJob("recent", keep);
        Path old = writeJob("old", expired);
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(Duration.ofDays(60))));

        gc.collect();

        assertThat(cas.exists(keep)).as("blob referenced by a surviving job manifest is kept").isTrue();
        assertThat(cas.exists(expired)).as("blob referenced only by a pruned (expired) job is swept").isFalse();
        assertThat(cas.exists(orphan)).as("blob referenced by no manifest is swept").isFalse();
    }
}
