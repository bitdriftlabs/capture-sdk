#!/bin/bash

set -e

# Usage function
usage() {
    echo "Usage: $0 -d <dump_file> -v <version> [-a <arch>] [-o <output_file>]"
    echo ""
    echo "Options:"
    echo "  -d <dump_file>    Path to the crash dump file (Tombstone or Bugsnag formatted)"
    echo "  -v <version>      SDK version (e.g., 0.19.1)"
    echo "  -a <arch>         [Optional] Architecture (arm64-v8a, armeabi-v7a, x86_64, x86)"
    echo "                    If not provided, will auto-detect from dump file"
    echo "  -o <output_file>  [Optional] Path to save the symbolicated output"
    echo ""
    echo "Example:"
    echo "  $0 -d dump.txt -v 0.19.1"
    echo "  $0 -d dump.txt -v 0.22.3 -a arm64-v8a -o output.txt"
    exit 1
}

# Parse arguments
DUMP_FILE=""
VERSION=""
ARCH=""
OUT_FILE=""

while getopts "d:v:a:o:h" opt; do
    case $opt in
        d) DUMP_FILE="$OPTARG" ;;
        v) VERSION="$OPTARG" ;;
        a) ARCH="$OPTARG" ;;
        o) OUT_FILE="$OPTARG" ;;
        h) usage ;;
        *) usage ;;
    esac
done

# Validate required arguments
if [[ -z "$DUMP_FILE" || -z "$VERSION" ]]; then
    echo "Error: Missing required arguments"
    usage
fi

if [[ ! -f "$DUMP_FILE" ]]; then
    echo "Error: Dump file '$DUMP_FILE' not found"
    exit 1
fi

# Check for required tools
echo "Checking prerequisites..."

# Check for llvm-addr2line (required)
if ! command -v llvm-addr2line &> /dev/null; then
    echo ""
    echo "✗ Error: llvm-addr2line not found (required for symbolication)"
    echo ""
    echo "To install:"
    echo "  brew install llvm"
    echo ""
    exit 1
fi
echo "✓ llvm-addr2line found"

# Check for rustfilt (optional but recommended for Rust symbol demangling)
if command -v rustfilt &> /dev/null; then
    echo "✓ rustfilt found"
else
    echo "⚠ rustfilt not found - Rust symbols will not be fully demangled"
    echo "  To install: cargo install rustfilt"
fi

# Check for curl and gunzip
if ! command -v curl &> /dev/null; then
    echo "✗ Error: curl not found (required for downloading symbols)"
    exit 1
fi

if ! command -v gunzip &> /dev/null; then
    echo "✗ Error: gunzip not found (required for extracting symbols)"
    exit 1
fi

echo ""

# Auto-detect architecture if not provided
if [[ -z "$ARCH" ]]; then
    echo "Auto-detecting architecture from dump file..."
    if grep -qE "ABI: 'arm64'|arm64-v8a|aarch64|arm64" "$DUMP_FILE"; then
        ARCH="arm64-v8a"
    elif grep -qE "ABI: 'arm'|armeabi-v7a|armv7" "$DUMP_FILE"; then
        ARCH="armeabi-v7a"
    elif grep -qE "ABI: 'x86_64'|x86_64|amd64" "$DUMP_FILE"; then
        ARCH="x86_64"
    elif grep -qE "ABI: 'x86'|x86|i686" "$DUMP_FILE"; then
        ARCH="x86"
    else
        echo "Error: Could not auto-detect architecture from dump file"
        exit 1
    fi
    echo "Detected architecture: $ARCH"
fi

# Create temp directory
TEMP_DIR=$(mktemp -d)
trap 'rm -rf $TEMP_DIR' EXIT

echo "Working directory: $TEMP_DIR"

# Download symbols
SYMBOLS_URL="https://dl.bitdrift.io/sdk/android-maven/io/bitdrift/capture/${VERSION}/capture-${VERSION}-symbols.tar"
SYMBOLS_TAR="$TEMP_DIR/capture-${VERSION}-symbols.tar"

echo "Downloading symbols from: $SYMBOLS_URL"
if ! curl -f -L -o "$SYMBOLS_TAR" "$SYMBOLS_URL"; then
    echo "Error: Failed to download symbols from $SYMBOLS_URL"
    exit 1
fi

# Extract symbols
echo "Extracting symbols..."
tar -xf "$SYMBOLS_TAR" -C "$TEMP_DIR"

# Find the symbols file
SYMBOLS_GZ="$TEMP_DIR/${ARCH}.debug.gz"
if [[ ! -f "$SYMBOLS_GZ" ]]; then
    echo "Error: Symbols file not found: $SYMBOLS_GZ"
    echo "Available files:"
    ls -la "$TEMP_DIR/"
    exit 1
fi

# Extract debug symbols
SYMBOLS_FILE="$TEMP_DIR/${ARCH}.debug"
echo "Extracting debug symbols..."
gunzip -c "$SYMBOLS_GZ" > "$SYMBOLS_FILE"

# Extract BuildId from dump
BUILD_ID=$(grep -E "(BuildId:|Build ID:)" "$DUMP_FILE" | head -1 | awk '{print $NF}' | tr -d ')')
echo "Build ID from dump: $BUILD_ID"

# Verify BuildId matches (if possible)
SYMBOLS_BUILD_ID=$(file "$SYMBOLS_FILE" | grep -o 'BuildID\[xxHash\]=[a-f0-9]*' | cut -d= -f2 || true)
if [[ -n "$SYMBOLS_BUILD_ID" ]]; then
    echo "Build ID from symbols: $SYMBOLS_BUILD_ID"
    if [[ "$BUILD_ID" != "$SYMBOLS_BUILD_ID" ]]; then
        echo "Warning: Initial Build ID from dump ($BUILD_ID) does not match symbols ($SYMBOLS_BUILD_ID)!"
        echo "Will attempt to symbolicate frames matching $SYMBOLS_BUILD_ID."
    fi
fi

# Extract backtrace lines
echo ""
echo "Extracting backtrace from dump..."

ADDR_FORMAT=""

# Extract all lines with a program counter address
BACKTRACE_LINES=$(grep "pc " "$DUMP_FILE" || true)

if [[ -z "$BACKTRACE_LINES" ]]; then
    # Try looking for STACKTRACE format if "pc " wasn't found natively, though bugsnag lines usually have "pc "
    BACKTRACE_LINES=$(grep "STACKTRACE" -A 100 "$DUMP_FILE" | grep -i "pc " || true)
fi

if [[ -z "$BACKTRACE_LINES" ]]; then
    # Try looking for raw debuggerd format like #00 0x0000...
    BACKTRACE_LINES=$(grep -E "#[0-9]{2} 0x[0-9a-f]+" "$DUMP_FILE" || true)
fi

if [[ -z "$BACKTRACE_LINES" ]]; then
    # Try "at 0x<addr> within <path>" format (bitdrift/Bugsnag/Firebase Crashlytics)
    BACKTRACE_LINES=$(grep -E " at 0x[0-9a-f]+" "$DUMP_FILE" || true)
    ADDR_FORMAT="at_0x"
fi

if [[ -z "$BACKTRACE_LINES" ]]; then
    echo "Error: No backtrace found in dump file"
    exit 1
fi

# For "at 0x" format, addresses are absolute virtual addresses.
# We need to estimate the library base address and compute relative offsets.
LIBRARY_BASE=0
if [[ "$ADDR_FORMAT" == "at_0x" ]]; then
    echo "Detected 'at 0x<addr> within <path>' format (absolute addresses)"
    echo "Estimating library base address..."

    # Look for APK paths that likely contain native code:
    # split_config.arm64_v8a.apk (split APKs) or base.apk (non-split)
    NATIVE_APK_PATH=$(echo "$BACKTRACE_LINES" | grep -oE 'within [^ ]+\.apk' | sed 's/^within //' | grep -E 'split_config\.arm64|split_config\.armeabi|split_config\.x86' | sort -u | head -1)
    if [[ -z "$NATIVE_APK_PATH" ]]; then
        # Fall back to base.apk from the app package
        NATIVE_APK_PATH=$(echo "$BACKTRACE_LINES" | grep -oE 'within [^ ]+\.apk' | sed 's/^within //' | sort -u | head -1)
    fi

    if [[ -n "$NATIVE_APK_PATH" ]]; then
        echo "Native library APK: $NATIVE_APK_PATH"
        # Find minimum address from frames in that APK to estimate the library base
        MIN_ADDR_HEX=$(echo "$BACKTRACE_LINES" | grep -F "$NATIVE_APK_PATH" | grep -oE 'at 0x[0-9a-f]+' | awk '{print $2}' | sed 's/^0x//' | sort | head -1)
        if [[ -n "$MIN_ADDR_HEX" ]]; then
            # Page-align down to 4KB boundary to get estimated base
            MIN_ADDR=$((16#$MIN_ADDR_HEX))
            LIBRARY_BASE=$(( MIN_ADDR & ~0xFFF ))
            printf "Estimated library base: 0x%x (from min addr 0x%s)\n" "$LIBRARY_BASE" "$MIN_ADDR_HEX"

            # Validate by testing one address
            TEST_ADDR_HEX=$(echo "$BACKTRACE_LINES" | grep -F "$NATIVE_APK_PATH" | grep -oE 'at 0x[0-9a-f]+' | awk '{print $2}' | sed 's/^0x//' | tail -1)
            TEST_OFFSET=$(( 16#$TEST_ADDR_HEX - LIBRARY_BASE ))
            TEST_RESULT=$(printf "0x%x" $TEST_OFFSET | xargs -I {} llvm-addr2line -f -e "$SYMBOLS_FILE" {} 2>/dev/null | head -1)
            if [[ "$TEST_RESULT" == "??" ]] || [[ -z "$TEST_RESULT" ]]; then
                echo "Warning: Validation failed for estimated base. Symbolication may be incomplete."
            else
                echo "Base address validated successfully."
            fi
        fi
    else
        echo "Warning: Could not identify native APK path. Addresses will be used as-is."
    fi
    echo ""
fi

# Create temp file for symbol mapping
SYMBOL_MAP_FILE="$TEMP_DIR/symbol_map.txt"
touch "$SYMBOL_MAP_FILE"

# Helper: extract address from a line and return it (without 0x prefix)
extract_addr() {
    local line="$1"
    if echo "$line" | grep -q "pc [0-9a-f]"; then
        echo "$line" | grep -o 'pc [0-9a-f]*' | head -1 | awk '{print $2}'
    elif echo "$line" | grep -qE "#[0-9]{2} 0x[0-9a-f]"; then
        echo "$line" | grep -oE '#[0-9]{2} 0x[0-9a-f]+' | head -1 | awk '{print $2}' | sed 's/^0x//'
    elif echo "$line" | grep -qE ' at 0x[0-9a-f]+'; then
        echo "$line" | grep -oE ' at 0x[0-9a-f]+' | head -1 | awk '{print $2}' | sed 's/^0x//'
    fi
}

# For "at 0x" format with a known native APK, only process lines from that APK
# (other lines are system/JVM code that won't resolve with our symbols)
if [[ "$ADDR_FORMAT" == "at_0x" ]] && [[ -n "$NATIVE_APK_PATH" ]]; then
    LINES_TO_SYMBOLICATE=$(echo "$BACKTRACE_LINES" | grep -F "$NATIVE_APK_PATH" || true)
else
    LINES_TO_SYMBOLICATE="$BACKTRACE_LINES"
fi

# Collect unique addresses to symbolicate (avoids redundant addr2line calls)
UNIQUE_ADDRS_FILE="$TEMP_DIR/unique_addrs.txt"
while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    ADDR=$(extract_addr "$line")
    if [[ -n "$ADDR" ]]; then
        # Extract Build ID if present on the line
        LINE_BUILD_ID=$(echo "$line" | grep -oE 'BuildId: [a-f0-9]+' | awk '{print $2}')
        TARGET_BUILD_ID=${SYMBOLS_BUILD_ID:-$BUILD_ID}

        if [[ -z "$LINE_BUILD_ID" ]] || [[ -z "$TARGET_BUILD_ID" ]] || [[ "$LINE_BUILD_ID" == "$TARGET_BUILD_ID" ]] || [[ "$line" == *".apk"* ]]; then
            # Compute the address to pass to addr2line
            if [[ "$ADDR_FORMAT" == "at_0x" ]] && [[ $LIBRARY_BASE -gt 0 ]] && [[ -n "$NATIVE_APK_PATH" ]] && echo "$line" | grep -qF "$NATIVE_APK_PATH"; then
                ABS_ADDR=$((16#$ADDR))
                REL_OFFSET=$((ABS_ADDR - LIBRARY_BASE))
                HEX_ADDR=$(printf "0x%x" $REL_OFFSET)
            else
                STRIPPED_ADDR="${ADDR#"${ADDR%%[!0]*}"}"
                HEX_ADDR="0x${STRIPPED_ADDR:-0}"
            fi
            echo "$ADDR $HEX_ADDR"
        fi
    fi
done <<< "$LINES_TO_SYMBOLICATE" | sort -u > "$UNIQUE_ADDRS_FILE"

# Symbolicate unique addresses
SYMBOL_COUNT=0
while IFS=' ' read -r ADDR HEX_ADDR; do
    [[ -z "$ADDR" ]] && continue

    # Skip if already symbolicated (dedup)
    if grep -q "^$ADDR|" "$SYMBOL_MAP_FILE" 2>/dev/null; then
        continue
    fi

    # Symbolicate the address with llvm-addr2line
    RESULT=$(llvm-addr2line -f -e "$SYMBOLS_FILE" "$HEX_ADDR" 2>/dev/null | head -1)

    # Only store if we got a valid result (not "??" or empty)
    if [[ -n "$RESULT" ]] && [[ "$RESULT" != "??" ]]; then
        # Demangle Rust symbols if rustfilt is available
        if command -v rustfilt &> /dev/null; then
            SYMBOL=$(echo "$RESULT" | rustfilt)
        else
            SYMBOL="$RESULT"
        fi

        # Store mapping (use original ADDR as key)
        echo "$ADDR|$SYMBOL" >> "$SYMBOL_MAP_FILE"
        SYMBOL_COUNT=$((SYMBOL_COUNT + 1))
    fi
done < "$UNIQUE_ADDRS_FILE"

echo "Found $SYMBOL_COUNT addresses to symbolicate"
echo ""

print_backtrace() {
    # Build a sed script from the symbol map for fast batch replacement
    local SED_SCRIPT="$TEMP_DIR/sed_script.sed"
    : > "$SED_SCRIPT"

    while IFS='|' read -r addr symbol; do
        [[ -z "$addr" ]] && continue
        # Escape sed special characters in symbol
        escaped_symbol=$(printf '%s\n' "$symbol" | sed 's/[&/\]/\\&/g')
        {
            # For "pc <addr>" format: replace everything after pc+addr with symbolicated name
            printf 's/\\(pc %s\\).*/\\1 [symbolicated] %s/\n' "${addr}" "${escaped_symbol}"
            # For "#XX 0x<addr>" format
            printf 's/\\(#[0-9][0-9] 0x%s\\).*/\\1 [symbolicated] %s/\n' "${addr}" "${escaped_symbol}"
            # For "at 0x<addr>" format
            printf 's/\\( at 0x%s\\).*/\\1 [symbolicated] %s/\n' "${addr}" "${escaped_symbol}"
        } >> "$SED_SCRIPT"
    done < "$SYMBOL_MAP_FILE"

    if [[ -s "$SED_SCRIPT" ]]; then
        sed -f "$SED_SCRIPT" "$DUMP_FILE"
    else
        cat "$DUMP_FILE"
    fi
}

if [[ -n "$OUT_FILE" ]]; then
    print_backtrace > "$OUT_FILE"
    echo "Symbolicated output saved to: $OUT_FILE"
else
    print_backtrace
fi
