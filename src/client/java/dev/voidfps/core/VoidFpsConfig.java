package dev.voidfps.core;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;import java.io.Reader;import java.io.Writer;import java.nio.file.Files;import java.nio.file.Path;import java.util.Properties;
public final class VoidFpsConfig {
 public boolean hudEnabled=true;
 private final Path file=FabricLoader.getInstance().getConfigDir().resolve("voidfps.properties");
 public void load(){
  if(!Files.exists(file)){save();return;}Properties p=new Properties();
  try(Reader r=Files.newBufferedReader(file)){p.load(r);hudEnabled=Boolean.parseBoolean(p.getProperty("hudEnabled","true"));}
  catch(IOException e){VoidFpsClient.LOGGER.warn("Could not read VOID FPS config; using defaults.",e);}
 }
 public void save(){
  Properties p=new Properties();p.setProperty("hudEnabled",Boolean.toString(hudEnabled));
  try{Files.createDirectories(file.getParent());try(Writer w=Files.newBufferedWriter(file)){p.store(w,"VOID FPS Core settings");}}
  catch(IOException e){VoidFpsClient.LOGGER.warn("Could not save VOID FPS config.",e);}
 }
}
