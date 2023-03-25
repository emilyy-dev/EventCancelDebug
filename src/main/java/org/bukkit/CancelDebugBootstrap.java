package org.bukkit;

import io.papermc.paper.plugin.provider.classloader.ConfiguredPluginClassLoader;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.logging.Level;

import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodType.methodType;

public final class CancelDebugBootstrap {

  private static final MethodType EXPECTED_DESCRIPTOR = methodType(Void.TYPE, Class.class, Boolean.TYPE);
  private static final MethodHandle DEBUG_CANCEL_MH;

  static {
    try {
      DEBUG_CANCEL_MH =
          MethodHandles.lookup().findStatic(
              CancelDebugBootstrap.class, "debugCancel",
              EXPECTED_DESCRIPTOR.insertParameterTypes(0, String.class, Boolean.TYPE)
          );
    } catch (final NoSuchMethodException | IllegalAccessException ex) {
      throw new ExceptionInInitializerError(ex);
    }
  }

  public static CallSite bootstrap(
      final MethodHandles.Lookup lookup, final String requestedMethodName, final MethodType requestedDescriptor,
      final int printStackTrace // int, because it cannot be a boolean (has to be ConstantDesc)
  ) {
    if (!requestedDescriptor.equals(EXPECTED_DESCRIPTOR) || !"debugCancel".equals(requestedMethodName)) {
      throw new IllegalArgumentException("Unexpected method descriptor " + requestedDescriptor);
    }

    return new ConstantCallSite(
        insertArguments(DEBUG_CANCEL_MH, 0, lookup.lookupClass().getSimpleName(), printStackTrace != 0)
    );
  }

  private static void debugCancel(
      final String eventName,
      final boolean printStackTrace,
      final Class<?> caller,
      final boolean cancelled
  ) {
    if (caller.getClassLoader() instanceof ConfiguredPluginClassLoader pluginClassLoader) {
      final String pluginName = pluginClassLoader.getConfiguration().getDisplayName();
      final String msg = "%s %scancelled %s".formatted(pluginName, cancelled ? "" : "un-", eventName);
      Bukkit.getLogger().log(Level.WARNING, msg, printStackTrace ? new Throwable() : null);
    }
  }

  private CancelDebugBootstrap() {
  }
}
