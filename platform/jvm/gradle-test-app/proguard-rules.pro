# Only required for running the Instrumented UI Tests
-keep public class kotlin.**

-keep,allowobfuscation class io.bitdrift.gradletestapp.init.PerfettoWorkload {
	private void perfettoJavaCpuWorkload(long);
}
