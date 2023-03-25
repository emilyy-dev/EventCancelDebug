package ar.emily.ecd;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.JarLibrary;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CancelDebugLoader implements PluginLoader {

  private static Path deleteOnExit(final Path path) {
    path.toFile().deleteOnExit();
    return path;
  }

  @Override
  public void classloader(final @NotNull PluginClasspathBuilder classpathBuilder) {
    try (final var zfs = FileSystems.newFileSystem(classpathBuilder.getContext().getPluginSource())) {
      final Path tmp = deleteOnExit(Files.createTempDirectory("event-cancel-debug-plugin-"));
      try (final var dependencyListing = Files.list(zfs.getPath("META-INF", "dependencies"))) {
        for (final Path dependency : (Iterable<? extends Path>) dependencyListing::iterator) {
          final Path dst = tmp.resolve(dependency.getFileName().toString());
          try (final var in = Files.newInputStream(dependency)) {
            Files.copy(in, dst);
          }

          classpathBuilder.addLibrary(new JarLibrary(deleteOnExit(dst)));
        }
      }
    } catch (final IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
