package dev.voidfps.core;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
public final class ProfileController {
 public enum Profile {
  VANILLA("Vanilla settings"),GAMING("Gaming"),EXTREME("Extreme");
  private final String label;Profile(String label){this.label=label;}public String displayName(){return label;}
 }
 private Profile current=Profile.VANILLA;
 private Integer baseRender,baseSimulation;
 public Profile currentProfile(){return current;}
 public void cycle(Minecraft client){
  if(client.options==null)return;
  if(baseRender==null){baseRender=client.options.renderDistance().get();baseSimulation=client.options.simulationDistance().get();}
  current=switch(current){case VANILLA->Profile.GAMING;case GAMING->Profile.EXTREME;case EXTREME->Profile.VANILLA;};
  switch(current){
   case GAMING->{client.options.renderDistance().set(Math.min(baseRender,10));client.options.simulationDistance().set(Math.min(baseSimulation,6));}
   case EXTREME->{client.options.renderDistance().set(Math.min(baseRender,6));client.options.simulationDistance().set(Math.min(baseSimulation,4));}
   case VANILLA->{client.options.renderDistance().set(baseRender);client.options.simulationDistance().set(baseSimulation);}
  }
  client.options.save();
  if(client.player!=null)client.player.displayClientMessage(Component.literal("VOID FPS: "+current.displayName()+" | render "+client.options.renderDistance().get()+" | simulation "+client.options.simulationDistance().get()),true);
  VoidFpsClient.LOGGER.info("Applied {} profile.",current.displayName());
 }
}
