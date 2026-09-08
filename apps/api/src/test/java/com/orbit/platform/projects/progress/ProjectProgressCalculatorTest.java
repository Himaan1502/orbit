package com.orbit.platform.projects.progress;

import com.orbit.platform.projects.progress.ProjectProgressCalculator.ProjectProgress;
import com.orbit.platform.projects.progress.ProjectProgressCalculator.SkillContribution;
import com.orbit.platform.projects.progress.ProjectProgressCalculator.TaskTally;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Direct tests of derived project progress (ADR-024 §6) — no Spring, no database. */
class ProjectProgressCalculatorTest {

    @Test
    void taskProgressIsDoneOverTotalAndNullWithNoTasks() {
        assertThat(ProjectProgressCalculator.compute(new TaskTally(4, 1), List.of()).taskProgress())
                .isEqualTo(0.25);
        assertThat(ProjectProgressCalculator.compute(TaskTally.empty(), List.of()).taskProgress())
                .isNull();
    }

    @Test
    void skillsDemonstratedIsWeightedAndCappedAtOne() {
        var p = ProjectProgressCalculator.compute(TaskTally.empty(), List.of(
                new SkillContribution(0.8, 0.4), new SkillContribution(0.6, 0.6)));
        // Σdem / Σexp = 1.0 / 1.4 = 0.714
        assertThat(p.skillsDemonstrated()).isCloseTo(0.714, within(1e-3));

        var over = ProjectProgressCalculator.compute(TaskTally.empty(), List.of(
                new SkillContribution(0.2, 0.2), new SkillContribution(0.1, 0.5)));  // demonstrated > expected
        assertThat(over.skillsDemonstrated()).isEqualTo(1.0);
    }

    @Test
    void overallIsTheMeanOfTheComponentsThatHaveData() {
        // tasks 0.5, skills 0.5 -> overall 0.5
        var both = ProjectProgressCalculator.compute(new TaskTally(2, 1),
                List.of(new SkillContribution(1.0, 0.5)));
        assertThat(both.overall()).isEqualTo(0.5);

        // only tasks
        assertThat(ProjectProgressCalculator.compute(new TaskTally(4, 3), List.of()).overall())
                .isEqualTo(0.75);

        // nothing at all
        assertThat(ProjectProgressCalculator.compute(TaskTally.empty(), List.of()).overall())
                .isEqualTo(0.0);
    }

    @Test
    void isReproducible() {
        TaskTally t = new TaskTally(5, 2);
        List<SkillContribution> s = List.of(new SkillContribution(0.7, 0.3));
        ProjectProgress a = ProjectProgressCalculator.compute(t, s);
        ProjectProgress b = ProjectProgressCalculator.compute(t, s);
        assertThat(a).isEqualTo(b);
    }
}
