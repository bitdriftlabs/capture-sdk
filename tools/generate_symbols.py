import re

from itertools import chain
from pathlib import Path
from typing import Generator

ROOT_PATH = (Path(__file__) / "../../").resolve()
SYMBOLS_PATH = (ROOT_PATH / "platform/jvm/jni_symbols.lds").resolve()
JNI_FILE = ROOT_PATH / "platform/jvm/src/jni.rs"


def parse(path: str) -> Generator[str, None, None]:
    with open(path, encoding="utf-8") as jni:
        externs = re.findall(r"extern.*?(\w+)[\(<]", jni.read(), re.S)
        for match in externs:
            if not match.startswith("Java_"):
                continue

            yield match


def main():
    symbols = chain(parse(JNI_FILE), ["JNI_OnLoad"])
    with open(SYMBOLS_PATH, "w", encoding="utf-8") as symbols_fd:
        symbols_fd.write("\n".join(symbols) + "\n")


if __name__ == "__main__":
    main()
