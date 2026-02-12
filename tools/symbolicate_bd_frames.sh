#!/bin/bash

set -e

# Usage function
usage() {
    echo "Usage: $0 -d <dump_file> -v <version> [-a <arch>]"
    echo ""
    echo "Options:"
    echo "  -d <dump_file>  Path to the crash dump file"
    echo "  -v <version>    SDK version (e.g., 0.19.1)"
    echo "  -a <arch>       Architecture (arm64-v8a, armeabi-v7a, x86_64, x86)"
    echo "                  If not provided, will auto-detect from dump file"
    echo ""
    echo "Example:"
    echo "  $0 -d dump.txt -v 0.19.1"
    echo "  $0 -d dump.txt -v 0.19.1 -a arm64-v8a"
    exit 1
}

# Parse arguments
DUMP_FILE=""
VERSION=""
ARCH=""

while getopts "d:v:a:h" opt; do
    case $opt in
        d) DUMP_FILE="$OPTARG" ;;
        v) VERSION="$OPTARG" ;;
        a) ARCH="$OPTARG" ;;
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
    if grep -q "ABI: 'arm64'" "$DUMP_FILE"; then
        ARCH="arm64-v8a"
    elif grep -q "ABI: 'arm'" "$DUMP_FILE"; then
        ARCH="armeabi-v7a"
    elif grep -q "ABI: 'x86_64'" "$DUMP_FILE"; then
        ARCH="x86_64"
    elif grep -q "ABI: 'x86'" "$DUMP_FILE"; then
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
BUILD_ID=$(grep "BuildId:" "$DUMP_FILE" | head -1 | awk '{print $NF}' | tr -d ')')
echo "Build ID from dump: $BUILD_ID"

# Verify BuildId matches (if possible)
SYMBOLS_BUILD_ID=$(file "$SYMBOLS_FILE" | grep -o 'BuildID\[xxHash\]=[a-f0-9]*' | cut -d= -f2 || true)
if [[ -n "$SYMBOLS_BUILD_ID" ]]; then
    echo "Build ID from symbols: $SYMBOLS_BUILD_ID"
    if [[ "$BUILD_ID" != "$SYMBOLS_BUILD_ID" ]]; then
        echo "Warning: Build IDs do not match!"
    fi
fi

# Extract backtrace lines
echo ""
echo "Extracting backtrace from dump..."

# Extract all backtrace lines
BACKTRACE_LINES=$(grep "backtrace:" -A 100 "$DUMP_FILE" | grep "^[ ]*#" | head -100)

if [[ -z "$BACKTRACE_LINES" ]]; then
    echo "Error: No backtrace found in dump file"
    exit 1
fi

# Create temp file for symbol mapping
SYMBOL_MAP_FILE="$TEMP_DIR/symbol_map.txt"
touch "$SYMBOL_MAP_FILE"

# Symbolicate addresses
SYMBOL_COUNT=0
while IFS= read -r line; do
    # Check if line contains a pc address and BuildId
    if echo "$line" | grep -q "pc [0-9a-f]"; then
        ADDR=$(echo "$line" | awk '{print $3}')
        LINE_BUILD_ID=$(echo "$line" | grep -oE 'BuildId: [a-f0-9]+' | awk '{print $2}')
        
        # Only symbolicate if BuildId matches our symbols (or no BuildId specified in line)
        if [[ -z "$LINE_BUILD_ID" ]] || [[ "$LINE_BUILD_ID" == "$BUILD_ID" ]]; then
            HEX_ADDR="0x${ADDR##+(0)}"
            
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
                
                # Store mapping
                echo "$ADDR|$SYMBOL" >> "$SYMBOL_MAP_FILE"
                SYMBOL_COUNT=$((SYMBOL_COUNT + 1))
            fi
        fi
    fi
done <<< "$BACKTRACE_LINES"

echo "Found $SYMBOL_COUNT addresses to symbolicate"
echo ""
echo "========================================"
echo "SYMBOLICATED BACKTRACE"
echo "========================================"

# Print the full backtrace with symbolicated lines
while IFS= read -r line; do
    echo "$line"
    
    # Check if this line has a symbolicated version
    if echo "$line" | grep -q "pc [0-9a-f]"; then
        ADDR=$(echo "$line" | awk '{print $3}')
        SYMBOL=$(grep "^$ADDR|" "$SYMBOL_MAP_FILE" 2>/dev/null | cut -d'|' -f2-)
        if [[ -n "$SYMBOL" ]]; then
            echo "       [SYMBOLICATED] $SYMBOL"
        fi
    fi
done <<< "$BACKTRACE_LINES"

echo ""
echo "========================================"
echo "END OF BACKTRACE"
echo "========================================"
