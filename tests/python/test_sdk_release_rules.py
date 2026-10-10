import ast
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
PYTHON_ROOTS = (
    ROOT / 'sdk/python/src',
    ROOT / 'TMessagesProj/src/main/python',
)
RULES = ROOT / 'TMessagesProj/proguard-rules.pro'


def java_imports():
    classes = set()

    for root in PYTHON_ROOTS:
        for path in root.rglob('*.py'):
            for node in ast.walk(ast.parse(path.read_text())):
                if isinstance(node, ast.ImportFrom) and (node.module or '').startswith('org.telegram.'):
                    classes.update(f'{node.module}.{name.name}' for name in node.names)
                elif isinstance(node, ast.Call) and isinstance(node.func, ast.Name) and node.func.id == 'jclass':
                    if node.args and isinstance(node.args[0], ast.Constant):
                        name = node.args[0].value
                        if isinstance(name, str) and name.startswith('org.telegram.'):
                            classes.add(name)

    return classes


class SdkReleaseRulesTest(unittest.TestCase):
    def test_python_java_imports_keep_names_and_members(self):
        patterns = re.findall(r'^-keep class (\S+) \{ \*; \}', RULES.read_text(), re.M)
        expressions = []

        for pattern in patterns:
            expression = re.escape(pattern).replace(r'\*\*', 'DOUBLE_STAR')
            expression = expression.replace(r'\*', '[^.]*').replace('DOUBLE_STAR', '.*')
            expressions.append(re.compile(expression))

        classes = java_imports()
        self.assertTrue(classes)

        for name in sorted(classes):
            with self.subTest(java_class=name):
                self.assertTrue(any(expression.fullmatch(name) for expression in expressions), name)
