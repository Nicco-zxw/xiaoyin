package studio;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PipelineTest {
  @Test void executesStagesInOrder() {
    var seen = new ArrayList<String>();
    new Pipeline().execute(seen::add);
    assertThat(seen).containsExactly("MATERIAL_CHECK", "MOCK_GENERATION", "RESULT_PACKAGING");
  }
  @Test void failureAtEveryStageStopsLaterSteps() {
    for (var failure : List.of("MATERIAL_CHECK", "MOCK_GENERATION", "RESULT_PACKAGING")) {
      var seen = new ArrayList<String>();
      assertThatThrownBy(() -> new Pipeline().execute(stage -> {
        seen.add(stage);
        if (stage.equals(failure)) throw new IllegalStateException("指定失败");
      })).hasMessage("指定失败");
      assertThat(seen).isEqualTo(Pipeline.STAGES.subList(0, Pipeline.STAGES.indexOf(failure) + 1));
    }
  }
}
