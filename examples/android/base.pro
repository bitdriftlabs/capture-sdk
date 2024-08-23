# Quick and dirty set of rules to quiet the output, not production ready

-keepattributes InnerClasses,EnclosingMethod
-keepattributes *Annotation*
-keepattributes Signature

-dontwarn androidx.**
-dontwarn kotlinx.coroutines.**

-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
