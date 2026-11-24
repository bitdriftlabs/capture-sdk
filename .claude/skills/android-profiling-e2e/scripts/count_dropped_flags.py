#!/usr/bin/env python3
"""List the packets in a raw Perfetto trace whose `previous_packet_dropped` flag is set, with their
trusted_packet_sequence_id, position within that sequence and `first_packet_on_sequence`.

Use it to explain the backend's `packet_loss_events`: a flag on the first packet of a sequence is
Perfetto's convention for "nothing before this was seen", not a loss.

    count_dropped_flags.py <raw .perfetto-trace>
"""
import argparse
import json
from collections import Counter


def varint(b, i):
    r = s = 0
    while True:
        c = b[i]
        i += 1
        r |= (c & 0x7F) << s
        s += 7
        if c < 0x80:
            return r, i


def fields(b):
    i = 0
    while i < len(b):
        key, i = varint(b, i)
        fn, wt = key >> 3, key & 7
        if wt == 0:
            v, i = varint(b, i)
        elif wt == 2:
            n, i = varint(b, i)
            v, i = b[i:i + n], i + n
        elif wt == 1:
            v, i = b[i:i + 8], i + 8
        elif wt == 5:
            v, i = b[i:i + 4], i + 4
        else:
            raise ValueError(f"wire type {wt}")
        yield fn, wt, v


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("trace", help="raw .perfetto-trace (not the processed TAR)")
    data = open(ap.parse_args().trace, "rb").read()
    seen, flagged = Counter(), []
    for fn, _, pkt in fields(data):
        if fn != 1:  # Trace.packet
            continue
        f = {pfn: v for pfn, _, v in fields(pkt) if pfn in (10, 42, 87)}  # seq id, dropped, first-on-seq
        seq = f.get(10, 0)
        idx = seen[seq]
        seen[seq] += 1
        if f.get(42):
            flagged.append({"sequence": seq, "index_in_sequence": idx, "first_packet_on_sequence": bool(f.get(87))})
    real = [x for x in flagged if x["index_in_sequence"] > 0 and not x["first_packet_on_sequence"]]
    print(json.dumps({"sequences": len(seen), "flagged_packets": len(flagged),
                      "flagged_only_on_first_packet": not real, "flagged_mid_sequence": real,
                      "flagged": flagged}, indent=1))


if __name__ == "__main__":
    main()
