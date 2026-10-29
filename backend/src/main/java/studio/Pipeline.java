package studio;
import java.util.List;
import java.util.function.Consumer;
public class Pipeline {
 public static final List<String> STAGES=List.of("MATERIAL_CHECK","MOCK_GENERATION","RESULT_PACKAGING");
 public void execute(Consumer<String> step) { for(String stage:STAGES)step.accept(stage); }
}
