#!/usr/bin/env python3

import argparse
import json
from pathlib import Path


def remove_branch_report(manifest_path: Path, safe_branch: str) -> None:
    with manifest_path.open("r", encoding="utf-8") as file:
        data = json.load(file)

    reports = data.get("reports", [])
    if not isinstance(reports, list):
        reports = []

    filtered = []
    for entry in reports:
        if isinstance(entry, dict) and isinstance(entry.get("name"), str) and isinstance(entry.get("url"), str):
            if entry["name"] != safe_branch:
                filtered.append({"name": entry["name"], "url": entry["url"]})

    filtered.sort(key=lambda entry: entry["name"].lower())

    with manifest_path.open("w", encoding="utf-8") as file:
        json.dump({"reports": filtered}, file, indent=2)
        file.write("\n")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Remove one branch entry from mutation/reports.json."
    )
    parser.add_argument(
        "--manifest",
        default="mutation/reports.json",
        help="Path to the reports manifest file.",
    )
    parser.add_argument(
        "--remove-branch",
        required=True,
        help="Sanitized branch name to remove from the manifest.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    remove_branch_report(Path(args.manifest), args.remove_branch)


if __name__ == "__main__":
    main()

