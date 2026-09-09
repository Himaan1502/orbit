package com.orbit.platform.common.jobs;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobRunRepository extends JpaRepository<JobRun, UUID> {

    Optional<JobRun> findByJobNameAndRunKey(String jobName, String runKey);

    List<JobRun> findTop20ByJobNameOrderByStartedAtDesc(String jobName);
}
