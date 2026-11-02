package dev.voidfps.core;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VoidFpsClient implements ClientModInitializer {
 public static final String MOD_ID="voidfps";
 public static final Logger LOGGER=LoggerFactory.getLogger(MOD_ID);
 private static final FrameTimeMonitor MONITOR=new FrameTimeMonitor();
 private static final VoidFpsConfig CONFIG=new VoidFpsConfig();
 private static final ProfileController PROFILES=new ProfileController();
 private static KeyMapping cycleKey, exportKey, hudKey;
 private static int refreshCounter;
 private static String cachedText="";
 @Override public void onInitializeClient() {
  CONFIG.load();
  cycleKey=KeyBindingHelper.registerKeyBinding(new KeyMapping("key.voidfps.cycle_profile",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_F8,KeyMapping.Category.MISC));
  exportKey=KeyBindingHelper.registerKeyBinding(new KeyMapping("key.voidfps.export_benchmark",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_F9,KeyMapping.Category.MISC));
  hudKey=KeyBindingHelper.registerKeyBinding(new KeyMapping("key.voidfps.toggle_hud",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_F10,KeyMapping.Category.MISC));
  ClientTickEvents.END_CLIENT_TICK.register(client->{
   while(cycleKey.consumeClick()) PROFILES.cycle(client);
   while(exportKey.consumeClick()){
    BenchmarkExporter.export(MONITOR);
    if(client.player!=null) client.player.displayClientMessage(Component.literal("VOID FPS: CSV exported to config/voidfps/"),true);
   }
   while(hudKey.consumeClick()){
    CONFIG.hudEnabled=!CONFIG.hudEnabled; CONFIG.save();
    if(client.player!=null) client.player.displayClientMessage(Component.literal("VOID FPS monitor: "+(CONFIG.hudEnabled?"ON":"OFF")),true);
   }
  });
  HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID,"performance_monitor"),
   (graphics,delta)->renderHud(Minecraft.getInstance(),graphics));
  CompatibilityManager.logDetectedMods();
  LOGGER.info("VOID FPS Core initialized. F8 profiles, F9 CSV export, F10 monitor.");
 }
 private static void renderHud(Minecraft client,GuiGraphicsExtractor graphics){
  if(!CONFIG.hudEnabled||client.level==null||client.player==null)return;
  MONITOR.recordFrame(System.nanoTime());
  if(++refreshCounter>=8){refreshCounter=0;cachedText=buildHudText(client);}
  graphics.text(client.font,cachedText,8,8,0xFFE8F2FF);
 }
 private static String buildHudText(Minecraft client){
  long ram=(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/(1024L*1024L);
  return String.format("VOID FPS | %s | %d FPS | %.1f ms avg / %.1f ms p99 | heap %d MB | RD %d / SD %d",
   PROFILES.currentProfile().displayName(),client.getFps(),MONITOR.averageFrameTimeMs(),MONITOR.percentileFrameTimeMs(.99),
   ram,client.options.renderDistance().get(),client.options.simulationDistance().get());
 }
}
