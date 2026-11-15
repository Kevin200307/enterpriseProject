import json
from collections.abc import Mapping


def encode_sse(event: str, data: Mapping[str, object]) -> bytes:
    serialized = json.dumps(data, separators=(",", ":"), ensure_ascii=False)
    return f"event: {event}\ndata: {serialized}\n\n".encode()
