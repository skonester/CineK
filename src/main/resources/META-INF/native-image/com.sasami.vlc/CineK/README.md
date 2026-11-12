# CineK VLC/JNA reachability metadata

`reachability-metadata.json` is generated from real Windows playback, snapshot,
fullscreen, repeat/end-of-media and Unicode file-association smoke runs with the
GraalVM tracing agent. It covers vlcj 4.7.0, its JNA dependency and CineK's two
JNA proxy interfaces. Nucleus supplies its own framework metadata separately.

To regenerate, set `GRAALVM_HOME` to a GraalVM JDK and run from the repository root:

```powershell
$env:CINEK_SMOKE_BACKEND = 'glwin32'
.\gradlew.bat desktopSmoke startupSmoke -PtraceNative=true
python scripts/update-native-metadata.py
```

The raw trace lives under `build/native-agent/`. The generator excludes test
entry points, AWT screenshot/audio-fixture helpers, and framework resources.
Review the generated diff after exercising additional features or upgrading VLC/JNA.
Tracing is evidence of dynamic access, not proof of native-image correctness;
the compiled executable must still pass strict registration and playback checks.
