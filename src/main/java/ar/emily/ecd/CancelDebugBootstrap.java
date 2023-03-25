package ar.emily.ecd;

import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CancelDebugBootstrap implements PluginBootstrap {

  private final YamlConfiguration config = new YamlConfiguration();

  {
    this.config.addDefault("test", false);
    this.config.addDefault("inject-during-bootstrap", false);
    this.config.addDefault("print-stack-traces", false);
    this.config.addDefault("events", List.of());
    this.config.addDefault("cancellable-events-to-sacrifice-in-unsafe-package", List.of());
  }

  @Override
  public void bootstrap(final @NotNull PluginProviderContext context) {
    final Logger logger = context.getLogger();
    final Path configFile = context.getDataDirectory().resolve("config.yml");
    if (Files.notExists(configFile)) {
      try (final var in = CancelDebugBootstrap.class.getClassLoader().getResourceAsStream("config.yml")) {
        Files.createDirectories(configFile.getParent());
        Files.copy(in, configFile);
      } catch (final IOException ex) {
        logger.error("Could not save config.yml", ex);
      }
    }

    try {
      this.config.load(configFile.toFile());
    } catch (final IOException | InvalidConfigurationException ex) {
      logger.error("Unable to load config.yml", ex);
    }


    if (this.config.getBoolean("inject-during-bootstrap")) {
      try {
        CancelDebugInjector.inject(this.config, logger);
      } catch (final IOException ex) {
        throw new UncheckedIOException(ex);
      }
    }
  }

  @Override
  public @NotNull JavaPlugin createPlugin(final @NotNull PluginProviderContext context) {
    try {
      return new CancelDebugPlugin(this.config);
    } catch (final IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
