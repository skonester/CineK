"""Keep traced VLC/JNA access; Nucleus supplies its own framework metadata."""
import json
from pathlib import Path

repo = Path(__file__).resolve().parent.parent
source = repo / "build/native-agent/reachability-metadata.json"
destination = repo / "src/main/resources/META-INF/native-image/com.sasami.vlc/CineK/reachability-metadata.json"
metadata = json.loads(source.read_text(encoding="utf-8"))


def runtime_type(entry):
    kind = entry["type"]
    if isinstance(kind, dict):
        return any(name.startswith(("com.sasami.vlc.player.", "com.sun.jna.", "uk.co.caprica.vlcj."))
                   for name in kind.get("proxy", []))
    return kind.startswith(("com.sun.jna.", "uk.co.caprica.vlcj."))


result = {
    "reflection": [entry for entry in metadata.get("reflection", []) if runtime_type(entry)],
    "resources": [entry for entry in metadata.get("resources", [])
                  if entry.get("glob", "").startswith(("com/sun/jna/", "META-INF/services/uk.co.caprica.vlcj."))],
}
destination.parent.mkdir(parents=True, exist_ok=True)
destination.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
print(f"Wrote {len(result['reflection'])} traced reflection/JNI/proxy types and {len(result['resources'])} resources to {destination.relative_to(repo)}")
