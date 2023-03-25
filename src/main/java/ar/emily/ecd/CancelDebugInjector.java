package ar.emily.ecd;

import com.destroystokyo.paper.event.block.AnvilDamagedEvent;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.destroystokyo.paper.event.server.GS4QueryEvent;
import io.papermc.paper.event.block.BeaconActivatedEvent;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import io.papermc.paper.event.world.border.WorldBorderEvent;
import org.bukkit.Server;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.hanging.HangingEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.raid.RaidEvent;
import org.bukkit.event.server.ServerEvent;
import org.bukkit.event.vehicle.VehicleEvent;
import org.bukkit.event.weather.WeatherEvent;
import org.bukkit.event.world.WorldEvent;
import org.glavo.classfile.ClassTransform;
import org.glavo.classfile.Classfile;
import org.glavo.classfile.TypeKind;
import org.glavo.classfile.instruction.ReturnInstruction;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static java.lang.constant.ConstantDescs.CD_CallSite;
import static java.lang.constant.ConstantDescs.CD_Class;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.ofCallsiteBootstrap;
import static java.util.stream.Collectors.toUnmodifiableMap;

abstract class CancelDebugInjector {

  private static final Map<String, MethodHandles.Lookup> LOOKUP_BY_PACKAGE_MAP;
  private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
  static final @Nullable Throwable UNAVAILABILITY_REASON;

  static {
    Throwable unavailabilityReason = null;
    Map<String, MethodHandles.Lookup> lookupByPackageMap;

    try {
      MethodHandles.privateLookupIn(Server.class, LOOKUP).defineClass(readCancelDebugBootstrapClass());
      lookupByPackageMap =
          Stream.of(
                  // org.bukkit.event.*
                  BlockEvent.class,
                  EntityEvent.class,
                  HangingEvent.class,
                  InventoryEvent.class,
                  PlayerEvent.class,
                  RaidEvent.class,
                  ServerEvent.class,
                  VehicleEvent.class,
                  WeatherEvent.class,
                  WorldEvent.class,

                  // com.destroystokyo.paper.event.*
                  AnvilDamagedEvent.DamageState.class,
                  EntityAddToWorldEvent.class,
                  PlayerConnectionCloseEvent.class,
                  GS4QueryEvent.class,

                  // io.papermc.paper.event.*
                  BeaconActivatedEvent.class,
                  PlayerInventorySlotChangeEvent.class,
                  WorldBorderEvent.class
              )
              .collect(toUnmodifiableMap(Class::getPackageName, CancelDebugInjector::asPrivateLookup));
    } catch (final IllegalAccessException | IOException ex) {
      unavailabilityReason = ex;
      lookupByPackageMap = Map.of();
    }

    UNAVAILABILITY_REASON = unavailabilityReason;
    LOOKUP_BY_PACKAGE_MAP = lookupByPackageMap;
  }

  private static MethodHandles.Lookup asPrivateLookup(final Class<?> clazz) {
    try {
      return MethodHandles.privateLookupIn(clazz, LOOKUP);
    } catch (final IllegalAccessException ex) {
      throw new ExceptionInInitializerError(ex);
    }
  }

  static void inject(final ConfigurationSection config, final Logger logger) throws IOException {
    if (UNAVAILABILITY_REASON != null) {
      logger.error("Unable to inject cancel debug method", UNAVAILABILITY_REASON);
      return;
    }

    final Map<String, MethodHandles.Lookup> lookupByUnsafePackageMap = new HashMap<>(0);
    final List<String> ohLord = config.getStringList("cancellable-events-to-sacrifice-in-unsafe-package");
    if (!ohLord.isEmpty()) {
      logger.warn("It seems you have set the hidden `cancellable-events-to-sacrifice-in-unsafe-package` setting.");
      logger.warn(
          "The events listed in this setting cannot exist in the `events` setting, and care must be taken " +
          "as adding events to this setting may load events inside `events` before it is injected."
      );
      for (final String alternateEvent : ohLord) {
        try {
          final Class<?> clazz = LOOKUP.findClass(alternateEvent);
          lookupByUnsafePackageMap.put(clazz.getPackageName(), MethodHandles.privateLookupIn(clazz, LOOKUP));
        } catch (final Throwable ex) {
          logger.error(
              "An error occurred trying to use " + alternateEvent +
              " as alternate ways to inject cancel debug functionality in the same package.",
              ex
          );
        }
      }
    }

    final boolean printStackTrace = config.getBoolean("print-stack-traces");
    for (final String event : config.getStringList("events")) {
      final String pckg = event.substring(0, event.lastIndexOf('.'));
      MethodHandles.@Nullable Lookup lookup = LOOKUP_BY_PACKAGE_MAP.get(pckg);
      if (lookup == null) { lookup = lookupByUnsafePackageMap.get(pckg); }
      if (lookup == null) {
        logger.warn("Unable to inject cancel debug functionality into " + event + '.');
        logger.warn("It is not possible or safe to alter events in " + pckg + '.');
        logger.warn(
            "It is possible to bypass this constraint by adding a \"sacrifice\" event in the (hidden) config setting " +
            "`cancellable-events-to-sacrifice-in-unsafe-package`."
        );
        logger.warn("This setting is a list as to allow for multiple events from different \"unsafe\" packages.");
        continue;
      }

      final byte @Nullable [] modifiedEvent = injectCancelDebug(event, printStackTrace);
      if (modifiedEvent == null) {
        logger.warn("Unable to inject cancel debug functionality into " + event + ". Does the event exist in this version?");
        continue;
      }

      try {
        lookup.defineClass(modifiedEvent);
        logger.info("Injected cancellation debug into " + event);
      } catch (final Throwable ex) {
        logger.warn("An error occurred trying to load altered version of " + event, ex);
        if (!config.getBoolean("inject-during-bootstrap")) {
          logger.warn("This problem *might* be resolvable by setting to true the hidden setting `inject-during-bootstrap`");
        }
      }
    }
  }

  private static byte[] readCancelDebugBootstrapClass() throws IOException {
    try (final var in = CancelDebugInjector.class.getClassLoader().getResourceAsStream("org/bukkit/CancelDebugBootstrap.class")) {
      return Objects.requireNonNull(in, "plugin broke").readAllBytes();
    }
  }

  private static byte @Nullable [] injectCancelDebug(final String eventName, final boolean printStackTrace) throws IOException {
    return Injector.inject(eventName, printStackTrace);
  }

  private CancelDebugInjector() {
  }

  private interface Injector {

    // @formatter:off
    String SET_CANCELLED = "setCancelled";
    MethodTypeDesc SET_CANCELLED_DESC = MethodTypeDesc.of(CD_void, CD_boolean);

    ClassDesc STACK_WALKER_DESC = ClassDesc.of("java.lang.StackWalker");
    ClassDesc STACK_WALKER_OPTION_DESC = STACK_WALKER_DESC.nested("Option");
    String RETAIN_CLASS_REFERENCE = "RETAIN_CLASS_REFERENCE";
    String GET_INSTANCE = "getInstance";
    MethodTypeDesc GET_INSTANCE_DESC = MethodTypeDesc.of(STACK_WALKER_DESC, STACK_WALKER_OPTION_DESC);
    String GET_CALLER_CLASS = "getCallerClass";
    MethodTypeDesc GET_CALLER_CLASS_DESC = MethodTypeDesc.of(CD_Class);

    ClassDesc CANCEL_DEBUG_BOOTSTRAP_DESC = ClassDesc.of("org.bukkit.CancelDebugBootstrap");
    String BOOTSTRAP = "bootstrap";
    DirectMethodHandleDesc CANCEL_DEBUG_BOOTSTRAP_BOOTSTRAP_DESC = ofCallsiteBootstrap(CANCEL_DEBUG_BOOTSTRAP_DESC, BOOTSTRAP, CD_CallSite, CD_int);
    String DEBUG_CANCEL = "debugCancel";
    MethodTypeDesc DEBUG_CANCEL_DESC = MethodTypeDesc.of(CD_void, CD_Class, CD_boolean);
    DynamicCallSiteDesc BOOTSTRAP_DESC = DynamicCallSiteDesc.of(CANCEL_DEBUG_BOOTSTRAP_BOOTSTRAP_DESC, DEBUG_CANCEL, DEBUG_CANCEL_DESC);
    // @formatter:on

    static byte @Nullable [] inject(final String eventName, final boolean printStackTrace) throws IOException {
      final byte[] classBytes;
      try (final var in = Server.class.getClassLoader().getResourceAsStream(eventName.replace('.', '/') + ".class")) {
        if (in == null) { return null; }
        classBytes = in.readAllBytes();
      }

      return Classfile.parse(classBytes).transform(
          ClassTransform.transformingMethodBodies(
              model -> SET_CANCELLED.equals(model.methodName().stringValue()) && SET_CANCELLED_DESC.equals(model.methodTypeSymbol()),
              (builder, element) -> {
                if (element instanceof ReturnInstruction ret && ret.typeKind() == TypeKind.VoidType) {
                  builder.getstatic(STACK_WALKER_OPTION_DESC, RETAIN_CLASS_REFERENCE, STACK_WALKER_OPTION_DESC)
                      .invokestatic(STACK_WALKER_DESC, GET_INSTANCE, GET_INSTANCE_DESC)
                      .invokevirtual(STACK_WALKER_DESC, GET_CALLER_CLASS, GET_CALLER_CLASS_DESC)
                      .iload(1)
                      .invokedynamic(BOOTSTRAP_DESC.withArgs(printStackTrace ? 1 : 0));
                }

                builder.accept(element);
              }
          )
      );
    }
  }
}
