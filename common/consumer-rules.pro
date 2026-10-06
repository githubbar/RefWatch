# Applied to every app module that depends on :common.

# Firestore maps documents onto these models by reflection (toObject<Game>()): it needs
# their original getter/setter names, no-arg constructors, enum constant names, the
# @Exclude annotations, and generic signatures such as List<LocationSample>.
# Only the top-level package is kept; theme/ and everything else is obfuscated.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keep class com.databelay.refwatch.common.* { *; }
