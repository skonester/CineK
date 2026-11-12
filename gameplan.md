# CineK desktop: Nucleus + GraalVM game plan

Updated: 2026-11-11. Target: the Windows x64 desktop app, compiled with GraalVM Native Image. Android remains a separate build.

## Outcome

Move CineK's desktop lifecycle, windowing, native-library layout, and packaging onto Nucleus. Keep VLC playback, the existing library/preferences, file associations, playlists, live channels, keyboard controls, and fullscreen behavior. A successful migration ends with a tested standalone native distribution and installer that need neither Java nor VLC installed on the destination machine.

## Findings and decisions

- CineK currently uses Kotlin 2.0.0, Compose 1.6.11, vlcj 4.7.0/JNA, Gradle 9.5.1, an AWT Compose window, Swing dialogs and VLC's AWT video component. Merely adding native-image flags will not migrate this UI.
- The supplied Nucleus source is `C:\Users\admin\Desktop\Nucleus-nucleus-3.0`. Use it to check APIs; do not make the app depend on that machine-specific path or modify the framework checkout.
- Maven Central has Nucleus `3.0.0-dev-202611061347`; pin the plugin and runtime modules to the same published version. The supplied source uses Kotlin 2.4.20 and Compose 1.12.1. Resolve the published dependencies and compile to validate that combination before committing to it.
- Use Nucleus's Tao window backend and `nucleusApplication(args)`. Native Image is the delivery target; JVM runs are the development/debug loop. GraalVM Community 25.0.2 (JDK 25) and the Windows MSVC C++ toolchain are native-build prerequisites.
- Preserve `%APPDATA%\CineK` and `cinek.configDir` isolation. Do not migrate or overwrite user data during verification.
- Keep the complete VLC DLL/plugin tree as external app resources. Resolve it through the packaged resources directory, independent of the working directory. The native executable still needs these VLC libraries and Nucleus/Skiko native sidecars.
- Replace the AWT video component with an AWT-free VLC surface hosted by Nucleus. Verify lifecycle ordering (attach, play, stop, detach, release), resizing, library preview, fullscreen, subtitles, and native callback reachability before switching delivery.
- Replace AWT window resizing, keyboard hooks, drag/drop, pointer/cursor handling and Swing dialogs with Compose/Nucleus/native equivalents. Do not ship a second toolkit event loop as the permanent architecture.
- Add only metadata justified by actual JNA/VLC reflection, JNI, callbacks and resources. Nucleus supplies framework metadata; its presence does not prove vlcj is native-image compatible. Exercise the native binary to verify coverage.

## Implementation sequence

### 1. Establish the build and compatibility baseline

- [x] Run the existing desktop checks and record the result.
- [x] Align desktop Kotlin/Compose versions with Nucleus, apply the Nucleus plugin, and add the application/Tao/GraalVM runtime modules.
- [x] Move packaging settings to `nucleus.application`, preserving the app name, version, icons, resources and output-directory override.
- [x] Configure GraalVM JDK 25 and a portable CPU baseline. Keep native build tasks separate from ordinary unit checks.
- [x] Resolve dependencies, compile and run behavior checks. Record any external toolchain or dependency blocker with the exact failing command.

### 2. Migrate the desktop runtime

- [x] Bootstrap with `nucleusApplication(args)` and a Tao window, preserving all launch file arguments. Keep independent Explorer launches: Nucleus's default single-instance relay handles one URI, so enabling it would lose multi-file launches. A full multi-file relay can be added separately.
- [x] Preserve the CineK title bar, minimum size, close/checkpoint behavior and native fullscreen controls.
- [x] Replace the Swing VLC surface with a native view; defer file-association playback until the surface is attached.
- [x] Replace AWT keyboard, drag/drop and idle-controls handling.
- [x] Replace Swing file/folder/subtitle/playlist/snapshot dialogs and Explorer actions.
- [x] Replace the JVM splash dependency with startup UI suitable for native execution.

### 3. Prove Native Image playback

- [ ] Build the application with the pinned GraalVM toolchain and fail on missing reachability registrations during verification.
- [ ] Exercise audio/video files, streams, seeking, track selection, subtitles, snapshots, device selection and end-of-media callbacks; collect targeted metadata as needed.
- [ ] Test packaged launches from an unrelated working directory and paths containing spaces/non-ASCII characters.
- [ ] Verify shutdown and repeated surface attach/detach without leaks, callbacks into released objects or a lingering process.

### 4. Migrate distribution and automation

- [x] Adapt `scripts/build-setup.ps1` and `installer/CineK.iss` to Nucleus's native distribution layout; stop requiring `runtime/bin/server/jvm.dll` for native packages.
- [x] Add native build prerequisites and smoke validation to Windows CI. Keep Android independent and preserve the existing association/uninstall behavior.
- [ ] Verify installation, Open with, multiple file arguments, upgrades and uninstall on a machine without Java/VLC installed.
- [x] Update README build/run commands and document the selected toolchain, output paths, metadata workflow and known limitations.

## Validation gates

1. **Build:** Gradle configuration, Kotlin compilation and existing desktop behavior tests pass with the pinned dependencies.
2. **Runtime:** the actual Tao window plays the bundled media fixtures; shortcuts, fullscreen, dialogs and persistence work.
3. **Native:** a produced native executable passes playback checks with strict registration reporting. A successful JVM run alone does not meet this gate.
4. **Distribution:** the packaged executable runs outside the repository and the installer passes association/upgrade/uninstall checks.

Do not report Native Image delivery complete until gates 3 and 4 have passed. Update the execution record as work proceeds.

## Execution record

- Initial inspection complete. The working tree was clean. Framework source and public release/dependency metadata were checked before implementation.
- **Build gate passed:** the original `gradlew.bat check` passed before migration. The pinned Nucleus plugin configured, the migrated sources compiled, and desktop tests/behavior checks passed after migration.
- **Runtime gate passed on the JVM:** `gradlew.bat desktopSmoke` passed on JDK 21 with Automatic/Direct3D playback. `desktopSmoke startupSmoke -PtraceNative=true` also passed on a GraalVM JDK with OpenGL. Checks include actual video pixels, all four preview corners, snapshots, seek/rate/resume, stable native handles across fullscreen/maximize transitions, pointer/keyboard controls, repeat/end-of-media, Unicode filenames, multiple file arguments and clean shutdown. Evidence is under `build/desktop-smoke/` and `build/startup-smoke/`.
- **Metadata captured:** `scripts/update-native-metadata.py` extracted 94 observed VLC/JNA reflection/JNI/proxy types and two runtime resources into `src/main/resources/META-INF/native-image/com.sasami.vlc/CineK/reachability-metadata.json`. Test-only entry points and framework resources were excluded. Broader native codec/stream/subtitle/device coverage remains pending.
- **JVM distribution verified:** `scripts/build-setup.ps1 -Runtime Jvm` produced `out/setup/CineK-1.0.3-Setup.exe`. `scripts/test-desktop-distribution.ps1 -Runtime Jvm -ApplicationPath build/compose/binaries/main/app/CineK/CineK.exe` passed: real rendered video, both Unicode/space filenames, saved progress, unrelated working directory and normal exit. This does not establish native-image or clean-machine installer correctness.
- **Native build attempted and externally blocked:** `gradlew.bat createGraalvmNativeDistributable` reached `nativeImageCompile` and failed because `vswhere.exe` could not find Visual Studio 2022 17.6+ with the C++ tools. The initial attempt downloaded Nucleus's default GraalVM innovation toolchain; the final configuration explicitly pins Community 25.0.2 for subsequent builds. No native executable or native installer is claimed as verified.
- **Native delivery path added:** `scripts/build-setup.ps1 -Runtime GraalVM` checks the C++ prerequisite and packages the native layout, with a separate `-Native-Setup.exe` output. The manual `desktop-native.yml` workflow builds on a Windows runner, runs the native executable with missing-registration reporting set to Exit, then checks installation/file associations/uninstall. Existing automatic releases retain the JVM installer until native checks pass.
- **Remaining gates:** install Visual Studio Build Tools with Desktop development with C++ and a Windows SDK, then run the native commands below or the manual workflow. Verify native streams, subtitles, audio devices and FileKit dialogs as well as the automated fixture path. Test upgrades and a machine without Java/VLC installed. Nucleus's local JVM packaging warned that MSVC redistributables could not be sourced without the C++ tools; release packaging must run on the provisioned Windows builder.

### Zig toolchain assessment

The installed Zig 0.16.0 was tested as an alternative to MSVC. All probes live in the ignored `build/zig-probe/` directory; CineK's build configuration was not changed for this experiment.

| Probe | Result |
| --- | --- |
| `zig cc -target x86_64-windows-gnu` on a small program using stdio and Win32 | Compiled and ran successfully. |
| `zig cc -target x86_64-windows-msvc -c` on the same program | Failed with `LibCStdLibHeaderNotFound`. |
| GraalVM Native Image on a minimal Java main using `--native-compiler-path` with a Zig CL-mode wrapper | Exited with code 20 while locating Visual Studio through `vswhere.exe`, before compiling the image. |

Zig's working MinGW toolchain does not replace the MSVC environment expected by the current Windows GraalVM distribution. GraalVM's Windows compiler adapter uses CL options and Microsoft compiler detection; the standard custom-compiler option does not bypass its Windows environment setup. No working Zig-based Native Image build was produced. Windows SDK 10.0.26100.0 files are already present; the missing prerequisite remains the MSVC C++ build tools. The probe used the already downloaded GraalVM innovation JDK; the pinned 25.0.2 Windows implementation was also checked in Oracle's source and has the same environment requirement.

### Next commands on a provisioned Windows builder

```powershell
.\gradlew.bat check desktopSmoke startupSmoke
.\scripts\build-setup.ps1 -Runtime GraalVM
.\scripts\test-desktop-distribution.ps1 -Runtime GraalVM -ApplicationPath build/compose/binaries/main/graalvm-app/CineK/CineK.exe
# Installer checks require a clean test account with no existing CineK registrations:
.\scripts\test-setup.ps1 -Runtime GraalVM
```

## References

- [Nucleus source and releases](https://github.com/NucleusFramework/Nucleus/releases)
- [Published Nucleus application versions](https://repo.maven.apache.org/maven2/dev/nucleusframework/nucleus.nucleus-application/maven-metadata.xml)
- [Nucleus Gradle plugin](https://plugins.gradle.org/plugin/dev.nucleusframework)
- [GraalVM reachability metadata](https://www.graalvm.org/latest/reference-manual/native-image/metadata/)
- [GraalVM Windows prerequisites](https://www.graalvm.org/latest/getting-started/windows/)
- [GraalVM 25.0.2 Windows environment setup](https://github.com/oracle/graal/blob/jdk-25.0.2/substratevm/src/com.oracle.svm.driver/src/com/oracle/svm/driver/WindowsBuildEnvironmentUtil.java)
