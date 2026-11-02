package dev.voidfps.core;
import net.fabricmc.loader.api.FabricLoader;import java.util.List;
public final class CompatibilityManager {
 private static final List<String> OPTIMIZERS=List.of("sodium","lithium","ferritecore","immediatelyfast","entityculling");
 private CompatibilityManager(){}
 public static void logDetectedMods(){
  FabricLoader loader=FabricLoader.getInstance();
  for(String id:OPTIMIZERS)if(loader.isModLoaded(id))VoidFpsClient.LOGGER.info("Detected optional optimizer '{}'; VOID FPS will not duplicate its systems.",id);
 }
}
