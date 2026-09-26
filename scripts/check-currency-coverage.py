"""Live coverage smoke check: python3 scripts/check-currency-coverage.py."""
import json
from pathlib import Path
import re
import subprocess

root = Path(__file__).resolve().parents[1]
source = (root / "app/src/main/java/dev/curex/app/Frankfurter.kt").read_text()


def fetch(method):
    path = re.search(r'override fun ' + method + r'\(\).*?request\("([^"]+)"', source)[1]
    result = subprocess.run(
        ["curl", "--fail", "--silent", "--show-error", "--max-time", "30",
         "https://api.frankfurter.dev/v2/" + path],
        check=True, capture_output=True, text=True,
    )
    return json.loads(result.stdout)


catalog = {row["iso_code"] for row in fetch("currencies")}
rates = {row["quote"] for row in fetch("rates")} | {"USD"}
available = catalog & rates
required = {"AMD", "AED", "GEL", "KZT", "UAH", "VND", "USD", "EUR"}
missing = required - available
assert not missing, f"Missing currencies in app picker feed: {sorted(missing)}"
print(f"PASS: {len(available)} currencies available, including AMD and other non-ECB currencies")
