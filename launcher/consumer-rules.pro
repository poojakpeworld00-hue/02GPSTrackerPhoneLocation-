# Room entities and DAOs are reflected over at runtime.
-keep class io.homedeck.grid.entities.** { *; }
-keep interface io.homedeck.grid.contracts.**Dao { *; }

# Widget providers and the device-admin receiver are named in the manifest and instantiated by
# the system; R8 cannot see those references.
-keep class io.homedeck.grid.clockwidgets.** { *; }
-keep class io.homedeck.grid.hooks.** { *; }

# The custom views in the launcher layouts are inflated by name.
-keep class io.homedeck.grid.canvas.** { *; }
-keep class io.homedeck.grid.panes.** { *; }

# The host implements these; keep the shapes it compiles against.
-keep interface io.homedeck.grid.bridge.** { *; }
