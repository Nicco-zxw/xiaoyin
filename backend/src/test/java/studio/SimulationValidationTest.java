package studio;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import studio.Models.Simulation;

class SimulationValidationTest {
 @Test void acceptsBothDelayEndpointsAndEveryDefinedFailureStage(){
   for(int delay:List.of(0,60000))for(String stage:Pipeline.STAGES)assertThatCode(()->TaskService.validateSimulation(new Simulation(Map.of("MATERIAL_CHECK",delay,"MOCK_GENERATION",delay,"RESULT_PACKAGING",delay),stage))).doesNotThrowAnyException();
   assertThatCode(()->TaskService.validateSimulation(Simulation.defaults())).doesNotThrowAnyException();
 }
 @Test void rejectsMissingUnknownNullAndOutOfRangeSimulationFields(){
   var values=new HashMap<>(Simulation.defaults().stageDelayMs());values.put("MOCK_GENERATION",null);
   var unknown=new HashMap<>(Simulation.defaults().stageDelayMs());unknown.put("OTHER",0);
   for(var config:List.of(new Simulation(null,null),new Simulation(Map.of(),null),new Simulation(values,null),new Simulation(unknown,null),new Simulation(Map.of("MATERIAL_CHECK",-1,"MOCK_GENERATION",0,"RESULT_PACKAGING",0),null),new Simulation(Map.of("MATERIAL_CHECK",0,"MOCK_GENERATION",60001,"RESULT_PACKAGING",0),null),new Simulation(Simulation.defaults().stageDelayMs(),"OTHER"))){
     assertThatThrownBy(()->TaskService.validateSimulation(config)).isInstanceOf(ApiError.class).hasMessageContaining("0–60000ms");
   }
 }
}
