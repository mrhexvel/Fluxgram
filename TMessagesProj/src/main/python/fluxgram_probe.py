import sys


def hello(app_name):
    version = sys.version.split()[0]
    return f"Hello, {app_name}! Python {version} is running"