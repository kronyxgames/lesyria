#!/usr/bin/env python3
"""Client RCON minimaliste (protocole Source RCON).

Permet a l'equipe d'administration d'envoyer une commande au serveur Lesyria
sans installer d'outil supplementaire : seuls la bibliotheque standard Python
et l'acces reseau au port RCON sont necessaires.

Exemples :
    python3 scripts/rcon.py --password lesyria-dev-rcon --command "list"
    python3 scripts/rcon.py --host 10.0.0.5 --command "eco give Alice 500"
"""

from __future__ import annotations

import argparse
import socket
import struct
import sys

SERVERDATA_AUTH = 3
SERVERDATA_AUTH_RESPONSE = 2
SERVERDATA_EXECCOMMAND = 2
SERVERDATA_RESPONSE_VALUE = 0


def _send(sock: socket.socket, request_id: int, packet_type: int, payload: str) -> None:
    data = payload.encode("utf-8") + b"\x00\x00"
    packet = struct.pack("<ii", len(data) + 8, request_id) + struct.pack("<ii", packet_type, 0) + data
    sock.sendall(packet)


def _recv(sock: socket.socket) -> tuple[int, int, str]:
    header = _recv_exactly(sock, 12)
    size, request_id, packet_type, _ = struct.unpack("<iiii", header)
    body = _recv_exactly(sock, size - 8)
    return request_id, packet_type, body.rstrip(b"\x00").decode("utf-8", errors="replace")


def _recv_exactly(sock: socket.socket, length: int) -> bytes:
    buffer = b""
    while len(buffer) < length:
        chunk = sock.recv(length - len(buffer))
        if not chunk:
            raise ConnectionError("connexion RCON interrompue")
        buffer += chunk
    return buffer


def run(host: str, port: int, password: str, command: str, timeout: float) -> str:
    with socket.create_connection((host, port), timeout=timeout) as sock:
        sock.settimeout(timeout)
        _send(sock, 1, SERVERDATA_AUTH, password)
        request_id, packet_type, _ = _recv(sock)
        if request_id == -1:
            raise PermissionError("mot de passe RCON refuse")
        if packet_type != SERVERDATA_AUTH_RESPONSE:
            raise ConnectionError("reponse d'authentification inattendue")

        _send(sock, 2, SERVERDATA_EXECCOMMAND, command)
        _, _, response = _recv(sock)
        return response


def main() -> int:
    parser = argparse.ArgumentParser(description="Client RCON Lesyria")
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, default=25575)
    parser.add_argument("--password", required=True)
    parser.add_argument("--command", required=True)
    parser.add_argument("--timeout", type=float, default=10.0)
    args = parser.parse_args()

    try:
        response = run(args.host, args.port, args.password, args.command, args.timeout)
    except (OSError, ConnectionError, PermissionError) as error:
        print(f"RCON: {error}", file=sys.stderr)
        return 1
    print(response)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
