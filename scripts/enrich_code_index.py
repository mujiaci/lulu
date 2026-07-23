#!/usr/bin/env python3
"""Enrich the generated repository index with exact local import relationships."""

from __future__ import annotations

import json
import re
from collections import defaultdict
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
INDEX_PATH = ROOT / "docs" / "architecture" / "code-index.json"
MARKDOWN_PATH = ROOT / "docs" / "architecture" / "CODE_INDEX.md"
IMPORT_PATTERN = re.compile(r"^\s*import\s+(me\.rerere\.[\w.*]+)", re.MULTILINE)
START_MARKER = "<!-- reverse-index:start -->"
END_MARKER = "<!-- reverse-index:end -->"


def load_index() -> dict[str, Any]:
    return json.loads(INDEX_PATH.read_text(encoding="utf-8"))


def exact_symbol_index(files: list[dict[str, Any]]) -> dict[str, list[dict[str, Any]]]:
    result: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for item in files:
        package_name = item.get("package")
        if not package_name:
            continue
        for symbol in item.get("symbols", []):
            full_name = f"{package_name}.{symbol['name']}"
            result[full_name].append(
                {
                    "path": item["path"],
                    "kind": symbol["kind"],
                    "line": symbol["line"],
                }
            )
    return dict(sorted(result.items()))


def local_imports(path: Path) -> list[str]:
    try:
        text = path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        return []
    return sorted(set(IMPORT_PATTERN.findall(text)))


def compact(item: dict[str, Any]) -> dict[str, Any]:
    return {
        "path": item["path"],
        "lines": item.get("lines", 0),
        "symbols": len(item.get("symbols", [])),
        "localImportCount": item.get("localImportCount", 0),
        "importedByCount": len(item.get("importedBy", [])),
        "todoMarkers": item.get("todoMarkers", 0),
    }


def enrich(index: dict[str, Any]) -> dict[str, Any]:
    files: list[dict[str, Any]] = index["files"]
    symbols = exact_symbol_index(files)
    imported_by: dict[str, set[str]] = defaultdict(set)
    resolved_by_source: dict[str, set[str]] = defaultdict(set)

    for item in files:
        source_path = item["path"]
        imports = local_imports(ROOT / source_path)
        item["localImports"] = imports
        item["localImportCount"] = len(imports)
        for imported in imports:
            if imported.endswith(".*"):
                continue
            candidates = symbols.get(imported, [])
            if len(candidates) != 1:
                continue
            target_path = candidates[0]["path"]
            if target_path == source_path:
                continue
            resolved_by_source[source_path].add(target_path)
            imported_by[target_path].add(source_path)

    for item in files:
        path = item["path"]
        item["resolvedImports"] = sorted(resolved_by_source.get(path, set()))
        item["importedBy"] = sorted(imported_by.get(path, set()))

    production_files = [item for item in files if item.get("isProductionCode")]
    highest_fan_in = sorted(
        production_files,
        key=lambda item: (len(item.get("importedBy", [])), int(item.get("lines", 0)), item["path"]),
        reverse=True,
    )[:30]

    index["schemaVersion"] = 3
    index["symbolIndex"] = symbols
    index["summary"]["resolvedImportEdges"] = sum(len(paths) for paths in resolved_by_source.values())
    index.setdefault("hotspots", {})["highestApproximateFanIn"] = [compact(item) for item in highest_fan_in]
    return index


def reverse_section(index: dict[str, Any]) -> str:
    rows = index.get("hotspots", {}).get("highestApproximateFanIn", [])[:15]
    lines = [
        START_MARKER,
        "## 反向依赖热点",
        "",
        "> 这里统计能由明确本地 import 唯一解析出的静态依赖；反射、同包引用、通配导入和运行时注册不会被计入。",
        "",
        f"- 已解析本地导入边：{index['summary'].get('resolvedImportEdges', 0)}",
        f"- 可检索完整符号：{len(index.get('symbolIndex', {}))}",
        "",
        "| 文件 | 被本地文件导入 | 行数 |",
        "|---|---:|---:|",
    ]
    for item in rows:
        lines.append(f"| `{item['path']}` | {item['importedByCount']} | {item['lines']} |")
    lines.extend([END_MARKER, ""])
    return "\n".join(lines)


def update_markdown(index: dict[str, Any]) -> None:
    text = MARKDOWN_PATH.read_text(encoding="utf-8")
    if START_MARKER in text and END_MARKER in text:
        prefix, remainder = text.split(START_MARKER, 1)
        _, suffix = remainder.split(END_MARKER, 1)
        text = prefix.rstrip() + "\n\n" + suffix.lstrip()
    section = reverse_section(index)
    anchor = "## 索引边界"
    if anchor in text:
        before, after = text.split(anchor, 1)
        text = before.rstrip() + "\n\n" + section + "\n" + anchor + after
    else:
        text = text.rstrip() + "\n\n" + section
    MARKDOWN_PATH.write_text(text, encoding="utf-8")


def main() -> None:
    index = enrich(load_index())
    INDEX_PATH.write_text(json.dumps(index, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    update_markdown(index)
    print(
        f"Enriched {len(index['files'])} files with "
        f"{index['summary']['resolvedImportEdges']} resolved local import edges"
    )


if __name__ == "__main__":
    main()
