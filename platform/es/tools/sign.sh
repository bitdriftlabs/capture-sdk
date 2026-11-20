#!/bin/bash

set -euo pipefail

#
# Install our provisioning certificates on the runner machine and sign the given
# binary.
#
# Set $BITDRIFT_CERT_P12 and $P12_PASSWORD before invoking the script
#

KEYCHAIN="bitdrift-temp.keychain"
KEYCHAIN_PASSWORD=$(openssl rand -base64 15)
CODESIGN=/usr/bin/codesign

# Mask the password from github output
echo "::add-mask::$KEYCHAIN_PASSWORD"

# Remove the keychain if it already exists
security delete-keychain $KEYCHAIN 2> /dev/null || true

security create-keychain -p "$KEYCHAIN_PASSWORD" "$KEYCHAIN"
security set-keychain-settings "$KEYCHAIN" # Remove relock timeout
security unlock-keychain -p "$KEYCHAIN_PASSWORD" "$KEYCHAIN" # Unlock keychain

# Add certificate to keychain
DIST_P12=$(mktemp)
echo "$BITDRIFT_CERT_P12" | base64 -d > $DIST_P12
security import $DIST_P12 -f pkcs12 -k "$KEYCHAIN" -P "$P12_PASSWORD" -T "$CODESIGN"
security set-key-partition-list -S apple-tool:,apple: -k "$KEYCHAIN_PASSWORD" "$KEYCHAIN"
security list-keychains -d user -s "$KEYCHAIN" login.keychain

echo "Signing $*"
codesign --force --timestamp --sign 31344F8EA6EAAF62BFAAA2E83746C03E5B56A7FF $*

rm $DIST_P12
security delete-keychain $KEYCHAIN
