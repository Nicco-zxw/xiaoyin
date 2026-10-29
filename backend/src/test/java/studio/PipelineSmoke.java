package studio;
import java.util.*;
/** Dependency-free smoke check, also usable when Maven dependencies are unavailable. */
public class PipelineSmoke {
 public static void main(String[] args) {
   var seen=new ArrayList<String>();new Pipeline().execute(seen::add);
   if(!seen.equals(Pipeline.STAGES))throw new AssertionError("Expected stages in order, got "+seen);
   for(String failure:Pipeline.STAGES){
     seen.clear();boolean failed=false;
     try{new Pipeline().execute(s->{seen.add(s);if(s.equals(failure))throw new IllegalStateException("fail");});}catch(IllegalStateException e){failed=true;}
     if(!failed||!seen.equals(Pipeline.STAGES.subList(0,Pipeline.STAGES.indexOf(failure)+1)))throw new AssertionError("Did not stop at "+failure);
   }
   System.out.println("Pipeline order and every failure boundary passed");
 }
}
