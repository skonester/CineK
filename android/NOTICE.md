# Android source attribution

CineK Android incorporates and adapts source from [Aperture by XDanfr](https://github.com/XDanfr/Aperture), from the supplied `Aperture-master` snapshot (`v0.5.0-pre-alpha` in its Gradle configuration). Aperture is licensed under GPLv3. The license text is included in [APERTURE-LICENSE](APERTURE-LICENSE), and CineK's GPLv3 license is at the repository root.

The imported Android implementation includes the library scanner, database, metadata integration, search, TV navigation and UI, settings, and Media3 player. The code retains its `me.xdan.aperture` internal package and many internal class names to make its origin and future upstream comparisons clear.

This adaptation adds CineK branding and the desktop palette, uses CineK's launcher and TV banner artwork, and removes Aperture's sponsor prompts and updater, which pointed at Aperture's releases. The APK keeps CineK's existing prototype application ID; installing over an earlier APK also requires the same signing certificate.
