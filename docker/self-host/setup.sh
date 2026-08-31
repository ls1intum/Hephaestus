#!/bin/sh

set -eu
umask 077

directory=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
environment_file="$directory/.env"
example_file="$directory/.env.example"

command -v openssl >/dev/null 2>&1 || {
	printf '%s\n' "openssl is required to generate installation secrets." >&2
	exit 1
}

if [ -L "$environment_file" ]; then
	printf '%s\n' "Refusing to write through symlink $environment_file." >&2
	exit 1
fi

working_file=$(mktemp "$directory/.env.setup.XXXXXX")
generated_keys=
trap 'rm -f "$working_file"' EXIT HUP INT TERM

if [ -f "$environment_file" ]; then
	cp "$environment_file" "$working_file"
else
	cp "$example_file" "$working_file"
fi
chmod 600 "$working_file"

for key in POSTGRES_PASSWORD HEPHAESTUS_SECURITY_ENCRYPTION_KEY HEPHAESTUS_SECURITY_CREDENTIAL_ENCRYPTION_KEY HEPHAESTUS_AUTH_STATE_COOKIE_KEY WEBHOOK_SECRET; do
	if [ "$(grep -c "^${key}=" "$working_file")" -gt 1 ]; then
		printf 'Refusing duplicate %s assignments in %s.\n' "$key" "$environment_file" >&2
		exit 1
	fi
done

set_if_empty() {
	key=$1
	format=$2
	if grep -q "^${key}=." "$working_file"; then
		return
	fi
	case "$format" in
		hex16) value=$(openssl rand -hex 16) || return 1 ;;
		hex32) value=$(openssl rand -hex 32) || return 1 ;;
		base64) value=$(openssl rand -base64 32 | tr -d '\n') || return 1 ;;
	esac
	if ! grep -q "^${key}=" "$working_file"; then
		printf '%s=%s\n' "$key" "$value" >> "$working_file"
		generated_keys="$generated_keys $key"
	elif grep -q "^${key}=$" "$working_file"; then
		temporary_file=$(mktemp "$directory/.env.value.XXXXXX")
		while IFS= read -r line; do
			if [ "$line" = "$key=" ]; then
				printf '%s=%s\n' "$key" "$value"
			else
				printf '%s\n' "$line"
			fi
		done < "$working_file" > "$temporary_file"
		chmod 600 "$temporary_file"
		mv "$temporary_file" "$working_file"
		generated_keys="$generated_keys $key"
	fi
}

set_if_empty POSTGRES_PASSWORD hex16
set_if_empty HEPHAESTUS_SECURITY_ENCRYPTION_KEY hex16
if ! grep -q '^HEPHAESTUS_SECURITY_CREDENTIAL_ENCRYPTION_KEY=.' "$working_file"; then
	credential_key=$(sed -n 's/^HEPHAESTUS_SECURITY_ENCRYPTION_KEY=//p' "$working_file")
	if grep -q '^HEPHAESTUS_SECURITY_CREDENTIAL_ENCRYPTION_KEY=' "$working_file"; then
		while IFS= read -r line; do
			if [ "$line" = 'HEPHAESTUS_SECURITY_CREDENTIAL_ENCRYPTION_KEY=' ]; then
				printf 'HEPHAESTUS_SECURITY_CREDENTIAL_ENCRYPTION_KEY=%s\n' "$credential_key"
			else
				printf '%s\n' "$line"
			fi
		done < "$working_file" > "$working_file.next"
		mv "$working_file.next" "$working_file"
	else
		printf 'HEPHAESTUS_SECURITY_CREDENTIAL_ENCRYPTION_KEY=%s\n' "$credential_key" >> "$working_file"
	fi
	generated_keys="$generated_keys HEPHAESTUS_SECURITY_CREDENTIAL_ENCRYPTION_KEY"
fi
set_if_empty HEPHAESTUS_AUTH_STATE_COOKIE_KEY base64
set_if_empty WEBHOOK_SECRET hex32

mv "$working_file" "$environment_file"

for key in $generated_keys; do
	printf 'Generated %s.\n' "$key"
done

printf '\nConfiguration written to %s. Generated values were not printed.\n' "$environment_file"
printf '%s\n' 'Set APP_HOSTNAME, ACME_EMAIL, one OAuth provider, and HEPHAESTUS_AUTH_BOOTSTRAP_ADMINS before starting Hephaestus.'
