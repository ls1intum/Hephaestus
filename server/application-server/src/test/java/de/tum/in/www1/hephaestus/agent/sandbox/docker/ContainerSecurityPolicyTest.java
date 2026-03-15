package de.tum.in.www1.hephaestus.agent.sandbox.docker;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.agent.sandbox.SandboxProperties;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.NetworkPolicy;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.ResourceLimits;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SecurityProfile;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ContainerSecurityPolicy")
class ContainerSecurityPolicyTest extends BaseUnitTest {

    private ContainerSecurityPolicy securityPolicy;

    @BeforeEach
    void setUp() {
        SandboxProperties properties = new SandboxProperties(
            true,
            "unix:///var/run/docker.sock",
            false,
            null,
            5,
            10,
            60,
            null,
            8080,
            null,
            null
        );
        securityPolicy = new ContainerSecurityPolicy(properties, null);
    }

    @Nested
    @DisplayName("Security flags")
    class SecurityFlags {

        @Test
        @DisplayName("should apply all default security options")
        void shouldApplyDefaultSecurityOptions() {
            DockerOperations.HostConfigSpec config = securityPolicy.buildHostConfig(
                SecurityProfile.DEFAULT,
                ResourceLimits.DEFAULT,
                new NetworkPolicy(false, null, null)
            );

            assertThat(config.readonlyRootfs()).isTrue();
            assertThat(config.privileged()).isFalse();
            assertThat(config.capDrop()).containsExactly("ALL");
            assertThat(config.securityOpts()).contains("no-new-privileges");
            assertThat(config.cgroupnsMode()).isEqualTo("private");
            assertThat(config.ipcMode()).isEqualTo("none");
        }

        @Test
        @DisplayName("should set memory and swap equal (no swap)")
        void shouldSetMemoryNoSwap() {
            DockerOperations.HostConfigSpec config = securityPolicy.buildHostConfig(
                SecurityProfile.DEFAULT,
                ResourceLimits.DEFAULT,
                new NetworkPolicy(false, null, null)
            );

            assertThat(config.memoryBytes()).isEqualTo(4L * 1024 * 1024 * 1024);
            assertThat(config.memorySwapBytes()).isEqualTo(config.memoryBytes());
        }

        @Test
        @DisplayName("should set CPU limit in nanoCPUs")
        void shouldSetCpuLimit() {
            DockerOperations.HostConfigSpec config = securityPolicy.buildHostConfig(
                SecurityProfile.DEFAULT,
                ResourceLimits.DEFAULT,
                new NetworkPolicy(false, null, null)
            );

            assertThat(config.nanoCpus()).isEqualTo(2_000_000_000L);
        }

        @Test
        @DisplayName("should set PID limit")
        void shouldSetPidLimit() {
            DockerOperations.HostConfigSpec config = securityPolicy.buildHostConfig(
                SecurityProfile.DEFAULT,
                ResourceLimits.DEFAULT,
                new NetworkPolicy(false, null, null)
            );

            assertThat(config.pidsLimit()).isEqualTo(256);
        }

        @Test
        @DisplayName("should set ulimit nofile")
        void shouldSetUlimitNofile() {
            DockerOperations.HostConfigSpec config = securityPolicy.buildHostConfig(
                SecurityProfile.DEFAULT,
                ResourceLimits.DEFAULT,
                new NetworkPolicy(false, null, null)
            );

            assertThat(config.ulimits()).containsKey("nofile");
            assertThat(config.ulimits().get("nofile").soft()).isEqualTo(1024);
            assertThat(config.ulimits().get("nofile").hard()).isEqualTo(1024);
        }
    }

    @Nested
    @DisplayName("Network DNS")
    class NetworkDns {

        @Test
        @DisplayName("should set dns=0.0.0.0 when internet is disabled")
        void shouldBlockDnsWhenNoInternet() {
            DockerOperations.HostConfigSpec config = securityPolicy.buildHostConfig(
                SecurityProfile.DEFAULT,
                ResourceLimits.DEFAULT,
                new NetworkPolicy(false, null, null)
            );

            assertThat(config.dns()).containsExactly("0.0.0.0");
        }

        @Test
        @DisplayName("should not block DNS when internet is enabled")
        void shouldAllowDnsWithInternet() {
            DockerOperations.HostConfigSpec config = securityPolicy.buildHostConfig(
                SecurityProfile.DEFAULT,
                ResourceLimits.DEFAULT,
                new NetworkPolicy(true, null, null)
            );

            assertThat(config.dns()).isEmpty();
        }

        @Test
        @DisplayName("should block DNS when networkPolicy is null")
        void shouldBlockDnsWhenNetworkPolicyNull() {
            DockerOperations.HostConfigSpec config = securityPolicy.buildHostConfig(
                SecurityProfile.DEFAULT,
                ResourceLimits.DEFAULT,
                null
            );

            assertThat(config.dns()).containsExactly("0.0.0.0");
        }
    }

    @Nested
    @DisplayName("Tmpfs mounts")
    class TmpfsMounts {

        @Test
        @DisplayName("should configure default tmpfs mounts")
        void shouldConfigureDefaultTmpfs() {
            DockerOperations.HostConfigSpec config = securityPolicy.buildHostConfig(
                SecurityProfile.DEFAULT,
                ResourceLimits.DEFAULT,
                new NetworkPolicy(false, null, null)
            );

            assertThat(config.tmpfsMounts()).containsKey("/tmp");
            assertThat(config.tmpfsMounts()).containsKey("/run");
            assertThat(config.tmpfsMounts()).containsKey("/home/agent/.local");
            assertThat(config.tmpfsMounts().get("/tmp")).contains("noexec");
        }
    }

    @Nested
    @DisplayName("Runtime configuration")
    class RuntimeConfiguration {

        @Test
        @DisplayName("should use gVisor runtime when configured in security profile")
        void shouldUseGvisorFromProfile() {
            SecurityProfile gvisorProfile = new SecurityProfile(
                "runsc",
                "sandbox/agent-seccomp-profile.json",
                true,
                true,
                true,
                "none",
                List.of("ALL"),
                Map.of()
            );

            DockerOperations.HostConfigSpec config = securityPolicy.buildHostConfig(
                gvisorProfile,
                ResourceLimits.DEFAULT,
                new NetworkPolicy(false, null, null)
            );

            assertThat(config.runtime()).isEqualTo("runsc");
        }

        @Test
        @DisplayName("should use global runtime when profile has none")
        void shouldUseGlobalRuntime() {
            SandboxProperties propsWithRuntime = new SandboxProperties(
                true,
                "unix:///var/run/docker.sock",
                false,
                null,
                5,
                10,
                60,
                "runsc",
                8080,
                null,
                null
            );
            ContainerSecurityPolicy policyWithRuntime = new ContainerSecurityPolicy(propsWithRuntime, null);

            DockerOperations.HostConfigSpec config = policyWithRuntime.buildHostConfig(
                SecurityProfile.DEFAULT,
                ResourceLimits.DEFAULT,
                new NetworkPolicy(false, null, null)
            );

            assertThat(config.runtime()).isEqualTo("runsc");
        }

        @Test
        @DisplayName("should have no runtime by default")
        void shouldHaveNoRuntimeByDefault() {
            DockerOperations.HostConfigSpec config = securityPolicy.buildHostConfig(
                SecurityProfile.DEFAULT,
                ResourceLimits.DEFAULT,
                new NetworkPolicy(false, null, null)
            );

            assertThat(config.runtime()).isNull();
        }
    }

    @Nested
    @DisplayName("Labels")
    class Labels {

        @Test
        @DisplayName("should build reconciliation labels")
        void shouldBuildLabels() {
            UUID jobId = UUID.randomUUID();
            Map<String, String> labels = securityPolicy.buildLabels(jobId);

            assertThat(labels).containsEntry("hephaestus.managed", "true");
            assertThat(labels).containsEntry("hephaestus.job-id", jobId.toString());
        }
    }

    @Nested
    @DisplayName("Seccomp profile")
    class SeccompProfile {

        @Test
        @DisplayName("should include seccomp profile in security options when provided")
        void shouldIncludeSeccompWhenProvided() {
            ContainerSecurityPolicy policyWithSeccomp = new ContainerSecurityPolicy(
                new SandboxProperties(
                    true,
                    "unix:///var/run/docker.sock",
                    false,
                    null,
                    5,
                    10,
                    60,
                    null,
                    8080,
                    null,
                    null
                ),
                "{\"defaultAction\":\"SCMP_ACT_ERRNO\"}"
            );

            DockerOperations.HostConfigSpec config = policyWithSeccomp.buildHostConfig(
                SecurityProfile.DEFAULT,
                ResourceLimits.DEFAULT,
                new NetworkPolicy(false, null, null)
            );

            assertThat(config.securityOpts()).anyMatch(
                opt -> opt.startsWith("seccomp=") && opt.contains("SCMP_ACT_ERRNO")
            );
        }

        @Test
        @DisplayName("should not include seccomp when profile is null")
        void shouldNotIncludeSeccompWhenNull() {
            DockerOperations.HostConfigSpec config = securityPolicy.buildHostConfig(
                SecurityProfile.DEFAULT,
                ResourceLimits.DEFAULT,
                new NetworkPolicy(false, null, null)
            );

            assertThat(config.securityOpts()).noneMatch(opt -> opt.startsWith("seccomp="));
        }
    }

    @Nested
    @DisplayName("Custom resource limits")
    class CustomResourceLimits {

        @Test
        @DisplayName("should apply custom resource limits")
        void shouldApplyCustomLimits() {
            ResourceLimits custom = new ResourceLimits(
                8L * 1024 * 1024 * 1024, // 8GB
                4.0,
                512,
                Duration.ofMinutes(30)
            );

            DockerOperations.HostConfigSpec config = securityPolicy.buildHostConfig(
                SecurityProfile.DEFAULT,
                custom,
                new NetworkPolicy(false, null, null)
            );

            assertThat(config.memoryBytes()).isEqualTo(8L * 1024 * 1024 * 1024);
            assertThat(config.nanoCpus()).isEqualTo(4_000_000_000L);
            assertThat(config.pidsLimit()).isEqualTo(512);
        }
    }

    @Nested
    @DisplayName("Security enforcement floors")
    class EnforcementFloors {

        @Test
        @DisplayName("should enforce no-new-privileges even when profile disables it")
        void shouldEnforceNoNewPrivileges() {
            SecurityProfile laxProfile = new SecurityProfile(
                null,
                null,
                false,
                false,
                false,
                "host",
                List.of(),
                Map.of()
            );

            DockerOperations.HostConfigSpec config = securityPolicy.buildHostConfig(
                laxProfile,
                ResourceLimits.DEFAULT,
                new NetworkPolicy(false, null, null)
            );

            assertThat(config.securityOpts()).contains("no-new-privileges");
        }

        @Test
        @DisplayName("should enforce read-only rootfs even when profile disables it")
        void shouldEnforceReadOnlyRootfs() {
            SecurityProfile laxProfile = new SecurityProfile(
                null,
                null,
                false,
                false,
                false,
                "host",
                List.of(),
                Map.of()
            );

            DockerOperations.HostConfigSpec config = securityPolicy.buildHostConfig(
                laxProfile,
                ResourceLimits.DEFAULT,
                new NetworkPolicy(false, null, null)
            );

            assertThat(config.readonlyRootfs()).isTrue();
        }

        @Test
        @DisplayName("should enforce cap-drop ALL even when profile drops nothing")
        void shouldEnforceCapDropAll() {
            SecurityProfile laxProfile = new SecurityProfile(null, null, true, true, true, "none", List.of(), Map.of());

            DockerOperations.HostConfigSpec config = securityPolicy.buildHostConfig(
                laxProfile,
                ResourceLimits.DEFAULT,
                new NetworkPolicy(false, null, null)
            );

            assertThat(config.capDrop()).containsExactly("ALL");
        }

        @Test
        @DisplayName("should enforce private cgroup namespace even when profile disables it")
        void shouldEnforcePrivateCgroupns() {
            SecurityProfile laxProfile = new SecurityProfile(
                null,
                null,
                true,
                true,
                false,
                "none",
                List.of("ALL"),
                Map.of()
            );

            DockerOperations.HostConfigSpec config = securityPolicy.buildHostConfig(
                laxProfile,
                ResourceLimits.DEFAULT,
                new NetworkPolicy(false, null, null)
            );

            assertThat(config.cgroupnsMode()).isEqualTo("private");
        }

        @Test
        @DisplayName("should reject ipc mode 'host' and force 'none'")
        void shouldRejectHostIpcMode() {
            SecurityProfile hostIpcProfile = new SecurityProfile(
                null,
                null,
                true,
                true,
                true,
                "host",
                List.of("ALL"),
                Map.of()
            );

            DockerOperations.HostConfigSpec config = securityPolicy.buildHostConfig(
                hostIpcProfile,
                ResourceLimits.DEFAULT,
                new NetworkPolicy(false, null, null)
            );

            assertThat(config.ipcMode()).isEqualTo("none");
        }

        @Test
        @DisplayName("should default ipc mode to none when profile sets null")
        void shouldDefaultIpcToNone() {
            SecurityProfile nullIpcProfile = new SecurityProfile(
                null,
                null,
                true,
                true,
                true,
                null,
                List.of("ALL"),
                Map.of()
            );

            DockerOperations.HostConfigSpec config = securityPolicy.buildHostConfig(
                nullIpcProfile,
                ResourceLimits.DEFAULT,
                new NetworkPolicy(false, null, null)
            );

            assertThat(config.ipcMode()).isEqualTo("none");
        }

        @Test
        @DisplayName("should allow ipc mode 'private'")
        void shouldAllowPrivateIpcMode() {
            SecurityProfile privateIpcProfile = new SecurityProfile(
                null,
                null,
                true,
                true,
                true,
                "private",
                List.of("ALL"),
                Map.of()
            );

            DockerOperations.HostConfigSpec config = securityPolicy.buildHostConfig(
                privateIpcProfile,
                ResourceLimits.DEFAULT,
                new NetworkPolicy(false, null, null)
            );

            assertThat(config.ipcMode()).isEqualTo("private");
        }

        @Test
        @DisplayName("should enforce mandatory tmpfs mounts over caller-supplied ones")
        void shouldEnforceMandatoryTmpfs() {
            SecurityProfile weakTmpfsProfile = new SecurityProfile(
                null,
                null,
                true,
                true,
                true,
                "none",
                List.of("ALL"),
                Map.of("/tmp", "rw,exec,size=100g", "/custom", "rw,size=512m")
            );

            DockerOperations.HostConfigSpec config = securityPolicy.buildHostConfig(
                weakTmpfsProfile,
                ResourceLimits.DEFAULT,
                new NetworkPolicy(false, null, null)
            );

            // Mandatory /tmp mount should use hardened options, not the caller's "exec"
            assertThat(config.tmpfsMounts().get("/tmp")).contains("noexec");
            // Custom mount should be preserved
            assertThat(config.tmpfsMounts()).containsKey("/custom");
            // All mandatory mounts present
            assertThat(config.tmpfsMounts()).containsKey("/run");
            assertThat(config.tmpfsMounts()).containsKey("/home/agent/.local");
        }

        @Test
        @DisplayName("should prevent runtime downgrade when global is configured")
        void shouldPreventRuntimeDowngrade() {
            SandboxProperties propsWithRuntime = new SandboxProperties(
                true,
                "unix:///var/run/docker.sock",
                false,
                null,
                5,
                10,
                60,
                "runsc",
                8080,
                null,
                null
            );
            ContainerSecurityPolicy policyWithRuntime = new ContainerSecurityPolicy(propsWithRuntime, null);

            // Caller tries to downgrade to runc
            SecurityProfile runcProfile = new SecurityProfile(
                "runc",
                null,
                true,
                true,
                true,
                "none",
                List.of("ALL"),
                Map.of()
            );

            DockerOperations.HostConfigSpec config = policyWithRuntime.buildHostConfig(
                runcProfile,
                ResourceLimits.DEFAULT,
                new NetworkPolicy(false, null, null)
            );

            // Global "runsc" should be enforced, not the caller's "runc"
            assertThat(config.runtime()).isEqualTo("runsc");
        }
    }

    @Nested
    @DisplayName("Ulimits")
    class UlimitTests {

        @Test
        @DisplayName("should set nproc ulimit matching pidsLimit")
        void shouldSetNprocUlimit() {
            DockerOperations.HostConfigSpec config = securityPolicy.buildHostConfig(
                SecurityProfile.DEFAULT,
                ResourceLimits.DEFAULT,
                new NetworkPolicy(false, null, null)
            );

            assertThat(config.ulimits()).containsKey("nproc");
            assertThat(config.ulimits().get("nproc").soft()).isEqualTo(256);
            assertThat(config.ulimits().get("nproc").hard()).isEqualTo(256);
        }

        @Test
        @DisplayName("should set core ulimit to zero (no core dumps)")
        void shouldDisableCoreDumps() {
            DockerOperations.HostConfigSpec config = securityPolicy.buildHostConfig(
                SecurityProfile.DEFAULT,
                ResourceLimits.DEFAULT,
                new NetworkPolicy(false, null, null)
            );

            assertThat(config.ulimits()).containsKey("core");
            assertThat(config.ulimits().get("core").soft()).isZero();
            assertThat(config.ulimits().get("core").hard()).isZero();
        }
    }
}
