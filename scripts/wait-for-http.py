#!/usr/bin/env python3
"""Wait for a local HTTP readiness response without changing proxy configuration."""
import json
import sys
import time
import urllib.error
import urllib.request

target = sys.argv[1]
deadline = time.monotonic() + 45
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
while time.monotonic() < deadline:
    try:
        with opener.open(target, timeout=2) as response:
            if response.status == 200:
                if target.endswith('/actuator/health') and json.load(response).get('status') != 'UP':
                    raise ValueError('API health is not UP')
                print('HTTP service ready')
                sys.exit(0)
    except (urllib.error.URLError, TimeoutError, ValueError):
        time.sleep(0.25)
raise SystemExit('Service did not become ready: ' + target)
