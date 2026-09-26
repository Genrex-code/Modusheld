#!/usr/bin/env python3
"""ModuShield observable E2E contract (E01-E12), using only Python stdlib."""

from __future__ import annotations

import argparse
import json
import os
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
import subprocess
import sys
import time
from typing import Iterable
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from uuid import uuid4


@dataclass
class HttpResult:
    status: int | None
    body: bytes
    headers: dict[str, str]
    error: str | None = None

    def json_body(self) -> dict[str, object]:
        try:
            parsed = json.loads(self.body.decode("utf-8"))
            return parsed if isinstance(parsed, dict) else {}
        except (UnicodeDecodeError, json.JSONDecodeError):
            return {}


@dataclass
class ScenarioResult:
    scenario_id: str
    name: str
    command: str
    expected: str
    actual: str
    outcome: str


SCENARIO_COMMANDS = {
    "E01": "GET /health",
    "E02": "POST /auth/login; GET /api/products + Authorization: Bearer <admin JWT>",
    "E03": "GET /api/products",
    "E04": "GET /api/products + Authorization: Bearer <invalid JWT>",
    "E05": "GET /api/admin/status + X-API-Key: <configured>",
    "E06": "POST /api/products + Authorization: Bearer <USER JWT>",
    "E07": "6 x authenticated GET /api/products in one rate window",
    "E08": "POST /api/orders with client-tests/payload-8192.json",
    "E09": "POST /api/orders with client-tests/payload-8193.json",
    "E10": "docker compose stop demo-api; GET /api/products; restart demo-api",
    "E11": "GET localhost:8081 and front-network -> demo-api:8081",
    "E12": "GET /api/products with unique requestId; inspect gateway logs",
}


def read_env_file(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.is_file():
        return values
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def request(
    base_url: str,
    method: str,
    path: str,
    *,
    api_key: str | None = None,
    bearer_token: str | None = None,
    body: bytes | None = None,
    request_id: str | None = None,
    timeout: float = 8.0,
) -> HttpResult:
    headers = {"Accept": "application/json"}
    if api_key is not None:
        headers["X-API-Key"] = api_key
    if bearer_token is not None:
        headers["Authorization"] = f"Bearer {bearer_token}"
    if request_id is not None:
        headers["X-Request-Id"] = request_id
    if body is not None:
        headers["Content-Type"] = "application/json"

    http_request = Request(
        f"{base_url.rstrip('/')}{path}",
        data=body,
        headers=headers,
        method=method,
    )
    try:
        with urlopen(http_request, timeout=timeout) as response:
            return HttpResult(
                response.status,
                response.read(),
                dict(response.headers.items()),
            )
    except HTTPError as error:
        return HttpResult(
            error.code,
            error.read(),
            dict(error.headers.items()),
        )
    except (URLError, TimeoutError, OSError) as error:
        return HttpResult(None, b"", {}, str(error))


def json_error(result: HttpResult) -> str | None:
    value = result.json_body().get("error")
    return value if isinstance(value, str) else None


def payload_fixture(repo_dir: Path, size: int) -> bytes:
    path = repo_dir / "client-tests" / f"payload-{size}.json"
    payload = path.read_bytes()
    if len(payload) != size:
        raise ValueError(f"{path} has {len(payload)} bytes; expected {size}")
    json.loads(payload.decode("utf-8"))
    return payload


class Runner:
    def __init__(self, args: argparse.Namespace) -> None:
        self.args = args
        self.results: list[ScenarioResult] = []
        self.admin_token: str | None = None

    def login(self, username: str, password: str) -> str | None:
        result = request(
            self.args.base_url,
            "POST",
            "/auth/login",
            body=json.dumps({"username": username, "password": password}).encode("utf-8"),
        )
        token = result.json_body().get("token")
        return token if result.status == 200 and isinstance(token, str) else None

    def record(
        self,
        scenario_id: str,
        name: str,
        expected: str,
        actual: str,
        passed: bool,
    ) -> None:
        outcome = "PASS" if passed else "FAIL"
        result = ScenarioResult(
            scenario_id,
            name,
            SCENARIO_COMMANDS[scenario_id],
            expected,
            actual,
            outcome,
        )
        self.results.append(result)
        print(f"[{outcome}] {scenario_id} {name} -> {actual}")

    def skip(self, scenario_id: str, name: str, reason: str) -> None:
        self.results.append(ScenarioResult(
            scenario_id,
            name,
            SCENARIO_COMMANDS[scenario_id],
            "not skipped",
            reason,
            "SKIP",
        ))
        print(f"[SKIP] {scenario_id} {name} -> {reason}")

    def check_http(
        self,
        scenario_id: str,
        name: str,
        result: HttpResult,
        expected_status: int,
        expected_error: str | None = None,
    ) -> None:
        error = json_error(result)
        passed = result.status == expected_status
        if expected_error is not None:
            passed = passed and error == expected_error
        actual = f"status={result.status}"
        if error:
            actual += f" error={error}"
        if result.error:
            actual += f" transport={result.error}"
        expected = f"status={expected_status}"
        if expected_error:
            expected += f" error={expected_error}"
        self.record(scenario_id, name, expected, actual, passed)

    def compose(self, *command: str, timeout: float = 90.0) -> subprocess.CompletedProcess[str]:
        invocation = [
            "docker",
            "compose",
            "-f",
            str(self.args.compose_file),
        ]
        if self.args.env_file.is_file():
            invocation.extend(["--env-file", str(self.args.env_file)])
        invocation.extend(command)
        environment = os.environ.copy()
        environment.setdefault("MODUSHIELD_API_KEY", self.args.api_key)
        return subprocess.run(
            invocation,
            cwd=self.args.repo_dir,
            env=environment,
            capture_output=True,
            text=True,
            timeout=timeout,
            check=False,
        )

    def wait_for_rate_window(self) -> None:
        if self.args.rate_window_seconds > 0:
            wait = self.args.rate_window_seconds + 1
            print(f"[INFO] Waiting {wait}s for an isolated rate-limit window...")
            time.sleep(wait)

    def wait_for_gateway(self, timeout_seconds: float = 30.0) -> bool:
        print("[INFO] Waiting for the gateway to become ready...")
        deadline = time.monotonic() + timeout_seconds
        while time.monotonic() < deadline:
            result = request(
                self.args.base_url,
                "GET",
                "/health",
                timeout=2.0,
            )
            if result.status == 200:
                return True
            time.sleep(1.0)
        return False

    def wait_for_upstream(self, timeout_seconds: float = 20.0) -> bool:
        deadline = time.monotonic() + timeout_seconds
        while time.monotonic() < deadline:
            result = request(
                self.args.base_url,
                "GET",
                "/api/products",
                bearer_token=self.admin_token,
                timeout=2.0,
            )
            if result.status == 200:
                return True
            time.sleep(1.0)
        return False

    def run_http_scenarios(self) -> None:
        base_url = self.args.base_url
        api_key = self.args.api_key

        if not self.wait_for_gateway():
            print("[WARN] Gateway did not become ready before the scenario timeout.")

        self.check_http("E01", "health", request(base_url, "GET", "/health"), 200)
        self.admin_token = self.login(self.args.admin_username, self.args.admin_password)
        self.check_http(
            "E02",
            "valid products request",
            request(base_url, "GET", "/api/products", bearer_token=self.admin_token),
            200,
        )
        self.check_http(
            "E03",
            "missing JWT",
            request(base_url, "GET", "/api/products"),
            401,
            "INVALID_TOKEN",
        )
        self.check_http(
            "E04",
            "invalid JWT",
            request(base_url, "GET", "/api/products", bearer_token="definitely-wrong"),
            401,
            "INVALID_TOKEN",
        )
        self.check_http(
            "E05",
            "blocked route",
            request(base_url, "GET", "/api/admin/status", api_key=api_key),
            403,
            "ROUTE_NOT_ALLOWED",
        )
        user_name = f"e2e-user-{uuid4().hex[:8]}"
        user_password = "e2e-password-123"
        request(
            base_url,
            "POST",
            "/auth/register",
            body=json.dumps({"username": user_name, "password": user_password}).encode("utf-8"),
        )
        user_token = self.login(user_name, user_password)
        self.check_http(
            "E06",
            "USER write denied",
            request(
                base_url,
                "POST",
                "/api/products",
                bearer_token=user_token,
                body=b'{"id":"P-DENIED","name":"Denied","stock":1}',
            ),
            403,
            "INSUFFICIENT_PERMISSIONS",
        )

        self.wait_for_rate_window()
        burst = [
            request(base_url, "GET", "/api/products", bearer_token=self.admin_token)
            for _ in range(6)
        ]
        statuses = [item.status for item in burst]
        error = json_error(burst[-1])
        self.record(
            "E07",
            "rate limit",
            "statuses=[200, 200, 200, 200, 200, 429] error=RATE_LIMIT_EXCEEDED",
            f"statuses={statuses} error={error}",
            statuses == [200, 200, 200, 200, 200, 429]
            and error == "RATE_LIMIT_EXCEEDED",
        )

        self.wait_for_rate_window()
        exact = payload_fixture(self.args.repo_dir, 8192)
        over = payload_fixture(self.args.repo_dir, 8193)
        self.check_http(
            "E08",
            "exact 8192-byte payload",
            request(base_url, "POST", "/api/orders", api_key=api_key, body=exact),
            201,
        )
        self.check_http(
            "E09",
            "8193-byte payload",
            request(base_url, "POST", "/api/orders", api_key=api_key, body=over),
            413,
            "PAYLOAD_TOO_LARGE",
        )

    def run_disruptive_scenarios(self) -> None:
        if self.args.http_only:
            self.skip("E10", "upstream unavailable", "--http-only was selected")
            self.skip("E11", "network isolation", "--http-only was selected")
            self.skip("E12", "audit event", "--http-only was selected")
            return

        try:
            stopped = self.compose("stop", "demo-api")
        except (FileNotFoundError, subprocess.SubprocessError) as error:
            reason = f"Docker Compose unavailable: {error}"
            self.record("E10", "upstream unavailable", "status=502", reason, False)
            self.record("E11", "network isolation", "backend unreachable", reason, False)
            self.record("E12", "audit event", "safe correlated log", reason, False)
            return

        if stopped.returncode != 0:
            actual = stopped.stderr.strip() or stopped.stdout.strip()
            self.record("E10", "upstream unavailable", "demo-api stops", actual, False)
        else:
            try:
                result = request(
                    self.args.base_url,
                    "GET",
                    "/api/products",
                    bearer_token=self.admin_token,
                )
                self.check_http(
                    "E10",
                    "upstream unavailable",
                    result,
                    502,
                    "UPSTREAM_UNAVAILABLE",
                )
            finally:
                restarted = self.compose("start", "demo-api")
                if restarted.returncode != 0:
                    print("[WARN] demo-api could not be restarted automatically.", file=sys.stderr)

        direct = request("http://localhost:8081", "GET", "/api/products", timeout=2.0)
        front_network = self.compose(
            "run",
            "--rm",
            "client-tests",
            "-sS",
            "--max-time",
            "3",
            "http://demo-api:8081/health",
        )
        isolated = direct.status is None and front_network.returncode != 0
        self.record(
            "E11",
            "network isolation",
            "host and front-network cannot reach demo-api:8081",
            f"host_status={direct.status} front_exit={front_network.returncode}",
            isolated,
        )

        request_id = f"e12-{uuid4()}"
        upstream_ready = self.wait_for_upstream()
        audit_request = (
            request(
                self.args.base_url,
                "GET",
                "/api/products",
                bearer_token=self.admin_token,
                request_id=request_id,
            )
            if upstream_ready
            else HttpResult(None, b"", {}, "demo-api did not become ready")
        )
        logs = self.compose("logs", "--no-color", "gateway")
        log_text = logs.stdout
        safe_log = (
            audit_request.status == 200
            and logs.returncode == 0
            and request_id in log_text
            and (self.admin_token is None or self.admin_token not in log_text)
        )
        self.record(
            "E12",
            "audit event",
            "requestId present and JWT absent from logs",
            (
                f"request_status={audit_request.status} request_id_found={request_id in log_text} "
                f"jwt_found={self.admin_token is not None and self.admin_token in log_text}"
            ),
            safe_log,
        )

    def write_evidence(self) -> Path:
        evidence_dir = self.args.repo_dir / "docs" / "evidence"
        evidence_dir.mkdir(parents=True, exist_ok=True)
        now = datetime.now(timezone.utc)
        destination = evidence_dir / f"e2e-{now.strftime('%Y%m%dT%H%M%SZ')}.json"
        payload = {
            "timestamp": now.isoformat(),
            "baseUrl": self.args.base_url,
            "commit": git_revision(self.args.repo_dir),
            "results": [asdict(item) for item in self.results],
        }
        destination.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
        return destination


def git_revision(repo_dir: Path) -> str:
    result = subprocess.run(
        ["git", "rev-parse", "--short", "HEAD"],
        cwd=repo_dir,
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        return "unknown"

    revision = result.stdout.strip()
    worktree = subprocess.run(
        ["git", "status", "--porcelain"],
        cwd=repo_dir,
        capture_output=True,
        text=True,
        check=False,
    )
    dirty = worktree.returncode == 0 and bool(worktree.stdout.strip())
    return f"{revision}-dirty" if dirty else revision


def parse_args(argv: Iterable[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run the ModuShield E01-E12 matrix")
    default_repo = Path(__file__).resolve().parents[1]
    parser.add_argument("--repo-dir", type=Path, default=default_repo)
    parser.add_argument("--base-url", default=os.getenv("BASE_URL", "http://localhost:8080"))
    parser.add_argument("--api-key", default=None)
    parser.add_argument("--compose-file", type=Path, default=None)
    parser.add_argument("--env-file", type=Path, default=None)
    parser.add_argument(
        "--rate-window-seconds",
        type=int,
        default=int(os.getenv("RATE_LIMIT_WINDOW_SECONDS", "10")),
    )
    parser.add_argument(
        "--http-only",
        action="store_true",
        help="Run E01-E09 and record E10-E12 as skipped instead of using Docker",
    )
    args = parser.parse_args(list(argv))
    args.repo_dir = args.repo_dir.resolve()
    args.compose_file = (args.compose_file or args.repo_dir / "infra" / "docker-compose.yml").resolve()
    args.env_file = (args.env_file or args.repo_dir / ".env").resolve()
    env_file = read_env_file(args.env_file)
    args.api_key = args.api_key or os.getenv("MODUSHIELD_API_KEY") or env_file.get(
        "MODUSHIELD_API_KEY",
        "demo-key-change-me",
    )
    args.admin_username = os.getenv("ADMIN_USERNAME") or env_file.get("ADMIN_USERNAME", "")
    args.admin_password = os.getenv("ADMIN_PASSWORD") or env_file.get("ADMIN_PASSWORD", "")
    return args


def main(argv: Iterable[str] = ()) -> int:
    args = parse_args(argv)
    runner = Runner(args)
    runner.run_http_scenarios()
    runner.run_disruptive_scenarios()
    evidence = runner.write_evidence()

    passed = sum(item.outcome == "PASS" for item in runner.results)
    failed = sum(item.outcome == "FAIL" for item in runner.results)
    skipped = sum(item.outcome == "SKIP" for item in runner.results)
    print(f"Summary: {passed} passed, {failed} failed, {skipped} skipped")
    print(f"Evidence: {evidence}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
