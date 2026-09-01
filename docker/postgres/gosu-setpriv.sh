#!/bin/sh
# Installed over /usr/local/bin/gosu in the image built by the Dockerfile beside this file.
#
# The official postgres entrypoints (docker-entrypoint.sh, docker-ensure-initdb.sh) each call
# `gosu postgres "$BASH_SOURCE" "$@"` exactly once, as root, to drop to the postgres user before
# re-running themselves. Upstream's gosu is a static Go binary, so every Go stdlib CVE lands in this
# image with nothing apt can do about it. setpriv (util-linux) makes the same uid/gid/supplementary
# -group switch and then execs, and it is an ordinary Debian package that OS updates keep patched.
#
# Only the `gosu <user> <command> [args...]` form those entrypoints use is supported. A `user:group`
# spec or an option fails loudly, because guessing at gosu's semantics would mean running a command
# with privileges nobody asked for.
set -eu

if [ "$#" -lt 2 ] || [ "${1#-}" != "$1" ] || [ "${1#*:}" != "$1" ]; then
	echo "gosu (setpriv stand-in): usage: gosu <user> <command> [args...]" >&2
	exit 1
fi

user="$1"
shift

# --init-groups reproduces gosu's supplementary groups (postgres is also in ssl-cert); the primary
# group is read from the passwd entry rather than assumed to share the user's name.
exec setpriv --reuid "$user" --regid "$(id -g "$user")" --init-groups -- "$@"
