# Only required for running the Instrumented UI Tests
-keep public class kotlin.**

-keep,allowobfuscation class io.bitdrift.gradletestapp.init.PerfettoWorkload$Companion {
	private void perfettoJavaCpuWorkload(long);
}

-keep,allowobfuscation class io.bitdrift.gradletestapp.init.PerfettoHeapNode {
	<fields>;
}
