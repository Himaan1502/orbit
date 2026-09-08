package com.orbit.platform.roadmaps;

import com.orbit.platform.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-022 §2 — the V7 seed is idempotent. Running its body a second time against
 * the already-migrated database inserts no rows and raises no error.
 */
class RoadmapSeedIdempotencyIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    @Test
    void reRunningTheSeedChangesNothing() throws Exception {
        long[] before = counts();

        try (Connection c = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(c, new ClassPathResource("db/migration/V7__seed_roadmaps.sql"));
        }

        assertThat(counts()).containsExactly(before);
        assertThat(before[0]).isEqualTo(13);   // roadmaps
        assertThat(before[1]).isEqualTo(125);  // topics
        assertThat(before[2]).isEqualTo(183);  // topic-skill mappings
    }

    private long[] counts() {
        return new long[] {
                count("orbit.roadmaps"), count("orbit.roadmap_topics"), count("orbit.roadmap_topic_skills"),
                count("orbit.skills"), count("orbit.skill_dependencies"),
        };
    }

    private long count(String table) {
        Long v = jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
        return v == null ? 0L : v;
    }
}
