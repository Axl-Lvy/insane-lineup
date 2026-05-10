"""
Script to check that all translation files have the same keys in the same order
as the base strings.xml file
"""

import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import List, Set, Optional


class Color:
    """ANSI escape codes for colored terminal output."""
    RED = '\033[0;31m'
    GREEN = '\033[0;32m'
    YELLOW = '\033[1;33m'
    NC = '\033[0m'


@dataclass(frozen=True)
class TranslationKey:
    """Represents a translation key with its type."""
    name: str
    key_type: str  # 'string' or 'plurals'

    def __str__(self) -> str:
        return f"{self.name} ({self.key_type})"


@dataclass(frozen=True)
class KeyDifference:
    """Represents differences between base and translation keys."""
    missing: Set[TranslationKey]
    extra: Set[TranslationKey]


@dataclass(frozen=True)
class ValidationResult:
    """Result of validating a single translation file."""
    lang_code: str
    is_valid: bool
    error_message: Optional[str] = None


def extract_keys_from_file(file_path: Path) -> List[TranslationKey]:
    """
    Extract string and plurals keys from an XML file in order.

    Args:
        file_path: Path to the XML file

    Returns:
        List of TranslationKey objects in the order they appear in the file
    """
    try:
        content = file_path.read_text(encoding='utf-8')
        keys = []
        # Match both <string name="..."> and <plurals name="...">
        for match in re.finditer(r'<(string|plurals)\s+name="([^"]+)"',
                                 content):
            key_type = match.group(1)
            key_name = match.group(2)
            keys.append(TranslationKey(name=key_name, key_type=key_type))
        return keys
    except Exception as e:
        print(f"{Color.RED}Error reading {file_path}: {e}{Color.NC}")
        return []


def get_key_differences(
    base_keys: List[TranslationKey],
    translation_keys: List[TranslationKey]
) -> KeyDifference:
    """
    Calculate missing and extra keys between base and translation.

    Args:
        base_keys: Keys from the base strings.xml
        translation_keys: Keys from a translation strings.xml

    Returns:
        KeyDifference object with missing and extra keys
    """
    base_set = set(base_keys)
    translation_set = set(translation_keys)
    return KeyDifference(
        missing=base_set - translation_set,
        extra=translation_set - base_set
    )


def print_key_count_mismatch(
    lang_code: str,
    expected_count: int,
    actual_count: int,
    differences: KeyDifference
):
    """
    Print details about key count mismatch.

    Args:
        lang_code: Language code being checked
        expected_count: Expected number of keys
        actual_count: Actual number of keys found
        differences: Missing and extra keys
    """
    print(f"{Color.RED}✗ {lang_code}: Key count mismatch "
          f"(expected {expected_count}, got {actual_count}){Color.NC}")

    if differences.missing:
        missing_str = ', '.join(str(key) for key in sorted(
            differences.missing,
            key=lambda k: k.name))
        print(f"{Color.YELLOW}  Missing keys: {missing_str}{Color.NC}")

    if differences.extra:
        extra_str = ', '.join(
            str(key) for key in sorted(differences.extra, key=lambda k: k.name))
        print(f"{Color.YELLOW}  Extra keys: {extra_str}{Color.NC}")


def print_key_order_mismatch(
    lang_code: str,
    base_keys: List[TranslationKey],
    translation_keys: List[TranslationKey]
):
    """
    Print details about keys being in wrong order.

    Args:
        lang_code: Language code being checked
        base_keys: Keys from base file
        translation_keys: Keys from translation file
    """
    print(
        f"{Color.RED}✗ {lang_code}: Keys do not match or are in different order{Color.NC}")
    print(f"{Color.YELLOW}Differences:{Color.NC}")

    for i, (base_key, trans_key) in enumerate(zip(base_keys, translation_keys)):
        if base_key != trans_key:
            print(
                f"  Position {i + 1}: expected '{base_key}', got '{trans_key}'")


def validate_translation_file(
    strings_file: Path,
    lang_code: str,
    base_keys: List[TranslationKey]
) -> ValidationResult:
    """
    Validate a single translation file against the base keys.

    Args:
        strings_file: Path to the translation strings.xml file
        lang_code: Language code being validated
        base_keys: List of keys from the base strings.xml

    Returns:
        ValidationResult indicating success or failure
    """
    if not strings_file.exists():
        print(f"{Color.RED}✗ {lang_code}: strings.xml not found{Color.NC}")
        return ValidationResult(lang_code=lang_code, is_valid=False,
                                error_message="File not found")

    print(f"Checking {lang_code}/strings.xml...")

    translation_keys = extract_keys_from_file(strings_file)

    # Check key count mismatch
    if len(base_keys) != len(translation_keys):
        differences = get_key_differences(base_keys, translation_keys)
        print_key_count_mismatch(lang_code, len(base_keys),
                                 len(translation_keys), differences)
        return ValidationResult(lang_code=lang_code, is_valid=False,
                                error_message="Key count mismatch")

    # Check key order
    if base_keys != translation_keys:
        print_key_order_mismatch(lang_code, base_keys, translation_keys)
        return ValidationResult(lang_code=lang_code, is_valid=False,
                                error_message="Keys out of order")

    print(
        f"{Color.GREEN}✓ {lang_code}: All keys match and are in correct order{Color.NC}")
    return ValidationResult(lang_code=lang_code, is_valid=True)


def get_translation_directories(compose_resources_dir: Path) -> List[Path]:
    """
    Find all translation directories (values-*).

    Args:
        compose_resources_dir: Path to the composeResources directory

    Returns:
        Sorted list of translation directory paths
    """
    return sorted([
        d for d in compose_resources_dir.iterdir()
        if d.is_dir() and d.name.startswith('values-')
    ])


def check_translations() -> int:
    """
    Check all translation files against the base strings.xml file.

    Returns:
        0 if all translations are valid, 1 otherwise
    """
    compose_resources_dir = Path("composeApp/src/commonMain/composeResources")
    base_strings_file = compose_resources_dir / "values" / "strings.xml"

    if not base_strings_file.exists():
        print(
            f"{Color.RED}Error: Base strings.xml not found at {base_strings_file}{Color.NC}")
        return 1

    print(f"{Color.GREEN}Checking translation files...{Color.NC}")
    print(f"Base file: {base_strings_file}")
    print()

    base_keys = extract_keys_from_file(base_strings_file)
    string_count = sum(1 for key in base_keys if key.key_type == 'string')
    plurals_count = sum(1 for key in base_keys if key.key_type == 'plurals')
    print(f"{Color.YELLOW}Base file contains {len(base_keys)} keys "
          f"({string_count} strings, {plurals_count} plurals){Color.NC}")
    print()

    translation_dirs = get_translation_directories(compose_resources_dir)

    if not translation_dirs:
        print(f"{Color.YELLOW}No translation directories found.{Color.NC}")
        return 0

    validation_results: List[ValidationResult] = []

    for dir_path in translation_dirs:
        lang_code = dir_path.name
        strings_file = dir_path / "strings.xml"

        result = validate_translation_file(strings_file, lang_code, base_keys)
        validation_results.append(result)
        print()

    has_errors = any(not result.is_valid for result in validation_results)

    if not has_errors:
        print(f"{Color.GREEN}✓ All translation files are valid!{Color.NC}")
        return 0
    else:
        print(f"{Color.RED}✗ Translation validation failed!{Color.NC}")
        return 1


if __name__ == "__main__":
    sys.exit(check_translations())
