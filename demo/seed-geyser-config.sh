#!/bin/sh
set -eu

config="${1:?Usage: seed-geyser-config.sh CONFIG_PATH}"
config_dir="$(dirname "${config}")"
mkdir -p "${config_dir}"

if ! test -f "${config}"; then
    printf 'java:\n  auth-type: floodgate\n' >"${config}"
    exit 0
fi

temporary="${config}.seed.$$"
cleanup() {
    rm -f "${temporary}"
}
trap cleanup EXIT INT TERM

awk '
    function is_top_level_mapping(line) {
        return line ~ /^[^[:space:]#][^:]*[[:space:]]*:/
    }
    function is_java_mapping(line) {
        return line ~ /^java[[:space:]]*:/
    }
    BEGIN {
        in_java = 0
        java_seen = 0
        skip_duplicate_java = 0
    }
    {
        if (is_top_level_mapping($0)) {
            in_java = 0
            skip_duplicate_java = 0
            if (is_java_mapping($0)) {
                if (java_seen) {
                    skip_duplicate_java = 1
                    next
                }
                java_seen = 1
                in_java = 1
                print
                print "  auth-type: floodgate"
                next
            }
        }
        if (skip_duplicate_java) {
            next
        }
        if (in_java && $0 ~ /^[[:space:]]+auth-type[[:space:]]*:/) {
            next
        }
        print
    }
    END {
        if (!java_seen) {
            print ""
            print "java:"
            print "  auth-type: floodgate"
        }
    }
' "${config}" >"${temporary}"

mv "${temporary}" "${config}"
trap - EXIT INT TERM
