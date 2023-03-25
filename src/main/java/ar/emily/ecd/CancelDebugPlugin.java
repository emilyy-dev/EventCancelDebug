package ar.emily.ecd;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;

public final class CancelDebugPlugin extends JavaPlugin implements Listener {

  @EventHandler
  private static void on(final BlockBreakEvent event) {
    event.setCancelled(true);
  }

  public CancelDebugPlugin(final ConfigurationSection config) throws IOException {
    if (!config.getBoolean("inject-during-bootstrap")) {
      CancelDebugInjector.inject(config, getSLF4JLogger());
    }
  }

  @Override
  public void onEnable() {
    if (CancelDebugInjector.UNAVAILABILITY_REASON == null && getConfig().getBoolean("test")) {
      getServer().getPluginManager().registerEvents(this, this);
    }
  }
}
