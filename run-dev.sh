#!/usr/bin/env bash
# Launch RuneLite with this plugin loaded from source (the supported dev workflow).
#
# Do NOT use RuneLite.app for local plugin dev — its JAR is the *launcher*, which
# always sets launcher.version and disables sideloading.

set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

find_java_home() {
	if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
		echo "${JAVA_HOME}"
		return 0
	fi

	local candidate
	for candidate in \
		"/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" \
		"/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" \
		"/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home" \
		"/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" \
		"/usr/local/opt/openjdk/libexec/openjdk.jdk/Contents/Home"
	do
		if [[ -x "${candidate}/bin/java" ]]; then
			echo "${candidate}"
			return 0
		fi
	done

	if /usr/libexec/java_home -v 17+ >/dev/null 2>&1; then
		/usr/libexec/java_home -v 17+
		return 0
	fi

	return 1
}

if ! JAVA_HOME="$(find_java_home)"; then
	echo "Java 17+ is required but was not found." >&2
	echo "Install with Homebrew: brew install openjdk@17" >&2
	echo "Then add to ~/.zshrc: export JAVA_HOME=\"\$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home\"" >&2
	exit 1
fi

export JAVA_HOME
export PATH="${JAVA_HOME}/bin:${PATH}"

SIDELOAD_DIR="${HOME}/.runelite/sideloaded-plugins"
SIDELOAD_JAR=$(ls "${SIDELOAD_DIR}"/flipx-plugin-*-all.jar 2>/dev/null | head -1 || true)
SIDELOAD_BAK=""
if [[ -n "${SIDELOAD_JAR}" ]]; then
	SIDELOAD_BAK="${SIDELOAD_JAR}.dev-bak"
fi

# gradlew run loads the plugin from source; sideloaded JAR duplicates it (two sidebar icons).
if [[ -n "${SIDELOAD_JAR}" && -f "${SIDELOAD_JAR}" ]]; then
	mv -f "${SIDELOAD_JAR}" "${SIDELOAD_BAK}"
	echo "Moved sideloaded JAR aside to avoid duplicate plugin icons."
fi

restore_sideload() {
	if [[ -n "${SIDELOAD_BAK}" && -f "${SIDELOAD_BAK}" ]]; then
		mv -f "${SIDELOAD_BAK}" "${SIDELOAD_JAR}"
	fi
}
trap restore_sideload EXIT

echo "Using Java: $("${JAVA_HOME}/bin/java" -version 2>&1 | head -1)"
echo "Quit RuneLite.app first if it is running."
echo "After startup: Configuration → search \"FlipX\" → enable"
echo "Then open the FlipX sidebar panel to pair."
echo ""

exec ./gradlew run
