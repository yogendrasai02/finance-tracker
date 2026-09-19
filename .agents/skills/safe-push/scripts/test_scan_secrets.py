#!/usr/bin/env python3
"""Self-test for the allowlist rules in scan_secrets.py.

Run from the repository root:
    python3 .agents/skills/safe-push/scripts/test_scan_secrets.py

The scanner also scans this file when it is tracked.
So every real-looking value below is joined from pieces at runtime, and no single line of source matches a rule.
"""
import os
import sys
import unittest

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from scan_secrets import scan_text  # noqa: E402

PW = "pass" + "word"


def rules(line):
    return [finding[2] for finding in scan_text(line, "fixture")]


class FalsePositivesPass(unittest.TestCase):

    def test_known_test_placeholders(self):
        self.assertEqual(rules('private static final String PASSWORD = "not-a-real-password-1234";'), [])
        self.assertEqual(rules('public static final String MIGRATOR_PASSWORD = "ft_migrator_test";'), [])
        self.assertEqual(rules('public static final String APP_PASSWORD = "ft_app_test";'), [])

    def test_shell_env_var_references(self):
        self.assertEqual(rules('  --set migrator_password="$FT_MIGRATOR_PASSWORD" \\'), [])
        self.assertEqual(rules('  --set app_password="${FT_APP_PASSWORD}" \\'), [])

    def test_psql_variable_references(self):
        self.assertEqual(rules("ALTER ROLE ft_migrator PASSWORD :'migrator_password';"), [])
        self.assertEqual(rules("ALTER ROLE ft_app      PASSWORD :'app_password';"), [])

    def test_aadhaar_shape_inside_a_uuid(self):
        self.assertEqual(rules("VALUES ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 0)"), [])


class RealValuesStillFail(unittest.TestCase):

    def test_real_looking_password(self):
        self.assertIn("Generic Secret/Password Assignment", rules(PW + ' = "Tr0ub4dor-horse-' + '93"'))

    def test_placeholder_is_matched_exactly_not_by_prefix(self):
        self.assertIn("Generic Secret/Password Assignment", rules(PW + ' = "not-a-real-password-' + '9999"'))

    def test_lowercase_dollar_value_is_not_an_env_var(self):
        self.assertIn("Generic Secret/Password Assignment", rules(PW + ' = "$ecretVal' + 'ue9"'))

    def test_quoted_value_after_colon_is_not_a_psql_variable(self):
        self.assertIn("Generic Secret/Password Assignment", rules(PW + ": 'Tr0ub4dor-horse-" + "93'"))

    def test_formatted_aadhaar_outside_a_uuid(self):
        self.assertIn("Indian Aadhaar Number (Formatted)", rules("aadhaar on file: 2345 " + "6789 0123"))

    def test_formatted_aadhaar_next_to_a_uuid(self):
        line = "id 22222222-2222-2222-2222-222222222222 and 2345-" + "6789-0123"
        self.assertEqual(rules(line), ["Indian Aadhaar Number (Formatted)"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
